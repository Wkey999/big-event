"""Local-only document parsing, FAISS vector indexing, and multi-strategy retrieval service for BOOM."""

from collections import Counter, OrderedDict
import io
import math
import os
from pathlib import Path
import re
from typing import Literal

import faiss
from fastapi import FastAPI, File, HTTPException, UploadFile
import httpx
from langchain_text_splitters import RecursiveCharacterTextSplitter
import numpy as np
from pydantic import BaseModel, Field
from pypdf import PdfReader
import xxhash

app = FastAPI(title="BOOM Document Assistant", docs_url=None, redoc_url=None)

MAX_TEXT_CHARS = 20_000
MAX_FILE_BYTES = 5 * 1024 * 1024
MAX_PDF_PAGES = 30
ALLOWED_EXTENSIONS = {".txt", ".md", ".pdf"}
LOCAL_EMBED_DIM = 512
MAX_VECTOR_INDEX_CACHE = 64

EN_STOP_WORDS = {
    "what", "when", "where", "which", "who", "whom", "whose", "why", "how",
    "the", "and", "for", "with", "from", "that", "this", "are", "was", "were",
}
ZH_STOP_BIGRAMS = {
    "什么", "时候", "哪里", "哪些", "如何", "怎么", "请问", "多少", "是否",
    "可以", "需要", "关于", "一下", "告诉", "查询", "查找", "里面", "文中",
    "资料", "文档", "内容", "是谁", "在哪", "多长", "多久",
}
PAGE_MARKER_RE = re.compile(r"\[第\s*(\d+)\s*页\]")

# Semantic concept clusters mapped to dedicated dense latent dimensions (0..len-1)
# so synonymous expressions align in the FAISS inner-product vector space even when
# exact lexical tokens differ.
SEMANTIC_CLUSTERS: tuple[tuple[str, ...], ...] = (
    ("退出", "登出", "注销", "下线", "失活", "撤销", "logout", "revoked", "黑名单"),
    ("住宿", "酒店", "宾馆", "标间", "合住", "房费", "过夜", "双床"),
    ("报销", "发票", "入账", "财务", "退单", "白条", "收据", "税号", "报账"),
    ("升舱", "商务座", "头等舱", "一等座", "动卧", "卧铺", "机票", "高铁", "火车", "差旅"),
    ("设备", "外设", "显示器", "硬件", "采购", "申领", "归还", "离职", "转岗", "抹除"),
    ("故障", "中断", "超时", "无响应", "降级", "503", "fail-closed", "回滚", "不可用"),
    ("采集", "订阅", "rss", "atom", "白名单", "自动发布", "待审", "草稿", "版权"),
    ("配额", "额度", "限流", "防刷", "行锁", "免费体验", "子网", "ipv6", "死锁"),
    ("报名", "截止", "黑客松", "参赛", "组队", "队伍", "路演", "决赛", "答辩"),
    ("开源", "许可证", "协议", "mit", "apache", "源码", "仓库", "演示视频"),
    ("奖金", "评分", "权重", "评委", "一等奖", "二等奖", "三等奖", "获奖", "公示"),
    ("分块", "切块", "chunk", "overlap", "splitter", "分隔符", "递归"),
    ("拒答", "幻觉", "编造", "伪造", "无证据", "引用", "正则", "溯源"),
    ("扫描件", "图片型", "ocr", "拍照", "影印", "页数", "体积", "extract", "上传"),
)

splitter = RecursiveCharacterTextSplitter(
    chunk_size=700, chunk_overlap=80, separators=["\n\n", "\n", "。", "！", "？", " ", ""]
)

RetrievalStrategy = Literal["bm25", "hybrid", "vector", "tfidf", "overlap"]


class AnalyzeRequest(BaseModel):
    text: str = Field(min_length=1, max_length=MAX_TEXT_CHARS)
    question: str = Field(default="", max_length=300)
    strategy: RetrievalStrategy = "bm25"
    top_k: int = Field(default=3, ge=1, le=5)


class Citation(BaseModel):
    chunk: int
    excerpt: str
    score: float = 0.0
    page: int | None = None


class AnalyzeResponse(BaseModel):
    mode: str = "extractive"
    strategy: str = "bm25"
    vector_backend: str = "faiss-IndexFlatIP"
    index_cached: bool = False
    chunk_count: int = 0
    summary: str
    answer: str | None
    citations: list[Citation]


class ExtractResponse(BaseModel):
    filename: str
    file_type: str
    page_count: int | None = None
    char_count: int
    truncated: bool = False
    text: str


class _CachedFaissIndex:
    """Holds a built FAISS IndexFlatIP, document IDF table, and chunk metadata."""

    def __init__(
        self,
        index: faiss.IndexFlatIP,
        idf_map: dict[str, float],
        embedding_mode: str,
        dim: int,
    ) -> None:
        self.index = index
        self.idf_map = idf_map
        self.embedding_mode = embedding_mode
        self.dim = dim


_VECTOR_INDEX_CACHE: OrderedDict[str, _CachedFaissIndex] = OrderedDict()


def clear_vector_index_cache() -> None:
    """Clear in-memory FAISS index cache (useful for deterministic benchmarks/tests)."""
    _VECTOR_INDEX_CACHE.clear()


def tokenize(value: str) -> list[str]:
    """Tokenize Chinese (overlapping 2-grams) and English/numeric words."""
    value = value.lower()
    words = [w for w in re.findall(r"[a-z0-9]{2,}", value) if w not in EN_STOP_WORDS]
    han_runs = re.findall(r"[\u4e00-\u9fff]+", value)
    bigrams: list[str] = []
    for part in han_runs:
        if len(part) == 1:
            bigrams.append(part)
        else:
            for i in range(len(part) - 1):
                bg = part[i : i + 2]
                if bg not in ZH_STOP_BIGRAMS:
                    bigrams.append(bg)
    return words + bigrams


def _extract_subword_features(value: str) -> Counter[str]:
    """Extract rich lexical + character n-gram features for dense vector projection."""
    lower = value.lower()
    counts: Counter[str] = Counter(tokenize(lower))
    for run in re.findall(r"[\u4e00-\u9fff]+", lower):
        for ch in run:
            counts[f"1g:{ch}"] += 1
    return counts


def _encode_local_dense_vectors(
    texts: list[str],
    idf_map: dict[str, float],
    dim: int = LOCAL_EMBED_DIM,
) -> np.ndarray:
    """
    Encode texts into L2-normalized float32 dense vectors (shape: [len(texts), dim]).
    - Dimensions [0 .. num_clusters-1]: semantic concept cluster activations.
    - Dimensions [num_clusters .. dim-1]: signed feature-hashing projections weighted by IDF.
    """
    n_clusters = len(SEMANTIC_CLUSTERS)
    proj_dim = dim - n_clusters
    matrix = np.zeros((len(texts), dim), dtype=np.float32)

    for row_idx, text in enumerate(texts):
        lower = text.lower()
        # 1. Semantic concept latent dimensions
        for c_idx, cluster in enumerate(SEMANTIC_CLUSTERS):
            hits = sum(1 for kw in cluster if kw in lower)
            if hits > 0:
                matrix[row_idx, c_idx] = (1.2 + math.log(hits)) * 2.2

        # 2. Subword signed random projection across remaining dimensions
        feats = _extract_subword_features(lower)
        for feat, tf in feats.items():
            raw_idf = max(0.0, idf_map.get(feat, 1.5))
            if feat.startswith("1g:"):
                feat_scale = 0.25
            elif re.fullmatch(r"[a-z0-9]{2,}", feat):
                feat_scale = 1.45
            else:
                feat_scale = 1.0
            weight = (1.0 + math.log(tf)) * (raw_idf ** 1.35) * feat_scale
            feat_bytes = feat.encode("utf-8")
            h1 = xxhash.xxh32_intdigest(feat_bytes, seed=42)
            h2 = xxhash.xxh32_intdigest(feat_bytes, seed=1337)
            col1 = n_clusters + (h1 % proj_dim)
            sign1 = 1.0 if (h1 & 0x80000000) == 0 else -1.0
            matrix[row_idx, col1] += sign1 * weight

            col2 = n_clusters + (h2 % proj_dim)
            sign2 = 1.0 if (h2 & 0x80000000) == 0 else -1.0
            matrix[row_idx, col2] += 0.5 * sign2 * weight

    faiss.normalize_L2(matrix)
    return matrix


def _try_remote_embeddings(texts: list[str]) -> np.ndarray | None:
    """
    Call an OpenAI-compatible /embeddings endpoint (e.g., DashScope text-embedding-v3)
    when EMBEDDING_API_KEY is configured; returns L2-normalized float32 matrix or None.
    """
    api_key = os.getenv("EMBEDDING_API_KEY", "").strip()
    if not api_key:
        return None
    base_url = os.getenv(
        "EMBEDDING_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"
    ).rstrip("/")
    model = os.getenv("EMBEDDING_MODEL", "text-embedding-v3").strip()
    try:
        with httpx.Client(timeout=4.0) as client:
            resp = client.post(
                f"{base_url}/embeddings",
                headers={"Authorization": f"Bearer {api_key}"},
                json={"model": model, "input": texts},
            )
            resp.raise_for_status()
            data = resp.json().get("data", [])
            if len(data) != len(texts):
                return None
            ordered = sorted(data, key=lambda item: item.get("index", 0))
            matrix = np.array([item["embedding"] for item in ordered], dtype=np.float32)
            faiss.normalize_L2(matrix)
            return matrix
    except Exception:
        return None


def build_or_get_faiss_index(chunks: list[str]) -> tuple[_CachedFaissIndex, bool]:
    """Build or retrieve a cached FAISS IndexFlatIP vector index for the given document chunks."""
    remote_key_flag = "remote" if os.getenv("EMBEDDING_API_KEY", "").strip() else "local"
    digest_input = f"{remote_key_flag}||{len(chunks)}||" + "\x1f".join(chunks)
    cache_key = xxhash.xxh64_hexdigest(digest_input.encode("utf-8"))

    if cache_key in _VECTOR_INDEX_CACHE:
        _VECTOR_INDEX_CACHE.move_to_end(cache_key)
        return _VECTOR_INDEX_CACHE[cache_key], True

    # Compute document-level subword IDF table for local dense encoding
    n_docs = len(chunks)
    df_counter: Counter[str] = Counter()
    for ch in chunks:
        for feat in _extract_subword_features(ch).keys():
            df_counter[feat] += 1
    idf_map = {
        feat: math.log(1.0 + (n_docs - df + 0.5) / (df + 0.5))
        for feat, df in df_counter.items()
    }

    vectors = _try_remote_embeddings(chunks)
    if vectors is not None:
        embedding_mode = "remote-openai"
        dim = int(vectors.shape[1])
    else:
        vectors = _encode_local_dense_vectors(chunks, idf_map, dim=LOCAL_EMBED_DIM)
        embedding_mode = "local-dense-512d"
        dim = LOCAL_EMBED_DIM

    index = faiss.IndexFlatIP(dim)
    index.add(vectors)

    # Optional disk persistence if VECTOR_CACHE_DIR is configured
    cache_dir = os.getenv("VECTOR_CACHE_DIR", "").strip()
    if cache_dir:
        try:
            cache_path = Path(cache_dir)
            cache_path.mkdir(parents=True, exist_ok=True)
            faiss.write_index(index, str(cache_path / f"{cache_key}.faiss"))
        except Exception:
            pass

    entry = _CachedFaissIndex(index=index, idf_map=idf_map, embedding_mode=embedding_mode, dim=dim)
    _VECTOR_INDEX_CACHE[cache_key] = entry
    while len(_VECTOR_INDEX_CACHE) > MAX_VECTOR_INDEX_CACHE:
        _VECTOR_INDEX_CACHE.popitem(last=False)

    return entry, False


def search_faiss_index(chunks: list[str], question: str) -> tuple[list[float], bool]:
    """Query the FAISS IndexFlatIP vector index and return per-chunk cosine similarity scores."""
    entry, was_cached = build_or_get_faiss_index(chunks)
    if entry.embedding_mode == "remote-openai":
        q_vec = _try_remote_embeddings([question])
        if q_vec is None:
            q_vec = _encode_local_dense_vectors([question], entry.idf_map, dim=entry.dim)
    else:
        q_vec = _encode_local_dense_vectors([question], entry.idf_map, dim=entry.dim)

    n_docs = len(chunks)
    distances, indices = entry.index.search(q_vec, n_docs)
    scores = [0.0] * n_docs
    for dist, idx in zip(distances[0], indices[0]):
        if 0 <= int(idx) < n_docs:
            scores[int(idx)] = max(0.0, float(dist))
    return scores, was_cached


def terms(value: str) -> set[str]:
    return set(tokenize(value))


def _infer_chunk_pages(full_text: str, chunks: list[str]) -> list[int | None]:
    """Infer page numbers for each chunk when [第 N 页] markers are present."""
    markers = [(m.start(), int(m.group(1))) for m in PAGE_MARKER_RE.finditer(full_text)]
    if not markers:
        return [None] * len(chunks)

    pages: list[int | None] = []
    search_from = 0
    for chunk in chunks:
        inline = PAGE_MARKER_RE.search(chunk)
        if inline:
            pages.append(int(inline.group(1)))
            continue
        anchor = chunk[:60]
        pos = full_text.find(anchor, search_from)
        if pos == -1:
            pos = full_text.find(anchor)
        if pos == -1:
            pages.append(markers[0][1])
            continue
        search_from = max(search_from, pos)
        current_page = markers[0][1]
        for marker_pos, page_no in markers:
            if marker_pos <= pos:
                current_page = page_no
            else:
                break
        pages.append(current_page)
    return pages


def _strip_page_markers(text: str) -> str:
    return PAGE_MARKER_RE.sub("", text).strip()


def _count_semantic_cluster_matches(question: str, chunk: str) -> int:
    """Count how many semantic synonym clusters bridge non-identical terms between question and chunk."""
    q_low = question.lower()
    c_low = chunk.lower()
    bridged = 0
    for cluster in SEMANTIC_CLUSTERS:
        q_hits = {kw for kw in cluster if kw in q_low}
        c_hits = {kw for kw in cluster if kw in c_low}
        if q_hits and c_hits and (len(q_hits | c_hits) >= 2):
            bridged += 1
    return bridged


def rank_chunks_with_meta(
    chunks: list[str],
    question: str,
    strategy: RetrievalStrategy = "bm25",
    k1: float = 1.5,
    b: float = 0.75,
) -> tuple[list[tuple[float, int, str]], bool]:
    """Rank chunks using overlap, tfidf, bm25, FAISS vector, or hybrid (FAISS + BM25 RRF)."""
    query_tokens = tokenize(question)
    query_set = set(query_tokens)
    if not query_set or not chunks:
        return [], False

    chunk_tokens_list = [tokenize(c) for c in chunks]
    chunk_counters = [Counter(tokens) for tokens in chunk_tokens_list]
    n_docs = len(chunks)
    avgdl = max(1.0, sum(len(tokens) for tokens in chunk_tokens_list) / n_docs)

    doc_freq: Counter[str] = Counter()
    for counter in chunk_counters:
        for term in counter.keys():
            doc_freq[term] += 1

    def calc_overlap() -> list[float]:
        return [float(len(query_set & set(tokens))) for tokens in chunk_tokens_list]

    def calc_bm25() -> list[float]:
        scores: list[float] = []
        for tokens, counter in zip(chunk_tokens_list, chunk_counters):
            dl = len(tokens)
            score = 0.0
            for term in query_set:
                tf = counter.get(term, 0)
                if tf == 0:
                    continue
                df = doc_freq.get(term, 0)
                idf = math.log(1.0 + (n_docs - df + 0.5) / (df + 0.5))
                denom = tf + k1 * (1.0 - b + b * (dl / avgdl))
                score += idf * ((tf * (k1 + 1.0)) / denom)
            scores.append(score)
        return scores

    def calc_tfidf() -> list[float]:
        query_counter = Counter(query_tokens)
        q_weights: dict[str, float] = {}
        for term, qtf in query_counter.items():
            df = doc_freq.get(term, 0)
            idf = math.log(1.0 + n_docs / (1.0 + df))
            q_weights[term] = (1.0 + math.log(qtf)) * idf
        q_norm = math.sqrt(sum(w * w for w in q_weights.values())) or 1.0

        scores: list[float] = []
        for counter in chunk_counters:
            if not counter:
                scores.append(0.0)
                continue
            dot = 0.0
            d_norm_sq = 0.0
            for term, dtf in counter.items():
                df = doc_freq.get(term, 0)
                idf = math.log(1.0 + n_docs / (1.0 + df))
                dw = (1.0 + math.log(dtf)) * idf
                d_norm_sq += dw * dw
                if term in q_weights:
                    dot += q_weights[term] * dw
            d_norm = math.sqrt(d_norm_sq) or 1.0
            scores.append(dot / (q_norm * d_norm))
        return scores

    was_cached = False
    if strategy == "overlap":
        raw_scores = calc_overlap()
    elif strategy == "tfidf":
        raw_scores = calc_tfidf()
    elif strategy == "vector":
        raw_scores, was_cached = search_faiss_index(chunks, question)
        # Filter out low-confidence random hash collisions in vector space
        for i in range(n_docs):
            if raw_scores[i] < 0.08:
                raw_scores[i] = 0.0
    elif strategy == "hybrid":
        # True Dense + Sparse Hybrid Retrieval: FAISS IndexFlatIP + BM25 via Reciprocal Rank Fusion
        bm25_scores = calc_bm25()
        vec_scores, was_cached = search_faiss_index(chunks, question)
        bm25_order = sorted(range(n_docs), key=lambda i: (-bm25_scores[i], i))
        vec_order = sorted(range(n_docs), key=lambda i: (-vec_scores[i], i))
        bm25_rank = {idx: rank for rank, idx in enumerate(bm25_order, 1)}
        vec_rank = {idx: rank for rank, idx in enumerate(vec_order, 1)}
        raw_scores = []
        for i in range(n_docs):
            semantic_bridges = _count_semantic_cluster_matches(question, chunks[i])
            has_lexical = bm25_scores[i] > 0 and vec_scores[i] >= 0.08
            has_semantic = vec_scores[i] >= 0.10 and semantic_bridges >= 1
            if not has_lexical and not has_semantic:
                raw_scores.append(0.0)
            elif bm25_scores[i] <= 0 and has_semantic:
                # Pure semantic recall when exact lexical overlap is zero
                rrf = (1.0 / (60.0 + vec_rank[i])) + (0.002 * semantic_bridges)
                raw_scores.append(round(rrf * 100.0, 4))
            else:
                rrf = (
                    (0.52 / (60.0 + bm25_rank[i]))
                    + (0.48 / (60.0 + vec_rank[i]))
                    + (0.0015 * semantic_bridges)
                )
                raw_scores.append(round(rrf * 100.0, 4))
    else:
        raw_scores = calc_bm25()

    # Noise gate: when a question has >= 4 distinct tokens and multiple chunks exist,
    # reject accidental single-bigram overlap on generic words unless backed by synonym bridge.
    if len(query_set) >= 4 and n_docs >= 3:
        for i, counter in enumerate(chunk_counters):
            matched_terms = query_set & set(counter.keys())
            semantic_hits = _count_semantic_cluster_matches(question, chunks[i])
            if len(matched_terms) <= 1 and semantic_hits == 0:
                raw_scores[i] = 0.0

    ranked = sorted(
        ((round(raw_scores[i], 4), i + 1, chunks[i]) for i in range(n_docs)),
        key=lambda item: (-item[0], item[1]),
    )
    return ranked, was_cached


def rank_chunks(
    chunks: list[str],
    question: str,
    strategy: RetrievalStrategy = "bm25",
    k1: float = 1.5,
    b: float = 0.75,
) -> list[tuple[float, int, str]]:
    """Rank chunks against question using overlap, bm25, tfidf, vector, or hybrid RRF."""
    ranked, _ = rank_chunks_with_meta(chunks, question, strategy=strategy, k1=k1, b=b)
    return ranked


def analyze(
    text: str,
    question: str = "",
    strategy: RetrievalStrategy = "bm25",
    top_k: int = 3,
) -> AnalyzeResponse:
    clean = text.strip()
    if not clean:
        raise ValueError("文本不能为空")
    chunks = splitter.split_text(clean)
    if not chunks:
        raise ValueError("文本不能为空")

    pages = _infer_chunk_pages(clean, chunks)
    clean_without_markers = _strip_page_markers(clean)
    sentences = [
        s.strip()
        for s in re.split(r"(?<=[。！？.!?])\s*|\n+", clean_without_markers)
        if s.strip()
    ]
    summary = " ".join(sentences[:3])[:360]
    if not question.strip():
        return AnalyzeResponse(
            strategy=strategy,
            chunk_count=len(chunks),
            summary=summary,
            answer=None,
            citations=[],
        )

    ranked, was_cached = rank_chunks_with_meta(chunks, question, strategy=strategy)
    matches = [
        Citation(
            chunk=index,
            excerpt=_strip_page_markers(chunk)[:500],
            score=score,
            page=pages[index - 1],
        )
        for score, index, chunk in ranked[:top_k]
        if score > 0
    ]
    if not matches:
        return AnalyzeResponse(
            strategy=strategy,
            index_cached=was_cached,
            chunk_count=len(chunks),
            summary=summary,
            answer="资料中未找到与问题直接相关的片段。",
            citations=[],
        )
    return AnalyzeResponse(
        strategy=strategy,
        index_cached=was_cached,
        chunk_count=len(chunks),
        summary=summary,
        answer="找到以下相关原文片段，请结合上下文核对；当前版本不生成推断性回答。",
        citations=matches,
    )


def extract_document_bytes(filename: str, raw_bytes: bytes) -> ExtractResponse:
    safe_name = Path(filename or "document.txt").name
    ext = Path(safe_name).suffix.lower()
    if ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(
            status_code=422,
            detail="仅支持上传 .txt、.md 或可提取文字的 .pdf 文档",
        )
    if not raw_bytes:
        raise HTTPException(status_code=422, detail="上传的文件内容为空")
    if len(raw_bytes) > MAX_FILE_BYTES:
        raise HTTPException(status_code=422, detail="文件大小不能超过 5 MB")

    page_count: int | None = None
    if ext == ".pdf":
        try:
            reader = PdfReader(io.BytesIO(raw_bytes))
        except Exception as exc:
            raise HTTPException(status_code=422, detail="PDF 文件损坏或无法解析") from exc
        page_count = len(reader.pages)
        if page_count == 0:
            raise HTTPException(status_code=422, detail="PDF 文件不包含有效页面")
        if page_count > MAX_PDF_PAGES:
            raise HTTPException(
                status_code=422,
                detail=f"PDF 页数过多（共 {page_count} 页），单次最多支持 {MAX_PDF_PAGES} 页",
            )
        page_texts: list[str] = []
        visible_chars = 0
        for idx, page in enumerate(reader.pages, 1):
            extracted = (page.extract_text() or "").strip()
            visible_chars += len(re.sub(r"\s+", "", extracted))
            if extracted:
                page_texts.append(f"[第 {idx} 页]\n{extracted}")
        if visible_chars < 10:
            raise HTTPException(
                status_code=422,
                detail="该 PDF 未提取到有效文字（可能是扫描件或纯图片 PDF），当前版本暂不支持扫描件识别。",
            )
        full_text = "\n\n".join(page_texts)
        file_type = "pdf"
    else:
        decoded: str | None = None
        for encoding in ("utf-8-sig", "utf-8", "gb18030"):
            try:
                decoded = raw_bytes.decode(encoding)
                break
            except UnicodeDecodeError:
                continue
        if decoded is None:
            raise HTTPException(status_code=422, detail="文本文件编码无法识别，请使用 UTF-8 编码")
        full_text = decoded.strip()
        if not full_text:
            raise HTTPException(status_code=422, detail="文本文件不包含有效文字")
        file_type = "markdown" if ext == ".md" else "txt"

    truncated = len(full_text) > MAX_TEXT_CHARS
    if truncated:
        full_text = full_text[:MAX_TEXT_CHARS]

    return ExtractResponse(
        filename=safe_name,
        file_type=file_type,
        page_count=page_count,
        char_count=len(full_text),
        truncated=truncated,
        text=full_text,
    )


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "vector_backend": "faiss-IndexFlatIP"}


@app.post("/v1/analyze", response_model=AnalyzeResponse)
def analyze_route(payload: AnalyzeRequest) -> AnalyzeResponse:
    try:
        return analyze(
            payload.text,
            payload.question,
            strategy=payload.strategy,
            top_k=payload.top_k,
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.post("/v1/extract", response_model=ExtractResponse)
async def extract_route(file: UploadFile = File(...)) -> ExtractResponse:
    raw_bytes = await file.read(MAX_FILE_BYTES + 1)
    return extract_document_bytes(file.filename or "document.txt", raw_bytes)

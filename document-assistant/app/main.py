"""Local-only document parsing and multi-strategy retrieval service for BOOM."""

from collections import Counter
import io
import math
from pathlib import Path
import re
from typing import Literal

from fastapi import FastAPI, File, HTTPException, UploadFile
from langchain_text_splitters import RecursiveCharacterTextSplitter
from pydantic import BaseModel, Field
from pypdf import PdfReader

app = FastAPI(title="BOOM Document Assistant", docs_url=None, redoc_url=None)

MAX_TEXT_CHARS = 20_000
MAX_FILE_BYTES = 5 * 1024 * 1024
MAX_PDF_PAGES = 30
ALLOWED_EXTENSIONS = {".txt", ".md", ".pdf"}

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

splitter = RecursiveCharacterTextSplitter(
    chunk_size=700, chunk_overlap=80, separators=["\n\n", "\n", "。", "！", "？", " ", ""]
)

RetrievalStrategy = Literal["bm25", "hybrid", "tfidf", "overlap"]


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


def rank_chunks(
    chunks: list[str],
    question: str,
    strategy: RetrievalStrategy = "bm25",
    k1: float = 1.5,
    b: float = 0.75,
) -> list[tuple[float, int, str]]:
    """Rank chunks against question using overlap, bm25, tfidf, or hybrid RRF."""
    query_tokens = tokenize(question)
    query_set = set(query_tokens)
    if not query_set or not chunks:
        return []

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

    if strategy == "overlap":
        raw_scores = calc_overlap()
    elif strategy == "tfidf":
        raw_scores = calc_tfidf()
    elif strategy == "hybrid":
        bm25_scores = calc_bm25()
        tfidf_scores = calc_tfidf()
        bm25_order = sorted(range(n_docs), key=lambda i: (-bm25_scores[i], i))
        tfidf_order = sorted(range(n_docs), key=lambda i: (-tfidf_scores[i], i))
        bm25_rank = {idx: rank for rank, idx in enumerate(bm25_order, 1)}
        tfidf_rank = {idx: rank for rank, idx in enumerate(tfidf_order, 1)}
        raw_scores = []
        for i in range(n_docs):
            if bm25_scores[i] <= 0 or tfidf_scores[i] < 0.03:
                raw_scores.append(0.0)
            else:
                rrf = (1.0 / (60.0 + bm25_rank[i])) + (1.0 / (60.0 + tfidf_rank[i]))
                raw_scores.append(round(rrf * 100.0, 4))
    else:
        raw_scores = calc_bm25()

    # Noise gate: when a question has >= 4 distinct tokens and multiple chunks exist,
    # reject accidental single-bigram overlap on generic words.
    if len(query_set) >= 4 and n_docs >= 3:
        for i, counter in enumerate(chunk_counters):
            matched_terms = query_set & set(counter.keys())
            if len(matched_terms) <= 1:
                raw_scores[i] = 0.0

    return sorted(
        ((round(raw_scores[i], 4), i + 1, chunks[i]) for i in range(n_docs)),
        key=lambda item: (-item[0], item[1]),
    )


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

    ranked = rank_chunks(chunks, question, strategy=strategy)
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
            chunk_count=len(chunks),
            summary=summary,
            answer="资料中未找到与问题直接相关的片段。",
            citations=[],
        )
    return AnalyzeResponse(
        strategy=strategy,
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
    return {"status": "ok"}


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

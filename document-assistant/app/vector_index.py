"""Configuration-bound FAISS indexes. No silent changes of embedding space."""

from collections import OrderedDict
from dataclasses import dataclass, field
import hashlib
import json
import logging
import os
from pathlib import Path
import re
import struct
import tempfile
import threading
import time
from typing import Callable

import faiss
import httpx
import numpy as np

logger = logging.getLogger(__name__)
MAGIC = b"BOOMVEC2\n"
MAX_CACHE_BYTES = 16 * 1024 * 1024
MAX_DIMENSION = 8192
MAX_DISK_INDEXES = 64
REMOTE_BUDGET_SECONDS = 4.0
UNAVAILABLE = "向量服务暂时不可用，请稍后重试，或改用 BM25 检索。"
LocalEncoder = Callable[[list[str], dict[str, float], int], np.ndarray]


class EmbeddingUnavailable(Exception):
    """A safe error: never includes a provider response, URL, or credential."""


@dataclass(frozen=True)
class EmbeddingConfig:
    encoder_revision: str
    local_dimension: int = 512
    base_url: str = ""
    model: str = ""
    api_key: str = field(default="", repr=False)
    batch_size: int = 10

    @property
    def remote(self) -> bool:
        return bool(self.api_key)

    @property
    def mode(self) -> str:
        return "remote-openai" if self.remote else f"local-feature-hash-{self.local_dimension}d"

    @property
    def identity(self) -> str:
        # Fingerprint credentials for account isolation; never persist the credentials.
        value = [2, self.encoder_revision, self.local_dimension, self.mode,
                 self.base_url, self.model, hashlib.sha256(self.api_key.encode()).hexdigest()]
        return hashlib.sha256(json.dumps(value, ensure_ascii=False).encode()).hexdigest()

    @classmethod
    def from_env(cls, revision: str, dimension: int = 512) -> "EmbeddingConfig":
        key = os.getenv("EMBEDDING_API_KEY", "").strip()
        try:
            batch_size = int(os.getenv("EMBEDDING_BATCH_SIZE", "10"))
            if not 1 <= batch_size <= 10:
                raise ValueError()
        except ValueError:
            raise EmbeddingUnavailable("向量分批配置无效，请将 EMBEDDING_BATCH_SIZE 设为 1 到 10。") from None
        return cls(
            revision, dimension,
            os.getenv("EMBEDDING_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1").strip().rstrip("/") if key else "",
            os.getenv("EMBEDDING_MODEL", "text-embedding-v4").strip() if key else "",
            key,
            batch_size,
        )


def remote_embeddings(texts: list[str], config: EmbeddingConfig) -> np.ndarray:
    """Batch within provider limits; only return a complete, consistent matrix."""
    if not texts or not 1 <= config.batch_size <= 10:
        raise EmbeddingUnavailable(UNAVAILABLE)
    try:
        deadline = time.monotonic() + REMOTE_BUDGET_SECONDS
        matrices = []
        with httpx.Client(timeout=httpx.Timeout(4.0, connect=1.0), follow_redirects=False) as client:
            for offset in range(0, len(texts), config.batch_size):
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise EmbeddingUnavailable(UNAVAILABLE)
                batch = texts[offset:offset + config.batch_size]
                response = client.post(
                    f"{config.base_url}/embeddings",
                    headers={"Authorization": f"Bearer {config.api_key}"},
                    json={"model": config.model, "input": batch},
                    timeout=httpx.Timeout(remaining, connect=min(1.0, remaining)),
                )
                response.raise_for_status()
                if time.monotonic() > deadline:
                    raise EmbeddingUnavailable(UNAVAILABLE)
                matrix = _validated_vectors(response.json()["data"], len(batch))
                if matrices and matrix.shape[1] != matrices[0].shape[1]:
                    raise ValueError("batch dimensions changed")
                matrices.append(matrix)
        return np.concatenate(matrices, axis=0)
    except (httpx.HTTPError, ValueError, KeyError, TypeError, OverflowError):
        raise EmbeddingUnavailable(UNAVAILABLE) from None


def _validated_vectors(data: object, count: int) -> np.ndarray:
    if not isinstance(data, list) or len(data) != count:
        raise ValueError("invalid vector count")
    indices = [item["index"] for item in data]
    if any(type(i) is not int for i in indices) or sorted(indices) != list(range(count)):
        raise ValueError("invalid vector order")
    ordered = sorted(data, key=lambda item: item["index"])
    raw_vectors = np.asarray([item["embedding"] for item in ordered])
    if raw_vectors.dtype.kind not in "if":
        raise ValueError("vectors must contain numbers")
    vectors = np.asarray(raw_vectors, dtype=np.float32)
    if (vectors.ndim != 2 or not 1 <= vectors.shape[1] <= MAX_DIMENSION
            or not np.isfinite(vectors).all()):
        raise ValueError("invalid vector values")
    norms = np.linalg.norm(vectors.astype(np.float64), axis=1)
    if np.any(norms < 1e-12):
        raise ValueError("zero vectors")
    return np.asarray(vectors.astype(np.float64) / norms[:, None], dtype=np.float32)


@dataclass
class CachedIndex:
    index: faiss.IndexFlatIP
    idf_map: dict[str, float]
    config: EmbeddingConfig = field(repr=False)

    @property
    def embedding_mode(self) -> str:
        return self.config.mode

    @property
    def dim(self) -> int:
        return self.index.d


class VectorIndexStore:
    """Bounded per-process LRU and optional atomic disk cache in a private directory.

    Builds are serialized in this process; searches run outside the build lock.
    Disk files are disposable indexes, not a persistent document knowledge base.
    """

    def __init__(self, capacity: int = 64):
        self.capacity = capacity
        self._cache: OrderedDict[str, CachedIndex] = OrderedDict()
        self._lock = threading.RLock()

    def clear(self) -> None:
        with self._lock:
            self._cache.clear()

    @staticmethod
    def cache_key(chunks: list[str], config: EmbeddingConfig) -> str:
        payload = json.dumps([config.identity, chunks], ensure_ascii=False, separators=(",", ":"))
        return hashlib.sha256(payload.encode()).hexdigest()

    def get_or_build(self, chunks: list[str], idf_map: dict[str, float], config: EmbeddingConfig,
                     encode_local: LocalEncoder) -> tuple[CachedIndex, bool]:
        key = self.cache_key(chunks, config)
        with self._lock:
            if key in self._cache:
                self._cache.move_to_end(key)
                return self._cache[key], True
            cached = self._read_disk(key, len(chunks), idf_map, config)
            was_cached = cached is not None
            if cached is None:
                vectors = (remote_embeddings(chunks, config) if config.remote
                           else encode_local(chunks, idf_map, config.local_dimension))
                index = faiss.IndexFlatIP(vectors.shape[1])
                index.add(vectors)
                cached = CachedIndex(index, idf_map, config)
                self._write_disk(key, cached)
            self._cache[key] = cached
            while len(self._cache) > self.capacity:
                self._cache.popitem(last=False)
            return cached, was_cached

    def search(self, entry: CachedIndex, question: str, encode_local: LocalEncoder) -> list[float]:
        # Use the immutable config captured when the document was encoded.
        query = (remote_embeddings([question], entry.config) if entry.config.remote
                 else encode_local([question], entry.idf_map, entry.dim))
        if query.shape != (1, entry.dim):
            raise EmbeddingUnavailable(UNAVAILABLE)
        distances, indices = entry.index.search(query, entry.index.ntotal)
        scores = [0.0] * entry.index.ntotal
        for distance, idx in zip(distances[0], indices[0]):
            if 0 <= int(idx) < len(scores):
                scores[int(idx)] = max(0.0, float(distance))
        return scores

    @staticmethod
    def _directory() -> Path | None:
        value = os.getenv("VECTOR_CACHE_DIR", "").strip()
        return Path(value) if value else None

    def _read_disk(self, key: str, count: int, idf_map: dict[str, float],
                   config: EmbeddingConfig) -> CachedIndex | None:
        directory = self._directory()
        if directory is None:
            return None
        try:
            path = directory / f"{key}.boomidx"
            # Only load files created by this service in a trusted, private directory.
            if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_CACHE_BYTES:
                return None
            raw = path.read_bytes()
            offset = len(MAGIC) + 4
            if raw[:len(MAGIC)] != MAGIC or len(raw) < offset:
                return None
            header_size = struct.unpack(">I", raw[len(MAGIC):offset])[0]
            if not 0 < header_size <= 4096 or offset + header_size >= len(raw):
                return None
            meta = json.loads(raw[offset:offset + header_size])
            payload = raw[offset + header_size:]
            if (meta["version"] != 2 or meta["key"] != key or meta["identity"] != config.identity
                    or meta["count"] != count or type(meta["dimension"]) is not int
                    or not 1 <= meta["dimension"] <= MAX_DIMENSION
                    or hashlib.sha256(payload).hexdigest() != meta["checksum"]):
                return None
            # Store only a checked float32 matrix, not an executable/pickled object
            # or an opaque FAISS serialization. Rebuild the flat index without embeddings.
            if len(payload) != count * meta["dimension"] * 4:
                return None
            vectors = np.ascontiguousarray(np.frombuffer(payload, dtype="<f4")
                                           .reshape(count, meta["dimension"]), dtype=np.float32)
            if not np.isfinite(vectors).all():
                return None
            index = faiss.IndexFlatIP(meta["dimension"])
            index.add(vectors)
            return CachedIndex(index, idf_map, config)
        except (OSError, ValueError, TypeError, KeyError, RuntimeError, struct.error):
            logger.warning("Ignoring an invalid vector index cache")
            return None

    def _write_disk(self, key: str, entry: CachedIndex) -> None:
        directory = self._directory()
        if directory is None:
            return
        temp_path = None
        try:
            directory.mkdir(parents=True, exist_ok=True, mode=0o700)
            payload = np.asarray(entry.index.reconstruct_n(0, entry.index.ntotal), dtype="<f4").tobytes()
            meta = json.dumps({"version": 2, "key": key, "identity": entry.config.identity,
                               "dimension": entry.dim, "count": entry.index.ntotal,
                               "checksum": hashlib.sha256(payload).hexdigest()}, sort_keys=True).encode()
            if len(payload) + len(meta) + len(MAGIC) + 4 > MAX_CACHE_BYTES:
                return
            with tempfile.NamedTemporaryFile(dir=directory, prefix=".writing-", delete=False) as output:
                temp_path = Path(output.name)
                output.write(MAGIC + struct.pack(">I", len(meta)) + meta + payload)
                output.flush()
                os.fsync(output.fileno())
            os.replace(temp_path, directory / f"{key}.boomidx")
            temp_path = None
            indexes = [p for p in directory.glob("*.boomidx")
                       if re.fullmatch(r"[0-9a-f]{64}\.boomidx", p.name) and not p.is_symlink()]
            indexes.sort(key=lambda p: p.stat().st_mtime, reverse=True)
            for path in indexes[MAX_DISK_INDEXES:]:
                path.unlink(missing_ok=True)
        except OSError:
            logger.warning("Vector disk cache unavailable; using process memory")
        finally:
            if temp_path is not None:
                try:
                    temp_path.unlink(missing_ok=True)
                except OSError:
                    pass

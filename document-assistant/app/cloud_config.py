"""Read a limited local configuration format without executing shell commands."""

import math
from pathlib import Path
import re
from urllib.parse import urlsplit

ALLOWED = {
    "EMBEDDING_API_KEY", "EMBEDDING_BASE_URL", "EMBEDDING_MODEL", "EMBEDDING_BATCH_SIZE",
    "EMBEDDING_MIN_SCORE", "VECTOR_CACHE_DIR",
}


class CloudConfigError(ValueError):
    pass


def cloud_environment(path: Path, parent: dict[str, str]) -> dict[str, str]:
    # Nonempty process settings override the local file; never mutate the caller's env.
    values = {
        "EMBEDDING_API_KEY": "",
        "EMBEDDING_BASE_URL": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "EMBEDDING_MODEL": "text-embedding-v4",
        "EMBEDDING_BATCH_SIZE": "10",
        "EMBEDDING_MIN_SCORE": "0.35",
        "VECTOR_CACHE_DIR": ".vector-cache",
    }
    try:
        lines = path.read_text(encoding="utf-8-sig").splitlines() if path.exists() else []
    except (OSError, UnicodeError):
        raise CloudConfigError("无法读取私有配置文件，请检查 .env.local 的编码和权限。") from None
    seen = set()
    for line_no, line in enumerate(lines, 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        key, value = key.strip(), value.strip()
        if not separator or key not in ALLOWED or key in seen:
            raise CloudConfigError(f"私有配置第 {line_no} 行格式错误或配置项重复。")
        if value.startswith(("'", '"')):
            if len(value) < 2 or value[-1] != value[0]:
                raise CloudConfigError(f"私有配置第 {line_no} 行引号不完整。")
            value = value[1:-1]
        values[key] = value
        seen.add(key)
    for key in ALLOWED:
        if parent.get(key, "").strip():
            values[key] = parent[key].strip()
    if not values["EMBEDDING_API_KEY"].strip():
        raise CloudConfigError("请先在 document-assistant/.env.local 中填写 EMBEDDING_API_KEY，然后重新执行。")
    try:
        uri = urlsplit(values["EMBEDDING_BASE_URL"])
        if (uri.scheme != "https" or not uri.hostname or uri.username or uri.password or uri.query or uri.fragment):
            raise ValueError()
        if not re.fullmatch(r"[\w./:-]{1,128}", values["EMBEDDING_MODEL"]):
            raise ValueError()
        batch = int(values["EMBEDDING_BATCH_SIZE"])
        score = float(values["EMBEDDING_MIN_SCORE"])
        if not 1 <= batch <= 10 or not math.isfinite(score) or not 0 <= score <= 1:
            raise ValueError()
    except ValueError:
        raise CloudConfigError("请检查云端地址（HTTPS）、模型名称、分批数量（1–10）和分数阈值（0–1）。") from None
    cache = values["VECTOR_CACHE_DIR"]
    if cache and not Path(cache).is_absolute():
        values["VECTOR_CACHE_DIR"] = str((path.parent / cache).resolve())
    return {**parent, **values}

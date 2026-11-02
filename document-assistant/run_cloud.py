"""Local cloud-embedding launcher; --check never makes a network request."""

import argparse
import os
from pathlib import Path
import subprocess
import sys

from app.cloud_config import CloudConfigError, cloud_environment

ROOT = Path(__file__).resolve().parent


def main() -> int:
    parser = argparse.ArgumentParser(description="BOOM cloud document assistant")
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument("--check", action="store_true", help="Validate local config without calling APIs")
    actions.add_argument("--probe", action="store_true", help="Call Embedding API once with two short synthetic texts (may be billed)")
    args = parser.parse_args()
    try:
        environment = cloud_environment(ROOT / ".env.local", dict(os.environ))
    except CloudConfigError as error:
        print(str(error), file=sys.stderr)
        return 1
    if args.check:
        print("配置格式检查通过；尚未验证 Key 权限、连通性或模型服务，没有调用 API。")
        print(f"模型：{environment['EMBEDDING_MODEL']}；每批最多 {environment['EMBEDDING_BATCH_SIZE']} 条。")
        return 0
    if args.probe:
        from app.vector_index import EmbeddingConfig, EmbeddingUnavailable, remote_embeddings
        config = EmbeddingConfig("cloud-probe-v1", base_url=environment["EMBEDDING_BASE_URL"],
                                 model=environment["EMBEDDING_MODEL"], api_key=environment["EMBEDDING_API_KEY"],
                                 batch_size=int(environment["EMBEDDING_BATCH_SIZE"]))
        try:
            matrix = remote_embeddings(["合成测试：活动将在周五开始。", "合成测试：开始日期是星期五。"], config)
        except EmbeddingUnavailable as error:
            print(str(error), file=sys.stderr)
            return 1
        print(f"云端连接成功：返回 {matrix.shape[0]} 条、每条 {matrix.shape[1]} 维向量。仅验证接口，不代表问答效果。")
        return 0
    print("启动云端文档助手；后续选择向量或混合检索可能产生 Embedding 费用。")
    return subprocess.run([sys.executable, "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1",
                           "--port", "8001"], cwd=ROOT, env=environment).returncode


if __name__ == "__main__":
    raise SystemExit(main())

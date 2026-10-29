# BOOM 文档助手：本地解析与多策略检索子服务

这是 BOOM 的 Python/FastAPI 本地子服务（监听 `127.0.0.1:8001`），负责长文本与文档（`.txt`、`.md`、可提取文字的 `.pdf`）解析、LangChain 递归文本切块（`RecursiveCharacterTextSplitter`）以及多策略片段检索（`BM25`、`Hybrid RRF`、`TF-IDF`、`2-gram Overlap`）。

Java 网关（`DocumentAssistantController`）统一负责登录鉴权、参数边界校验、AI 免费体验配额控制（`AiTrialQuotaService`）与可选的大模型有据生成（RAG）及 `[#chunk]` 引用正则核验。

## 核心能力与边界

1. **多策略检索与噪声门控（`POST /v1/analyze`）**
   - 支持 `strategy`: `bm25`（默认，Okapi BM25 稀疏加权）、`hybrid`（BM25 + TF-IDF 倒数秩融合 RRF）、`tfidf`（余弦相似度）、`overlap`（2-gram 词集重叠基线）。
   - 自动识别 `[第 N 页]` 页码标记并回填至 `citations[].page`。
   - 内置多词提问单字面碰撞噪声门控（Noise Gate），对仅偶然命中单个泛词的语料外提问直接返回空引用列表。
2. **文档解析与扫描件拦截（`POST /v1/extract`）**
   - 支持上传 `.txt`、`.md` 与包含文字层的 `.pdf`（上限 `5 MB`，PDF 最多 `30 页`，提取正文上限 `20,000 字符`）。
   - 若上传的 PDF 提取出的非空白有效字符少于 10 个，判定为扫描件或纯图片 PDF，直接返回 `HTTP 422` 明确提示暂不支持扫描件识别。
3. **Java 网关侧最小 RAG 闭环（`POST /document/analyze`）**
   - `mode=extractive`：仅返回原文摘要与检索片段，不调用外部大模型（0 Token 消耗）。
   - `mode=platform|custom`：先调用 Python 检索 Top-K 片段；**若无匹配片段则直接触发无证据拒答（`mode=rag-refused`），不调用大模型且不扣减免费体验额度**；若有匹配片段则注入严格受控 Prompt 生成回答，并通过正则校验剔除模型虚构的 `[#chunk]` 编号。

## 本地启动与测试

在 `document-assistant` 目录执行：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

运行单元测试：

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

运行毕业设计检索策略对比评测基准（生成 `docs/检索策略对比实验报告.md`）：

```powershell
.\.venv\Scripts\python.exe eval/evaluate_retrieval.py --write-report
```

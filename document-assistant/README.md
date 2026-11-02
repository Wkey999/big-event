# BOOM 文档助手：本地解析与多策略检索子服务

这是 BOOM 的 Python/FastAPI 本地子服务（监听 `127.0.0.1:8001`），负责长文本与文档（`.txt`、`.md`、可提取文字的 `.pdf`）解析、LangChain 递归文本切块（`RecursiveCharacterTextSplitter`）以及多策略片段检索（`BM25`、`Hybrid RRF`、`TF-IDF`、`2-gram Overlap`）。

Java 网关（`DocumentAssistantController`）统一负责登录鉴权、参数边界校验、AI 免费体验配额控制（`AiTrialQuotaService`）与可选的大模型有据生成（RAG）及 `[#chunk]` 引用正则核验。

## 核心能力与边界

1. **多策略检索、FAISS 向量索引与噪声门控（`POST /v1/analyze`）**
   - 支持 `strategy`: `hybrid`（FAISS + BM25 的 RRF 融合）、`vector`（FAISS `IndexFlatIP`）、`bm25`（默认）、`tfidf`、`overlap`。没有配置 Embedding Key 时，向量由手写词簇与特征哈希编码为 512 维；这是离线实验基线，不是预训练语义模型。FAISS 负责索引与检索，不负责生成 Embedding。
   - 配置 `EMBEDDING_API_KEY` 后，文档与问题固定使用同一远程地址、模型和凭据。远程超时、限额、返回异常或维度不一致时，`vector` / `hybrid` 返回 HTTP 503，提示改用 BM25；不再悄悄切换本地编码。Java 网关在检索成功后才可能扣减聊天额度；远程 Embedding 自身的费用仍按供应商计费。
   - 自动识别 `[第 N 页]` 页码标记并回填至 `citations[].page`。
   - 内置多词提问单字面碰撞噪声门控（Noise Gate），对仅偶然命中单个泛词的语料外提问直接返回空引用列表。
2. **文档解析与扫描件拦截（`POST /v1/extract`）**
   - 支持上传 `.txt`、`.md` 与包含文字层的 `.pdf`（上限 `5 MB`，PDF 最多 `30 页`，提取正文上限 `20,000 字符`）。
   - 若上传的 PDF 提取出的非空白有效字符少于 10 个，判定为扫描件或纯图片 PDF，直接返回 `HTTP 422` 明确提示暂不支持扫描件识别。
3. **Java 网关侧最小 RAG 闭环（`POST /document/analyze`）**
   - `mode=extractive`：仅返回原文摘要与检索片段，不调用聊天模型；若检索使用远程 Embedding，其向量请求仍可能计费。
   - `mode=platform|custom`：先调用 Python 检索 Top-K 片段；**若无匹配片段则直接触发无证据拒答（`mode=rag-refused`），不调用大模型且不扣减免费体验额度**；若有匹配片段则注入严格受控 Prompt 生成回答，并通过正则校验剔除模型虚构的 `[#chunk]` 编号。

## 本地启动与测试

在 `document-assistant` 目录执行：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

### 本机使用云端 Embedding

本轮选择百炼北京地域 `text-embedding-v4`。将 `.env.local.example` 复制为 `.env.local`，只需填写 `EMBEDDING_API_KEY`；该私有文件已被 Git 忽略。Key 必须来自与地址匹配的百炼地域，不能填 OSS AccessKey 或其他平台的聊天 Key。已有非空进程环境变量优先于文件；启动器不修改系统环境变量。

```powershell
# 在 document-assistant 目录执行；如果旧服务占用 8001，先在其终端 Ctrl+C 停止。
.\.venv\Scripts\python.exe run_cloud.py --check
# 可选：发送两句合成测试文本验证云端连接，会产生一次小量 Embedding 用量。
.\.venv\Scripts\python.exe run_cloud.py --probe
# 启动服务；.env.local 只由此启动器读取，直接 uvicorn 不会自动读取它。
.\.venv\Scripts\python.exe run_cloud.py
```

`--check` 仅检查配置格式，不联网，不证明 Key 权限或余额。`--probe` 成功只证明接口能返回合法向量，不证明语义检索或 RAG 回答质量。服务启动本身不调用 Embedding；页面选择向量/混合检索才调用。未填写 Key 时云端启动器会明确停止，不会假装已接云端或自动回退本地。

远程编码默认每批最多 10 条，逐批核对顺序、数量和维度，全部成功后才建索引；中途失败不保存半份索引、不自动重试。每次编码设置 4 秒预算，在批次边界检查剩余时间并将其用于 HTTP 超时；这不是可强制中断所有阻塞阶段的硬实时期限。长文在慢网络下仍可能超时，此时可重试或改用 BM25；部分成功请求也可能已被供应商计费。

运行单元测试：

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

运行毕业设计检索策略对比评测基准（生成 `docs/检索策略对比实验报告.md`）：

```powershell
.\.venv\Scripts\python.exe eval/evaluate_retrieval.py --write-report
```

评测脚本只运行离线基线，执行前需在当前进程清空 `EMBEDDING_API_KEY` 与 `VECTOR_CACHE_DIR`。使用和服务一致的 700/80 分块、正分片段返回规则，不再对无答案题额外设置更宽松的评测阈值。报告保留失败样例；30 题自建开发集的结果不能代表线上准确率或远程模型能力。

## 索引配置与恢复

| 环境变量 | 默认与作用 |
|---|---|
| `EMBEDDING_API_KEY` | 空：离线特征编码；非空：远程 Embedding。私有凭据，不提交仓库 |
| `EMBEDDING_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1`，调用其 `/embeddings` |
| `EMBEDDING_MODEL` | `text-embedding-v4`；实际维度以合法响应为准，最大 8192 |
| `EMBEDDING_BATCH_SIZE` | `10`，允许 1–10；减少单次发送的文本条数，不改变向量编码空间 |
| `EMBEDDING_MIN_SCORE` | 远程向量最低余弦分数，默认 `0.35`，范围 0–1；仅为原型起点，需独立数据集校准，不适用于所有模型 |
| `VECTOR_CACHE_DIR` | 空：仅进程内缓存；指定私有目录后启用磁盘缓存 |

本机可在启动 Python 服务前设置 `$env:VECTOR_CACHE_DIR = "$PWD\.vector-cache"`。索引键由文档分块、模型、地址、凭据指纹及编码版本生成 SHA-256 摘要；切换配置会建新索引。密钥原文、文档原文不会写进缓存元信息，但向量仍应作为私有数据保护。

远程检索不依赖离线手写同义词表：达到分数阈值的片段可以在无字面重叠时保留；混合检索按有效词法/向量通道进行 RRF 融合。离线模式保留原有词簇和噪声门控。阈值与词簇尚未通过独立保留集验证，不能把“有片段”当成“有充分证据”。

内存 LRU 最多 64 个索引。磁盘缓存使用 `.boomidx` 单文件保存校验信息和 float32 向量矩阵，以临时文件 + 原子替换写入；每次成功写入后只保留最近写入的 64 个同格式缓存。再次提交相同文档及相同配置时，可从磁盘恢复 FAISS 索引，无需重新编码文档。文件损坏、截断或不匹配则重建；磁盘不可写则使用内存。旧 `.faiss` 文件不会被加载，也不会自动删除。关闭服务后可删除专用缓存目录中的 `.boomidx` 文件清理数据。

这只是可重建的索引缓存：未提供用户文档列表、原文件持久化、版本管理或删除 API；重启后仍须由调用方提供同一文档。进程内串行建索引以避免重复编码，查询在建索引锁外进行；多进程不共享内存锁，可能重复编码。Python 接口会额外返回 `vector_backend` 与 `embedding_mode`，词法检索时两者为空；Java 当前只转发既有问答字段。

## 部署准备与当前边界

`deploy/ubuntu/boom-assistant.service` 只监听本机，使用专用状态目录缓存索引；可选私有环境文件为 `/etc/boom/assistant.env`，模板见 `deploy/ubuntu/assistant.env.example`。本轮没有部署服务器；用户授权的一次小量云端验证已成功返回 2 条 1024 维向量，尚未验收真实检索或生成质量。LangChain 当前用于递归文本切分；索引与检索由 FAISS 和项目代码实现，模型生成由 Spring AI 负责。

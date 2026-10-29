# 20 - 文档助手最小 RAG 升级、检索评测与前端体验简化交接（本地实现，尚未部署）

> 更新时间：2026-10-29
> 涉及仓库：
> - 后端与 Python 子服务：`D:\workplace\big-event`
> - 前端：`D:\workplace\VUE_CODE\big-event-web`（`main` 分支）

---

## 一、本次完成了什么（按模块划分）

### 1. Python 文档助手子服务升级（`D:\workplace\big-event\document-assistant`）

- **多策略分块检索（`POST /v1/analyze`）**
  - 文件：`document-assistant/app/main.py`
  - 保留原有 `RecursiveCharacterTextSplitter(chunk_size=700, chunk_overlap=80)` 切块与 `mode="extractive"` 默认兼容契约。
  - 新增 `strategy` 参数，支持 4 种检索排序策略动态切换：
    1. `bm25`（默认）：Okapi BM25（$k_1=1.5, b=0.75$）稀疏加权检索，压低高频泛词权重，突出稀有实体与专有名词；
    2. `hybrid`：BM25 与 TF-IDF 余弦相似度的倒数秩融合（RRF, $k=60$）；
    3. `tfidf`：TF-IDF 余弦相似度；
    4. `overlap`：2-gram 词集重叠计数（原始基线）。
  - 新增**多词提问单字面碰撞噪声门控（Noise Gate）**：当问题词项 $\ge 4$ 且文档包含多个片段时，过滤仅偶然命中 1 个通用 2-gram 的低相关片段，减少无答案问题误召回。
  - 新增 PDF 页码标记追踪：自动识别正文中的 `[第 N 页]` 标记并回填至 `citations[].page`，同时在返回的 `excerpt` 中剥离页码标签。

- **文档上传文本提取与扫描件 PDF 拦截（`POST /v1/extract`）**
  - 文件：`document-assistant/app/main.py`、`document-assistant/requirements.txt`
  - 新增依赖：`pypdf>=5.0,<6`、`python-multipart>=0.0.18,<1`。
  - 支持上传 `.txt`、`.md`（自动兼容 UTF-8 / GB18030 解码）与可提取文字的 `.pdf`。
  - 安全边界：单文件体积上限 `5 MB`、PDF 最大页数 `30 页`、提取后正文最大 `20,000 字符`（超出时标记 `truncated=true` 并截断）。
  - **扫描件拦截**：若上传的 PDF 页面提取出的非空白有效字符总数 `< 10`，直接返回 `HTTP 422`，提示“该 PDF 未提取到有效文字（可能是扫描件或纯图片 PDF），当前版本暂不支持扫描件识别”。

### 2. Java 网关侧最小 RAG 闭环与引用核验（`D:\workplace\big-event`）

- **文件**：`src/main/java/com/itheima/controller/DocumentAssistantController.java`
- **保留安全基线**：构造函数继续强制校验 Python 服务地址必须为回环 HTTP 地址（`http://127.0.0.1:<port>`），保留 `2s` 连接超时与 `10s` 读取超时；所有接口均受 `JwtInterceptor` 登录鉴权保护。
- **支持三种分析模式（`POST /document/analyze`）**：
  1. `mode = "extractive"`（默认）：仅调用本机 Python 分块检索，返回原文摘要与匹配片段，**零 Token 消耗**；
  2. `mode = "platform"`：复用现有 `AiSearchClientRouter` 与 `AiTrialQuotaService` 平台免费体验配额；
  3. `mode = "custom"`：使用用户在前端配置的自有大模型 API Key。
- **无证据前置拒答门控（防幻觉 + 省配额）**：
  - 在 `platform` / `custom` 模式下，Java 先向 Python 获取 Top-K 检索片段；
  - 若用户提出了问题但 Python 返回的 `citations` 为空，**Java 直接拦截并返回 `mode = "rag-refused"`，不调用外部大模型 API，也不扣减用户的每日免费体验额度**。
- **程序级引用编号正则核验（`verifyAndSanitizeCitations`）**：
  - 系统提示词要求模型仅依据提供的 `[#片段编号]` 回答并标注出处；
  - Java 收到模型输出后，通过正则 `\[#?(\d+)]` 提取所有引用编号，**自动剥离不在本次候选 `citations` 集合内的虚构编号**，并将真实被模型引用的片段置顶、回填至 `verified_citations` 数组；所有展示给前端的 `excerpt` 与 `page` 均来自 Python 确定性切块，不允许模型自行编造。
- **新增文件提取转发接口（`POST /document/extract`）**：
  - 接收前端 `MultipartFile`（校验非空、$\le 5\text{ MB}$、后缀为 `.txt/.md/.pdf`），转发至 Python `/v1/extract`，并将 Python 侧的 `422 detail` 错误信息转换为前端可读的 `BusinessException`。

### 3. 毕业设计检索策略对比评测基准（`document-assistant/eval`）

- **文件**：
  - 评测脚本：`document-assistant/eval/evaluate_retrieval.py`
  - 生成报告：`docs/检索策略对比实验报告.md`
- **内容**：构建了 14 条企业差旅、社区安全架构与赛事规范长文档（切分为 18 个高干扰 Chunks）及 **28 道结构化测试题**（精确事实 8 题、同义改写 8 题、跨句多条件 6 题、无答案陷阱 6 题），实测对比 `overlap`、`tfidf`、`bm25`、`hybrid` 的 `Hit@1`、`Recall@3`、`MRR@3`、`无答案拒答率` 与 `P50/P95 延迟`。

### 4. 前端升级与体验减负（`D:\workplace\VUE_CODE\big-event-web`）

- **文档助手 RAG 实验室页面（`src/views/DocumentAssistantView.vue`、`src/api/documentAssistant.js`）**
  - 新增 `.txt / .md / .pdf` 文件上传解析入口与文件元数据提示条；
  - 新增 3 组内置演示样例（黑客松通知、企业差旅规范、BOOM 安全与 RAG 架构），每组均提供「有据问答」与「无答案拒答测试」快捷填入按钮；
  - 新增检索策略（`BM25` / `Hybrid RRF` / `TF-IDF` / `Overlap`）与生成模式（`仅原文摘录基线` / `RAG 大模型有据生成`）切换，支持展示 PDF 页码、相关度得分及“模型回答引用了此片段（编号已核对）”标记。编号核对不等于事实核验，回答仍需对照原文。

- **文章发布工作台精简（`src/views/article/ArticleManageView.vue`、`src/components/ArticleRichEditor.vue`）**
  - 移除原先占地方的 `300px` 右侧边栏、大尺寸封面拖拽框、“一句话导语”输入框以及重复的“可见性（草稿/公开发布）”切换开关；
  - 改为 `820px` 单栏沉浸式弹窗：标题下方合并为单行「频道胶囊 + 可选封面按钮」，底栏直接提供「存为草稿」与「立即发布」两个按钮；
  - 富文本编辑器切换为 `mode="simple"` 单行工具栏，仅保留标题、加粗、斜体、引用、列表、代码块、链接、图片、分割线、撤销/重做。

- **首页「纯净流 / 文字流」双方案实时切换（`src/views/HomeView.vue`、`src/components/ArticleCard.vue`、`src/components/AiBriefingCard.vue`）**
  - 在首页信息流控制栏新增 **「纯净流 / 文字流」** 切换按钮（状态持久化至 `localStorage['boom:home-view-style']`）：
    - **纯净流（`pure`，默认）**：收起右侧口号边栏，顶部搜索条紧凑化，AI 周报折叠为单行胶囊吧台，卡片去掉灰底 3 行长摘要框与海报多余小字，突出视觉封面与标题；
    - **文字流（`text`）**：保留完整三栏布局、顶部 Hero 横幅、展开式双栏「今日必读 TOP 3 + AI 周报」面板及卡片内的 3 行「内容速览」摘要框。

---

## 二、已验证项与未验证边界（如实记录）

| 检查项 | 当前状态 | 验证命令 / 说明 |
| --- | --- | --- |
| Python 单元测试 | ✅ 已通过（6/6） | `.\.venv\Scripts\python.exe -m unittest discover -s tests -v` |
| 检索对比评测脚本 | ✅ 已通过并生成报告 | `.\.venv\Scripts\python.exe eval/evaluate_retrieval.py --write-report` |
| Java 控制器与网关测试 | ✅ 已通过（6/6） | `mvn test "-Dtest=DocumentAssistantControllerTest"` |
| 前端生产构建与分包检查 | ✅ 已通过（67 chunks） | `npm.cmd run build && npm.cmd run check:build` |
| 本地四服务联调启动 | ✅ 已拉起监听 | MySQL (`3306`)、Python (`8001`)、Java (`8080`)、Vite (`5173`) |
| 真实 Redis 会话撤销联调 | ⏳ 尚未验证 | 本机目前未安装/未运行 `redis-server`（`6379` 未监听），仅通过 Mock 单测 |
| 文档持久化与向量数据库 | ⏳ 尚未实现 | 当前文档每次随请求解析切块，不落盘存入 MySQL/OSS，未接入向量库 |
| 生产环境部署 | ⏳ 尚未部署 | 所有改动均在本地工作区，尚未 `git commit` 或发布至云主机 |

---

## 三、改动文件清单

### 后端仓库（`D:\workplace\big-event`）
- `document-assistant/requirements.txt`（新增 `pypdf`、`python-multipart`）
- `document-assistant/app/main.py`（BM25/TF-IDF/Hybrid RRF 检索、噪声门控、页码追踪、`/v1/extract` 文件提取与扫描件拦截）
- `document-assistant/tests/test_main.py`（新增多策略排序、页码追踪、TXT/扫描 PDF 提取测试）
- `document-assistant/eval/evaluate_retrieval.py`（新增 28 题检索对比评测脚本）
- `document-assistant/README.md`（同步最新接口与复现命令）
- `src/main/java/com/itheima/controller/DocumentAssistantController.java`（新增 RAG 生成路由、无证据前置拒答、引用编号正则核验、`/document/extract` 文件转发）
- `src/test/java/com/itheima/controller/DocumentAssistantControllerTest.java`（新增引用剥离、无证据拒答不扣配额、RAG 完整链路与扫描 PDF 异常转换单测）
- `docs/检索策略对比实验报告.md`（自动生成的评测报告）
- `docs/备忘录/20-文档助手RAG升级与前端体验优化交接.md`（本交接文档）

### 前端仓库（`D:\workplace\VUE_CODE\big-event-web`）
- `src/api/documentAssistant.js`（支持 `mode`、`strategy`、`aiConfig` 参数与 `/document/extract` 文件上传）
- `src/views/DocumentAssistantView.vue`（文件上传、内置正反例演示样例、检索策略与 RAG 模式切换、核验引用展示）
- `src/views/article/ArticleManageView.vue`（发布弹窗单栏极简化、直出草稿/发布双按钮）
- `src/components/ArticleRichEditor.vue`（精简为单行高频工具栏）
- `src/views/HomeView.vue`（新增「纯净流 / 文字流」双方案切换与持久化）
- `src/components/ArticleCard.vue`（支持 `variant="pure|text"` 双形态渲染）
- `src/components/AiBriefingCard.vue`（支持 `variant="pure|text"` 单行胶囊吧台与展开式 Bento 双形态）

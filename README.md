# BOOM！大事件 · 后端

一个内容社区的**服务端**：C 端浏览信息流 + B 端内容管理（文章/分类/个人设置）。基于黑马程序员《大事件》项目复刻，并在安全、校验、工程化上做了大量超出课程范围的自主改进。配套前端仓库：[Wkey999/big-event-web](https://github.com/Wkey999/big-event-web)。完整功能边界与接口清单见 [docs/功能实现说明书.md](docs/功能实现说明书.md)。

## 技术栈

| 类别 | 技术 |
|---|---|
| 语言 / 框架 | Java 21 · Spring Boot 3.4.1 |
| 持久层 | MyBatis 3.0.4 · MySQL 5.7（本地）/ 8.0（云端） |
| 认证 | JWT（jjwt 0.12.6）+ HandlerInterceptor |
| 对象存储 | 阿里云 OSS（图片上传） |

| 校验 | Jakarta Validation + 自定义注解（@InValues）+ 校验分组 |
| 其他 | Lombok · spring-security-crypto（BCrypt 密码哈希） |

单机部署模板与当前验收状态见 [Ubuntu 部署说明](deploy/ubuntu/README.md)。未备案阶段仅通过 SSH 隧道私下验收。

## 功能模块

- **用户**：注册 / 登录（JWT 无状态鉴权）/ 个人信息与头像更新 / 修改密码；`role=1` 为管理员
- **分类（频道）**：全站共享频道，仅管理员可增改删；排序权重 + 全站重名校验
- **文章**：CRUD + 草稿/发布双状态 + 分类·状态组合筛选 + 分页 + 富文本正文 + 封面图；已发布文章全站登录用户可见，草稿仅作者与管理员可见，管理员可改删任意文章
- **文件**：头像/封面/正文插图统一走 `POST /user/upload`，后端上传 OSS 后返回公网 URL；云端支持内网上传与公网访问地址分离

接口面：16 个 REST 端点（user 6 / category 5 / article 5 + 上传），统一 `Result` 响应体，`GlobalExceptionHandler` 兜底。

## 核心工作与安全加固（超出课程的部分）

**权限模型（共享频道制 + 角色化越权防护）**
- 2026-09-06 由「按 userId 隔离」调整为共享频道制：分类是全站频道，增改删需 `role=1` 管理员（普通用户一律「需要管理员权限」）；文章改删限作者本人或管理员；草稿只进作者与管理员的列表，已发布文章全站共享。
- `categoryId` 在新增/修改文章前做存在性校验（`assertCategoryExists`），对「不存在」与「已软删」返回同一条模糊错误，防止端点被用来探测频道 ID。

**输入校验闭环**
- 注册/改密/分类/文章全部走 DTO + `@Valid`：改密接口原先收裸 `Map`，空串能直接写库导致账号永久无法登录，重构为 `UserUpdatePwdDTO`（保留下划线 JSON 契约）；文章更新新增 `ArticleUpdateDTO`；分类用校验分组区分新增/更新；自定义 `@InValues` 约束文章状态只收 0/1。
- 分类重名从「裸 SQL 报错泄漏」改为服务层预检 + `DuplicateKeyException` 兜底双层防御。

**数据层正确性**
- 修复 `CategoryMapper` insert/update 静默丢列导致 `sortOrder/status` 永远写不进库的 bug（`IFNULL` 局部更新语义）；明确 `UserMapper` 局部更新 vs `ArticleMapper` 全量覆盖的不同契约并在前端对齐。
- 表设计沿用课程思想：软删除、无物理外键、冗余计数字段、多态用户行为表（为后续点赞/收藏预留）。
- 分类删除前置校验：该频道名下仍有未删除文章时拒绝软删（全站计数，无物理外键，靠服务层守住引用完整性），避免文章失去分类后既改不动也筛不出。

**密钥与配置工程化**
- JWT 密钥、数据库密码、OSS AccessKey 和 LLM API Key 全部放在**仓库根目录**的 gitignored 配置文件中，通过 `optional:file:./...` 注入，不会被 Maven 复制进 classpath/JAR；OSS AccessKey 经全历史扫描确认**零出现**（`git log --all -p` 中无 `LTAI`，命中的只是 `@Value` 配置键名与本文档说明）。初始提交 `a47a8cb` 的 `application.yml` 曾明文含 JWT 密钥与本地库密码，迁出后 JWT 密钥已于 2026-09-12 轮换、旧值失效，git 历史未重写——完整交代见[说明书 §6](docs/功能实现说明书.md)；
- dev/prod 多环境 profile 拆分（端口、日志、上传路径隔离）。

**配套前端（Vue 3，独立仓库）**
- Element Plus 主题层单一来源（CSS 令牌 + 玻璃拟态），瀑布流信息流不依赖任何布局库（贪心列分配 + 确定性封面比例），IntersectionObserver 无限滚动，wangeditor 富文本集成。

## 本地运行

AI 搜索现支持个人 API 和限额免费体验：每账号每天 3 次、同一网络默认每天 10 次、全站默认每天 100 次；免费体验需管理员单独配置试用密钥，默认关闭，个人模式不回退使用站主密钥。需执行 `sql/migration/009_ai_trial_quota.sql`，配置方式及验证边界见 [AI 搜索说明](docs/备忘录/15-AI搜索个人API与免费体验.md)。

资讯来源功能还需依次执行增量迁移 `sql/migration/006_content_feeds.sql` 、`sql/migration/007_feed_title_translation.sql` 与 `sql/migration/008_rename_ai_channel.sql`（新库也需在基础表与既有迁移后执行）。新增三张表，兼容 MySQL 5.7，不清空原有数据。

```bash
# 1. 建库：执行 SQL 脚本创建 big_event 库及 8 张表
# 2. 在仓库根目录按需创建本地配置（已被 .gitignore 排除，不入库）：
#    application-secret.yml  -> spring.datasource.password / jwt.secret
#    application-oss.yml     -> oss.endpoint / bucket-name / access-key-id / access-key-secret
#    application-llm.yml     -> DeepSeek 等 OpenAI 兼容模型的地址 / API Key / 模型名
# 3. 启动（JDK 21）
mvn spring-boot:run          # dev profile，端口 8080；也可 PowerShell 执行 ./start-dev.ps1，日志写入 logs/development-server.log
```

接口调试示例见 `src/test/http/user-api.http`（IDEA HTTP Client / 导入 Postman）。

## 官方订阅与链接卡片（2026-10-19）

已接入阮一峰网络日志、GitHub 官方博客、BBC Sport、Esports Insider、IT之家、少数派的官方 RSS／Atom，使用 ROME 2.1.0 解析。后端每 30 分钟采集一次，规范化链接并通过唯一索引去重，每来源单次最多新增 5 条、每来源每日最多 10 条、每日合计最多 60 条；只保存标题、作者、原文时间和链接，不保存正文与配图。新条目先进入独立待审表，管理员在前端 `/admin/feeds` 选择频道并审核发布后，才进入社区信息流及检索。

新增接口均受 `/admin/**` 管理员权限保护：`GET /admin/feeds/sources`、`GET /admin/feeds/entries`、`POST /admin/feeds/{source}/collect`、`POST /admin/feeds/entries/{id}/publish`、`POST /admin/feeds/entries/{id}/reject`。机器作者为普通权限且禁止登录。来源快照保留原文发布时间，时间检索不会将新收录的旧文当成当天新文；问答只可依据实际保存的元数据推荐原文。

自动采集需要后端持续运行，可用 `APP_CONTENT_FEEDS_ENABLED=false` 关闭（手动采集不受影响）。当前为单实例实现，尚未接入分布式调度。配置、数据库结构和本机验收记录见[实施说明](docs/备忘录/14-内容机器人采集与发布实施方案.md)。后端 63 项测试通过；两源实际采集、重复去重、审核发布与前端原文入口已联调。

## 未来规划（任务清单）

### 阶段 0 · 现状态收尾
- [x] `GlobalExceptionHandler` 记录堆栈 + 返回笼统「服务器内部错误」，不再向客户端泄漏 `e.getMessage()`；只回传「裸」`RuntimeException` 的业务提示，框架异常（`DataAccessException` 等子类）一律按内部错误处理
- [x] 引入 `BusinessException` 类型，把业务提示与基础设施异常从 `RuntimeException` 里分离出来（业务代码一律抛 `BusinessException`，全局异常处理器按类型放行，不再靠「异常类恰好是 RuntimeException」的脆弱约定）；顺带补齐 `HttpMessageNotReadableException`（请求体格式错误）与 `MaxUploadSizeExceededException`（文件超限）的友好提示
- [x] 分类删除保护：名下仍有文章时拒绝删除（返回「该分类下仍有文章，请先删除或转移文章」），消除「孤儿文章改不动」问题
- [ ] 清理历史测试账号（演示文章已于 2026-09-06 清洗：25 篇占位文软删、保留 17 篇补齐低饱和封面；ossselftest/layouttest 等测试账号仍在库）

### 阶段 1 · 登录与安全
- [ ] Redis 登录态治理：token 吊销、单设备登录、`userInfo` 缓存
- [ ] JWT 双令牌（access + refresh），前端 axios 拦截器无感续期
- [ ] 注册密码 BCrypt 化（`spring-security-crypto` 依赖已在），存量密码迁移方案
- [ ] OSS AccessKey 定期轮换 + RAM 最小权限策略复核

### 阶段 2 · 社区互动
- [ ] 评论模块：`comment` 表 CRUD + 分页 + 文章评论数冗余计数
- [ ] 点赞/收藏：基于已建的多态 `user_action` 表，含防重复计数
- [ ] 标签体系：发文自动抽取 + 手动打标，接入 `tag` / `article_tag` 表，标签聚合页

### 阶段 3 · 工程化与部署
- [ ] Dockerfile + docker-compose 一键起（app + MySQL + Redis + Nginx 托管前端静态资源）
- [ ] GitHub Actions CI：构建 + 单测 + 镜像推送
- [ ] 可观测性：actuator 健康检查、日志文件滚动、慢 SQL 采样
- [ ] 前端 TypeScript 重构 + 移动端适配

> **已搁置的愿景——多源信息聚合**：曾计划做「按平台订阅信息来源（X/B站/权威媒体等）+ 定时抓取 + 去重清洗 + 公开信息流」的资讯门户方向（类似 [newsnow](https://github.com/ourongxing/newsnow) 这类开源聚合站）。评估后**明确搁置**：抓取面的维护成本超出个人可持续范围，且大规模抓取存在平台服务条款与版权合规风险。2026-10-19 已在限定范围内接入两个站点主动提供的官方 RSS／Atom，仅保存元数据并审核发布链接卡片；大范围抓取与通用爬虫仍不安排，详见上面的订阅功能说明。

## License

学习项目，仅供个人研究与简历展示。

GitHub 待审内容会批量生成中文标题，同时保留英文原题；管理员可补译、核对并修改译名后发布。翻译依赖现有本地大模型配置，失败保留原题，不自动发布。2026-10-19 已补译 8 条待审标题，后端 72 项测试通过，前端构建通过。详见实施说明的中文标题章节。

2026-10-19 多频道扩源：频道现名为 AI/技术热点；体育、电竞来源支持中文翻译与原题对照，审核时预选对应频道。四个新增来源已实际采集成功，仍需人工审核。Maven 本地启动沿用系统代理配置；直接在 IDE 或运行 JAR 时可设置 JVM 参数 -Djava.net.useSystemProxies=true。当前后端 77 项测试、前端构建通过。

## 2026-10-19 验收修复

采集日志需追加执行 sql/migration/010_feed_run_skipped.sql，达上限记录以 success=0、skipped=1 标记。鉴权权限快照缓存 20 秒；平台试用按全站、网络、账号顺序事务校验。修复明细及完整验证记录见 [验收工单](docs/备忘录/16-验收发现与修复工单.md)。运行日志写入 logs/，避免占用 target/。

## 官方白名单自动发布（2026-10-22）

资讯来源现增加 DeepSeek 与千问的指定官方仓库，和 GitHub 官方博客共同组成默认自动发布白名单。只保存元数据与原文链接；其他来源继续审核，历史待审条目不自动放行。仓库发布使用 API，并在限流时读取同仓库的最新正式版本 Atom；不采集普通代码提交或预发行版本。电竞频道扩大为“游戏/电竞”，保留原 ID。

升级需依次执行 sql/migration/011_game_channel.sql 与 012_feed_automatic_publish.sql，再重启后端。APP_CONTENT_FEEDS_AUTO_PUBLISH_ENABLED=false 可关闭自动发布。来源范围、中文标题、审计字段、重试与验收记录见 [白名单说明](docs/备忘录/17-官方来源白名单与游戏频道.md)。

# BOOM！大事件 · 后端

一个内容社区的**服务端**：C 端浏览信息流 + B 端内容管理（文章/分类/个人设置）。基于黑马程序员《大事件》项目复刻，并在安全、校验、工程化上做了大量超出课程范围的自主改进。配套前端仓库：[Wkey999/big-event-web](https://github.com/Wkey999/big-event-web)。完整功能边界与接口清单见 [docs/功能实现说明书.md](docs/功能实现说明书.md)。

## 技术栈

| 类别 | 技术 |
|---|---|
| 语言 / 框架 | Java 21 · Spring Boot 3.4.1 |
| 持久层 | MyBatis 3.0.4 · MySQL 5.7 |
| 认证 | JWT（jjwt 0.12.6）+ HandlerInterceptor |
| 对象存储 | 阿里云 OSS（图片上传） |
| 校验 | Jakarta Validation + 自定义注解（@InValues）+ 校验分组 |
| 其他 | Lombok · spring-security-crypto（BCrypt 预留） |

## 功能模块

- **用户**：注册 / 登录（JWT 无状态鉴权）/ 个人信息与头像更新 / 修改密码；`role=1` 为管理员
- **分类（频道）**：全站共享频道，仅管理员可增改删；排序权重 + 全站重名校验
- **文章**：CRUD + 草稿/发布双状态 + 分类·状态组合筛选 + 分页 + 富文本正文 + 封面图；已发布文章全站登录用户可见，草稿仅作者与管理员可见，管理员可改删任意文章
- **文件**：头像/封面/正文插图统一走 `POST /user/upload` 直传 OSS 返回公网 URL

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
- JWT 密钥、数据库密码、OSS AccessKey 全部收敛到 gitignored 的 `application-secret.yml` / `application-oss.yml`，经 `spring.config.import` 注入；OSS AccessKey 经全历史扫描确认**零出现**（`git log --all -p` 中无 `LTAI`，命中的只是 `@Value` 配置键名与本文档说明）。初始提交 `a47a8cb` 的 `application.yml` 曾明文含 JWT 密钥与本地库密码，迁出后 JWT 密钥已于 2026-09-12 轮换、旧值失效，git 历史未重写——完整交代见[说明书 §6](docs/功能实现说明书.md)；
- dev/prod 多环境 profile 拆分（端口、日志、上传路径隔离）。

**配套前端（Vue 3，独立仓库）**
- Element Plus 主题层单一来源（CSS 令牌 + 玻璃拟态），瀑布流信息流不依赖任何布局库（贪心列分配 + 确定性封面比例），IntersectionObserver 无限滚动，wangeditor 富文本集成。

## 本地运行

```bash
# 1. 建库：执行 SQL 脚本创建 big_event 库及 8 张表
# 2. 在 src/main/resources/ 下自行创建（已被 .gitignore 排除，不入库）：
#    application-secret.yml  -> spring.datasource.password / jwt.secret
#    application-oss.yml     -> oss.endpoint / bucket-name / access-key-id / access-key-secret
# 3. 启动（JDK 21）
mvn spring-boot:run          # dev profile，端口 8080
```

接口调试示例见 `src/test/http/user-api.http`（IDEA HTTP Client / 导入 Postman）。

## 未来规划（任务清单）

### 阶段 0 · 现状态收尾
- [x] `GlobalExceptionHandler` 记录堆栈 + 返回笼统「服务器内部错误」，不再向客户端泄漏 `e.getMessage()`；只回传「裸」`RuntimeException` 的业务提示，框架异常（`DataAccessException` 等子类）一律按内部错误处理
- [ ] 引入 `BusinessException` 类型，把业务提示与基础设施异常从 `RuntimeException` 里分离出来（当前用「异常类恰好是 RuntimeException」判定业务提示，类型化后可直接替换该判断）
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

> **已搁置的愿景——多源信息聚合**：曾计划做「按平台订阅信息来源（X/B站/权威媒体等）+ 定时抓取 + 去重清洗 + 公开信息流」的资讯门户方向（类似 [newsnow](https://github.com/ourongxing/newsnow) 这类开源聚合站）。评估后**明确搁置**：抓取面的维护成本超出个人可持续范围，且大规模抓取存在平台服务条款与版权合规风险。若未来重启，只从官方授权 API / RSS 起步、限个人自托管用途，不做公开抓取服务。

## License

学习项目，仅供个人研究与简历展示。

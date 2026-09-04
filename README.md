# BOOM！大事件 · 后端

一个内容社区的**服务端**：C 端浏览信息流 + B 端内容管理（文章/分类/个人设置）。基于黑马程序员《大事件》项目复刻，并在安全、校验、工程化上做了大量超出课程范围的自主改进。配套前端仓库：[Wkey999/big-event-web](https://github.com/Wkey999/big-event-web)。

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

- **用户**：注册 / 登录（JWT 无状态鉴权）/ 个人信息与头像更新 / 修改密码
- **分类**：按用户隔离的分类 CRUD，排序权重 + 重名校验
- **文章**：CRUD + 草稿/发布双状态 + 分类·状态组合筛选 + 分页 + 富文本正文 + 封面图
- **文件**：头像/封面/正文插图统一走 `POST /user/upload` 直传 OSS 返回公网 URL

接口面：16 个 REST 端点（user 6 / category 5 / article 5 + 上传），统一 `Result` 响应体，`GlobalExceptionHandler` 兜底。

## 核心工作与安全加固（超出课程的部分）

**越权防护（IDOR）**
- 分类/文章的读写删全部按 JWT 中的 `userId` 隔离；`categoryId` 在新增/修改文章前做归属校验（`assertCategoryOwned`），对「不存在」与「他人资源」返回同一条模糊错误，防止端点被用来探测他人 ID；软删除的分类同样被拒绝。

**输入校验闭环**
- 注册/改密/分类/文章全部走 DTO + `@Valid`：改密接口原先收裸 `Map`，空串能直接写库导致账号永久无法登录，重构为 `UserUpdatePwdDTO`（保留下划线 JSON 契约）；文章更新新增 `ArticleUpdateDTO`；分类用校验分组区分新增/更新；自定义 `@InValues` 约束文章状态只收 0/1。
- 分类重名从「裸 SQL 报错泄漏」改为服务层预检 + `DuplicateKeyException` 兜底双层防御。

**数据层正确性**
- 修复 `CategoryMapper` insert/update 静默丢列导致 `sortOrder/status` 永远写不进库的 bug（`IFNULL` 局部更新语义）；明确 `UserMapper` 局部更新 vs `ArticleMapper` 全量覆盖的不同契约并在前端对齐。
- 表设计沿用课程思想：软删除、无物理外键、冗余计数字段、多态用户行为表（为后续点赞/收藏预留）。

**密钥与配置工程化**
- JWT 密钥、数据库密码、OSS AccessKey 全部收敛到 gitignored 的 `application-secret.yml` / `application-oss.yml`，经 `spring.config.import` 注入——**仓库零明文密钥**；
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

## 未来规划

**信息聚合方向（核心演进目标）**：从「用户自发创作」升级为「个性化资讯门户」——

- **多源订阅**：按平台选择信息来源（X/Twitter、抖音、B 站、微博、权威媒体官网等），按领域打标签（如 NBA 快讯 · Shams 首发推文、央视权威新闻、国际政治人物发言快照、西方科技媒体动态）；
- **定时爬虫**：授权 API 优先（X API / 哔哩哔哩开放平台），网页源做合规抓取，产出统一落进现有 `article` 模型（新增 `source` 字段与来源分类）；
- **清洗与去重**：同一事件多源转载的指纹去重、正文抽取、机打分类标签（`tag` / `article_tag` 表已建好）；
- **公开信息流**：C 端免登录浏览按分类/来源筛选的瀑布流（前端信息流组件已就绪），逐步引入推荐排序。

**工程优化路线**：

- Redis 登录态治理（token 吊销 / 单设备登录 / userInfo 缓存）
- JWT 双令牌刷新、密码全面 BCrypt 化
- 评论 / 点赞 / 收藏模块（`comment`、`user_action` 表已预留）
- Docker + docker-compose 一键部署（app + MySQL + Redis）
- 前端 TypeScript 重构

## License

学习项目，仅供个人研究与简历展示。

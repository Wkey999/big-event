# tools/data —— 演示种子数据

## 用途

为本地开发/演示生成一批**中文、可信、可复现**的种子数据，让首页信息流、频道、搜索、问答、后台统计都有真实感的数据可看。

当前生成规模：**30 个普通用户 + 5 个机器作者 / 100 篇文章（90 已发布 + 10 草稿）/ 约 1000 条行为**（浏览 750 + 点赞收藏 250）。

## 运行

前置：MySQL 已启动、库为 `big_event`、`pom.xml` 同级的本地配置已就绪（本脚本只连数据库，不依赖 Spring）。

```powershell
$env:JAVA_HOME='C:\Users\wkey\.jdks\ms-21.0.12.1'
$jar='D:\develop\repository\com\mysql\mysql-connector-j\9.1.0\mysql-connector-j-9.1.0.jar'

# 生成（首次可加 --reset，重复执行必须加 --reset）
& "$env:JAVA_HOME\bin\java.exe" -cp $jar tools\data\SeedDemoData.java --reset

# 只清理自己造的数据，不生成
& "$env:JAVA_HOME\bin\java.exe" -cp $jar tools\data\SeedDemoData.java --reset --articles=0 --behaviors=0 --users=0
```

可选参数：`--users=30 --articles=100 --behaviors=1000`（默认即此规模）。

## 设计要点

| 项 | 说明 |
|---|---|
| **只造自己的数据** | 用户名固定为 `demo_user_XX` / `bot_XX`；`--reset` 只按这些用户名清理，**绝不触碰**既有用户与文章 |
| **可复现** | 固定随机种子（`20261013`），同一命令两次运行生成同样数据 |
| **不编造事实** | 标题/正文为"常青型"内容（如「大模型推理成本：三条路线对比」），不虚构具体事件、比分、人名，避免假新闻 |
| **计数一致** | 生成后回写 `article.view_count/like_count/collect_count`：`like/collect` = `user_action` 行数，`view` = `browse_log` 去重用户数；脚本结尾输出不一致文章数（应为 0） |
| **时间分布** | 文章分布在近 60 天；其中 6 篇已发布文章落在**近 24 小时**，便于演示搜索的时间窗（`timeRange=1d/7d`） |
| **草稿** | 10 篇草稿，让后台"草稿"视图与统计有内容 |
| **封面** | 循环复用库中已有的 OSS 封面图，不引入失效外链 |

## 已知限制

- 种子用户的密码统一复用既有 BCrypt 哈希（等价于 `123456`），**仅供本地演示**，不要用于任何线上环境
- 生成的文章作者为种子用户/机器作者（`user_type=1`），不参与真实业务判断
- 用户画像（`user_profile`）不在此脚本内生成；如需"为你推荐"有内容，可登录种子用户后调用 `POST /profile/refresh`（或等每日 3 点的定时任务）

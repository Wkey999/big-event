# BOOM 单机部署记录与操作说明

更新时间：2026-10-22。此目录只有模板和脚本，真实密码、私钥、数据库备份不得放入仓库。

## 当前事实与边界

- 目标：Wkey666，Ubuntu 24.04.5，杭州，公网 `121.40.150.252`；实际检查为 2 vCPU、系统可见内存 3627 MiB、40GB 系统盘，部署后约 34GB 可用。
- 已安装 Java 21.0.12.1、MySQL 8.0.46、Nginx 1.24.0；不安装 Redis、Docker、模型或额外云产品。云厂商原有系统代理保留。
- SSH 已通过部署密钥认证，服务器 ED25519 指纹与用户控制台检查一致。Windows 联网进程使用 `D:\workplace\deployment-private\boom_ed25519.windows`，权限仅当前 Windows 用户可读。
- 已导入当前数据库、核对 21 张表的快照行数；Nginx、MySQL、后端和备份 timer 均已启用开机自启，后端实际重启后登录和数据读回通过。整机重启验证结果见交付的 `status.json`。
- 已实际整机重启，boot ID 变化且三项服务自动恢复；重启后重新登录、草稿和已保存的 OSS 头像读回均通过。
- 每日备份 timer 已启动；首次备份已在隔离实例实际恢复核对。两份云端备份已额外下载至本机 `D:\workplace\deployment-private\cloud-backups`，SHA256 和 gzip 校验通过。后续仍需定期下载新备份，同机自动备份不能替代异地副本。
- 用户目前没有域名、没有备案；用户明确此毕设算力包实例不能作为 ICP 备案接入资源。现阶段仅做服务器内运行和 SSH 隧道验收。
- UFW 已启用，只允许 SSH；网站 80、后端 9090、数据库 3306/33060 均只监听本机。阿里云安全组规则明细未核实、未修改；SSH 的精确来源限制需结合用户管理网络设置，当前保留 SSH 可达。
- 算力包初始总额 850 元、页面显示到期 2027-04-22，不代表可免费运行至该日。余额、资源抵扣范围和流量账单以账号实际账单为准；不自动购买资源或调整计费方式。

## 已核实的项目结构

- 后端 Java 21 / Spring Boot 3.4.1；生产端口 9090，仅绑定 127.0.0.1。
- 前端 Axios 的 baseURL 是 `/api`；开发代理剥离此前缀，后端接口为 `/user/...`、`/article/...`、`/admin/...`。Nginx 必须以 `proxy_pass http://127.0.0.1:9090/;` 保留尾斜线实现相同剥离。
- Vue Router 使用 History 模式；非 API 页面由 `try_files ... /index.html` 回退。缺失静态文件应返回 404，而不是 HTML。
- Redis 只有依赖，未发现业务调用；首版不安装。
- 本机数据库实际版本 MySQL 5.7.26、21 张表，已确认 migration 012 的字段存在。完整快照已包含当前结构，导入空库时不再盲目重放所有迁移。
- 暂未发现业务数据的 AES 等可逆加密。账户密码为 BCrypt 哈希，保留哈希即可。生产 JWT 使用新密钥，已有本地 token 不迁移，用户重新登录。
- 用户自带 API Key 是请求级临时数据，后端不将其持久化。生产数据库没有需要迁移的个人 API 解密密钥。

## 部署布局

| 位置 | 用途 |
|---|---|
| `/opt/boom/app/boom.jar` | 后端构建产物 |
| `/var/www/boom` | 前端 dist 内容，不是源码、不放配置或 SQL |
| `/etc/boom/app.env` | 生产环境变量，root 所有、0600 |
| `/etc/boom/logback-spring.xml` | 后端日志轮转配置 |
| `/etc/systemd/system/boom.service` | 后端开机启动、失败重启 |
| `/opt/boom/app/logs` | 后端运行日志，单文件 10MB、14 天、总上限 200MB |
| `/var/backups/boom` | 数据库快照，root 专用、保留至少 14 天 |
| `/usr/local/sbin/boom-backup` | 本机数据库备份脚本 |

程序用户 `boom` 不拥有 sudo；数据库使用独立 `boom@localhost` 账户，只授予 `big_event` 的 SELECT/INSERT/UPDATE/DELETE 权限。结构迁移通过本机数据库管理员单独执行。

## 实施顺序

1. SSH 登录后先运行 `inspect.sh`，核实磁盘、内存、架构、监听端口、已装软件及现有数据库；已有应用或数据库时先备份并调整方案，不覆盖。
2. 核实无冲突后，按实际情况安装 Nginx、Java 21 运行时、MySQL。Ubuntu 24.04 官方仓库的 MySQL 版本必须在安装前后核实，不将已停止维护的本地 5.7 安装包搬到云端。MySQL 5.7 -> 8.x 的兼容性以实际导入和查询结果为准。
3. 建立上述目录及专用系统用户。检查 `/usr/bin/java -version` 确认为 21；其他应用依赖不同 Java 时，服务改用 Java 21 的绝对路径，不随意修改全局 Java。
4. 本地构建并上传 JAR、dist 压缩包及部署模板。带数据的 SQL 单独上传到 root 私有目录，权限 0600，不进入 Web 根目录。
5. 创建全新的随机数据库密码及至少 32 字节的生产 JWT 密钥，写入 `/etc/boom/app.env`，不在日志或聊天回显。不要复用本地 root/123456。
6. 导入前确认目标 `big_event` 不存在或确为空库。完整快照包含 DROP TABLE 和 USE，**只允许导入该全新目标库**。已有生产数据时先停止，另做迁移与回滚方案。
7. 用 root 的 MySQL 本机 socket 认证执行导入，然后按 `dump-row-counts.tsv` 比较每张表的精确导出行数。源库的另一次实时 SELECT 可能受采集任务影响，不作为一致快照的行数基准。
8. 安装 `boom.service`、日志配置、`nginx-preview.conf`。启动前 `nginx -t`、`systemd-analyze verify`；确认 Nginx 仅监听 127.0.0.1:80，Java 仅监听 127.0.0.1:9090，MySQL 的 3306/33060 均未暴露公网。
9. 手动启动并验证，再启用开机自启。首轮 `app.env` 保持采集、自动发布、翻译和平台免费体验关闭。规则画像每日 03:00 在库内统计，不调用模型。
10. 通过 SSH 隧道完成验收。确认备份脚本实跑成功后，安装并启用备份 timer。服务器 root 若不支持 socket 认证，先调整备份认证方式，不能把密码拼进命令。

## 私下访问

假设部署密钥已经加入服务器，并且此电脑已固定核实的服务器 host key：

```powershell
ssh -N -L 127.0.0.1:18080:127.0.0.1:80 -i D:\workplace\deployment-private\boom_ed25519.windows -o UserKnownHostsFile=D:/workplace/tmp/boom-known-hosts root@121.40.150.252
```

保持终端开启，访问 `http://127.0.0.1:18080/`。此时网页和数据库确实运行在云端，但不是向互联网公开的网站；终端关闭会断开隧道。SSH 隧道里 Nginx 看到的是本机连接，所以不要用这种访问方式验证多网络 IP 配额隔离。

也可以在 Windows PowerShell 运行本目录的 `start-preview.ps1`：已有隧道时直接显示访问地址，没有时会启动隐藏的 SSH 隧道；私钥只读取当前电脑私有目录，不写入脚本。网站使用原有数据库账号登录。此前创建的验收账号已删除，不用于日常访问。

不开放 9090、3306、33060；备案前无需放行公网 80/443。SSH 安全组按实际管理来源限制，修改前确保不会锁死已有管理连接。

## OSS 图片上传调整

当前链路是浏览器 -> 后端 `/user/upload` -> OSS，不是浏览器拿长期 AccessKey 直传；部署后继续沿用。

- `OSS_ENDPOINT` 只决定后端 SDK 上传位置。
- 新增 `OSS_PUBLIC_BASE_URL` 指定返回浏览器的图片访问前缀。留空时沿用 `https://bucket.oss-region.aliyuncs.com`；标准 `-internal` Endpoint 会转换成对应公网域名返回。
- 只有 OSS Bucket 与 ECS 同地域时才使用内网 Endpoint。现有 Bucket 已核实为杭州且已完成真实上传测试；其他 Bucket 不应直接照搬。
- 保留原 Bucket 与对象名，已有数据库图片 URL 不做批量替换。
- 新生产上传凭据应采用该 Bucket 的最小权限 RAM 身份；在服务器的私有配置中填写，不发到聊天。Bucket 读取策略和真实图片公网访问仍须实测；URL 可构造不等于已经获得读取权限。
- 若后续改为浏览器直传，另做短期签名/STS 与上传策略；此次不把长期凭据交给前端。
- 本次沿用原本机 OSS 凭据完成验收；该凭据曾被用户贴到聊天，应尽快在 RAM 控制台轮换，并只将新值填入私有 `app.env`，重传到 `/etc/boom/app.env` 后重启服务。数据库/JWT 已使用独立随机密钥，不受 OSS 轮换影响。

## AI、资讯与外部访问

首轮只测试无密钥的网络可达性，不调用付费推理。确认模型名称、账户余额及预算后才启用翻译或平台体验。

最终状态：资讯定时采集与此前授权的官方白名单发布已恢复开启，生产 `LLM_ENABLED=false`、`LLM_CHAT_PROVIDER=none`、`AI_TRIAL_ENABLED=false`，本次没有模型推理调用。首轮自动任务完成 5 个来源、按每日上限跳过 3 个来源，千问源新增并自动发布 1 条。需要模型翻译的新英文标题会留待处理，可人工审核，不会退用开发机密钥。

开启标题翻译需要一起设置 `LLM_ENABLED=true`、`LLM_CHAT_PROVIDER=openai`、`LLM_BASE_URL`、`LLM_MODEL` 和独立的 `LLM_API_KEY`。

平台体验使用另一个 `AI_TRIAL_API_KEY`，确认后再开启 `AI_TRIAL_ENABLED=true`，默认个人每日 3 次、网络 10 次、全站 50 次。配额保存在 MySQL。Nginx 覆盖客户端传来的转发头，Tomcat 只信任本机代理；若未来加入 CDN/负载均衡，需要重新定义信任边界。

资讯定时采集通过 `APP_CONTENT_FEEDS_ENABLED=true` 开启，白名单发布通过 `APP_CONTENT_FEEDS_AUTO_PUBLISH_ENABLED=true` 开启。GitHub API 限流、RSS 源超时、英语标题翻译失败均须保留可审核的状态，不以强制发布掩盖失败。

## 验收清单（必须逐项记录实际结果）

本次已通过：静态路径与 History 回退、匿名接口 401、注册登录、普通用户访问管理接口 403、文章列表和详情、草稿写入及读回、OSS 内网上传和公网图片读取、头像保存与重新登录读回、后端重启后的数据持久化、生产配置权限、首次定时备份及隔离 MySQL 恢复演练（21 表一致）。浏览器也验证了首页与资料页刷新、预设头像和资料保存。

本次临时创建的两个验收账号、一个草稿及相应行为记录已清理，保留原有账号文章和新的资讯采集结果。一个 1 像素 OSS 测试图片的自动删除请求未成功，未为此扩大 RAM 权限；该测试图片仍保留。临时账号密码文件已删除，因此 `restart-check.py` 不应直接再次运行，重新验收应创建新的独立临时账号。

云端无密钥网络测试：阮一峰、GitHub 博客、Esports Insider、IT之家、少数派、DeepSeek/Qwen 官方仓库 API 返回 200；BBC 体育源超时；DeepSeek API 首页返回 401，说明网络可达，不代表凭据/模型调用已验证。付费模型推理调用为 0，平台体验及服务端翻译保持关闭。

已发现但未在部署任务中扩展修复的旧问题：用户名允许 20 字符，注册默认昵称沿用用户名，而资料编辑限制昵称 10 字符；长用户名首次打开资料页会显示昵称超长，修改成 10 字以内可正常保存。

`initial-install.py` 是与本次构建哈希绑定的首次安装脚本，拒绝已有应用、业务账号和目标库；不是后续更新工具。恢复导入后的安装阶段不会重导数据，必须先重新核对全部快照行数。

- 页面加载、直接打开并刷新 `/profile`、`/admin/feeds`、`/read/{id}`；不存在的静态图片返回 404。
- 现有账号登录、文章列表和详情；普通账号无法访问管理员接口。
- 新建一篇带唯一标识的草稿并读回，确认数据库写入；只清理由该次验收创建的数据。
- 上传一张测试图片，确认返回 URL 是公网地址、图片可打开，保存头像后重新登录仍可读回。
- 明确核实配置与计费后才进行少量真实 AI 调用，记录成功/失败和配额变化；不输出 Key。
- `systemctl restart boom` 后服务恢复；记录服务自启状态。实际机器重启须确认其他服务及用户会话后再执行，不将 `enable` 当成已完成重启验证。
- 日志轮转配置、备份 timer 和首次实际备份成功。异地副本仍需定期下载，同机备份不能防止整盘丢失。

## 日志、更新、备份与恢复

```bash
systemctl status boom --no-pager
journalctl -u boom -n 80 --no-pager
tail -n 80 /opt/boom/app/logs/boom.log
tail -n 80 /var/log/nginx/error.log
systemctl list-timers boom-backup.timer
systemctl start boom-backup.service
journalctl -u boom-backup.service -n 30 --no-pager
```

更新：本地测试和构建 -> 备份数据库 -> 将旧 JAR/静态文件保留为上一版本 -> 上传新产物到暂存目录 -> 检查 SHA256 -> `systemctl stop boom` -> 替换产物 -> `systemctl start boom` -> 验收。不要在正运行的 JAR 上直接覆盖写入；配置和数据库不随静态发布覆盖。数据库结构变化需先单独评估迁移兼容性，回退 JAR 不能自动回退数据库。

恢复：先校验 `.sha256` 和 `gzip -t`。演练应在独立测试 MySQL 实例导入，再按行数和关键查询验证。完整 SQL 内带 `CREATE DATABASE/USE big_event` 和 DROP TABLE，**不要通过简单指定另一个库名就向现有数据库导入**。故障恢复到同名生产库时，先停止后端、备份故障现场并明确确认恢复点，再由管理员执行 `gzip -dc BACKUP.sql.gz | mysql --protocol=socket`；恢复后校验账号、文章、来源与配额数据。

## 域名与正式公开访问

杭州服务器未备案前不能通过公网 IP 或换端口直接开放网站。购买域名之前，先向接入商核实个人主体、社区/资讯与评论功能的备案适用范围，以及可用的备案接入资源。当前实例不能作为接入资源；不得擅自另购机器或迁移地域。

正式开放需要已有可用备案接入方案、域名解析和 HTTPS 证书，并经过 Nginx 配置检查后再开放 80/443。小游戏素材授权尚未确认的部分在正式公开前单独处理。

官方参考：
- [阿里云：IP 访问及通用网站备案要求](https://help.aliyun.com/zh/icp-filing/basic-icp-service/product-overview/icp-filing-requirements-for-a-regular-website)
- [阿里云：ECS 按量付费](https://help.aliyun.com/zh/ecs/pay-as-you-go-1)
- [阿里云：OSS 内网 Endpoint 访问条件](https://help.aliyun.com/zh/oss/network-exceptions-occur-when-you-try-to-access-oss-buckets)
- [Spring Boot：代理头配置](https://docs.spring.io/spring-boot/how-to/webserver.html)

# 美国实例迁移记录（2026-10-24）

## 最新上线状态（2026-10-24 23:49，北京时间）

- 继续使用两个仓库的 `design/gemini` 分支：后端提交 `1198541`、前端提交 `b22f79f`，均已推送 GitHub 并部署到美国实例。后端 JAR SHA256 为 `e1d9f5d39881f0e8bf3b0302b4828de9904da92a837146feb114ea31064814bd`；前端 `index.html` SHA256 为 `e20a8773f002d5d71dc8686e3d65093b55389e41041dd29b7cd798b2a2663f77`。
- 更新前核对了 `feed_entry`、`feed_run` 所需字段，服务器已具备迁移 011/012 对应结构和“游戏/电竞”频道，本次未重放迁移或覆盖现有数据。更新前备份为 `/var/backups/boom/big_event-20261024-234259.sql.gz`；首次新版本采集后又生成 `/var/backups/boom/big_event-20261024-234857.sql.gz`，均通过 `gzip -t`。旧 JAR 和前端留在服务器作应用回退。
- 服务 `boom`、`nginx`、`mysql` 正常。公网 HTTPS `/admin/feeds` 返回 200，实际内容与构建产物哈希一致；新管理页 JS 资源返回 200，匿名 `/api/user/userInfo` 返回 401，证书验证通过。后端 135 项测试中 0 失败、5 跳过；前端生产构建与分包检查通过。管理员登录后的页面操作尚未在浏览器手动回归。
- 杭州旧实例已由用户释放后，恢复了美国实例的定时采集与官方来源白名单自动发布；`LLM_ENABLED=false`、`LLM_CHAT_PROVIDER=none`、`AI_TRIAL_ENABLED=false` 继续保持，未调用付费模型。首次定时任务为 10 个来源写入成功记录，BBC Sport、中新体育、电竞热点、IT之家等有新条目进入待审队列，千问官方仓库本次有 1 条白名单内容自动发布。GitHub 博客英文标题在付费翻译关闭时仍需中文标题审核，不能声称已全自动发布。阮一峰订阅源用 `curl` 直连返回 403，但应用本次读取成功、未新增，后续观察稳定性。

## 实例与初次迁移状态

- 美国（弗吉尼亚），公网 IP `47.85.104.89`，Ubuntu 24.04.5，2核4GB，40GB系统盘。
- 用户通过阿里云终端提供的 ED25519 指纹已与实际公钥核对一致。SSH 使用原有部署公钥，私钥保留本机私有目录。
- 已安装 Java 21.0.12.1、MySQL 8.0.46、Nginx；未安装 Redis、Docker 或模型。
- 初次迁移时 Gemini 前端提交为 `d9fe937`，后端复用杭州已运行 JAR，SHA256 为 `f97a470597ee5e872dbe987a45f0fd18a467b402c3073e0b9d8f01360bfdb3b2`。最新版本见上节。
- 数据来自杭州 2026-10-24 21:39:03 的快照，导入21张表，原有用户54条、文章219条。它是该时间点的副本，不是持续双向同步。
- 服务器内验收通过。用户添加安全组 TCP80 入方向规则后，已从本机绕过代理、未使用SSH隧道直接验证公网HTTP：主页及/login、/profile、/admin/feeds均返回200且为同一SPA入口；21个入口静态资源可读；缺失资源404、匿名用户接口401。主页SHA256与Gemini d9fe937发布产物一致。此结果仅证明本次测试网络可达，不代表所有运营商网络的访问质量。

## 部署位置与隔离

- 前端 `/var/www/boom`，后端 `/opt/boom/app/boom.jar`。
- `/etc/boom/app.env` root:root、0600；独立随机数据库密码和 JWT 密钥，现有用户需重新登录。
- OSS 沿用本机已有配置中的 Bucket/凭据，上传端点使用杭州公网端点，避免跨地域内网访问失败。
- Nginx监听公网80/443；Java9090、MySQL3306/33060仅监听本机。UFW放行22/80/443。
- `/api/` 剥离前缀转发给9090；History路由回退保留。HTTPS为正式访问入口，HTTP308跳转至相同IP的HTTPS路径，ACME验证路径除外。
- 服务 nginx/mysql/boom 与 boom-backup.timer 已启用开机启动；实际验证后端服务重启后登录、草稿和头像可读回，未进行整机重启测试。

## 已验证与尚未验证

通过：静态与路由回退、匿名401、普通用户管理接口403、注册登录、文章列表详情、草稿写入读回、OSS小图片上传公网读取、头像保存后重新登录读回、后端重启持久化。付费模型调用为0。

本次临时创建的1个账号及1篇草稿已按精确ID清理，未删除原有内容；1个1像素OSS测试图片因删除权限不足保留，没有扩大凭据权限。

初次迁移时 LLM_ENABLED、AI_TRIAL_ENABLED、APP_CONTENT_FEEDS_ENABLED、APP_CONTENT_FEEDS_AUTO_PUBLISH_ENABLED 暂为false，避免费用及迁移期间双机重复任务；后续已恢复免费采集和白名单，见最新上线状态。模型能力仍未实测。

待办：管理员登录后的实际浏览器回归、继续观察定时来源稳定性。用户后续表示已释放杭州旧实例；本轮未执行该释放操作，不能再将旧实例当成可用回滚来源。如需找回迁移时间点之后的杭州新增数据，应先检查保留的备份，不能假设两台数据曾持续同步。当前公网地址为 https://47.85.104.89/ ，无需SSH隧道。

## HTTPS（2026-10-24追加）

使用官方snap Certbot5.8.0及Let's Encrypt免费的shortlived IP证书，无需购买域名或付费证书。已检查证书SAN包含47.85.104.89；本轮证书到期时间2026-10-31 05:07:37 UTC，依靠自动续期维持。

- 证书链 `/etc/letsencrypt/live/boom-ip/fullchain.pem`，私钥同目录的 `privkey.pem`，不要下载或提交私钥。
- HTTP-01 webroot `/var/www/letsencrypt`，Nginx对 `/.well-known/acme-challenge/` 单独开放读取，保留80端口用于续期。
- snap.certbot.renew.timer已启用，Certbot按计划检查并在需要时续期。
- `/etc/letsencrypt/renewal-hooks/deploy/10-boom-nginx` 在续期成功后运行nginx配置校验与reload。
- 实际执行带 `--dry-run --run-deploy-hooks --no-random-sleep-on-renew` 的续期演练通过，并执行了Nginx重载钩子；未仅依赖定时器状态判断续期可用。
- Nginx改动前的配置在 `/root/boom-https-audit`，修改失败自动恢复上一配置。
- 已从本机不使用代理或SSH隧道访问HTTPS，并使用Python默认信任库及Windows curl校验证书，未跳过TLS校验。主页、登录页、资料页、管理SPA路径和21个入口资源可读；匿名接口401，缺失资源404。HTTP/profile返回308并转至HTTPS/profile的200。
- IP证书采用短有效期，不能依赖人工隔几个月续期。后续检查：

```bash
/snap/bin/certbot certificates
systemctl list-timers --all snap.certbot.renew.timer
journalctl -u snap.certbot.renew.service -n 50 --no-pager
/snap/bin/certbot renew --dry-run --run-deploy-hooks --no-random-sleep-on-renew --cert-name boom-ip
nginx -t
```

参考：https://letsencrypt.org/2026/03/11/shorter-certs-certbot.html 。证书签发与续期免费，ECS与公网流量仍遵循原算力包/账户计费规则。

## 日志与备份

```bash
systemctl status boom nginx mysql --no-pager
journalctl -u boom -n 80 --no-pager
tail -n 80 /opt/boom/app/logs/boom.log
systemctl list-timers boom-backup.timer
systemctl start boom-backup.service
gzip -t /var/backups/boom/big_event-*.sql.gz
```

数据库每日北京时间约04:15自动备份，保留至少14天，位于 `/var/backups/boom`。当前备份已生成并通过gzip校验；未对美国备份进行隔离恢复演练，不把文件完整性检查等同恢复验证。

自动审批拒绝了从杭州整体导出私有生产配置及将美国数据库备份下载到本机。已改用本机私有配置生成美国独立密钥、在美国本机校验备份；未绕过拒绝。美国备份目前只有同机副本；若后续下载数据库备份，应取得明确授权。

本机原杭州迁移快照与JAR位于 `D:\workplace\deployment-private\migration-us-20261024`，不得提交仓库。实际迁移脚本也位于该私有目录；已有应用或数据库时拒绝重复首次安装，不能直接重新运行安装脚本更新网站。

恢复前先备份故障现场，确认恢复点及目标库。完整SQL含CREATE DATABASE/USE/DROP TABLE，只能按明确的停机恢复方案使用；不要直接导入现有开发库。前端更新可参考既有README，但不要重新执行首次安装。


#!/usr/bin/env bash
# Read-only inspection. Does not install, stop, enable, or modify services.
set -eu
printf '\n--- identity and operating system ---\n'
id
cat /etc/os-release
uname -m
printf '\n--- disks and memory ---\n'
df -hT / /var /opt
lsblk -o NAME,SIZE,FSTYPE,MOUNTPOINTS
free -m
printf '\n--- listeners ---\n'
ss -lntp
printf '\n--- existing packages and services ---\n'
dpkg-query -W -f='${Package} ${Version}\n' nginx mysql-server mysql-server-8.0 openjdk-21-jre-headless redis-server 2>/dev/null || true
systemctl is-active nginx mysql redis-server 2>/dev/null || true
systemctl is-enabled nginx mysql 2>/dev/null || true
command -v java >/dev/null && java -version 2>&1 || true
printf '\n--- firewall ---\n'
command -v ufw >/dev/null && ufw status verbose || true
printf '\n--- existing BOOM directories ---\n'
ls -ld /opt/boom /etc/boom /var/www/boom /var/backups/boom 2>/dev/null || true
printf '\n--- local database metadata (socket, no password arguments) ---\n'
if command -v mysql >/dev/null; then
  mysql --protocol=socket -NBe "SELECT VERSION(); SELECT SCHEMA_NAME FROM information_schema.SCHEMATA;" 2>/dev/null || true
fi
printf '\n--- public source and API reachability (no model calls or API keys) ---\n'
for url in https://github.blog/feed/ https://api.github.com/rate_limit https://www.ruanyifeng.com/blog/atom.xml https://api.deepseek.com/; do
  printf '%s ' "$url"
  curl --silent --output /dev/null --connect-timeout 8 --max-time 15 --write-out 'HTTP %{http_code} total=%{time_total}s\n' "$url" || true
done

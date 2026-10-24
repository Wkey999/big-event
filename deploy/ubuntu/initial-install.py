#!/usr/bin/env python3
"""Initial deployment only. Refuses existing app/database; keeps all secrets off stdout."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import pwd
import tarfile

STAGE = Path('/root/boom-deploy-stage')

def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)

def sql(statement):
    result = subprocess.run(['mysql', '--protocol=socket', '-NBe', statement],
                            capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError('Database command failed; SQL/credentials withheld from output')
    return result.stdout.strip()

def install(source, target, mode='644'):
    run(['install', '-m', mode, str(source), str(target)])

def main():
    # Resume never reimports data. It rechecks every exported row count before continuing.
    resume = sys.argv[1:] == ['--resume-import']
    if sys.argv[1:] and not resume:
        raise RuntimeError('Unknown arguments')
    if os.geteuid() != 0:
        raise RuntimeError('Run as root')
    if Path('/opt/boom/app/boom.jar').exists() or Path('/etc/boom/app.env').exists():
        raise RuntimeError('Existing application/config detected; initial deployment refused')
    if not resume and sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='big_event'") != '0':
        raise RuntimeError('Target database already exists; import refused')
    if sql("SELECT COUNT(*) FROM mysql.user WHERE User='boom'") != '0':
        raise RuntimeError('Database business account already exists; refusing changes')

    expected = {
        'boom.jar': 'f97a470597ee5e872dbe987a45f0fd18a467b402c3073e0b9d8f01360bfdb3b2',
        'frontend.tar.gz': 'f3d5a9a3981e59657878a31f4a9633d5ecbae51e8920138151fd4ec02c663591',
        'ubuntu-config.tar.gz': '61149bf77d73a217bcbda9e32d40d28ab36fe198e899ec93fc5a17d97aaccaf3',
        'big_event.sql': '30a070d3643dff6ff1bced342a693444704a7da324368ff9c34cde5d46fbba2f',
    }
    for name, digest in expected.items():
        if hashlib.sha256((STAGE / name).read_bytes()).hexdigest() != digest:
            raise RuntimeError('Upload hash mismatch: ' + name)
    config = STAGE / 'config'
    config.mkdir(exist_ok=True)
    with tarfile.open(STAGE / 'ubuntu-config.tar.gz') as archive:
        archive.extractall(config, filter='data')

    env = {}
    for line in (STAGE / 'app.env').read_text().splitlines():
        if line.strip() and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            env[key.strip()] = value.strip()
    password = env.get('DB_PASSWORD', '')
    if not re.fullmatch('[a-f0-9]{48}', password):
        raise RuntimeError('Expected independently generated database credential')
    if not re.fullmatch('[a-f0-9]{64}', env.get('JWT_SECRET', '')):
        raise RuntimeError('Expected independently generated JWT secret')
    for key in ('LLM_ENABLED', 'AI_TRIAL_ENABLED', 'APP_CONTENT_FEEDS_ENABLED',
                'APP_CONTENT_FEEDS_AUTO_PUBLISH_ENABLED'):
        if env.get(key) != 'false':
            raise RuntimeError('Initial deployment requires background/paid features off: ' + key)

    account_exists = subprocess.run(['id', '-u', 'boom'], stdout=subprocess.DEVNULL,
                                    stderr=subprocess.DEVNULL).returncode == 0
    if account_exists:
        account = pwd.getpwnam('boom')
        if not resume or account.pw_uid >= 1000 or account.pw_dir != '/opt/boom' or account.pw_shell != '/usr/sbin/nologin':
            raise RuntimeError('Unexpected system account boom; inspect before reusing')
    elif resume:
        raise RuntimeError('Resume requires the account from the initial attempt')
    else:
        run(['useradd', '--system', '--user-group', '--home-dir', '/opt/boom',
             '--shell', '/usr/sbin/nologin', 'boom'])
    run(['install', '-d', '-o', 'boom', '-g', 'boom', '-m', '750', '/opt/boom/app',
         '/opt/boom/app/logs'])
    run(['install', '-d', '-o', 'root', '-g', 'boom', '-m', '750', '/etc/boom'])
    run(['install', '-d', '-m', '700', '/var/backups/boom'])
    run(['install', '-d', '-m', '755', '/var/www/boom'])

    # This import can only target the newly verified absent database above.
    if not resume:
        with (STAGE / 'big_event.sql').open('rb') as snapshot:
            run(['mysql', '--protocol=socket', '--default-character-set=utf8mb4'],
                stdin=snapshot, stdout=subprocess.DEVNULL)
    for line in (STAGE / 'dump-row-counts.tsv').read_text().splitlines():
        name, expected_count = line.split('\t')
        if not re.fullmatch('[a-zA-Z0-9_]+', name):
            raise RuntimeError('Invalid table in count manifest')
        if sql('SELECT COUNT(*) FROM big_event.`' + name + '`') != expected_count:
            raise RuntimeError('Imported row count mismatch: ' + name)
    print('Imported MySQL 5.7 snapshot into MySQL 8; all 21 table row counts match.')

    # The password is sent over stdin, never as command arguments or stdout.
    statements = ("CREATE USER 'boom'@'localhost' IDENTIFIED BY '" + password + "';"
                  "GRANT SELECT,INSERT,UPDATE,DELETE ON big_event.* TO 'boom'@'localhost';")
    result = subprocess.run(['mysql', '--protocol=socket'], input=statements.encode(),
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if result.returncode:
        raise RuntimeError('Business account creation failed; credentials withheld')
    install(STAGE / 'app.env', '/etc/boom/app.env', '600')
    install(config / 'logback-spring.xml', '/etc/boom/logback-spring.xml')
    install(STAGE / 'boom.jar', '/opt/boom/app/boom.jar')
    run(['chown', 'boom:boom', '/opt/boom/app/boom.jar'])
    with tarfile.open(STAGE / 'frontend.tar.gz') as archive:
        archive.extractall('/var/www/boom', filter='data')
    run(['find', '/var/www/boom', '-type', 'd', '-exec', 'chmod', '755', '{}', '+'])
    run(['find', '/var/www/boom', '-type', 'f', '-exec', 'chmod', '644', '{}', '+'])
    install(config / 'nginx-preview.conf', '/etc/nginx/sites-available/boom-preview')
    default = Path('/etc/nginx/sites-enabled/default')
    if default.is_symlink() and str(default.resolve()) == '/etc/nginx/sites-available/default':
        shutil.copy2('/etc/nginx/sites-available/default', '/root/boom-deploy-audit/nginx-default-before')
        default.unlink()
    elif default.exists():
        raise RuntimeError('Unexpected Nginx default site; inspect before changing')
    Path('/etc/nginx/sites-enabled/boom-preview').symlink_to('/etc/nginx/sites-available/boom-preview')
    install(config / 'boom.service', '/etc/systemd/system/boom.service')
    install(config / 'boom-backup', '/usr/local/sbin/boom-backup', '750')
    install(config / 'boom-backup.service', '/etc/systemd/system/boom-backup.service')
    install(config / 'boom-backup.timer', '/etc/systemd/system/boom-backup.timer')
    run(['nginx', '-t'])
    run(['systemd-analyze', 'verify', '/etc/systemd/system/boom.service',
         '/etc/systemd/system/boom-backup.service', '/etc/systemd/system/boom-backup.timer'])
    run(['systemctl', 'daemon-reload'])
    run(['systemctl', 'enable', '--now', 'mysql', 'nginx', 'boom'])
    run(['systemctl', 'start', 'boom-backup.service'])
    run(['systemctl', 'enable', '--now', 'boom-backup.timer'])
    print('Private preview services installed; first database backup completed; public web access remains disabled.')

if __name__ == '__main__':
    main()

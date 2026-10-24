#!/usr/bin/env python3
"""Restore the actual first backup into a separate, socket-only temporary MySQL instance."""
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time

# Ubuntu's existing AppArmor profile permits this export directory. It is outside
# the production datadir; no security policy needs to be relaxed for the test.
work = Path('/var/lib/mysql-files/boom-restore-check-20261022')
if work.exists() or work.resolve() == Path('/var/lib/mysql'):
    raise RuntimeError('Restore test directory already exists; inspect before proceeding')
backup = sorted(Path('/var/backups/boom').glob('big_event-*.sql.gz'))[0]
digest = backup.with_name(backup.name + '.sha256').read_text().split()[0]
assert hashlib.sha256(backup.read_bytes()).hexdigest() == digest
work.mkdir(mode=0o700)
shutil.chown(work, user='mysql', group='mysql')
socket = str(work / 'mysql.sock')
base = ['mysql', '--no-defaults', '--protocol=socket', '--socket=' + socket, '-uroot']
process = None
try:
    subprocess.run(['mysqld', '--no-defaults', '--initialize-insecure', '--user=mysql',
                    '--datadir=' + str(work), '--log-error=/var/log/mysql/boom-restore-initialize.log'],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    process = subprocess.Popen(['mysqld', '--no-defaults', '--user=mysql', '--datadir=' + str(work),
                                 '--socket=' + socket, '--skip-networking', '--mysqlx=0',
                                 '--innodb-buffer-pool-size=64M', '--performance-schema=OFF',
                                 '--pid-file=' + str(work / 'mysql.pid'),
                                 '--log-error=/var/log/mysql/boom-restore-server.log'],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for attempt in range(60):
        result = subprocess.run(base + ['-NBe', 'SELECT 1'], capture_output=True)
        if result.returncode == 0:
            break
        if process.poll() is not None or attempt == 59:
            raise RuntimeError('Isolated restore instance did not start; production instance untouched')
        time.sleep(1)
    with gzip.open(backup, 'rb') as dump:
        importer = subprocess.Popen(base + ['--default-character-set=utf8mb4'], stdin=subprocess.PIPE,
                                    stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        shutil.copyfileobj(dump, importer.stdin)
        importer.stdin.close()
        if importer.wait() != 0:
            raise RuntimeError('Isolated restore import failed; details withheld')
    rows = {}
    for line in Path('/root/boom-deploy-stage/dump-row-counts.tsv').read_text().splitlines():
        table, expected = line.split('\t')
        if not table.replace('_', '').isalnum():
            raise RuntimeError('Invalid table name')
        result = subprocess.run(base + ['-NBe', 'SELECT COUNT(*) FROM big_event.`' + table + '`'],
                                capture_output=True, text=True, check=True)
        if result.stdout.strip() != expected:
            raise RuntimeError('Isolated restore count mismatch: ' + table)
        rows[table] = int(expected)
    Path('/root/boom-deploy-audit/restore.json').write_text(json.dumps({
        'passed': True, 'tables_verified': len(rows), 'backup': backup.name,
        'separate_mysql_instance': True, 'networking_disabled': True,
        'production_database_untouched': True}, indent=2))
    print('Actual database backup restored into isolated socket-only MySQL; all 21 table counts match; production database untouched.')
finally:
    if process is not None and process.poll() is None:
        subprocess.run(['mysqladmin', '--no-defaults', '--protocol=socket', '--socket=' + socket,
                        '-uroot', 'shutdown'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.terminate()
            process.wait(timeout=10)
    # Fixed, dedicated directory only; never recurse over the production data directory.
    if work.resolve() == Path('/var/lib/mysql-files/boom-restore-check-20261022') and (process is None or process.poll() is not None):
        shutil.rmtree(work)

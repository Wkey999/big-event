#!/usr/bin/env python3
"""Remove only the two accounts, one draft and one image created by this acceptance run."""
import base64
from email.utils import formatdate
import hashlib
import hmac
import json
from pathlib import Path
import re
import subprocess
import urllib.request
import urllib.parse

audit = Path('/root/boom-deploy-audit')
session_path = audit / 'qa-session.json'
session = json.loads(session_path.read_text())
ui_name = 'qauimvjojeg3'  # Exact username observed in the browser in this deployment run.
names = [session['username'], ui_name]
assert all(re.fullmatch('[a-zA-Z0-9]{3,20}', name) for name in names)

def sql(statement):
    result = subprocess.run(['mysql', '--protocol=socket', '-NB', 'big_event'],
                            input=statement, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError('Acceptance cleanup database operation failed')
    return result.stdout.strip()

quoted_names = ','.join("'" + name + "'" for name in names)
rows = sql('SELECT id,username FROM user WHERE username IN (' + quoted_names + ')').splitlines()
ids = [int(row.split('\t')[0]) for row in rows]
assert len(ids) == 2 and session['user_id'] in ids
id_list = ','.join(map(str, ids))
articles = sql('SELECT id,title,state FROM article WHERE user_id IN (' + id_list + ')').splitlines()
assert len(articles) == 1
draft_id, title, state = articles[0].split('\t')
assert int(draft_id) == session['article_id'] and title.startswith('Deployment acceptance ') and state == '0'
statements = ['START TRANSACTION;']
for table in ('post_media', 'post_topic', 'article_tag', 'article_source'):
    statements.append('DELETE FROM ' + table + ' WHERE article_id=' + draft_id + ';')
for table in ('browse_log', 'ask_log', 'search_log', 'user_action', 'comment', 'file_record',
              'user_profile_interest', 'user_profile'):
    statements.append('DELETE FROM ' + table + ' WHERE user_id IN (' + id_list + ');')
statements.append('DELETE FROM user_follow WHERE follower_id IN (' + id_list + ') OR followee_id IN (' + id_list + ');')
statements.append('DELETE FROM article WHERE id=' + draft_id + ' AND user_id IN (' + id_list + ');')
statements.append('DELETE FROM user WHERE id IN (' + id_list + ') AND username IN (' + quoted_names + ');')
statements.append('COMMIT;')
sql('\n'.join(statements))
assert sql('SELECT COUNT(*) FROM user WHERE username IN (' + quoted_names + ')') == '0'

image_cleanup = 'not_needed'
if session.get('uploaded_url'):
    env = dict(line.split('=', 1) for line in Path('/etc/boom/app.env').read_text().splitlines()
               if line and not line.startswith('#'))
    uri = urllib.parse.urlparse(session['uploaded_url'])
    bucket = env['OSS_BUCKET_NAME']
    assert uri.hostname == bucket + '.oss-cn-hangzhou.aliyuncs.com'
    assert re.fullmatch('/images/[a-f0-9]{32}\\.png', uri.path)
    date = formatdate(usegmt=True)
    canonical = 'DELETE\n\n\n' + date + '\n/' + bucket + uri.path
    signature = base64.b64encode(hmac.new(env['OSS_ACCESS_KEY_SECRET'].encode(), canonical.encode(), hashlib.sha1).digest()).decode()
    req = urllib.request.Request('https://' + bucket + '.oss-cn-hangzhou-internal.aliyuncs.com' + uri.path,
                                 method='DELETE', headers={'Date': date,
                                 'Authorization': 'OSS ' + env['OSS_ACCESS_KEY_ID'] + ':' + signature})
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            assert response.status == 204
        image_cleanup = 'deleted_only_acceptance_image'
    except Exception as error:
        image_cleanup = 'pending:' + type(error).__name__

session_path.unlink()
result = {'accounts_removed': 2, 'drafts_removed': 1, 'image': image_cleanup,
          'existing_accounts_and_articles_preserved': True}
(audit / 'cleanup.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result, indent=2))

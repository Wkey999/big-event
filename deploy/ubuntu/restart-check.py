#!/usr/bin/env python3
import json
from pathlib import Path
import time
import urllib.request

def api(path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request('http://127.0.0.1/api' + path,
                                 data=json.dumps(body).encode() if body else None,
                                 headers=headers)
    with urllib.request.urlopen(req, timeout=10) as response:
        result = json.load(response)
    assert result['code'] == 0, 'Business request failed after restart'
    return result['data']

session = json.loads(Path('/root/boom-deploy-audit/qa-session.json').read_text())
for attempt in range(45):
    try:
        token = api('/user/login', {'username': session['username'], 'password': session['password']})['token']
        break
    except Exception:
        if attempt == 44:
            raise
        time.sleep(1)
user = api('/user/userInfo', token=token)
assert user['id'] == session['user_id']
assert api('/article/edit/' + str(session['article_id']), token=token)['state'] == 0
if session.get('uploaded_url'):
    assert user['avatar'] == session['uploaded_url']
print('Restart acceptance passed: login, database draft, saved OSS avatar survive service restart.')

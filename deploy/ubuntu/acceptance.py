#!/usr/bin/env python3
"""Private deployment acceptance. Creates only a tagged account and draft; no model calls."""
import base64
import hashlib
import json
from pathlib import Path
import secrets
import time
import urllib.request
import urllib.error

BASE = 'http://127.0.0.1'
OUT = Path('/root/boom-deploy-audit')
checks = {}
token = None

def request(path, body=None, method='GET', headers=None):
    options = dict(headers or {})
    if token:
        options['Authorization'] = 'Bearer ' + token
    if isinstance(body, dict):
        body = json.dumps(body, ensure_ascii=False).encode()
        options['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + path, data=body, method=method, headers=options)
    try:
        with urllib.request.urlopen(req, timeout=40) as response:
            return response.status, response.read(), dict(response.headers)
    except urllib.error.HTTPError as error:
        return error.code, error.read(), dict(error.headers)

def api(path, body=None, method='GET', headers=None):
    status, raw, _ = request('/api' + path, body, method, headers)
    if status != 200:
        raise RuntimeError('API HTTP status mismatch: ' + path + ' ' + str(status))
    result = json.loads(raw)
    if result.get('code') != 0:
        raise RuntimeError('API business error: ' + path + ' code=' + str(result.get('code')))
    return result.get('data')

def main():
    global token
    status, home, _ = request('/')
    assert status == 200 and b'<div id="app"' in home
    for path in ['/profile', '/admin/feeds', '/read/216']:
        status, data, _ = request(path)
        assert status == 200 and data == home
    for path in ['/assets/boom-does-not-exist.js', '/avatars/boom-does-not-exist.png', '/.vite/manifest.json']:
        assert request(path)[0] in (403, 404)
    checks['static_and_history_fallback'] = 'passed'
    assert request('/api/user/userInfo')[0] == 401
    checks['anonymous_api_guard'] = 'passed'

    username = 'qadeploy' + secrets.token_hex(4)
    password = secrets.token_urlsafe(12)
    # Store only in the root-only audit directory, never in stdout or browser URLs.
    session = {'username': username, 'password': password}
    (OUT / 'qa-session.json').write_text(json.dumps(session))
    (OUT / 'qa-session.json').chmod(0o600)
    api('/user/register', {'username': username, 'password': password, 'rePassword': password}, 'POST')
    token = api('/user/login', {'username': username, 'password': password}, 'POST')['token']
    user = api('/user/userInfo')
    session['user_id'] = user['id']
    (OUT / 'qa-session.json').write_text(json.dumps(session))
    assert user['username'] == username
    assert request('/api/admin/article/list')[0] == 403
    checks['registration_login_and_admin_guard'] = 'passed'

    articles = api('/article/list?mode=recommend&pageSize=5')['items']
    assert articles
    article = api('/article/detail/' + str(articles[0]['id']))
    assert article['id'] == articles[0]['id']
    checks['article_list_and_detail'] = 'passed'

    categories = api('/category/list')
    category = next(c for c in categories if c.get('status') == 1)
    title = 'Deployment acceptance ' + secrets.token_hex(4)
    api('/article/add', {'title': title, 'content': '<p>Private cloud deployment acceptance.</p>',
                         'categoryId': category['id'], 'state': 0}, 'POST')
    mine = api('/article/mine?pageSize=10')['items']
    draft = next(row for row in mine if row['title'] == title)
    assert api('/article/edit/' + str(draft['id']))['content'] == '<p>Private cloud deployment acceptance.</p>'
    session['article_id'] = draft['id']
    (OUT / 'qa-session.json').write_text(json.dumps(session))
    checks['database_draft_write_and_readback'] = 'passed'

    png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jVekAAAAASUVORK5CYII=')
    boundary = 'BoomTest' + secrets.token_hex(8)
    body = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="deployment-check.png"\r\nContent-Type: image/png\r\n\r\n').encode() + png + ('\r\n--' + boundary + '--\r\n').encode()
    try:
        url = api('/user/upload', body, 'POST', {'Content-Type': 'multipart/form-data; boundary=' + boundary})
        assert url.startswith('https://') and '-internal.' not in url
        with urllib.request.urlopen(url, timeout=20) as image:
            assert image.status == 200 and hashlib.sha256(image.read()).digest() == hashlib.sha256(png).digest()
        api('/user/update', {'nickname': '部署验收', 'email': 'deploy-check@example.invalid', 'avatar': url}, 'PUT')
        token = api('/user/login', {'username': username, 'password': password}, 'POST')['token']
        assert api('/user/userInfo')['avatar'] == url
        session['uploaded_url'] = url
        (OUT / 'qa-session.json').write_text(json.dumps(session))
        checks['oss_upload_public_read_and_saved_avatar'] = 'passed'
    except Exception as error:
        checks['oss_upload_public_read_and_saved_avatar'] = 'failed:' + type(error).__name__

    assert api('/article/ai/status')['enabled'] is False
    checks['paid_ai_trial_disabled'] = 'passed'
    checks['model_inference_calls'] = 0
    (OUT / 'acceptance.json').write_text(json.dumps(checks, indent=2))
    print(json.dumps(checks, indent=2))

if __name__ == '__main__':
    try:
        main()
    except Exception:
        (OUT / 'acceptance-partial.json').write_text(json.dumps(checks, indent=2))
        raise

#!/usr/bin/env python3
"""Connectivity only: fixed public URLs, no credentials, no inference endpoints."""
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import time
import urllib.request
import urllib.error

URLS = {
    'ruanyifeng': 'https://www.ruanyifeng.com/blog/atom.xml',
    'github_blog': 'https://github.blog/feed/',
    'bbc_sport': 'https://feeds.bbci.co.uk/sport/rss.xml',
    'esports_insider': 'https://esportsinsider.com/feed',
    'ithome': 'https://www.ithome.com/rss/',
    'sspai': 'https://sspai.com/feed',
    'deepseek_repository': 'https://api.github.com/repos/deepseek-ai/DeepInfer',
    'qwen_repository': 'https://api.github.com/repos/QwenLM/Qwen3',
    'deepseek_api_home': 'https://api.deepseek.com/',
}

def probe(item):
    name, url = item
    started = time.monotonic()
    req = urllib.request.Request(url, headers={'User-Agent': 'BOOM-FeedReader/1.0'})
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            response.read(512)
            result = {'http': response.status, 'redirected': response.url != url}
    except urllib.error.HTTPError as error:
        result = {'http': error.code, 'redirected': error.url != url}
    except Exception as error:
        result = {'error': type(error).__name__}
    result['seconds'] = round(time.monotonic() - started, 2)
    return name, result

with ThreadPoolExecutor(max_workers=3) as executor:
    results = dict(executor.map(probe, URLS.items()))
Path('/root/boom-deploy-audit/network.json').write_text(json.dumps(results, indent=2))
print(json.dumps(results, indent=2))

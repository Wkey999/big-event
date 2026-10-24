#!/usr/bin/env python3
"""Enable the already-authorized feed/whitelist workflow without enabling paid AI."""
from pathlib import Path
import sys

path = Path(sys.argv[1]) if len(sys.argv) == 2 else Path('/etc/boom/app.env')
text = path.read_text()
values = dict(line.split('=', 1) for line in text.splitlines() if line and not line.startswith('#'))
assert values.get('LLM_ENABLED') == 'false'
assert values.get('LLM_CHAT_PROVIDER') == 'none'
assert values.get('AI_TRIAL_ENABLED') == 'false'
for key in ('APP_CONTENT_FEEDS_ENABLED', 'APP_CONTENT_FEEDS_AUTO_PUBLISH_ENABLED'):
    assert values.get(key) in ('false', 'true')
    text = text.replace(key + '=false', key + '=true')
path.write_text(text)
print('Feed collection and existing official-source whitelist enabled; paid AI and translation remain disabled.')

"""Desktop-only public-page probe. Does not connect to or operate the phone."""
import json
import pathlib
import time
from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / 'evidence'
URL = 'https://www.douyin.com/video/7409533098766896422'
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
                                headless=True, args=['--disable-gpu'])
    context = browser.new_context(viewport={'width': 1280, 'height': 800},
                                  user_agent='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36')
    page = context.new_page()
    responses = []
    media = []
    def on_response(response):
        if 'iteminfo' in response.url or '/aweme/detail' in response.url:
            try:
                text = response.text()
                filename = f'browser-api-{len(responses)}.json'
                (EVIDENCE / filename).write_text(text, encoding='utf-8')
                responses.append({'url': response.url, 'status': response.status, 'bytes': len(text), 'file': filename})
            except Exception as exc:
                responses.append({'error': str(exc)})
        if '.douyinvod.com/' in response.url:
            media.append(response.url)
    page.on('response', on_response)
    error = None
    try:
        page.goto(URL, wait_until='domcontentloaded', timeout=40000)
        page.wait_for_timeout(12000)
        (EVIDENCE / 'browser-page.html').write_text(page.content(), encoding='utf-8')
        script = (ROOT / 'app/src/main/assets/inspect-page.js').read_text(encoding='utf-8')
        (EVIDENCE / 'browser-snapshot.json').write_text(page.evaluate(script), encoding='utf-8')
        page.screenshot(path=str(EVIDENCE / 'browser-desktop-preview.png'))
    except Exception as exc:
        error = str(exc)
    result = {'final_url': page.url, 'title': page.title(), 'api_responses': responses, 'media_count': len(media), 'error': error}
    (EVIDENCE / 'browser-probe.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    (EVIDENCE / 'browser-media.json').write_text(json.dumps(media, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))
    browser.close()

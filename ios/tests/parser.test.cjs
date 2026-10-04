'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert/strict');
const root = path.resolve(__dirname, '..');
const context = vm.createContext({});
vm.runInContext(fs.readFileSync(path.join(root, 'Shiying/Resources/parser.js'), 'utf8'), context);
const parser = context.ShiyingParser;
const id = '7409533098766896422', other = '7409533098766896423';
const cdn = 'https://v26-web.douyinvod.com/video/a.mp4';
function fixture(extra = {}) {
  return Object.assign({aweme_id: id, desc: '测试 + {字幕}', author: {nickname: '作者'}, video_control: {allow_download: false}, video: {play_addr: {url_list: [cdn]}, download_addr: {url_list: ['https://v26-web.douyinvod.com/video/watermarked.mp4']}}}, extra);
}
function parse(value, target = id) { return JSON.parse(parser.parse(typeof value === 'string' ? value : JSON.stringify(value), target)); }
let passed = 0;
function test(name, fn) { fn(); passed++; process.stdout.write('PASS ' + name + '\n'); }
test('extract full share text', () => assert.equal(parser.extract('复制 https://v.douyin.com/HOpFK-XHaEg/ 打开'), 'https://v.douyin.com/HOpFK-XHaEg/'));
test('markdown URL punctuation', () => assert.equal(parser.extract('[视频](https://v.douyin.com/HOpFK-XHaEg/)'), 'https://v.douyin.com/HOpFK-XHaEg/'));
test('HTTP upgrade', () => assert.equal(parser.extract('http://v.douyin.com/test/'), 'https://v.douyin.com/test/'));
test('reject deceptive hosts', () => assert.throws(() => parser.extract('https://douyin.com.evil.test/video/123')));
test('reject credentials', () => assert.equal(parser.isShare('https://user@douyin.com/video/123'), false));
test('reject non HTTPS and port', () => { assert.equal(parser.isShare('http://douyin.com/'), false); assert.equal(parser.isShare('https://douyin.com:444/'), false); });
test('reject slash confusion', () => assert.equal(parser.isShare('https://douyin.com\\@evil.test/'), false));
test('allow standard TLS port', () => assert.equal(parser.isShare('https://www.douyin.com:443/'), true));
test('reject empty and oversized share input', () => { assert.throws(() => parser.extract('')); assert.throws(() => parser.extract('a'.repeat(32769))); });
test('extract canonical ID', () => assert.equal(parser.videoID('https://www.douyin.com/video/' + id), id));
test('extract query ID', () => assert.equal(parser.videoID('https://www.douyin.com/?modal_id=' + id + '&x=1'), id));
test('reject overlong ID', () => assert.equal(parser.videoID('https://www.douyin.com/video/' + '1'.repeat(30)), ''));
test('download disabled does not hide public playback', () => { const v = parse(fixture()); assert.equal(v.id, id); assert.deepEqual(v.urls, [cdn]); });
test('download watermark field excluded', () => { const f = fixture(); delete f.video.play_addr; assert.equal(parse(f), null); });
test('wrong video rejected', () => assert.equal(parse(fixture({aweme_id: other})), null));
test('nonnumeric IDs cannot form filenames', () => assert.equal(parse(fixture({aweme_id: '../outside'}), ''), null));
test('private or deleted video rejected', () => { assert.equal(parse(fixture({status: {is_private: true}})), null); assert.equal(parse(fixture({status: {is_delete: true}})), null); });
test('recommendation skipped but matching sibling used', () => assert.equal(parse({items: [fixture({aweme_id: other}), fixture()]}).id, id));
test('percent encoded RENDER_DATA preserves plus', () => { const html = '<script id="RENDER_DATA">' + encodeURIComponent(JSON.stringify(fixture())) + '</script>'; assert.equal(parse(html).title, '测试 + {字幕}'); });
test('balanced assignment ignores trailing JS', () => { const html = '<script>window._ROUTER_DATA = ' + JSON.stringify(fixture()) + ';window.other={};</script>'; assert.equal(parse(html).id, id); });
test('JSON script body', () => assert.equal(parse('<script type="application/json">' + JSON.stringify(fixture()) + '</script>').id, id));
test('malformed data does not execute', () => { assert.equal(parse('<script>window._ROUTER_DATA = alert(1)</script>'), null); assert.equal(parse('{bad'), null); });
test('codec order and bitrate preference', () => {
  const v = parse(fixture({video: {play_addr_h264: cdn, bit_rate: [
    {bit_rate: 10, play_addr: 'https://v1.douyinvod.com/low.mp4'},
    {bit_rate: 999, gear_name: 'bytevc2', play_addr: 'https://v1.douyinvod.com/unsupported.mp4'},
    {bit_rate: 100, play_addr: 'https://v1.douyinvod.com/high.mp4'}]}}));
  assert.deepEqual(v.urls, [cdn, 'https://v1.douyinvod.com/high.mp4', 'https://v1.douyinvod.com/low.mp4']);
});
test('alternate API camel fields', () => assert.equal(parse({awemeId: id, title: 'x', video: {bitrateInfo: [{Bitrate: 10, PlayAddr: {UrlList: [cdn]}}]}}).urls[0], cdn));
test('playwm normalized to public play', () => assert.equal(parse(fixture({video: {play_addr: 'https://aweme.snssdk.com/aweme/v1/playwm/?video_id=abcdabcd&amp;line=0'}})).urls[0], 'https://aweme.snssdk.com/aweme/v1/play/?video_id=abcdabcd&line=0'));
test('URI fallback', () => assert.ok(parse(fixture({video: {play_addr: {uri: 'v0200123456789'}}})).urls[0].includes('video_id=v0200123456789')));
test('URL list deduplication', () => assert.equal(parse(fixture({video: {play_addr: [cdn, cdn]}})).urls.length, 1));
test('untrusted media and HLS rejected', () => { assert.equal(parser.isMedia('https://evil.test/a.mp4'), false); assert.equal(parser.isMedia('https://v1.douyinvod.com/a.m3u8'), false); });
test('scheme-relative playback upgraded', () => assert.equal(parse(fixture({video: {play_addr: '//v1.douyinvod.com/a.mp4'}})).urls[0], 'https://v1.douyinvod.com/a.mp4'));
test('depth bounded', () => { let f = fixture(); for (let i=0;i<60;i++) f = {nested:f}; assert.equal(parse(f), null); });
test('oversized page ignored', () => assert.equal(parser.parse('x'.repeat(8*1024*1024+1), id), 'null'));
function snapshot(extra = {}) { return Object.assign({href: 'https://www.douyin.com/video/' + id, title: '原视频', states: [], videoSrc: 'blob:local', resources: [cdn]}, extra); }
test('browser blob falls back to MP4 resources', () => assert.equal(JSON.parse(parser.snapshot(JSON.stringify(snapshot()), id)).urls[0], cdn));
test('browser states preferred', () => assert.equal(JSON.parse(parser.snapshot(JSON.stringify(snapshot({states: [JSON.stringify(fixture())]})), id)).author, '作者'));
test('browser wrong page blocked', () => assert.throws(() => parser.snapshot(JSON.stringify(snapshot({href: 'https://www.douyin.com/video/' + other})), id)));
test('browser external page blocked', () => assert.throws(() => parser.snapshot(JSON.stringify(snapshot({href: 'https://evil.test/video/' + id})), id)));
test('browser no playable media returns null', () => assert.equal(parser.snapshot(JSON.stringify(snapshot({resources: []})), id), 'null'));
test('inspector production script output', () => {
  const script = fs.readFileSync(path.join(root, 'Shiying/Resources/inspect-page.js'), 'utf8');
  const raw = vm.runInNewContext(script, {location: {href: snapshot().href}, document: {title:'原视频', querySelectorAll: s => s === 'video' ? [{paused:false,currentTime:1,currentSrc:'blob:local'}] : []}, window: {}, performance: {getEntriesByType: () => [{name:cdn}]}});
  assert.equal(JSON.parse(parser.snapshot(raw, id)).urls[0], cdn);
});
const evidence = path.resolve(root, '../evidence');
let realEvidence = false;
if (fs.existsSync(path.join(evidence, 'browser-api-0.json'))) {
  test('previous real share API shape', () => { const result = parse(fs.readFileSync(path.join(evidence, 'browser-api-0.json'), 'utf8')); assert.equal(result.id, id); assert.ok(result.urls.length > 0); });
  test('previous real browser snapshot shape', () => { const result = JSON.parse(parser.snapshot(fs.readFileSync(path.join(evidence, 'browser-snapshot.json'), 'utf8'), id)); assert.equal(result.id, id); assert.ok(result.urls.length > 0); });
  realEvidence = true;
}
const report = {passed, realEvidence, scope: 'Production JavaScript parser and inspector, executed in Node; not an iOS build or device test.'};
console.log(JSON.stringify(report));
if (process.env.REPORT_FILE) fs.writeFileSync(process.env.REPORT_FILE, JSON.stringify(report, null, 2) + '\n');

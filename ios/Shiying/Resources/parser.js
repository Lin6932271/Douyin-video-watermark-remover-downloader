(function (root) {
  'use strict';
  var shareDomains = ['douyin.com', 'iesdouyin.com', 'amemv.com'];
  var mediaDomains = shareDomains.concat(['douyinvod.com', 'snssdk.com', 'bytecdn.cn', 'bytecdn.com', 'bytedance.com', 'zijieapi.com', 'pstatp.com', 'bytedance.net']);
  function trusted(url, domains) {
    if (typeof url !== 'string' || /[\s\\\x00-\x1f]/.test(url)) return false;
    var m = /^https:\/\/([a-z0-9.-]+)(?::443)?(?:[/?#]|$)/i.exec(url);
    if (!m) return false;
    var host = m[1].toLowerCase();
    return domains.some(function (d) { return host === d || host.slice(-d.length - 1) === '.' + d; });
  }
  function media(url) { return trusted(url, mediaDomains) && !/\.m3u8|\/playwm\//i.test(url); }
  function normalize(url) {
    if (typeof url !== 'string') return '';
    return url.replace(/&amp;/g, '&').replace(/^\/\//, 'https://').replace(/^http:\/\//i, 'https://').replace(/\/playwm\//g, '/play/');
  }
  function id(url) {
    var m = /\/(?:share\/)?(?:video|note)\/(\d{10,25})(?:[/?#]|$)/.exec(url || '') || /[?&](?:modal_id|aweme_id|item_ids)=(\d{10,25})(?:[&#]|$)/.exec(url || '');
    return m ? m[1] : '';
  }
  function extract(text) {
    if (!text || text.length > 32768) throw new Error('分享文本为空或过长');
    var links = text.match(/https?:\/\/[^\s<>"'，。！？；（）【】]+/ig) || [];
    for (var i = 0; i < links.length; i++) {
      var url = links[i].replace(/[)\]}>.,;!]+$/, '').replace(/^http:\/\//i, 'https://');
      if (trusted(url, shareDomains)) return url;
    }
    throw new Error('没有找到抖音链接，请粘贴完整分享文本');
  }
  function address(info, value) {
    if (typeof value === 'string') {
      var url = normalize(value);
      if (media(url) && info.urls.indexOf(url) < 0) info.urls.push(url);
    } else if (Array.isArray(value)) value.slice(0, 1000).forEach(function (v) { address(info, v); });
    else if (value && typeof value === 'object') {
      var urls = value.url_list || value.UrlList;
      address(info, urls); address(info, value.src); address(info, value.url);
      if ((!urls || !urls.length) && /^[A-Za-z0-9_-]{8,120}$/.test(value.uri || '')) {
        address(info, 'https://aweme.snssdk.com/aweme/v1/play/?video_id=' + value.uri + '&ratio=1080p&line=0');
      }
    }
  }
  function walk(node, target, depth) {
    if (!node || typeof node !== 'object' || depth > 48) return null;
    if (Array.isArray(node)) {
      for (var i = 0; i < Math.min(node.length, 1000); i++) { var result = walk(node[i], target, depth + 1); if (result) return result; }
      return null;
    }
    var videoID = String(node.aweme_id || node.awemeId || node.itemId || node.id || '');
    var video = node.video;
    if (video && videoID) {
      if (!/^\d{10,25}$/.test(videoID)) return null;
      if ((target && videoID !== target) || (node.status && (node.status.is_delete || node.status.is_private))) return null;
      var info = { id: videoID, title: String(node.desc || node.title || '抖音视频'), author: String((node.author || {}).nickname || (node.author || {}).uniqueId || ''), source: '', urls: [] };
      address(info, video.play_addr_h264);
      (video.bit_rate || video.bitrateInfo || []).filter(function (b) { return b && !/bytevc2/.test(b.gear_name || ''); })
        .sort(function (a, b) { return (b.bit_rate || b.Bitrate || 0) - (a.bit_rate || a.Bitrate || 0); })
        .forEach(function (b) { address(info, b.play_addr || b.PlayAddr); });
      address(info, video.play_addr); address(info, video.playAddr); address(info, video.play_addr_bytevc1);
      // download_addr is excluded. Only public playback addresses are collected.
      if (info.urls.length) return info;
    }
    var keys = Object.keys(node);
    for (var k = 0; k < keys.length; k++) { var found = walk(node[keys[k]], target, depth + 1); if (found) return found; }
    return null;
  }
  function balanced(text) {
    text = text.trim();
    if (text[0] !== '{' && text[0] !== '[') return '';
    var stack = [], quoted = false, escaped = false;
    for (var i = 0; i < text.length; i++) {
      var c = text[i];
      if (quoted) { if (escaped) escaped = false; else if (c === '\\') escaped = true; else if (c === '"') quoted = false; continue; }
      if (c === '"') quoted = true;
      else if (c === '{' || c === '[') stack.push(c);
      else if (c === '}' || c === ']') {
        if (stack.pop() !== (c === '}' ? '{' : '[')) return '';
        if (!stack.length) return text.slice(0, i + 1);
      }
    }
    return '';
  }
  function parse(text, target) {
    if (!text || text.length > 8 * 1024 * 1024) return null;
    function candidate(s) { try { return walk(JSON.parse(balanced(s)), target || '', 0); } catch (_) { return null; } }
    var found = candidate(text); if (found) return found;
    var re = /<script\b([^>]*)>([\s\S]*?)<\/script>/ig, m;
    while ((m = re.exec(text))) {
      var body = m[2].trim();
      if (/RENDER_DATA/i.test(m[1])) { try { found = candidate(decodeURIComponent(body)); } catch (_) {} }
      else found = candidate(body);
      if (found) return found;
      var assignment = /(?:window\.)?(?:_ROUTER_DATA|__INITIAL_STATE__|__NEXT_DATA__|SIGI_STATE)\s*=\s*/g, a;
      while ((a = assignment.exec(body))) { found = candidate(body.slice(assignment.lastIndex)); if (found) return found; }
    }
    return null;
  }
  function snapshot(raw, target) {
    var s = JSON.parse(raw), videoID = id(s.href);
    if (!trusted(s.href, shareDomains) || !videoID) throw new Error('请打开具体的视频页面');
    if (target && target !== videoID) throw new Error('当前页面不是原视频，请返回原视频页面');
    var states = s.states || [];
    for (var i = 0; i < Math.min(states.length, 30); i++) { var info = parse(states[i], videoID); if (info) { info.source = s.href; return info; } }
    var fallback = { id: videoID, title: String(s.title || '抖音视频'), author: '', source: s.href, urls: [] };
    address(fallback, s.videoSrc);
    if (!fallback.urls.length) (s.resources || []).slice(-12).reverse().forEach(function (url) { address(fallback, url); });
    return fallback.urls.length ? fallback : null;
  }
  root.ShiyingParser = Object.freeze({ extract: extract, videoID: id, isShare: function (u) { return trusted(u, shareDomains); }, isMedia: media, parse: function (s, i) { return JSON.stringify(parse(s, i)); }, snapshot: function (s, i) { return JSON.stringify(snapshot(s, i)); } });
})(this);

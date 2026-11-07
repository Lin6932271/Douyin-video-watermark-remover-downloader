(function (root) {
  'use strict';
  var shareDomains = ['douyin.com', 'iesdouyin.com', 'amemv.com'];
  var mediaDomains = shareDomains.concat(['douyinvod.com', 'snssdk.com', 'bytecdn.cn', 'bytecdn.com', 'bytedance.com', 'zijieapi.com', 'pstatp.com', 'bytedance.net', 'douyinpic.com', 'byteimg.com', 'ibytedtos.com']);
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
    var m = /\/(?:share\/)?(?:video|note|slides)\/(\d{10,25})(?:[/?#]|$)/.exec(url || '') || /[?&](?:modal_id|aweme_id|item_ids)=(\d{10,25})(?:[&#]|$)/.exec(url || '');
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
  function address(info, value, allowVideoID) {
    if (typeof value === 'string') {
      var url = normalize(value);
      if (media(url) && info.urls.indexOf(url) < 0) info.urls.push(url);
    } else if (Array.isArray(value)) value.slice(0, 1000).forEach(function (v) { address(info, v, allowVideoID); });
    else if (value && typeof value === 'object') {
      var urls = value.url_list || value.UrlList || value.urlList;
      address(info, urls, allowVideoID); address(info, value.src, allowVideoID); address(info, value.url, allowVideoID);
      if (allowVideoID !== false && (!urls || !urls.length) && /^[A-Za-z0-9_-]{8,120}$/.test(value.uri || '')) {
        address(info, 'https://aweme.snssdk.com/aweme/v1/play/?video_id=' + value.uri + '&ratio=1080p&line=0');
      }
    }
  }
  function videoAddresses(info, video) {
    address(info, video.play_addr_h264);
    (video.bit_rate || video.bitrateInfo || []).filter(function (b) { return b && !/bytevc2/.test(b.gear_name || ''); })
      .sort(function (a, b) { return (b.bit_rate || b.Bitrate || 0) - (a.bit_rate || a.Bitrate || 0); })
      .forEach(function (b) { address(info, b.play_addr || b.PlayAddr); });
    address(info, video.play_addr); address(info, video.playAddr); address(info, video.play_addr_bytevc1);
  }
  function imageArray(node) {
    var sources = [node.image_post_info || node.imagePostInfo, node];
    var keys = ['images', 'image_list', 'imageList', 'image_infos', 'original_images'];
    for (var i = 0; i < sources.length; i++) {
      if (!sources[i]) continue;
      for (var k = 0; k < keys.length; k++) {
        var a = sources[i][keys[k]];
        if (Array.isArray(a) && a.length) return a;
      }
    }
    return null;
  }
  function album(info, images) {
    info.items = []; info.warning = '';
    var missing = 0, missingMotion = 0;
    images.slice(0, 1000).forEach(function (raw, index) {
      var image = typeof raw === 'string' ? {url: raw} : (raw || {});
      var still = {kind: 'image', index: index + 1, urls: []};
      ['animated_image', 'animatedImage', 'dynamic_image', 'dynamicImage'].forEach(function (key) { address(still, image[key], false); });
      if (still.urls.length) still.kind = 'animation';
      if (still.kind !== 'animation') {
        ['origin_image', 'originImage', 'original_image', 'display_image', 'displayImage'].forEach(function (key) { address(still, image[key], false); });
        address(still, image, false);
      }
      if (still.urls.length) info.items.push(still); else missing++;
      var clip = {kind: 'live_video', index: index + 1, urls: []};
      [image, image.live_photo || image.livePhoto].forEach(function (container) {
        if (!container) return;
        if (container.video) videoAddresses(clip, container.video);
        ['video_play_addr', 'videoPlayAddr', 'play_addr', 'playAddr', 'playApi'].forEach(function (key) { address(clip, container[key]); });
      });
      if (clip.urls.length) info.items.push(clip);
      else if (Number(image.live_photo_type) > 0 || image.clip_type === 5) missingMotion++;
    });
    if (missing) info.warning += missing + ' 张图片缺少可用地址。';
    if (missingMotion) info.warning += missingMotion + ' 张实况未返回动态片段；可尝试内置网页重新读取。';
    if (images.length > 1000) info.warning += '图集过大，仅读取前 1000 项。';
    return info.items.length ? info : null;
  }
  function walk(node, target, depth) {
    if (!node || typeof node !== 'object' || depth > 48) return null;
    if (Array.isArray(node)) {
      for (var i = 0; i < Math.min(node.length, 1000); i++) { var result = walk(node[i], target, depth + 1); if (result) return result; }
      return null;
    }
    var videoID = String(node.aweme_id || node.awemeId || node.itemId || node.id || '');
    var video = node.video, images = imageArray(node);
    if ((video || images) && videoID) {
      if (!/^\d{10,25}$/.test(videoID)) return null;
      if ((target && videoID !== target) || (node.status && (node.status.is_delete || node.status.is_private))) return null;
      var info = {id: videoID, title: String(node.desc || node.title || '抖音作品'), author: String((node.author || {}).nickname || (node.author || {}).uniqueId || ''), source: '', urls: []};
      if (images) return album(info, images);
      videoAddresses(info, video);
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
    if (!fallback.urls.length && !/(?:note|slides)\//.test(s.href)) (s.resources || []).slice(-12).reverse().forEach(function (url) { address(fallback, url); });
    return fallback.urls.length ? fallback : null;
  }
  root.ShiyingParser = Object.freeze({ extract: extract, videoID: id, isShare: function (u) { return trusted(u, shareDomains); }, isMedia: media, parse: function (s, i) { return JSON.stringify(parse(s, i)); }, snapshot: function (s, i) { return JSON.stringify(snapshot(s, i)); } });
})(this);

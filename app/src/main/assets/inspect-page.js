(function () {
  var result = {href: location.href, title: document.title, states: [], videoSrc: ''};
  function add(value) {
    try {
      var text = typeof value === 'string' ? value : JSON.stringify(value);
      if (text && text.length < 3000000) result.states.push(text);
    } catch (_) {}
  }
  add(window._ROUTER_DATA);
  add(window.__INITIAL_STATE__);
  add(window.__NEXT_DATA__);
  add(window.SIGI_STATE);
  document.querySelectorAll('script').forEach(function (node) {
    if (/RENDER_DATA|__NEXT_DATA__|SIGI_STATE/.test(node.id)) {
      try { add(node.id === 'RENDER_DATA' ? decodeURIComponent(node.textContent) : node.textContent); } catch (_) {}
    }
  });
  var videos = Array.from(document.querySelectorAll('video'));
  var active = videos.find(function (v) { return !v.paused && v.currentTime > 0; }) || videos[0];
  if (active) result.videoSrc = active.currentSrc || active.src || '';
  result.resources = performance.getEntriesByType('resource').map(function (r) { return r.name; })
    .filter(function (url) { return /https:\/\/[^/]+\.douyinvod\.com\//.test(url) && !/\.m3u8/.test(url); }).slice(-12);
  return JSON.stringify(result);
})();

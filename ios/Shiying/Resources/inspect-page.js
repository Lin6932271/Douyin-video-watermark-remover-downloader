(function () {
  // Capture only detail responses already requested by this page. No native JS bridge or cookie export.
  if (!window.__shiyingCapture) {
    window.__shiyingCapture = true;
    window.__shiyingBodies = [];
    function remember(url, body) {
      if (/\/aweme\/v\d+\/(?:web\/)?aweme\/detail\//.test(String(url)) && typeof body === 'string' && body.length < 1500000) {
        window.__shiyingBodies.push(body);
        window.__shiyingBodies = window.__shiyingBodies.slice(-3);
      }
    }
    if (window.fetch) {
      var originalFetch = window.fetch;
      window.fetch = function () {
        return originalFetch.apply(this, arguments).then(function (response) {
          if (/\/aweme\/v\d+\/(?:web\/)?aweme\/detail\//.test(response.url))
            response.clone().text().then(function (body) { remember(response.url, body); }).catch(function () {});
          return response;
        });
      };
    }
    if (window.XMLHttpRequest) {
      var originalOpen = window.XMLHttpRequest.prototype.open;
      window.XMLHttpRequest.prototype.open = function (method, url) {
        this.addEventListener('load', function () {
          try { remember(url, this.responseType === 'json' ? JSON.stringify(this.response) : this.responseText); } catch (_) {}
        });
        return originalOpen.apply(this, arguments);
      };
    }
  }
  var result = {href: location.href, title: document.title, states: [], videoSrc: ''};
  var total = 0;
  function add(value) {
    try {
      var text = typeof value === 'string' ? value : JSON.stringify(value);
      if (text && text.length < 3000000 && total + text.length < 6500000) { result.states.push(text); total += text.length; }
    } catch (_) {}
  }
  add(window._ROUTER_DATA);
  add(window.__INITIAL_STATE__);
  add(window.__NEXT_DATA__);
  add(window.SIGI_STATE);
  (window.__shiyingBodies || []).forEach(add);
  document.querySelectorAll('script').forEach(function (node) {
    if (/RENDER_DATA|__NEXT_DATA__|SIGI_STATE/.test(node.id)) {
      try { add(node.id === 'RENDER_DATA' ? decodeURIComponent(node.textContent) : node.textContent); } catch (_) {}
    }
  });
  // Modern React pages may retain the current detail only in props, not in a global SSR object.
  var match = /\/(?:video|note|slides)\/(\d{10,25})/.exec(location.href) || /[?&]modal_id=(\d{10,25})/.exec(location.href);
  if (match) {
    var seen = new WeakSet(), visited = 0;
    function scan(value, depth) {
      if (!value || typeof value !== 'object' || depth > 24 || visited++ > 2500 || seen.has(value)) return;
      seen.add(value);
      if (String(value.aweme_id || value.awemeId || value.itemId || '') === match[1] && (value.video || value.images || value.image_post_info || value.imagePostInfo)) { add(value); return; }
      Object.keys(value).slice(0, 80).forEach(function (key) {
        if (key !== 'stateNode' && key !== 'return' && key !== 'ownerDocument') {
          try { scan(value[key], depth + 1); } catch (_) {}
        }
      });
    }
    Array.from(document.querySelectorAll('#root, #douyin-right-container, [data-e2e="note-detail"], [data-e2e="video-detail"]')).slice(0, 6).forEach(function (node) {
      Object.keys(node).filter(function (key) { return /^__react(?:Props|Fiber)/.test(key); }).forEach(function (key) { scan(node[key], 0); });
    });
  }
  var videos = Array.from(document.querySelectorAll('video'));
  var active = videos.find(function (v) { return !v.paused && v.currentTime > 0; }) || videos[0];
  if (active) result.videoSrc = active.currentSrc || active.src || '';
  result.resources = performance.getEntriesByType('resource').map(function (r) { return r.name; })
    .filter(function (url) { return /https:\/\/[^/]+\.douyinvod\.com\//.test(url) && !/\.m3u8/.test(url); }).slice(-12);
  return JSON.stringify(result);
})();

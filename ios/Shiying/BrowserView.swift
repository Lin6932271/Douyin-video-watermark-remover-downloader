import SwiftUI
import WebKit

@MainActor
final class BrowserController: NSObject, ObservableObject, WKNavigationDelegate, WKUIDelegate {
    let id = UUID()
    let webView: WKWebView
    private let initial: URL
    private var target: String
    private var timer: Timer?
    private var polls = 0
    private var reading = false
    private var delivered = false
    @Published var status = "正在加载网页；出现验证时请在这里完成"
    var onVideo: ((VideoInfo, [HTTPCookie]) -> Void)?

    init(url: URL, target: String) {
        self.initial = url
        self.target = target
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init()
        webView.customUserAgent = WebHeaders.desktopUA
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.load(URLRequest(url: initial))
    }
    func start() {
        stop()
        polls = 0
        timer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                self.polls += 1
                self.read()
                if self.polls >= 30 { self.stop() }
            }
        }
    }
    func stop() { timer?.invalidate(); timer = nil }
    func read() {
        guard !reading, !delivered, let url = webView.url, let parser = try? ParserBridge(), parser.isShare(url) else { return }
        let currentID = parser.videoID(url)
        guard !currentID.isEmpty else { status = "请等页面打开具体视频，再点读取播放地址"; return }
        if target.isEmpty { target = currentID }
        guard currentID == target else { status = "页面不是原视频，请返回原视频"; return }
        guard let scriptURL = Bundle.main.url(forResource: "inspect-page", withExtension: "js"),
              let script = try? String(contentsOf: scriptURL, encoding: .utf8) else { status = "网页读取资源缺失"; return }
        reading = true
        webView.evaluateJavaScript(script) { [weak self] value, error in
            guard let self = self else { return }
            self.reading = false
            if let error = error { self.status = "读取失败：\(error.localizedDescription)"; return }
            do {
                guard let raw = value as? String, let info = try parser.snapshot(raw, id: self.target) else {
                    self.status = "暂未找到播放地址，可点播放视频后再读取"
                    return
                }
                self.delivered = true
                self.stop()
                self.status = "播放地址已读取"
                self.webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { [weak self] cookies in
                    self?.onVideo?(info, cookies)
                }
            } catch { self.status = error.localizedDescription }
        }
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else { decisionHandler(.cancel); return }
        if navigationAction.targetFrame?.isMainFrame == false {
            decisionHandler(url.scheme == "https" || url.absoluteString == "about:blank" ? .allow : .cancel)
            return
        }
        guard let parser = try? ParserBridge(), parser.isShare(url) else {
            status = "已拦截外部跳转；保留当前网页"
            decisionHandler(.cancel); return
        }
        let newID = parser.videoID(url)
        if !target.isEmpty && !newID.isEmpty && newID != target {
            status = "已拦截切换到其他视频"
            decisionHandler(.cancel); return
        }
        if target.isEmpty && !newID.isEmpty { target = newID }
        decisionHandler(.allow)
    }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { if !delivered { start(); read() } }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code != NSURLErrorCancelled { status = "网页加载失败：\(error.localizedDescription)" }
    }
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if let url = navigationAction.request.url, let parser = try? ParserBridge(), parser.isShare(url) { webView.load(navigationAction.request) }
        return nil
    }
    deinit { timer?.invalidate() }
}

@MainActor
struct EmbeddedBrowser: UIViewRepresentable {
    let controller: BrowserController
    func makeUIView(context: Context) -> WKWebView { controller.webView }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

@MainActor
struct BrowserView: View {
    @ObservedObject var controller: BrowserController
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Text(controller.status).font(.footnote).padding(10).frame(maxWidth: .infinity).background(Color.secondary.opacity(0.12))
                EmbeddedBrowser(controller: controller)
                Button("读取播放地址") { controller.read(); controller.start() }.buttonStyle(.borderedProminent).padding()
            }
            .navigationTitle("网页辅助解析").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) { Button("重载") { controller.webView.reload(); controller.start() } }
                ToolbarItem(placement: .navigationBarTrailing) { Button("关闭") { dismiss() } }
            }
        }.onAppear { controller.start() }.onDisappear { controller.stop() }
    }
}

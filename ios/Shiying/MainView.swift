import SwiftUI
import AVKit
import UIKit

@MainActor
final class MainModel: ObservableObject {
    @Published var text = ""
    @Published var video: VideoInfo?
    @Published var resolving = false
    @Published var browser: BrowserController?
    @Published var showBrowser = false
    @Published var showCloud = false
    @Published var cloudKey = ""
    @Published var errorMessage: String?
    @Published var notice = "仅从公开播放页面读取，不添加下载水印"
    private(set) var cookies: [HTTPCookie] = []
    private var resolution: Resolution?
    private var task: Task<Void, Never>?
    private var generation = UUID()

    func resolve() {
        task?.cancel()
        let token = UUID(); generation = token
        video = nil; cookies = []; resolution = nil; browser?.stop(); browser = nil
        errorMessage = nil; resolving = true; notice = "正在展开链接和解析网页"
        let input = text
        task = Task {
            defer { if generation == token { resolving = false } }
            do {
                let result = try await Resolver.resolve(input)
                try Task.checkCancellation()
                resolution = result
                if let info = result.video { video = info; notice = "播放地址已解析" }
                else { notice = "网页需要渲染，已打开辅助解析"; openBrowser() }
            } catch is CancellationError { }
              catch { if generation == token { errorMessage = error.localizedDescription; notice = "解析失败，可修改链接后重试" } }
        }
    }
    func cancel() { generation = UUID(); task?.cancel(); task = nil; resolving = false; notice = "解析已取消" }
    func openBrowser() {
        do {
            let parser = try ParserBridge()
            let url = try resolution?.url ?? parser.extract(text)
            let target = resolution?.id ?? parser.videoID(url)
            if browser == nil {
                let controller = BrowserController(url: url, target: target)
                controller.onVideo = { [weak self] info, cookies in
                    self?.video = info
                    self?.cookies = cookies
                    self?.notice = "网页播放地址已读取"
                    self?.showBrowser = false
                }
                browser = controller
            }
            showBrowser = true
        } catch { errorMessage = error.localizedDescription }
    }
    func cloud() {
        guard !resolving else { return }
        task?.cancel()
        let token = UUID(); generation = token
        resolving = true; showCloud = false; notice = "正在调用可选的 Firecrawl 云解析"
        task = Task {
            defer { if generation == token { resolving = false } }
            do {
                let parser = try ParserBridge()
                let url = try resolution?.url ?? parser.extract(text)
                let info = try await Resolver.cloud(url: url, id: resolution?.id ?? parser.videoID(url), apiKey: cloudKey)
                try Task.checkCancellation()
                video = info; cookies = []; notice = "云解析完成"
            } catch is CancellationError { }
              catch { if generation == token { errorMessage = error.localizedDescription } }
        }
    }
}

private struct ShareItem: Identifiable { let id = UUID(); let url: URL }

@MainActor
struct MainView: View {
    @StateObject private var model = MainModel()
    @EnvironmentObject private var downloads: DownloadStore
    @Environment(\.scenePhase) private var scenePhase
    @State private var player: SavedVideo?
    @State private var share: ShareItem?
    @State private var deleting: SavedVideo?
    @State private var saving: Set<UUID> = []
    private let accent = Color(red: 0.26, green: 0.91, blue: 0.76)
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Label("拾影", systemImage: "arrow.down.circle.fill").font(.largeTitle.bold()).foregroundStyle(accent)
                        Text("粘贴分享链接，把视频留在手机里").foregroundStyle(.secondary)
                    }.padding(.top, 12)
                    VStack(alignment: .leading, spacing: 12) {
                        Text("抖音分享链接").font(.headline)
                        TextEditor(text: $model.text).frame(minHeight: 100, maxHeight: 130)
                            .scrollContentBackground(.hidden).padding(8).background(Color.black.opacity(0.25)).cornerRadius(12)
                            .autocorrectionDisabled().textInputAutocapitalization(.never)
                        HStack {
                            Button { if let value = UIPasteboard.general.string { model.text = value } else { model.errorMessage = "剪贴板没有文本" } } label: { Label("粘贴", systemImage: "doc.on.clipboard") }
                                .disabled(model.resolving || downloads.downloading)
                            Spacer()
                            if model.resolving {
                                ProgressView()
                                Button("取消") { model.cancel() }
                            } else {
                                Button("解析视频") { model.resolve() }.buttonStyle(.borderedProminent)
                                    .disabled(model.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || downloads.downloading)
                            }
                        }
                        Text(model.notice).font(.footnote).foregroundStyle(.secondary)
                        HStack {
                            Button("网页辅助解析") { model.openBrowser() }
                            Spacer()
                            Button("可选云解析") { model.showCloud = true }
                        }.font(.footnote).disabled(model.resolving || downloads.downloading || model.text.isEmpty)
                    }.padding().background(cardBackground).cornerRadius(18)
                    if let info = model.video {
                        VStack(alignment: .leading, spacing: 12) {
                            Text(info.title).font(.headline).lineLimit(4)
                            if !info.author.isEmpty { Text("作者：\(info.author)").font(.subheadline).foregroundStyle(.secondary) }
                            Text("视频 ID：\(info.id)").font(.caption).foregroundStyle(.secondary).textSelection(.enabled)
                            Button { downloads.download(info, cookies: model.cookies) } label: { Label("下载原播放视频", systemImage: "arrow.down.to.line") }
                                .buttonStyle(.borderedProminent).disabled(downloads.downloading || model.resolving)
                        }.padding().frame(maxWidth: .infinity, alignment: .leading).background(cardBackground).cornerRadius(18)
                    }
                    if downloads.downloading {
                        VStack(alignment: .leading, spacing: 8) {
                            ProgressView(value: downloads.progress)
                            HStack { Text(downloads.status).font(.footnote); Spacer(); Button("取消下载", role: .destructive) { downloads.cancel() } }
                            Text("下载时保持应用在前台").font(.caption).foregroundStyle(.secondary)
                        }.padding().background(cardBackground).cornerRadius(18)
                    } else { Text(downloads.status).font(.footnote).foregroundStyle(.secondary) }
                    HStack { Text("下载记录").font(.title3.bold()); Spacer(); Text("\(downloads.history.count) 个").foregroundStyle(.secondary) }
                    if downloads.history.isEmpty { Text("下载完成后，可保存到相册或分享到文件").font(.subheadline).foregroundStyle(.secondary) }
                    ForEach(downloads.history) { entry in
                        VStack(alignment: .leading, spacing: 10) {
                            Text(entry.video.title).font(.headline).lineLimit(2)
                            Text("\(ByteCountFormatter.string(fromByteCount: entry.bytes, countStyle: .file)) · \(entry.created.formatted(date: .abbreviated, time: .shortened))")
                                .font(.caption).foregroundStyle(.secondary)
                            HStack {
                                Button("播放") { player = entry }
                                Button(saving.contains(entry.id) ? "保存中…" : "存入相册") {
                                    saving.insert(entry.id)
                                    Task { @MainActor in
                                        defer { saving.remove(entry.id) }
                                        do { try await downloads.saveToPhotos(entry); model.notice = "已存入系统相册" }
                                        catch { model.errorMessage = error.localizedDescription }
                                    }
                                }.disabled(saving.contains(entry.id))
                                Button("分享") { share = ShareItem(url: entry.url) }
                                Spacer()
                                Button(role: .destructive) { deleting = entry } label: { Image(systemName: "trash") }.disabled(saving.contains(entry.id))
                            }.font(.subheadline)
                        }.padding().background(cardBackground).cornerRadius(16)
                    }
                    Text("读取的是公开播放地址，不依赖作者的下载按钮。私密、删除、付费或无法播放的视频不做突破；已烧进画面的水印不会被擦除。平台接口变化或验证可能影响解析。")
                        .font(.caption).foregroundStyle(.secondary)
                }.padding()
            }
            .background(Color(red: 0.04, green: 0.07, blue: 0.12).ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar).tint(accent)
        }
        .sheet(isPresented: $model.showBrowser) { if let controller = model.browser { BrowserView(controller: controller) } }
        .sheet(isPresented: $model.showCloud) {
            NavigationStack {
                Form {
                    Section("Firecrawl（可选，不影响本地解析）") {
                        SecureField("API Key", text: $model.cloudKey).autocorrectionDisabled().textInputAutocapitalization(.never)
                        Text("点开始后仅将公开分享链接发送给 Firecrawl；不上传本机网页 Cookie。Key 只在本次应用运行期间保留，使用你自己的服务额度。")
                            .font(.footnote)
                        Button("开始云解析") { model.cloud() }.disabled(model.cloudKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        Button("清空 Key", role: .destructive) { model.cloudKey = "" }
                    }
                }.navigationTitle("云解析设置").toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("关闭") { model.showCloud = false } } }
            }
        }
        .sheet(item: $player) { entry in PlaybackView(url: entry.url) }
        .sheet(item: $share) { item in ShareSheet(url: item.url) }
        .alert("操作结果", isPresented: Binding(get: { model.errorMessage != nil || downloads.errorMessage != nil }, set: { if !$0 { model.errorMessage = nil; downloads.errorMessage = nil } })) {
            Button("确定", role: .cancel) { model.errorMessage = nil; downloads.errorMessage = nil }
        } message: { Text(model.errorMessage ?? downloads.errorMessage ?? "") }
        .confirmationDialog("删除本地视频？已存入相册的副本不受影响", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button("删除", role: .destructive) { if let entry = deleting { downloads.remove(entry) }; deleting = nil }
        }
        .onChange(of: scenePhase) { phase in
            if phase != .active && downloads.downloading { model.notice = "下载可能因应用挂起而暂停，返回应用后继续；失败可重新解析" }
        }
    }
    private var cardBackground: Color { Color(red: 0.09, green: 0.13, blue: 0.19) }
}

@MainActor
struct PlaybackView: View {
    @State private var player: AVPlayer
    @Environment(\.dismiss) private var dismiss
    init(url: URL) { _player = State(initialValue: AVPlayer(url: url)) }
    var body: some View {
        NavigationStack {
            VideoPlayer(player: player).navigationTitle("本地视频").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("关闭") { dismiss() } } }
        }.onAppear { player.play() }.onDisappear { player.pause() }
    }
}

@MainActor
struct ShareSheet: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: [url], applicationActivities: nil) }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

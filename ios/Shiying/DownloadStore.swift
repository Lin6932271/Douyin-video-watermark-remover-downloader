import Foundation
import Combine
import Photos

final class DownloadStore: NSObject, ObservableObject, URLSessionDownloadDelegate {
    static let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("Videos", isDirectory: true)
    private static let historyFile = directory.appendingPathComponent("history.json")
    @Published private(set) var history: [SavedVideo] = []
    @Published private(set) var downloading = false
    @Published private(set) var progress: Double = 0
    @Published private(set) var status = "等待粘贴分享链接"
    @Published var errorMessage: String?
    private var activeTask: URLSessionDownloadTask?
    private var currentVideo: VideoInfo?
    private var cookies: [HTTPCookie] = []
    private var candidate = 0
    private var failure: String?
    private lazy var session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 600
        config.httpShouldSetCookies = false
        return URLSession(configuration: config, delegate: self, delegateQueue: .main)
    }()

    override init() {
        super.init()
        do {
            try FileManager.default.createDirectory(at: Self.directory, withIntermediateDirectories: true)
            if FileManager.default.fileExists(atPath: Self.historyFile.path) {
                history = try JSONDecoder().decode([SavedVideo].self, from: Data(contentsOf: Self.historyFile))
                    .filter { FileManager.default.fileExists(atPath: $0.url.path) }
            }
            var directory = Self.directory
            var values = URLResourceValues(); values.isExcludedFromBackup = true
            try directory.setResourceValues(values)
        } catch { errorMessage = "本地存储初始化失败：\(error.localizedDescription)" }
    }
    func download(_ video: VideoInfo, cookies: [HTTPCookie]) {
        guard !downloading else { return }
        guard video.id.range(of: "^[0-9]{10,25}$", options: .regularExpression) != nil else {
            errorMessage = "视频 ID 无效，请重新解析"
            return
        }
        currentVideo = video
        self.cookies = cookies
        candidate = 0
        downloading = true
        progress = 0
        errorMessage = nil
        next()
    }
    private func next() {
        guard let video = currentVideo else { downloading = false; return }
        let parser = try? ParserBridge()
        while candidate < video.urls.count {
            let value = video.urls[candidate]; candidate += 1
            guard let url = URL(string: value), parser?.isMedia(url) == true else { continue }
            var request = URLRequest(url: url)
            request.setValue(WebHeaders.desktopUA, forHTTPHeaderField: "User-Agent")
            request.setValue(video.source.isEmpty ? "https://www.douyin.com/" : video.source, forHTTPHeaderField: "Referer")
            let matching = cookies.filter { Self.matches($0, url: url) }
            for (key, value) in HTTPCookie.requestHeaderFields(with: matching) { request.setValue(value, forHTTPHeaderField: key) }
            failure = nil
            progress = 0
            status = "正在下载（地址 \(candidate)/\(video.urls.count)）"
            let task = session.downloadTask(with: request)
            activeTask = task
            task.resume()
            return
        }
        activeTask = nil
        downloading = false
        status = "下载失败"
        errorMessage = failure ?? "播放地址不可用或已过期，请重新解析"
    }
    private static func matches(_ cookie: HTTPCookie, url: URL) -> Bool {
        guard let host = url.host?.lowercased(), cookie.expiresDate.map({ $0 > Date() }) ?? true else { return false }
        let domain = cookie.domain.lowercased()
        let normalized = domain.hasPrefix(".") ? String(domain.dropFirst()) : domain
        let hostMatches = host == normalized || (domain.hasPrefix(".") && host.hasSuffix("." + normalized))
        let path = url.path.isEmpty ? "/" : url.path
        let cookiePath = cookie.path.isEmpty ? "/" : cookie.path
        let pathMatches = path == cookiePath || (path.hasPrefix(cookiePath) && (cookiePath.hasSuffix("/") || path.dropFirst(cookiePath.count).hasPrefix("/")))
        return hostMatches && pathMatches && (!cookie.isSecure || url.scheme == "https")
    }
    func cancel() {
        activeTask?.cancel()
        activeTask = nil
        downloading = false
        progress = 0
        status = "下载已取消"
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        guard let url = request.url, let parser = try? ParserBridge(), parser.isMedia(url) else { completionHandler(nil); return }
        var safe = request
        safe.setValue(nil, forHTTPHeaderField: "Cookie")
        for (key, value) in HTTPCookie.requestHeaderFields(with: cookies.filter({ Self.matches($0, url: url) })) { safe.setValue(value, forHTTPHeaderField: key) }
        completionHandler(safe)
    }
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64,
                    totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        guard downloadTask === activeTask else { return }
        progress = totalBytesExpectedToWrite > 0 ? min(1, Double(totalBytesWritten) / Double(totalBytesExpectedToWrite)) : 0
        status = "已下载 \(ByteCountFormatter.string(fromByteCount: totalBytesWritten, countStyle: .file))"
    }
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        guard downloadTask === activeTask, let video = currentVideo else { return }
        do {
            guard let response = downloadTask.response as? HTTPURLResponse, response.statusCode == 200,
                  let url = response.url, let parser = try? ParserBridge(), parser.isMedia(url) else {
                throw AppError.message("媒体服务器 HTTP \((downloadTask.response as? HTTPURLResponse)?.statusCode ?? 0)")
            }
            let file = try FileHandle(forReadingFrom: location)
            defer { try? file.close() }
            let header = try file.read(upToCount: 32) ?? Data()
            guard header.count >= 12, String(data: header.subdata(in: 4..<8), encoding: .ascii) == "ftyp" else {
                throw AppError.message("返回内容不是 MP4 视频，正在尝试其他播放地址")
            }
            let size = (try FileManager.default.attributesOfItem(atPath: location.path)[.size] as? NSNumber)?.int64Value ?? 0
            guard size > 32, response.expectedContentLength <= 0 || size == response.expectedContentLength else { throw AppError.message("下载文件不完整") }
            let entry = SavedVideo(id: UUID(), video: video, filename: "\(video.id)-\(UUID().uuidString.prefix(8)).mp4", created: Date(), bytes: size)
            try FileManager.default.moveItem(at: location, to: entry.url)
            let updated = [entry] + history
            do { try persist(updated) } catch { try? FileManager.default.removeItem(at: entry.url); throw error }
            history = updated
            progress = 1
            status = "已保存到本地，可存入相册或分享"
            downloading = false
            activeTask = nil
        } catch { failure = error.localizedDescription }
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard task === activeTask else { return }
        if let error = error { failure = error.localizedDescription }
        activeTask = nil
        next()
    }
    private func persist(_ entries: [SavedVideo]) throws { try JSONEncoder().encode(entries).write(to: Self.historyFile, options: .atomic) }
    func remove(_ entry: SavedVideo) {
        let updated = history.filter { $0.id != entry.id }
        do {
            // Update the index first: failures keep the existing record recoverable.
            try persist(updated)
            do { try FileManager.default.removeItem(at: entry.url) }
            catch { try? persist(history); throw error }
            history = updated
        } catch { errorMessage = "删除失败：\(error.localizedDescription)" }
    }
    func saveToPhotos(_ entry: SavedVideo) async throws {
        let status = await withCheckedContinuation { (continuation: CheckedContinuation<PHAuthorizationStatus, Never>) in
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { continuation.resume(returning: $0) }
        }
        guard status == .authorized || status == .limited else { throw AppError.message("相册添加权限未开启；可在设置中允许，或用分享保存到文件") }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHPhotoLibrary.shared().performChanges({ _ = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: entry.url) }) { success, error in
                if success { continuation.resume() }
                else { continuation.resume(throwing: error ?? AppError.message("相册保存失败")) }
            }
        }
    }
}

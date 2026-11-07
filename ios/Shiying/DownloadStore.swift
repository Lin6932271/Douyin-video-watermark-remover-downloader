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
    private var itemIndex = 0
    private var savedCount = 0
    private var failedCount = 0
    private var itemCompleted = false
    private var batchID = ""
    private var failures: [String] = []
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
        guard video.id.range(of: "^[0-9]{10,25}$", options: .regularExpression) != nil, !video.mediaItems.isEmpty else {
            errorMessage = "视频 ID 无效，请重新解析"
            return
        }
        currentVideo = video
        self.cookies = cookies
        candidate = 0
        itemIndex = 0; savedCount = 0; failedCount = 0; failures = []; failure = nil; batchID = String(UUID().uuidString.prefix(8))
        downloading = true
        progress = 0
        errorMessage = nil
        next()
    }
    private func next() {
        guard let video = currentVideo else { downloading = false; return }
        let items = video.mediaItems
        guard itemIndex < items.count else {
            activeTask = nil; downloading = false; progress = 1
            status = "已保存 \(savedCount)/\(items.count) 个素材" + (failedCount > 0 ? "，失败 \(failedCount) 个" : "")
            if failedCount > 0 { errorMessage = failures.joined(separator: "\n") }
            return
        }
        let item = items[itemIndex]
        let parser = try? ParserBridge()
        while candidate < item.urls.count {
            let value = item.urls[candidate]; candidate += 1
            guard let url = URL(string: value), parser?.isMedia(url) == true else { continue }
            var request = URLRequest(url: url)
            request.setValue(WebHeaders.desktopUA, forHTTPHeaderField: "User-Agent")
            request.setValue(video.source.isEmpty ? "https://www.douyin.com/" : video.source, forHTTPHeaderField: "Referer")
            let matching = cookies.filter { Self.matches($0, url: url) }
            for (key, value) in HTTPCookie.requestHeaderFields(with: matching) { request.setValue(value, forHTTPHeaderField: key) }
            itemCompleted = false
            status = "素材 \(itemIndex + 1)/\(items.count) · \(item.label) · 地址 \(candidate)/\(item.urls.count)"
            let task = session.downloadTask(with: request)
            activeTask = task
            task.resume()
            return
        }
        failedCount += 1
        failures.append("\(item.label)：\(failure ?? "地址不可用或已过期")")
        itemIndex += 1; candidate = 0; failure = nil
        next()
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
        status = "下载已取消，保留已保存的 \(savedCount) 个文件"
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
        let total = max(1, currentVideo?.mediaItems.count ?? 1)
        let fraction = totalBytesExpectedToWrite > 0 ? min(1, Double(totalBytesWritten) / Double(totalBytesExpectedToWrite)) : 0
        progress = (Double(itemIndex) + fraction) / Double(total)
        status = "\(itemIndex + 1)/\(total) · 已下载 \(ByteCountFormatter.string(fromByteCount: totalBytesWritten, countStyle: .file))"
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
            let item = video.mediaItems[itemIndex]
            let format = try Self.format(header, isVideo: item.isVideo)
            let size = (try FileManager.default.attributesOfItem(atPath: location.path)[.size] as? NSNumber)?.int64Value ?? 0
            guard size >= 16, size >= Int64(header.count), response.expectedContentLength <= 0 || size == response.expectedContentLength else { throw AppError.message("下载文件不完整") }
            let entry = SavedVideo(id: UUID(), video: video, filename: "\(video.id)-\(batchID)-\(item.label).\(format.ext)", created: Date(), bytes: size, mime: format.mime)
            try FileManager.default.moveItem(at: location, to: entry.url)
            let updated = [entry] + history
            do { try persist(updated) } catch { try? FileManager.default.removeItem(at: entry.url); throw error }
            history = updated
            savedCount += 1; itemCompleted = true
        } catch { failure = error.localizedDescription }
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard task === activeTask else { return }
        if let error = error { failure = error.localizedDescription }
        activeTask = nil
        if itemCompleted { itemIndex += 1; candidate = 0; failure = nil }
        next()
    }
    private static func format(_ data: Data, isVideo: Bool) throws -> (mime: String, ext: String) {
        let b = [UInt8](data)
        func text(_ start: Int, _ length: Int) -> String {
            guard b.count >= start + length else { return "" }
            return String(bytes: b[start..<(start + length)], encoding: .ascii) ?? ""
        }
        if isVideo, text(4, 4) == "ftyp", ["isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9", "mp41", "mp42", "avc1", "M4V ", "MSNV", "dash", "qt  "].contains(text(8, 4)) {
            return text(8, 4) == "qt  " ? ("video/quicktime", "mov") : ("video/mp4", "mp4")
        }
        if !isVideo {
            if b.count >= 3, b[0] == 255, b[1] == 216, b[2] == 255 { return ("image/jpeg", "jpg") }
            if b.starts(with: [137, 80, 78, 71, 13, 10, 26, 10]) { return ("image/png", "png") }
            if ["GIF87a", "GIF89a"].contains(text(0, 6)) { return ("image/gif", "gif") }
            if text(0, 4) == "RIFF", text(8, 4) == "WEBP" { return ("image/webp", "webp") }
            if text(4, 4) == "ftyp" {
                if ["avif", "avis"].contains(text(8, 4)) { return ("image/avif", "avif") }
                if ["heic", "heix", "hevc", "hevx", "mif1", "msf1"].contains(text(8, 4)) { return ("image/heic", "heic") }
            }
        }
        throw AppError.message("返回内容不是预期的图片/视频，可能是验证页或已失效")
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
            PHPhotoLibrary.shared().performChanges({
                if entry.isImage {
                    PHAssetCreationRequest.forAsset().addResource(with: .photo, fileURL: entry.url, options: nil)
                } else { _ = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: entry.url) }
            }) { success, error in
                if success { continuation.resume() }
                else { continuation.resume(throwing: error ?? AppError.message("相册保存失败")) }
            }
        }
    }
}

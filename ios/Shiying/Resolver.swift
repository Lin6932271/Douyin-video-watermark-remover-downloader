import Foundation

enum WebHeaders {
    static let desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    static let mobileUA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
}

final class ShareRedirectGuard: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        guard let url = request.url, let parser = try? ParserBridge(), parser.isShare(url) else { completionHandler(nil); return }
        completionHandler(request)
    }
}

struct Resolution {
    var url: URL
    var id: String
    var video: VideoInfo?
}

enum Resolver {
    static func resolve(_ text: String) async throws -> Resolution {
        let parser = try ParserBridge(), initial = try parser.extract(text)
        var id = parser.videoID(initial), finalURL = initial
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 25
        config.timeoutIntervalForResource = 40
        let session = URLSession(configuration: config, delegate: ShareRedirectGuard(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        var request = URLRequest(url: initial)
        var fallback: VideoInfo?
        request.setValue(WebHeaders.mobileUA, forHTTPHeaderField: "User-Agent")
        do {
            let (data, response) = try await session.data(for: request)
            if let url = response.url, parser.isShare(url) { finalURL = url; if id.isEmpty { id = parser.videoID(url) } }
            if let http = response as? HTTPURLResponse, http.statusCode == 200, data.count <= 8 * 1024 * 1024,
               let html = String(data: data, encoding: .utf8), var video = try parser.parse(html, id: id) {
                video.source = finalURL.absoluteString
                if video.items?.isEmpty != false { return Resolution(url: finalURL, id: video.id, video: video) }
                fallback = video
            }
        } catch is CancellationError { throw CancellationError() }
          catch { if Task.isCancelled { throw CancellationError() } /* On-device browser is the rendering fallback. */ }
        if !id.isEmpty {
            let pages = ["https://api.amemv.com/aweme/v1/aweme/detail/?aweme_id=\(id)&aid=6383&device_platform=web&version_code=190500&version_name=19.5.0",
                         "https://www.iesdouyin.com/share/note/\(id)/", "https://www.iesdouyin.com/share/video/\(id)/"]
            for page in pages {
                try Task.checkCancellation()
                var next = URLRequest(url: URL(string: page)!)
                next.setValue(WebHeaders.mobileUA, forHTTPHeaderField: "User-Agent")
                next.setValue("https://www.douyin.com/", forHTTPHeaderField: "Referer")
                do {
                    let (data, response) = try await session.data(for: next)
                    if let http = response as? HTTPURLResponse, http.statusCode == 200, data.count <= 8 * 1024 * 1024,
                       let text = String(data: data, encoding: .utf8), var info = try parser.parse(text, id: id) {
                        info.source = finalURL.absoluteString
                        if info.mediaItems.count >= (fallback?.mediaItems.count ?? 0) { return Resolution(url: finalURL, id: id, video: info) }
                    }
                } catch is CancellationError { throw CancellationError() }
                  catch { if Task.isCancelled { throw CancellationError() } }
            }
        }
        return Resolution(url: finalURL, id: id, video: fallback)
    }

    static func cloud(url: URL, id: String, apiKey: String) async throws -> VideoInfo {
        let parser = try ParserBridge()
        guard parser.isShare(url), !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw AppError.message("请填写 Firecrawl API Key") }
        var request = URLRequest(url: URL(string: "https://api.firecrawl.dev/v2/scrape")!)
        request.httpMethod = "POST"
        request.timeoutInterval = 60
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(apiKey.trimmingCharacters(in: .whitespacesAndNewlines))", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["url": url.absoluteString, "formats": ["rawHtml"], "onlyMainContent": false,
                                                                      "mobile": true, "maxAge": 0, "timeout": 30000, "waitFor": 2000])
        let session = URLSession(configuration: .ephemeral)
        defer { session.invalidateAndCancel() }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw AppError.message("云解析 HTTP \((response as? HTTPURLResponse)?.statusCode ?? 0)，请检查额度和网络")
        }
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any], root["success"] as? Bool == true,
              let result = root["data"] as? [String: Any], let html = result["rawHtml"] as? String else { throw AppError.message("云解析未返回网页正文") }
        let metadata = result["metadata"] as? [String: Any]
        let source = (metadata?["sourceURL"] as? String) ?? (metadata?["url"] as? String) ?? url.absoluteString
        let sourceURL = URL(string: source).flatMap { parser.isShare($0) ? $0 : nil } ?? url
        let target = id.isEmpty ? parser.videoID(sourceURL) : id
        guard var video = try parser.parse(html, id: target) else { throw AppError.message("云页面没有播放地址，请使用网页辅助解析") }
        video.source = sourceURL.absoluteString
        return video
    }
}

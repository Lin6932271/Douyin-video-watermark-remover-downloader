import Foundation
import JavaScriptCore

struct VideoInfo: Codable, Identifiable {
    var id: String
    var title: String
    var author: String
    var source: String
    var urls: [String]
}

struct SavedVideo: Codable, Identifiable {
    var id: UUID
    var video: VideoInfo
    var filename: String
    var created: Date
    var bytes: Int64
    var url: URL { DownloadStore.directory.appendingPathComponent(filename) }
}

enum AppError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let text) = self { return text }; return nil }
}

struct ParserBridge {
    private let script: String
    init() throws {
        guard let url = Bundle.main.url(forResource: "parser", withExtension: "js") else {
            throw AppError.message("解析资源缺失，请重新构建工程")
        }
        script = try String(contentsOf: url, encoding: .utf8)
    }
    private func call(_ function: String, _ arguments: [Any]) throws -> JSValue {
        guard let context = JSContext() else { throw AppError.message("JavaScriptCore 初始化失败") }
        context.evaluateScript(script)
        guard context.exception == nil,
              let api = context.objectForKeyedSubscript("ShiyingParser"),
              let method = api.forProperty(function),
              let result = method.call(withArguments: arguments), context.exception == nil else {
            throw AppError.message(context.exception?.toString() ?? "解析器执行失败")
        }
        return result
    }
    func extract(_ text: String) throws -> URL {
        guard let value = try call("extract", [text]).toString(), let url = URL(string: value) else {
            throw AppError.message("链接格式无效")
        }
        return url
    }
    func videoID(_ url: URL) -> String { (try? call("videoID", [url.absoluteString]).toString()) ?? "" }
    func isShare(_ url: URL) -> Bool { (try? call("isShare", [url.absoluteString]).toBool()) ?? false }
    func isMedia(_ url: URL) -> Bool { (try? call("isMedia", [url.absoluteString]).toBool()) ?? false }
    func parse(_ text: String, id: String) throws -> VideoInfo? { try decode("parse", text, id) }
    func snapshot(_ text: String, id: String) throws -> VideoInfo? { try decode("snapshot", text, id) }
    private func decode(_ method: String, _ text: String, _ id: String) throws -> VideoInfo? {
        guard let json = try call(method, [text, id]).toString(), json != "null", let data = json.data(using: .utf8) else { return nil }
        return try JSONDecoder().decode(VideoInfo.self, from: data)
    }
}

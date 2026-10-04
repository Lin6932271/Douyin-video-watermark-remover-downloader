import SwiftUI

@main
@MainActor
struct ShiyingApp: App {
    @StateObject private var downloads = DownloadStore()
    var body: some Scene {
        WindowGroup { MainView().environmentObject(downloads).preferredColorScheme(.dark) }
    }
}

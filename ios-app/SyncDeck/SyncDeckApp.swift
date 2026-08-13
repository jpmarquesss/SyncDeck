import SwiftUI

@main
struct SyncDeckApp: App {
    @StateObject private var model = DeckViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
                .preferredColorScheme(.dark)
        }
    }
}

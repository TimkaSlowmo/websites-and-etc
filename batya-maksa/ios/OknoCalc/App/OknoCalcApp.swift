import SwiftUI

@main
struct OknoCalcApp: App {
    @StateObject private var settings = AppSettings()
    @StateObject private var catalogStore = CatalogStore()
    @StateObject private var history = HistoryStore()

    var body: some Scene {
        WindowGroup {
            RootView(settings: settings, catalogStore: catalogStore, history: history)
                .environmentObject(settings)
                .environmentObject(catalogStore)
                .environmentObject(history)
        }
    }
}

private struct RootView: View {
    @StateObject private var vm: EstimateViewModel

    init(settings: AppSettings, catalogStore: CatalogStore, history: HistoryStore) {
        _vm = StateObject(wrappedValue: EstimateViewModel(settings: settings, catalogStore: catalogStore, history: history))
    }

    var body: some View {
        NavigationStack {
            HomeView()
        }
        .environmentObject(vm)
    }
}

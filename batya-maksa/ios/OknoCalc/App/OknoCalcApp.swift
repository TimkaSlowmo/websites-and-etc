import SwiftUI

@main
struct OknoCalcApp: App {
    @StateObject private var settings = AppSettings()
    @StateObject private var catalogStore = CatalogStore()

    var body: some Scene {
        WindowGroup {
            RootView(settings: settings, catalogStore: catalogStore)
                .environmentObject(settings)
                .environmentObject(catalogStore)
        }
    }
}

private struct RootView: View {
    @StateObject private var vm: EstimateViewModel

    init(settings: AppSettings, catalogStore: CatalogStore) {
        _vm = StateObject(wrappedValue: EstimateViewModel(settings: settings, catalogStore: catalogStore))
    }

    var body: some View {
        NavigationStack {
            HomeView()
        }
        .environmentObject(vm)
    }
}

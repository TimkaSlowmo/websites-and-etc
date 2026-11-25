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
    @EnvironmentObject private var settings: AppSettings
    @StateObject private var vm: EstimateViewModel

    init(settings: AppSettings, catalogStore: CatalogStore, history: HistoryStore) {
        _vm = StateObject(wrappedValue: EstimateViewModel(settings: settings, catalogStore: catalogStore, history: history))
    }

    var body: some View {
        TabView(selection: $vm.tab) {
            NavigationStack { HomeView() }
                .tabItem { Label("Расчёт", systemImage: "camera.viewfinder") }
                .tag(AppTab.estimate)
            NavigationStack { HistoryView() }
                .tabItem { Label("История", systemImage: "clock.arrow.circlepath") }
                .tag(AppTab.history)
            NavigationStack { CatalogView() }
                .tabItem { Label("Каталог", systemImage: "list.bullet.rectangle") }
                .tag(AppTab.catalog)
            NavigationStack { SettingsView() }
                .tabItem { Label("Настройки", systemImage: "gearshape") }
                .tag(AppTab.settings)
        }
        .tint(Theme.accent)
        .preferredColorScheme(settings.colorScheme)
        .environmentObject(vm)
    }
}

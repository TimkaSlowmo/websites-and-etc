import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var catalogStore: CatalogStore
    @Environment(\.dismiss) private var dismiss
    @State private var importing = false
    @State private var message: String?

    var body: some View {
        Form {
            Section {
                TextField("https://batya-maksa-ai.<аккаунт>.workers.dev", text: $settings.workerURL)
                    .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
            } header: { Text("ИИ-сервер (Cloudflare)") }
            footer: { Text("Адрес сервера уже зашит в приложение, менять не нужно.") }

            Section {
                TextField("Название компании (для КП)", text: $settings.companyName)
            } header: { Text("Коммерческое предложение") }

            Section {
                LabeledContent("Текущий", value: catalogStore.catalog.name)
                LabeledContent("Профилей / стеклопакетов", value: "\(catalogStore.catalog.profiles.count) / \(catalogStore.catalog.glass.count)")
                Button { importing = true } label: { Label("Загрузить каталог (JSON)", systemImage: "square.and.arrow.down") }
                Button("Вернуть пример каталога", role: .destructive) { catalogStore.resetToBundled() }
            } header: { Text("Каталог и цены") }

            if let message { Section { Text(message).font(.footnote) } }
        }
        .navigationTitle("Настройки")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Готово") { dismiss() } } }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
            switch result {
            case .success(let url):
                do { try catalogStore.importCatalog(from: url); message = "Каталог загружен" }
                catch { message = error.localizedDescription }
            case .failure(let e): message = e.localizedDescription
            }
        }
    }
}

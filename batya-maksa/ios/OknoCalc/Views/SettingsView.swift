import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var store: CatalogStore
    @State private var message: String?

    var body: some View {
        let c = store.catalog
        Form {
            Section("Оформление") {
                Picker("Тема", selection: $settings.theme) {
                    Text("Как в системе").tag("system")
                    Text("Светлая").tag("light")
                    Text("Тёмная").tag("dark")
                }
                .pickerStyle(.segmented)
            }

            Section {
                TextField("Название компании (для КП)", text: $settings.companyName)
            } header: { Text("Коммерческое предложение") }

            Section {
                percent("Наценка", c.markupPercent) { v in store.update { $0.markupPercent = v } }
                percent("НДС", c.vatPercent) { v in store.update { $0.vatPercent = v } }
                percent("Отходы профиля", c.wastePercent) { v in store.update { $0.wastePercent = v } }
                money("Изготовление, за м²", c.laborPerM2) { v in store.update { $0.laborPerM2 = v } }
                money("Монтаж, за м²", c.installPerM2) { v in store.update { $0.installPerM2 = v } }
                money("Монтаж, минимум", c.installMin) { v in store.update { $0.installMin = v } }
                percent("Двухцветное исполнение", c.twoColorPercent) { v in store.update { $0.twoColorPercent = v } }
            } header: { Text("Коэффициенты расчёта") }

            Section {
                ForEach(Array(c.glass.enumerated()), id: \.element.fillMm) { i, g in
                    money(g.name, g.pricePerM2, suffix: "/м²") { v in store.update { $0.glass[i].pricePerM2 = v } }
                }
            } header: { Text("Стеклопакеты") }
            footer: { Text("Стеклопакетов нет в прайсе профилей — укажите цены поставщика.") }

            Section("Фурнитура (за створку)") {
                ForEach(Array(c.hardware.enumerated()), id: \.element.opening) { i, h in
                    money(h.name, h.price) { v in store.update { $0.hardware[i].price = v } }
                }
            }

            Section("Надбавка за цвет к профилям") {
                ForEach(Array(c.colorGroups.enumerated()), id: \.element.id) { i, g in
                    percent(g.name, g.surchargePercent) { v in store.update { $0.colorGroups[i].surchargePercent = v } }
                }
            }

            Section {
                LabeledContent("Прайс", value: c.name)
                LabeledContent("Позиций / систем", value: "\(c.articles.count) / \(c.systems.count)")
                if store.isModified {
                    Button("Вернуть цены из прайса", role: .destructive) { store.resetToBundled(); message = "Цены сброшены к прайсу" }
                }
            } header: { Text("Каталог") }

            if let message { Section { Text(message).font(.footnote) } }

            Section {
                LabeledContent("Версия", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")
            } footer: { Text("Проём — ИИ-расчёт окон и дверей по чертежу.") }
        }
        .navigationTitle("Настройки")
        .keyboardDone()
    }

    private func percent(_ title: String, _ value: Double, set: @escaping (Double) -> Void) -> some View {
        LabeledContent(title) {
            HStack(spacing: 4) {
                TextField("0", value: Binding(get: { value }, set: set), format: .number.grouping(.never).precision(.fractionLength(0)))
                    .keyboardType(.numberPad).multilineTextAlignment(.trailing).frame(width: 70)
                Text("%").foregroundStyle(.secondary)
            }
        }
    }

    private func money(_ title: String, _ value: Double, suffix: String = "", set: @escaping (Double) -> Void) -> some View {
        LabeledContent(title) {
            HStack(spacing: 4) {
                TextField("0", value: Binding(get: { value }, set: set), format: .number.grouping(.never).precision(.fractionLength(0)))
                    .keyboardType(.numberPad).multilineTextAlignment(.trailing).frame(width: 90)
                Text("₸" + suffix).foregroundStyle(.secondary)
            }
        }
    }
}

import SwiftUI

/// Прайс текущего каталога (только просмотр; загрузка — во вкладке «Настройки»).
struct CatalogView: View {
    @EnvironmentObject private var store: CatalogStore

    var body: some View {
        let c = store.catalog
        List {
            Section {
                LabeledContent("Валюта", value: c.currency)
                LabeledContent("Наценка", value: "\(Int(c.markupPercent))%")
                LabeledContent("НДС", value: "\(Int(c.vatPercent))%")
            } header: { Text(c.name) }

            Section("Профили, за м²") {
                ForEach(c.profiles) { price($0.name, $0.pricePerM2, c.currency) }
            }
            Section("Стеклопакеты, за м²") {
                ForEach(c.glass) { price($0.name, $0.pricePerM2, c.currency) }
            }
            if !c.hardware.isEmpty {
                Section("Фурнитура, за створку") {
                    ForEach(c.hardware) { price($0.name, $0.price, c.currency) }
                }
            }
            if !c.colors.isEmpty {
                Section("Цвет, наценка к профилю") {
                    ForEach(c.colors) { LabeledContent($0.name, value: "+\(Int($0.surchargePercent))%") }
                }
            }
            if !c.extras.isEmpty {
                Section("Дополнительно") {
                    ForEach(c.extras) { e in
                        LabeledContent(e.name, value: "\(Money.format(e.price, currency: c.currency)) \(e.unit.title)")
                    }
                }
            }
            Section("Монтаж") {
                LabeledContent("За м²", value: Money.format(c.installation.pricePerM2, currency: c.currency))
                LabeledContent("Минимум", value: Money.format(c.installation.minPrice, currency: c.currency))
            }
        }
        .navigationTitle("Каталог")
    }

    private func price(_ name: String, _ value: Double, _ cur: String) -> some View {
        LabeledContent(name, value: Money.format(value, currency: cur))
    }
}

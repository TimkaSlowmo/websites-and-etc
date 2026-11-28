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
                    Text("Авто").tag("system")
                    Text("Светлая").tag("light")
                    Text("Тёмная").tag("dark")
                }
                .pickerStyle(.segmented)
            }

            Section {
                TextField("Название компании (для КП)", text: $settings.companyName)
                unit("Срок действия КП", Double(c.validDays), "дн.") { v in store.update { $0.offerValidDays = max(1, Int(v)) } }
            } header: { Text("Коммерческое предложение") }

            Section {
                unit("Хлыст профиля", c.barDefaultMm ?? 6500, "мм") { v in store.update { $0.barDefaultMm = max(1000, v) } }
                ForEach(Array((c.barLengths ?? []).enumerated()), id: \.element.prefix) { i, b in
                    unit(b.title ?? "Хлыст \(b.prefix)…", b.mm, "мм") { v in store.update { $0.barLengths?[i].mm = max(1000, v) } }
                }
                unit("Ширина пропила", c.kerf, "мм") { v in store.update { $0.sawKerfMm = v } }
                unit("Обрезка концов хлыста", c.trim, "мм") { v in store.update { $0.barTrimMm = v } }
                unit("Уплотнитель кратно", c.gasketStep, "м") { v in store.update { $0.gasketStepM = max(1, v) } }
            } header: { Text("Раскрой и упаковка") }
            footer: { Text("Как в картах раскроя ORGADATA: детали всех окон КП раскладываются по хлыстам, платим за целые хлысты.") }

            ForEach(Array(c.systems.enumerated()).filter { $0.element.limits != nil }, id: \.element.id) { si, sys in
                Section {
                    let l = sys.limits ?? Limits()
                    unit("Створка, макс. ширина", l.sashMaxW ?? 0, "мм") { v in store.update { $0.systems[si].limits?.sashMaxW = v } }
                    unit("Створка, макс. высота", l.sashMaxH ?? 0, "мм") { v in store.update { $0.systems[si].limits?.sashMaxH = v } }
                    unit("Створка, макс. вес", l.sashMaxKg ?? 0, "кг") { v in store.update { $0.systems[si].limits?.sashMaxKg = v } }
                    unit("Импост, макс. длина", l.impostMaxMm ?? 0, "мм") { v in store.update { $0.systems[si].limits?.impostMaxMm = v } }
                    unit("Дверь, макс. ширина", l.doorMaxW ?? 0, "мм") { v in store.update { $0.systems[si].limits?.doorMaxW = v } }
                    unit("Дверь, макс. высота", l.doorMaxH ?? 0, "мм") { v in store.update { $0.systems[si].limits?.doorMaxH = v } }
                } header: { Text("Стандартные размеры · \(sys.name)") }
                footer: { Text("Больше — нестандарт: расчёт по стандартным профилям с предупреждением и рекомендацией профиля.") }
            }

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

    private func unit(_ title: String, _ value: Double, _ suffix: String, set: @escaping (Double) -> Void) -> some View {
        LabeledContent(title) {
            HStack(spacing: 4) {
                TextField("0", value: Binding(get: { value }, set: set), format: .number.grouping(.never).precision(.fractionLength(0)))
                    .keyboardType(.numberPad).multilineTextAlignment(.trailing).frame(width: 80)
                Text(suffix).foregroundStyle(.secondary)
            }
        }
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

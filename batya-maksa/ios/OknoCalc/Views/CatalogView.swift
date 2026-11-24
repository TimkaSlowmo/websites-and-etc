import SwiftUI

/// Каталог компании: системы с составом по артикулам, весь прайс с поиском, цвета.
struct CatalogView: View {
    @EnvironmentObject private var store: CatalogStore
    @State private var tab = 0
    @State private var query = ""

    var body: some View {
        let c = store.catalog
        List {
            Section {
                Picker("", selection: $tab) {
                    Text("Системы").tag(0)
                    Text("Прайс").tag(1)
                    Text("Цвета").tag(2)
                }
                .pickerStyle(.segmented)
                .listRowBackground(Color.clear)
            }
            switch tab {
            case 0:
                ForEach(c.systems) { s in
                    NavigationLink { SystemDetailView(system: s) } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(s.name).font(.headline)
                            Text(s.description).font(.caption).foregroundStyle(.secondary)
                            Text("Заполнение \(s.fillMin)–\(s.fillMax) мм · \(s.rules.count) позиций в расчёте").font(.caption2).foregroundStyle(.tertiary)
                        }
                    }
                }
            case 1:
                let items = c.articles.filter(matches)
                Section {
                    ForEach(items.prefix(300)) { a in
                        HStack(alignment: .firstTextBaseline) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(a.code).font(.caption.monospaced()).foregroundStyle(Theme.accent)
                                Text(a.name).font(.callout)
                            }
                            Spacer()
                            Text(a.price > 0 ? "\(Money.format(a.price, currency: c.currency))/\(a.unitTitle)" : "нет цены")
                                .font(.callout).monospacedDigit().foregroundStyle(a.price > 0 ? .primary : .secondary)
                        }
                    }
                } header: { Text("Позиций: \(items.count) из \(c.articles.count)") }
                footer: { if items.count > 300 { Text("Показаны первые 300 — уточните поиск.") } }
            default:
                ForEach(c.colorGroups) { g in
                    Section(g.surchargePercent > 0 ? "\(g.name) · +\(Int(g.surchargePercent))%" : g.name) {
                        ForEach(c.colors.filter { $0.group == g.id && (query.isEmpty || $0.title.lowercased().contains(query.lowercased())) }) { col in
                            HStack(spacing: 12) {
                                RoundedRectangle(cornerRadius: 6).fill(Color(hex: col.hex)).frame(width: 30, height: 30)
                                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(.secondary.opacity(0.3)))
                                Text(col.code).font(.headline)
                                Text(col.name).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
        .searchable(text: $query, prompt: tab == 2 ? "RAL, название" : "Артикул или название")
        .navigationTitle("Каталог")
    }

    private func matches(_ a: Article) -> Bool {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        return q.isEmpty || a.code.lowercased().contains(q) || a.name.lowercased().contains(q)
    }
}

struct SystemDetailView: View {
    @EnvironmentObject private var store: CatalogStore
    let system: SystemDef

    var body: some View {
        let c = store.catalog
        List {
            Section { Text(system.description) }
            Section("Что входит в расчёт") {
                ForEach(Array(system.rules.enumerated()), id: \.offset) { _, r in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(r.role).font(.callout)
                        if let fills = r.byFill {
                            Text(fills.map { "\($0.min)–\($0.max) мм: \($0.code)" }.joined(separator: "\n"))
                                .font(.caption2.monospaced()).foregroundStyle(.secondary)
                        } else if let code = r.code {
                            let a = c.article(code)
                            Text("\(code) · \(a?.name ?? "нет в прайсе")").font(.caption).foregroundStyle(.secondary)
                            if let a { Text("\(Money.format(a.price, currency: c.currency))/\(a.unitTitle) · \(baseTitle(r.base, r.qty))").font(.caption2).foregroundStyle(.tertiary) }
                            if let d = r.doorCode { Text("для дверей: \(d)").font(.caption2).foregroundStyle(.tertiary) }
                        }
                    }
                }
            }
        }
        .navigationTitle(system.name)
    }

    private func baseTitle(_ base: String, _ qty: Double) -> String {
        let names = [
            "frame_perimeter_m": "периметр рамы", "impost_length_m": "длина импостов", "imposts": "на импост",
            "sash_perimeter_m": "периметр створок", "sashes": "на створку", "door_perimeter_m": "периметр дверных створок",
            "doors": "на дверь", "glass_perimeter_m": "периметр стёкол", "glass_area_m2": "площадь стёкол", "panes": "на стекло",
            "door_width_m": "ширина двери", "vert_len_m": "длина стоек", "horiz_len_m": "длина ригелей", "grid_len_m": "длина стоек и ригелей",
            "area_m2": "площадь", "frame_corners": "на угол рамы",
        ]
        let n = names[base] ?? base
        return qty == 1 ? n : "\(String(format: qty < 1 ? "%.1f" : "%.0f", qty)) × \(n)"
    }
}

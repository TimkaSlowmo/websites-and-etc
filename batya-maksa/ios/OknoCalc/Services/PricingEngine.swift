import Foundation

/// Выбор позиций каталога под конкретное изделие.
struct Selection: Codable, Equatable {
    var profileId: String
    var glassId: String
    var colorId: String
    var extraIds: Set<String> = []
    var includeInstallation: Bool = false
}

struct PriceLine: Identifiable, Equatable {
    var id = UUID()
    var title: String
    var detail: String
    var amount: Double
}

struct PriceBreakdown: Equatable {
    var lines: [PriceLine]
    var subtotal: Double
    var markup: Double
    var vat: Double
    var total: Double
    var currency: String
}

enum PricingEngine {

    // MARK: Автоподбор позиций каталога по тексту из чертежа

    static func autoSelection(spec: WindowSpec, catalog: Catalog) -> Selection {
        let profile = match(spec.profile, in: catalog.profiles, keywords: { $0.keywords ?? [] }, isDefault: { $0.isDefault ?? false })?.id ?? catalog.profiles.first?.id ?? ""
        let glass = match(spec.glass, in: catalog.glass, keywords: { $0.keywords ?? [] }, isDefault: { $0.isDefault ?? false })?.id ?? catalog.glass.first?.id ?? ""
        let color = match(spec.color, in: catalog.colors, keywords: { $0.keywords ?? [] }, isDefault: { $0.isDefault ?? false })?.id ?? catalog.colors.first?.id ?? ""

        let haystack = (spec.extras + [spec.notes]).joined(separator: " ").lowercased()
        let extras = catalog.extras.filter { item in
            (item.keywords ?? []).contains { !$0.isEmpty && haystack.contains($0.lowercased()) }
        }.map(\.id)

        return Selection(profileId: profile, glassId: glass, colorId: color, extraIds: Set(extras))
    }

    /// Ключевое слово, найденное в тексте, выигрывает; самое длинное совпадение точнее.
    private static func match<T>(_ text: String, in items: [T], keywords: (T) -> [String], isDefault: (T) -> Bool) -> T? {
        let t = text.lowercased()
        if !t.isEmpty {
            var best: (item: T, len: Int)?
            for item in items {
                for k in keywords(item) where !k.isEmpty && t.contains(k.lowercased()) {
                    if best == nil || k.count > best!.len { best = (item, k.count) }
                }
            }
            if let best { return best.item }
        }
        return items.first(where: isDefault) ?? items.first
    }

    // MARK: Расчёт

    static func calculate(spec: WindowSpec, selection: Selection, catalog: Catalog) -> PriceBreakdown {
        var lines: [PriceLine] = []
        let area = max(spec.areaM2, catalog.minAreaM2)
        let areaText = String(format: "%.2f м²", area)

        // Рама/профиль
        var frameCost = 0.0
        if let p = catalog.profiles.first(where: { $0.id == selection.profileId }) {
            frameCost = area * p.pricePerM2
            lines.append(PriceLine(title: p.name, detail: "\(areaText) × \(Int(p.pricePerM2))", amount: frameCost))
        }

        // Цвет — наценка на профиль
        if let c = catalog.colors.first(where: { $0.id == selection.colorId }), c.surchargePercent > 0 {
            let amount = frameCost * c.surchargePercent / 100
            lines.append(PriceLine(title: "Цвет: \(c.name)", detail: "+\(Int(c.surchargePercent))% к профилю", amount: amount))
        }

        // Стеклопакет
        if let g = catalog.glass.first(where: { $0.id == selection.glassId }) {
            let glassArea = area * catalog.glassAreaRatio
            lines.append(PriceLine(title: g.name, detail: String(format: "%.2f м² × %d", glassArea, Int(g.pricePerM2)), amount: glassArea * g.pricePerM2))
        }

        // Фурнитура по створкам
        var counts: [OpeningKind: Int] = [:]
        for s in spec.sashes { counts[s.opening, default: 0] += 1 }
        for kind in OpeningKind.allCases {
            guard let n = counts[kind], n > 0,
                  let h = catalog.hardware.first(where: { $0.opening == kind }), h.price > 0 else { continue }
            lines.append(PriceLine(title: h.name, detail: "\(n) шт × \(Int(h.price))", amount: Double(n) * h.price))
        }

        // Допы
        for e in catalog.extras where selection.extraIds.contains(e.id) {
            let qty: Double
            switch e.unit {
            case .pc: qty = 1
            case .widthM: qty = Double(spec.widthMm) / 1000
            case .heightM: qty = Double(spec.heightMm) / 1000
            case .perimeterM: qty = spec.perimeterM
            case .areaM2: qty = area
            }
            lines.append(PriceLine(title: e.name, detail: String(format: "%.2f × %d (%@)", qty, Int(e.price), e.unit.title), amount: qty * e.price))
        }

        // Монтаж
        if selection.includeInstallation {
            let amount = max(catalog.installation.minPrice, area * catalog.installation.pricePerM2)
            lines.append(PriceLine(title: "Монтаж", detail: "от \(Int(catalog.installation.minPrice))", amount: amount))
        }

        let subtotal = lines.reduce(0) { $0 + $1.amount }
        let markup = subtotal * catalog.markupPercent / 100
        let vat = (subtotal + markup) * catalog.vatPercent / 100
        let total = ((subtotal + markup + vat) / 100).rounded() * 100  // до 100

        return PriceBreakdown(lines: lines, subtotal: subtotal, markup: markup, vat: vat, total: total, currency: catalog.currency)
    }
}

enum Money {
    static func format(_ value: Double, currency: String) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.groupingSeparator = " "
        f.maximumFractionDigits = 0
        let s = f.string(from: NSNumber(value: value.rounded())) ?? "\(Int(value))"
        let symbol = ["KZT": "₸", "RUB": "₽", "USD": "$", "EUR": "€"][currency] ?? currency
        return "\(s) \(symbol)"
    }
}

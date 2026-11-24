import Foundation

/// Выбор по каталогу для конкретного изделия.
struct Selection: Codable, Equatable, Hashable {
    var systemId: String = ""
    var fillMm: Int = 32
    var colorId: String = ""
    var colorInsideId: String = ""   // пусто — как снаружи
    var installation: Bool = false

    init(systemId: String = "", fillMm: Int = 32, colorId: String = "", colorInsideId: String = "", installation: Bool = false) {
        self.systemId = systemId; self.fillMm = fillMm; self.colorId = colorId
        self.colorInsideId = colorInsideId; self.installation = installation
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        systemId = (try? c.decode(String.self, forKey: .systemId)) ?? ""
        fillMm = (try? c.decode(Int.self, forKey: .fillMm)) ?? 32
        colorId = (try? c.decode(String.self, forKey: .colorId)) ?? ""
        colorInsideId = (try? c.decode(String.self, forKey: .colorInsideId)) ?? ""
        installation = (try? c.decode(Bool.self, forKey: .installation)) ?? false
    }
}

enum LineGroup: String, CaseIterable {
    case profile, gasket, accessory, glass, hardware, color, labor, install

    var title: String {
        switch self {
        case .profile: return "Профили"
        case .gasket: return "Уплотнители"
        case .accessory: return "Комплектующие"
        case .glass: return "Заполнение"
        case .hardware: return "Фурнитура"
        case .color: return "Цвет"
        case .labor: return "Изготовление"
        case .install: return "Монтаж"
        }
    }
}

struct PriceLine: Identifiable, Equatable {
    var id = UUID()
    var group: LineGroup
    var title: String
    var code: String
    var qty: Double
    var unit: String
    var price: Double
    var amount: Double

    var detail: String {
        let q = String(format: unit == "шт" ? "%.0f" : "%.2f", qty)
        if group == .color { return String(format: "+%.0f%% к профилям", qty) }
        return "\(q) \(unit) × \(Money.format(price, currency: "KZT"))"
    }
}

struct PriceBreakdown: Equatable {
    var lines: [PriceLine]
    var warnings: [String]
    var subtotal: Double
    var markup: Double
    var vat: Double
    var unitTotal: Double
    var quantity: Int
    var total: Double
    var currency: String

    func sum(_ g: LineGroup) -> Double { lines.filter { $0.group == g }.reduce(0) { $0 + $1.amount } }
}

/// Величины изделия, от которых считаются позиции (как в tools/pricing_ref.py).
struct Geometry {
    var v: [String: Double] = [:]
    subscript(key: String) -> Double { v[key] ?? 0 }
}

enum PricingEngine {

    static func geometry(_ spec: WindowSpec, _ system: SystemDef) -> Geometry {
        let W = Double(spec.widthMm), H = Double(spec.heightMm)
        let cols = spec.layout.split == .columns
        var g = Geometry()
        g.v["frame_perimeter_m"] = 2 * (W + H) / 1000
        g.v["area_m2"] = W * H / 1_000_000
        g.v["frame_corners"] = 4
        var vert = 2 * H, horiz = 2 * W, impLen = 0.0, impN = 0.0
        for _ in spec.layout.sections.dropFirst() {
            impN += 1
            if cols { impLen += H; vert += H } else { impLen += W; horiz += W }
        }
        for s in spec.layout.sections {
            for _ in s.cells.dropFirst() {
                impN += 1
                impLen += Double(s.sizeMm)
                if cols { horiz += Double(s.sizeMm) } else { vert += Double(s.sizeMm) }
            }
        }
        g.v["impost_length_m"] = impLen / 1000
        g.v["imposts"] = impN
        g.v["vert_len_m"] = vert / 1000
        g.v["horiz_len_m"] = horiz / 1000
        g.v["grid_len_m"] = (vert + horiz) / 1000
        for r in spec.cellRects {
            let c = r.cell
            let ded: Double
            if c.door && c.opening != .fixed {
                g.v["door_perimeter_m", default: 0] += 2 * (r.w + r.h) / 1000
                g.v["doors", default: 0] += 1
                g.v["door_width_m", default: 0] += r.w / 1000
                ded = system.deductionDoorMm
            } else if c.opening != .fixed {
                g.v["sash_perimeter_m", default: 0] += 2 * (r.w + r.h) / 1000
                g.v["sashes", default: 0] += 1
                ded = system.deductionSashMm
            } else {
                ded = system.deductionFixedMm
            }
            let gw = max(r.w - ded, 100), gh = max(r.h - ded, 100)
            g.v["glass_area_m2", default: 0] += gw * gh / 1_000_000
            g.v["glass_perimeter_m", default: 0] += 2 * (gw + gh) / 1000
            g.v["panes", default: 0] += 1
        }
        return g
    }

    static func calculate(spec: WindowSpec, selection sel: Selection, catalog c: Catalog) -> PriceBreakdown {
        var lines: [PriceLine] = []
        var warnings: [String] = []
        guard let system = c.system(sel.systemId) ?? c.systems.first else {
            return PriceBreakdown(lines: [], warnings: ["Нет систем в каталоге"], subtotal: 0, markup: 0, vat: 0, unitTotal: 0, quantity: 1, total: 0, currency: c.currency)
        }
        let g = geometry(spec, system)
        let fill = sel.fillMm
        let doorProduct = spec.product == .door
        let waste = 1 + c.wastePercent / 100
        var profiles = 0.0

        if fill < system.fillMin || fill > system.fillMax {
            warnings.append("Заполнение \(fill) мм вне диапазона \(system.name) (\(system.fillMin)–\(system.fillMax) мм)")
        }
        if let m = spec.layoutMismatch { warnings.append(m) }

        for r in system.rules {
            var code = r.code
            if doorProduct, let d = r.doorCode { code = d }
            if let byFill = r.byFill { code = byFill.first { $0.min <= fill && fill <= $0.max }?.code }
            var qty = g[r.base] * r.qty
            guard qty > 0 else { continue }
            guard let code else {
                warnings.append("\(r.role): нет позиции под заполнение \(fill) мм")
                continue
            }
            guard let a = c.article(code) else {
                warnings.append("\(r.role): артикул \(code) не найден в прайсе")
                continue
            }
            if a.price <= 0 { warnings.append("\(code): цена в прайсе не указана") }
            if r.profile && a.unit == "m" { qty *= waste }
            let amount = qty * a.price
            if r.profile { profiles += amount }
            let group: LineGroup = r.profile ? .profile : (a.kind == "gasket" ? .gasket : .accessory)
            lines.append(PriceLine(group: group, title: r.role, code: code, qty: qty, unit: a.unitTitle, price: a.price, amount: amount))
        }

        // Цвет: надбавка группы снаружи + двухцветное исполнение.
        var pct = 0.0
        var colorTitle = ""
        if let col = c.color(sel.colorId) {
            pct += c.group(col.group)?.surchargePercent ?? 0
            colorTitle = col.code
        }
        if !sel.colorInsideId.isEmpty && sel.colorInsideId != sel.colorId {
            pct += c.twoColorPercent
            colorTitle += " / внутри " + (c.color(sel.colorInsideId)?.code ?? "")
        }
        if pct > 0 {
            lines.append(PriceLine(group: .color, title: "Цвет: \(colorTitle)", code: "", qty: pct, unit: "%", price: 0, amount: profiles * pct / 100))
        }

        if let gi = c.glassItem(for: fill) {
            let area = g["glass_area_m2"]
            lines.append(PriceLine(group: .glass, title: gi.name, code: "", qty: area, unit: "м²", price: gi.pricePerM2, amount: area * gi.pricePerM2))
        }

        var counts: [String: Int] = [:]
        for r in spec.cellRects where r.cell.opening != .fixed {
            counts[r.cell.door ? "door" : r.cell.opening.rawValue, default: 0] += 1
        }
        for key in ["tilt_turn", "turn", "tilt", "top_hung", "slide", "door"] {
            guard let n = counts[key], n > 0, let h = c.hardware.first(where: { $0.opening == key }) else { continue }
            lines.append(PriceLine(group: .hardware, title: h.name, code: "", qty: Double(n), unit: "шт", price: h.price, amount: Double(n) * h.price))
        }

        let area = g["area_m2"]
        lines.append(PriceLine(group: .labor, title: "Изготовление", code: "", qty: area, unit: "м²", price: c.laborPerM2, amount: area * c.laborPerM2))
        if sel.installation {
            lines.append(PriceLine(group: .install, title: "Монтаж", code: "", qty: area, unit: "м²", price: c.installPerM2, amount: max(c.installMin, area * c.installPerM2)))
        }

        let subtotal = lines.reduce(0) { $0 + $1.amount }
        let markup = subtotal * c.markupPercent / 100
        let vat = (subtotal + markup) * c.vatPercent / 100
        let unitTotal = ((subtotal + markup + vat) / 100 + 0.5).rounded(.down) * 100
        let q = max(1, spec.quantity)
        return PriceBreakdown(lines: lines, warnings: warnings, subtotal: subtotal, markup: markup, vat: vat,
                              unitTotal: unitTotal, quantity: q, total: unitTotal * Double(q), currency: c.currency)
    }

    // MARK: Автоподбор по тексту с чертежа

    static func autoSelection(spec: WindowSpec, catalog c: Catalog) -> Selection {
        let system = matchSystem(spec, c)
        var fill = spec.fillMm ?? system?.fillDefault ?? 32
        if let s = system, spec.fillMm == nil { fill = s.fillDefault }
        let outside = matchColor(spec.color, c) ?? matchColor(spec.notes, c) ?? c.colors.first?.id ?? ""
        var inside = matchColor(spec.colorInside, c) ?? ""
        if inside == outside { inside = "" }
        return Selection(systemId: system?.id ?? "", fillMm: fill, colorId: outside, colorInsideId: inside)
    }

    static func matchSystem(_ spec: WindowSpec, _ c: Catalog) -> SystemDef? {
        let text = (spec.system + " " + spec.info.map { "\($0.label) \($0.value)" }.joined(separator: " ")).lowercased()
        // Первая упомянутая система (по позиции в тексте), подходящая к изделию.
        var hits: [(pos: Int, sys: SystemDef)] = []
        for s in c.systems {
            for k in s.keywords where !k.isEmpty {
                if let r = text.range(of: k.lowercased()) {
                    hits.append((text.distance(from: text.startIndex, to: r.lowerBound), s))
                    break
                }
            }
        }
        hits.sort { $0.pos < $1.pos }
        if let h = hits.first(where: { $0.sys.products.contains(spec.product.rawValue) }) ?? hits.first { return h.sys }
        if spec.product == .facade { return c.system("bkf50") ?? c.systems.first }
        return c.system("bkt77") ?? c.systems.first
    }

    static func matchColor(_ text: String, _ c: Catalog) -> String? {
        let t = text.lowercased()
        guard !t.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        if let r = t.range(of: #"\d{4}"#, options: .regularExpression), let col = c.color("ral" + t[r]) { return col.id }
        var best: (len: Int, id: String)?
        for col in c.colors {
            for k in col.keywords where k.count >= 3 && t.contains(k.lowercased()) {
                if best == nil || k.count > best!.len { best = (k.count, col.id) }
            }
        }
        return best?.id
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

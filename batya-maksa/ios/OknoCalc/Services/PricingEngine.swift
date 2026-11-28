import Foundation

/// Выбор по каталогу для конкретного изделия.
struct Selection: Codable, Equatable, Hashable {
    var systemId: String = ""
    var fillMm: Int = 32
    var colorId: String = ""
    var colorInsideId: String = ""   // пусто — как снаружи
    var installation: Bool = false
    /// Замены профилей по рекомендации для нестандарта (роль правила → артикул)
    var overrides: [Override] = []

    struct Override: Codable, Equatable, Hashable {
        var role: String
        var code: String
    }

    func override(_ role: String) -> String? { overrides.first { $0.role == role }?.code }
    mutating func setOverride(_ role: String, _ code: String?) {
        overrides.removeAll { $0.role == role }
        if let code { overrides.append(Override(role: role, code: code)) }
    }

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
        overrides = (try? c.decode([Override].self, forKey: .overrides)) ?? []
    }
}

/// Что входит в КП: только материалы (профиль, комплектующие, уплотнители — как КП BKM Aluminium) или изделие под ключ.
enum OfferScope: String, Codable, CaseIterable, Identifiable {
    case materials, turnkey
    var id: String { rawValue }
    var title: String { self == .materials ? "Материалы" : "Под ключ" }
}

struct OfferOptions: Codable, Equatable, Hashable {
    var scope: OfferScope = .materials
    var cutting: Bool = true          // раскрой профилей по хлыстам
    var gasketRound: Bool = true      // уплотнители кратно шагу (20 м)
    var discountPercent: Double = 0
    var objectName: String = ""       // объект / проект

    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        scope = (try? c.decode(OfferScope.self, forKey: .scope)) ?? .materials
        cutting = (try? c.decode(Bool.self, forKey: .cutting)) ?? true
        gasketRound = (try? c.decode(Bool.self, forKey: .gasketRound)) ?? true
        discountPercent = (try? c.decode(Double.self, forKey: .discountPercent)) ?? 0
        objectName = (try? c.decode(String.self, forKey: .objectName)) ?? ""
    }
}

/// Окно в КП.
struct Position: Codable, Identifiable, Equatable, Hashable {
    var id = UUID()
    var mark: String = "ОК-1"
    var spec = WindowSpec()
    var selection = Selection()
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
    var note: String = ""

    var detail: String {
        if group == .color { return String(format: "+%.0f%% к профилям", qty) }
        let q = String(format: unit == "шт" ? "%.0f" : "%.2f", qty)
        return "\(q) \(unit) × \(Money.format(price, currency: "KZT"))" + (note.isEmpty ? "" : " · \(note)")
    }
}

/// Нестандартный размер: предупреждение и рекомендуемый профиль.
struct LimitWarning: Identifiable, Equatable, Hashable {
    var kind: String
    var text: String
    var role: String
    var code: String
    var rec: String
    var applied: Bool
    var id: String { kind + text }
}

/// Расчёт одного окна в составе КП (цены за 1 шт).
struct PriceBreakdown: Equatable {
    var lines: [PriceLine]
    var warnings: [String]
    var limits: [LimitWarning]
    var subtotal: Double
    var markup: Double
    var unitTotal: Double
    var quantity: Int
    var total: Double
    var areaM2: Double
    var currency: String

    func sum(_ g: LineGroup) -> Double { lines.filter { $0.group == g }.reduce(0) { $0 + $1.amount } }
    var pricePerM2: Double { areaM2 > 0 ? unitTotal / areaM2 : 0 }
}

/// Строка спецификации материалов по всему КП.
struct MaterialLine: Identifiable, Equatable {
    var group: LineGroup
    var code: String
    var name: String
    var colorKey: String
    var unit: String          // м | шт
    var need: Double          // нужно по расчёту
    var bought: Double        // к отпуску (хлысты / кратно шагу)
    var bars: Int?            // хлыстов
    var barMm: Double?
    var packM: Double?
    var price: Double
    var amount: Double
    var kg: Double
    var id: String { group.rawValue + code + colorKey }
    var wastePercent: Double { bought > 0 ? max(0, (bought - need) / bought * 100) : 0 }
}

struct OfferBreakdown: Equatable {
    var items: [PriceBreakdown]
    var materials: [MaterialLine]
    var subtotal: Double
    var discount: Double
    var total: Double
    var vatIncluded: Double
    var areaM2: Double
    var weightKg: Double
    var currency: String
    var bars: [String: Int] {
        var out: [String: Int] = [:]
        for m in materials where m.group == .profile { if let b = m.bars { out[m.code, default: 0] += b } }
        return out
    }
}

/// Детали изделия (мм) и величины, от которых считаются позиции (как в tools/pricing_ref.py).
struct Geometry {
    var v: [String: Double] = [:]
    var pieces: [String: [Int]] = [:]
    var sashes: [(ow: Int, oh: Int, gw: Int, gh: Int, section: Int, index: Int, door: Bool)] = []
    subscript(key: String) -> Double { v[key] ?? 0 }
}

enum PricingEngine {

    static let piecesOf: [String: String] = [
        "frame_perimeter_m": "frame", "impost_length_m": "impost", "sash_perimeter_m": "sash", "door_perimeter_m": "door",
        "door_width_m": "threshold", "bead_length_m": "bead", "vert_len_m": "vert", "horiz_len_m": "horiz", "grid_len_m": "grid",
    ]

    static func r0(_ x: Double) -> Double { (x + 0.5).rounded(.down) }

    static func geometry(_ spec: WindowSpec, _ system: SystemDef) -> Geometry {
        let geo = system.geo ?? Geo()
        let F = geo.frameFace, I2 = geo.impostFace / 2
        let ov = Int(geo.sashOverlap), sf = Int(geo.sashFace), df = Int(geo.doorFace)
        let bd = Int(geo.beadDeduct), gap = Int(geo.glassGap)
        let W = spec.widthMm, H = spec.heightMm
        let cols = spec.layout.split == .columns
        let secs = spec.layout.sections
        let crossTotal = cols ? H : W
        var g = Geometry()
        var p: [String: [Int]] = ["frame": spec.product == .door ? [W, H, H] : [W, W, H, H]]
        g.v["frame_corners"] = 4
        g.v["area_m2"] = Double(W) * Double(H) / 1_000_000
        let n = secs.count
        var panes: [(Int, Int, Bool)] = []
        for (si, sec) in secs.enumerated() {
            let a = si == 0 ? F : I2, b = si == n - 1 ? F : I2
            let lightMain = Double(sec.sizeMm) - a - b
            if si > 0 { p["impost", default: []].append(Int(r0(Double(crossTotal) - 2 * F))) }
            let m = sec.cells.count
            for (ci, cell) in sec.cells.enumerated() {
                let c1 = ci == 0 ? F : I2, c2 = ci == m - 1 ? F : I2
                let lightCross = Double(cell.sizeMm) - c1 - c2
                if ci > 0 { p["impost", default: []].append(Int(r0(lightMain))) }
                let lw = Int(r0(cols ? lightMain : lightCross)), lh = Int(r0(cols ? lightCross : lightMain))
                let glw: Int, glh: Int
                if cell.opening == .fixed {
                    glw = lw; glh = lh
                } else {
                    let door = cell.door
                    let ow = lw + 2 * ov, oh = lh + 2 * ov
                    let face = door ? df : sf
                    glw = ow - 2 * face; glh = oh - 2 * face
                    if door {
                        p["door", default: []] += [ow, ow, oh, oh]
                        p["threshold", default: []].append(lw)
                        g.v["doors", default: 0] += 1
                    } else {
                        p["sash", default: []] += [ow, ow, oh, oh]
                        g.v["sashes", default: 0] += 1
                    }
                    g.sashes.append((ow, oh, glw - 2 * gap, glh - 2 * gap, si, ci, door))
                }
                p["bead", default: []] += [glw, glw, glh - 2 * bd, glh - 2 * bd]
                panes.append((max(glw - 2 * gap, 50), max(glh - 2 * gap, 50), cell.opening != .fixed))
            }
        }
        let inner = secs.flatMap { s in s.cells.dropFirst().map { _ in s.sizeMm } }
        if cols {
            p["vert"] = [H, H] + Array(repeating: H, count: max(0, n - 1))
            p["horiz"] = [W, W] + inner
        } else {
            p["horiz"] = [W, W] + Array(repeating: W, count: max(0, n - 1))
            p["vert"] = [H, H] + inner
        }
        p["grid"] = (p["vert"] ?? []) + (p["horiz"] ?? [])
        g.v["imposts"] = Double(p["impost"]?.count ?? 0)
        for (k, base) in [("frame", "frame_perimeter_m"), ("impost", "impost_length_m"), ("sash", "sash_perimeter_m"),
                          ("door", "door_perimeter_m"), ("threshold", "door_width_m"), ("bead", "bead_length_m"),
                          ("vert", "vert_len_m"), ("horiz", "horiz_len_m"), ("grid", "grid_len_m")] {
            g.v[base] = Double((p[k] ?? []).reduce(0, +)) / 1000
        }
        for (gw, gh, inSash) in panes {
            g.v["glass_area_m2", default: 0] += Double(gw) * Double(gh) / 1_000_000
            g.v["glass_perimeter_m", default: 0] += 2 * Double(gw + gh) / 1000
            g.v["panes", default: 0] += 1
            if inSash { g.v["sash_panes", default: 0] += 1 }
        }
        g.pieces = p
        return g
    }

    static func ruleCode(_ r: Rule, _ spec: WindowSpec, _ sel: Selection) -> String? {
        var code = r.code
        if spec.product == .door, let d = r.doorCode { code = d }
        if let byFill = r.byFill { code = byFill.first { $0.min <= sel.fillMm && sel.fillMm <= $0.max }?.code }
        if let o = sel.override(r.role) { return o }
        return code
    }

    /// Предупреждения о нестандартных размерах. Считаем всё равно стандартными профилями (или применённой заменой).
    static func checkLimits(_ spec: WindowSpec, _ sel: Selection, _ system: SystemDef, _ c: Catalog, _ g: Geometry) -> [LimitWarning] {
        var out: [LimitWarning] = []
        let lim = system.limits
        let recs = Dictionary((system.recommend ?? []).map { ($0.when, $0) }, uniquingKeysWith: { a, _ in a })
        func add(_ kind: String, _ text: String) {
            let r = recs[kind]
            out.append(LimitWarning(kind: kind, text: text, role: r?.role ?? "", code: r?.code ?? "", rec: r?.text ?? "",
                                    applied: r != nil && sel.override(r!.role) == r!.code))
        }
        func i(_ x: Double?) -> Int { Int(x ?? 0) }
        if let fr = system.rules.first(where: { $0.base == "frame_perimeter_m" }), let code = ruleCode(fr, spec, sel),
           let longest = g.pieces["frame"]?.max() {
            let usable = Int(c.barLength(code) - c.trim - c.kerf)
            if longest > usable { add("frame_long", "Сторона рамы \(longest) мм длиннее хлыста (\(usable) мм) — нужна стыковка, нестандарт") }
        }
        if let maxI = lim?.impostMaxMm, let longest = g.pieces["impost"]?.max(), Double(longest) > maxI {
            add("impost_long", "Импост \(longest) мм длиннее стандартного (\(Int(maxI)) мм)")
        }
        let kgM2 = c.glassItem(for: sel.fillMm)?.kgM2 ?? 25
        for s in g.sashes {
            let name = "\(s.door ? "Дверь" : "Створка") \(s.section + 1).\(s.index + 1) (\(s.ow)×\(s.oh) мм)"
            if s.door {
                if let l = lim, (l.doorMaxW != nil && Double(s.ow) > l.doorMaxW!) || (l.doorMaxH != nil && Double(s.oh) > l.doorMaxH!) {
                    add("door_big", "\(name) больше стандартной двери (\(i(l.doorMaxW))×\(i(l.doorMaxH)) мм)")
                }
                continue
            }
            guard let l = lim else { continue }
            let kg = Double(s.gw) * Double(s.gh) / 1_000_000 * kgM2 + 2 * Double(s.ow + s.oh) / 1000 * 1.65
            if Double(s.ow) > (l.sashMaxW ?? .infinity) || Double(s.oh) > (l.sashMaxH ?? .infinity) || kg > (l.sashMaxKg ?? .infinity) {
                add("sash_big", "\(name), ≈\(Int(kg.rounded())) кг — больше стандартной (\(i(l.sashMaxW))×\(i(l.sashMaxH)) мм, до \(i(l.sashMaxKg)) кг)")
            } else if Double(s.ow) < (l.sashMinW ?? 0) || Double(s.oh) < (l.sashMinH ?? 0) {
                add("sash_small", "\(name) меньше минимальной для фурнитуры (\(i(l.sashMinW))×\(i(l.sashMinH)) мм)")
            }
        }
        return out
    }

    struct Need {
        var group: LineGroup
        var role: String
        var code: String
        var unit: String      // m | pc | m2
        var qty: Double       // на 1 шт
        var price: Double
        var pieces: [Int]?
        var colorKey: String
        var pool: String? = nil
    }

    static func needs(_ spec: WindowSpec, _ sel: Selection, _ c: Catalog, _ scope: OfferScope)
        -> (needs: [Need], g: Geometry, warnings: [String], limits: [LimitWarning]) {
        var warnings: [String] = []
        guard let system = c.system(sel.systemId) ?? c.systems.first else { return ([], Geometry(), ["Нет систем в каталоге"], []) }
        let g = geometry(spec, system)
        if sel.fillMm < system.fillMin || sel.fillMm > system.fillMax {
            warnings.append("Заполнение \(sel.fillMm) мм вне диапазона \(system.name) (\(system.fillMin)–\(system.fillMax) мм)")
        }
        if let m = spec.layoutMismatch { warnings.append(m) }
        let colorKey = sel.colorId + "/" + sel.colorInsideId
        var out: [Need] = []
        for r in system.rules {
            let qty = g[r.base] * r.qty + (r.plus ?? []).reduce(0) { $0 + g[$1.base] * $1.qty }
            guard qty > 0 else { continue }
            guard let code = ruleCode(r, spec, sel) else {
                warnings.append("\(r.role): нет позиции под заполнение \(sel.fillMm) мм"); continue
            }
            guard let a = c.article(code) else { warnings.append("\(r.role): артикул \(code) не найден в прайсе"); continue }
            if a.price <= 0 { warnings.append("\(code): цена в прайсе не указана") }
            let group: LineGroup = r.profile ? .profile : (a.kind == "gasket" ? .gasket : .accessory)
            var pieces: [Int]? = nil
            if group == .profile, a.unit == "m", (r.plus ?? []).isEmpty, r.qty == 1, let k = piecesOf[r.base] { pieces = g.pieces[k] ?? [] }
            out.append(Need(group: group, role: r.role, code: code, unit: a.unit, qty: qty, price: a.price, pieces: pieces,
                            colorKey: group == .profile ? colorKey : ""))
        }
        if scope == .turnkey {
            if let gi = c.glassItem(for: sel.fillMm) {
                out.append(Need(group: .glass, role: gi.name, code: "", unit: "m2", qty: g["glass_area_m2"], price: gi.pricePerM2, pieces: nil, colorKey: ""))
            }
            var counts: [String: Int] = [:]
            for s in spec.layout.sections { for cell in s.cells where cell.opening != .fixed { counts[cell.door ? "door" : cell.opening.rawValue, default: 0] += 1 } }
            for key in ["tilt_turn", "turn", "tilt", "top_hung", "slide", "door"] {
                guard let n = counts[key], n > 0, let h = c.hardware.first(where: { $0.opening == key }) else { continue }
                out.append(Need(group: .hardware, role: h.name, code: "", unit: "pc", qty: Double(n), price: h.price, pieces: nil, colorKey: ""))
            }
            let area = g["area_m2"]
            out.append(Need(group: .labor, role: "Изготовление", code: "", unit: "m2", qty: area, price: c.laborPerM2, pieces: nil, colorKey: ""))
            if sel.installation {
                let amount = max(c.installMin, area * c.installPerM2)
                out.append(Need(group: .install, role: "Монтаж", code: "", unit: "m2", qty: area, price: area > 0 ? amount / area : 0, pieces: nil, colorKey: ""))
            }
        }
        return (out, g, warnings, checkLimits(spec, sel, system, c, g))
    }

    /// Раскрой: первый подходящий хлыст, детали от длинных к коротким (пропил и обрезка концов).
    static func cutBars(_ pieces: [Int], barMm: Double, kerf: Double, trim: Double) -> (bars: Int, over: Int) {
        let cap = barMm - trim
        var free: [Double] = []
        var over = 0, extra = 0
        for L in pieces.sorted(by: >) {
            let need = Double(L) + kerf
            if need > cap { over += 1; extra += Int((need / cap).rounded(.up)); continue }
            if let i = free.firstIndex(where: { $0 >= need }) { free[i] -= need } else { free.append(cap - need) }
        }
        return (free.count + extra, over)
    }

    private struct Pool {
        var group: LineGroup
        var code: String
        var colorKey: String
        var need = 0.0
        var pieces: [Int] = []
        var by: [String: Double] = [:]
        var bought = 0.0
        var bars: Int? = nil
        var barMm: Double? = nil
    }

    /// Расчёт КП: все окна вместе (общий раскрой и округление уплотнителей).
    static func offer(_ positions: [Position], options opt: OfferOptions, catalog c: Catalog) -> OfferBreakdown {
        let waste = 1 + c.wastePercent / 100
        var per: [(needs: [Need], g: Geometry, warnings: [String], limits: [LimitWarning], q: Int)] = []
        for p in positions {
            let r = needs(p.spec, p.selection, c, opt.scope)
            per.append((r.needs, r.g, r.warnings, r.limits, max(1, p.spec.quantity)))
        }
        var pools: [String: Pool] = [:]
        for i in per.indices {
            for j in per[i].needs.indices {
                let n = per[i].needs[j]
                var key: String? = nil
                if n.group == .profile, opt.cutting, let pc = n.pieces, !pc.isEmpty { key = "profile|\(n.code)|\(n.colorKey)" }
                else if n.group == .gasket, opt.gasketRound, c.gasketStep > 0, n.unit == "m" { key = "gasket|\(n.code)|" }
                guard let key else { continue }
                var pool = pools[key] ?? Pool(group: n.group, code: n.code, colorKey: n.colorKey)
                let m = n.qty * Double(per[i].q)
                pool.need += m
                pool.by["\(i)|\(n.role)", default: 0] += m
                if n.group == .profile, let pc = n.pieces { for _ in 0..<per[i].q { pool.pieces += pc } }
                pools[key] = pool
                per[i].needs[j].pool = key
            }
        }
        for (k, var pool) in pools {
            if pool.group == .profile {
                let bar = c.barLength(pool.code)
                let r = cutBars(pool.pieces, barMm: bar, kerf: c.kerf, trim: c.trim)
                pool.bars = r.bars; pool.barMm = bar
                pool.bought = Double(r.bars) * bar / 1000
            } else {
                pool.bought = (pool.need / c.gasketStep - 1e-9).rounded(.up) * c.gasketStep
            }
            pools[k] = pool
        }

        var items: [PriceBreakdown] = []
        var mats: [String: MaterialLine] = [:]
        let groups = Dictionary(c.colorGroups.map { ($0.id, $0.surchargePercent) }, uniquingKeysWith: { a, _ in a })
        for (i, x) in per.enumerated() {
            var lines: [PriceLine] = []
            var profiles = 0.0
            for n in x.needs {
                var qty: Double
                var note = ""
                if let key = n.pool, let pool = pools[key] {
                    let share = pool.need > 0 ? (pool.by["\(i)|\(n.role)"] ?? 0) / pool.need : 0
                    qty = pool.bought * share / Double(x.q)
                    note = pool.group == .profile ? "раскрой по КП" : "кратно \(Int(c.gasketStep)) м"
                } else if n.group == .profile && n.unit == "m" {
                    qty = n.qty * waste
                    note = "+\(Int(c.wastePercent))% отход"
                } else {
                    qty = n.qty
                }
                let amount = qty * n.price
                if n.group == .profile { profiles += amount }
                let unitTitle = n.unit == "m" ? "м" : n.unit == "m2" ? "м²" : "шт"
                lines.append(PriceLine(group: n.group, title: n.role, code: n.code, qty: qty, unit: unitTitle, price: n.price, amount: amount, note: note))
                // Спецификация материалов КП
                if [.profile, .gasket, .accessory].contains(n.group) {
                    let key = n.group.rawValue + "|" + n.code + "|" + n.colorKey
                    let a = c.article(n.code)
                    var m = mats[key] ?? MaterialLine(group: n.group, code: n.code, name: a?.name ?? n.role, colorKey: n.colorKey,
                                                      unit: unitTitle, need: 0, bought: 0, bars: nil, barMm: nil, packM: a?.packM,
                                                      price: n.price, amount: 0, kg: 0)
                    m.need += n.qty * Double(x.q)
                    m.amount += amount * Double(x.q)
                    if let key = n.pool, let pool = pools[key] {
                        m.bought = pool.bought; m.bars = pool.bars; m.barMm = pool.barMm
                    } else {
                        m.bought += qty * Double(x.q)
                    }
                    m.kg = (a?.kgM ?? 0) * m.bought
                    mats[key] = m
                }
            }
            let sel = positions[i].selection
            var pct = 0.0
            var colorTitle = ""
            if let col = c.color(sel.colorId) { pct += groups[col.group] ?? 0; colorTitle = col.code }
            if !sel.colorInsideId.isEmpty && sel.colorInsideId != sel.colorId {
                pct += c.twoColorPercent
                colorTitle += " / внутри " + (c.color(sel.colorInsideId)?.code ?? "")
            }
            if pct > 0 {
                lines.append(PriceLine(group: .color, title: "Цвет: \(colorTitle)", code: "", qty: pct, unit: "%", price: 0, amount: profiles * pct / 100))
            }
            let subtotal = lines.reduce(0) { $0 + $1.amount }
            let markup = opt.scope == .turnkey ? subtotal * c.markupPercent / 100 : 0
            let unitTotal = r0(subtotal + markup)
            items.append(PriceBreakdown(lines: lines, warnings: x.warnings, limits: x.limits, subtotal: subtotal, markup: markup,
                                        unitTotal: unitTotal, quantity: x.q, total: unitTotal * Double(x.q),
                                        areaM2: x.g["area_m2"], currency: c.currency))
        }
        let subtotal = items.reduce(0) { $0 + $1.total }
        let discount = r0(subtotal * min(max(opt.discountPercent, 0), 100) / 100)
        let total = subtotal - discount
        let vat = r0(total * c.vatPercent / (100 + c.vatPercent))
        let area = items.reduce(0) { $0 + $1.areaM2 * Double($1.quantity) }
        let order: [LineGroup] = [.profile, .accessory, .gasket]
        let materials = mats.values.sorted {
            (order.firstIndex(of: $0.group) ?? 9, $0.code, $0.colorKey) < (order.firstIndex(of: $1.group) ?? 9, $1.code, $1.colorKey)
        }
        return OfferBreakdown(items: items, materials: materials, subtotal: subtotal, discount: discount, total: total,
                              vatIncluded: vat, areaM2: area, weightKg: materials.reduce(0) { $0 + $1.kg }, currency: c.currency)
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

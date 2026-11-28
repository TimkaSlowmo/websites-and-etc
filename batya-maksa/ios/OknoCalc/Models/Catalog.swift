import Foundation

/// Позиция прайса компании (артикул).
struct Article: Codable, Identifiable, Hashable {
    var code: String
    var name: String
    var price: Double
    var unit: String   // "m" | "pc"
    var kind: String   // profile | gasket | accessory | other
    var packM: Double? = nil   // метров в рулоне (уплотнители)
    var kgM: Double? = nil     // вес, кг/м (профили)
    var id: String { code }
    var unitTitle: String { unit == "m" ? "м" : "шт" }
}

struct FillCode: Codable, Hashable {
    var min: Int
    var max: Int
    var code: String
}

struct BarLength: Codable, Hashable {
    var prefix: String
    var mm: Double
    /// Подпись в настройках («Хлыст створки»)
    var title: String?
}

struct RulePlus: Codable, Hashable {
    var base: String
    var qty: Double
}

/// Правило расчёта: какой артикул и от какой величины считается (+ добавки от других величин).
struct Rule: Codable, Hashable {
    var role: String
    var code: String?
    var base: String
    var qty: Double
    var profile: Bool
    var doorCode: String?
    var byFill: [FillCode]?
    var plus: [RulePlus]?
}

/// Вычеты профилей системы, мм (как в ORGADATA).
struct Geo: Codable, Hashable {
    var frameFace: Double = 42
    var impostFace: Double = 42
    var sashOverlap: Double = 6
    var sashFace: Double = 59
    var doorFace: Double = 73
    var beadDeduct: Double = 22
    var glassGap: Double = 6
}

/// Стандартные размеры для утверждённых профилей.
struct Limits: Codable, Hashable {
    var sashMinW: Double?
    var sashMinH: Double?
    var sashMaxW: Double?
    var sashMaxH: Double?
    var sashMaxKg: Double?
    var doorMaxW: Double?
    var doorMaxH: Double?
    var impostMaxMm: Double?
}

/// Рекомендация для нестандарта: какой профиль поставить вместо стандартного.
struct Recommendation: Codable, Hashable {
    var when: String
    var role: String
    var code: String
    var text: String
}

struct SystemDef: Codable, Identifiable, Hashable {
    var id: String
    var name: String
    var description: String
    var keywords: [String]
    var products: [String]
    var fillMin: Int
    var fillMax: Int
    var fillDefault: Int
    var geo: Geo?
    var limits: Limits?
    var recommend: [Recommendation]?
    var rules: [Rule]
}

struct GlassItem: Codable, Hashable, Identifiable {
    var fillMm: Int
    var name: String
    var pricePerM2: Double
    var kgM2: Double? = nil
    var id: Int { fillMm }
}

struct HardwareItem: Codable, Hashable, Identifiable {
    var opening: String   // tilt_turn | turn | tilt | top_hung | slide | door
    var name: String
    var price: Double
    var id: String { opening }
}

struct ColorGroup: Codable, Hashable, Identifiable {
    var id: String
    var name: String
    var surchargePercent: Double
}

struct ColorItem: Codable, Hashable, Identifiable {
    var id: String
    var code: String
    var name: String
    var hex: String
    var group: String
    var keywords: [String]
    var title: String { "\(code) · \(name)" }
}

/// Каталог и цены компании (catalog.json, собирается tools/build_catalog.py из прайса).
struct Catalog: Codable, Equatable {
    var version: Int
    var name: String
    var currency: String
    var vatPercent: Double
    var markupPercent: Double
    var wastePercent: Double
    var laborPerM2: Double
    var installPerM2: Double
    var installMin: Double
    var twoColorPercent: Double
    var barDefaultMm: Double? = nil
    var barLengths: [BarLength]? = nil
    var sawKerfMm: Double? = nil
    var barTrimMm: Double? = nil
    var gasketStepM: Double? = nil
    var offerValidDays: Int? = nil
    var colorGroups: [ColorGroup]
    var glass: [GlassItem]
    var hardware: [HardwareItem]
    var colors: [ColorItem]
    var systems: [SystemDef]
    var articles: [Article]

    private var _index: [String: Article]? = nil

    enum CodingKeys: String, CodingKey {
        case version, name, currency, vatPercent, markupPercent, wastePercent, laborPerM2, installPerM2, installMin
        case twoColorPercent, colorGroups, glass, hardware, colors, systems, articles
        case barDefaultMm, barLengths, sawKerfMm, barTrimMm, gasketStepM, offerValidDays
    }

    static func == (a: Catalog, b: Catalog) -> Bool {
        a.version == b.version && a.name == b.name && a.currency == b.currency && a.vatPercent == b.vatPercent
            && a.markupPercent == b.markupPercent && a.wastePercent == b.wastePercent && a.laborPerM2 == b.laborPerM2
            && a.installPerM2 == b.installPerM2 && a.installMin == b.installMin && a.twoColorPercent == b.twoColorPercent
            && a.barDefaultMm == b.barDefaultMm && a.barLengths == b.barLengths && a.sawKerfMm == b.sawKerfMm && a.barTrimMm == b.barTrimMm
            && a.gasketStepM == b.gasketStepM && a.offerValidDays == b.offerValidDays
            && a.colorGroups == b.colorGroups && a.glass == b.glass && a.hardware == b.hardware
            && a.colors == b.colors && a.systems == b.systems && a.articles == b.articles
    }

    mutating func buildIndex() { _index = Dictionary(articles.map { ($0.code, $0) }, uniquingKeysWith: { a, _ in a }) }

    func article(_ code: String) -> Article? { _index?[code] ?? articles.first { $0.code == code } }
    func system(_ id: String) -> SystemDef? { systems.first { $0.id == id } }
    func color(_ id: String) -> ColorItem? { colors.first { $0.id == id } }
    func group(_ id: String) -> ColorGroup? { colorGroups.first { $0.id == id } }

    /// Длина хлыста артикула: самое длинное совпадение начала артикула, иначе по умолчанию.
    func barLength(_ code: String) -> Double {
        let best = (barLengths ?? []).filter { code.hasPrefix($0.prefix) }.max { $0.prefix.count < $1.prefix.count }
        return best?.mm ?? barDefaultMm ?? 6500
    }
    var kerf: Double { sawKerfMm ?? 10 }
    var trim: Double { barTrimMm ?? 20 }
    var gasketStep: Double { gasketStepM ?? 20 }
    var validDays: Int { offerValidDays ?? 5 }

    /// Стеклопакет под заполнение: ближайший не тоньше, иначе самый толстый.
    func glassItem(for fill: Int) -> GlassItem? {
        let sorted = glass.sorted { $0.fillMm < $1.fillMm }
        return sorted.first { $0.fillMm >= fill } ?? sorted.last
    }

    static func decode(_ data: Data) throws -> Catalog {
        var c = try JSONDecoder.snake.decode(Catalog.self, from: data)
        guard c.version >= 3, !c.systems.isEmpty, !c.articles.isEmpty else {
            throw NSError(domain: "Catalog", code: 1, userInfo: [NSLocalizedDescriptionKey: "Каталог старого формата"])
        }
        c.buildIndex()
        return c
    }
}

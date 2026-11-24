import Foundation

/// Позиция прайса компании (артикул).
struct Article: Codable, Identifiable, Hashable {
    var code: String
    var name: String
    var price: Double
    var unit: String   // "m" | "pc"
    var kind: String   // profile | gasket | accessory | other
    var id: String { code }
    var unitTitle: String { unit == "m" ? "м" : "шт" }
}

struct FillCode: Codable, Hashable {
    var min: Int
    var max: Int
    var code: String
}

/// Правило расчёта: какой артикул и от какой величины считается.
struct Rule: Codable, Hashable {
    var role: String
    var code: String?
    var base: String
    var qty: Double
    var profile: Bool
    var doorCode: String?
    var byFill: [FillCode]?
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
    var deductionFixedMm: Double
    var deductionSashMm: Double
    var deductionDoorMm: Double
    var rules: [Rule]
}

struct GlassItem: Codable, Hashable, Identifiable {
    var fillMm: Int
    var name: String
    var pricePerM2: Double
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
    }

    static func == (a: Catalog, b: Catalog) -> Bool {
        a.version == b.version && a.name == b.name && a.currency == b.currency && a.vatPercent == b.vatPercent
            && a.markupPercent == b.markupPercent && a.wastePercent == b.wastePercent && a.laborPerM2 == b.laborPerM2
            && a.installPerM2 == b.installPerM2 && a.installMin == b.installMin && a.twoColorPercent == b.twoColorPercent
            && a.colorGroups == b.colorGroups && a.glass == b.glass && a.hardware == b.hardware
            && a.colors == b.colors && a.systems == b.systems && a.articles == b.articles
    }

    mutating func buildIndex() { _index = Dictionary(articles.map { ($0.code, $0) }, uniquingKeysWith: { a, _ in a }) }

    func article(_ code: String) -> Article? { _index?[code] ?? articles.first { $0.code == code } }
    func system(_ id: String) -> SystemDef? { systems.first { $0.id == id } }
    func color(_ id: String) -> ColorItem? { colors.first { $0.id == id } }
    func group(_ id: String) -> ColorGroup? { colorGroups.first { $0.id == id } }

    /// Стеклопакет под заполнение: ближайший не тоньше, иначе самый толстый.
    func glassItem(for fill: Int) -> GlassItem? {
        let sorted = glass.sorted { $0.fillMm < $1.fillMm }
        return sorted.first { $0.fillMm >= fill } ?? sorted.last
    }

    static func decode(_ data: Data) throws -> Catalog {
        var c = try JSONDecoder.snake.decode(Catalog.self, from: data)
        guard c.version >= 2, !c.systems.isEmpty, !c.articles.isEmpty else {
            throw NSError(domain: "Catalog", code: 1, userInfo: [NSLocalizedDescriptionKey: "Каталог старого формата"])
        }
        c.buildIndex()
        return c
    }
}

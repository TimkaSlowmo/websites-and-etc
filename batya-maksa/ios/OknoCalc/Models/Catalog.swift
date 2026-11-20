import Foundation

struct ProfileItem: Codable, Identifiable, Hashable {
    var id: String
    var name: String
    var keywords: [String]? = nil
    var pricePerM2: Double
    var isDefault: Bool? = nil
}

struct GlassItem: Codable, Identifiable, Hashable {
    var id: String
    var name: String
    var keywords: [String]? = nil
    var pricePerM2: Double
    var isDefault: Bool? = nil
}

struct HardwareItem: Codable, Identifiable, Hashable {
    var opening: OpeningKind
    var name: String
    var price: Double
    var id: String { opening.rawValue }
}

struct ColorItem: Codable, Identifiable, Hashable {
    var id: String
    var name: String
    var keywords: [String]? = nil
    var surchargePercent: Double
    var isDefault: Bool? = nil
}

enum ExtraUnit: String, Codable, Hashable {
    case pc
    case widthM = "width_m"
    case heightM = "height_m"
    case perimeterM = "perimeter_m"
    case areaM2 = "area_m2"

    var title: String {
        switch self {
        case .pc: return "шт"
        case .widthM: return "за м ширины"
        case .heightM: return "за м высоты"
        case .perimeterM: return "за м периметра"
        case .areaM2: return "за м²"
        }
    }
}

struct ExtraItem: Codable, Identifiable, Hashable {
    var id: String
    var name: String
    var keywords: [String]? = nil
    var price: Double
    var unit: ExtraUnit
}

struct Installation: Codable, Hashable {
    var pricePerM2: Double
    var minPrice: Double
}

/// Каталог и цены. Загружается из catalog.json (встроенный пример или импорт пользователя).
struct Catalog: Codable, Equatable {
    var name: String = "Каталог"
    var currency: String = "KZT"
    var vatPercent: Double = 0
    var markupPercent: Double = 0
    var minAreaM2: Double = 0.5
    var glassAreaRatio: Double = 0.85
    var profiles: [ProfileItem]
    var glass: [GlassItem]
    var hardware: [HardwareItem]
    var colors: [ColorItem]
    var extras: [ExtraItem]
    var installation: Installation

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? "Каталог"
        currency = try c.decodeIfPresent(String.self, forKey: .currency) ?? "KZT"
        vatPercent = try c.decodeIfPresent(Double.self, forKey: .vatPercent) ?? 0
        markupPercent = try c.decodeIfPresent(Double.self, forKey: .markupPercent) ?? 0
        minAreaM2 = try c.decodeIfPresent(Double.self, forKey: .minAreaM2) ?? 0.5
        glassAreaRatio = try c.decodeIfPresent(Double.self, forKey: .glassAreaRatio) ?? 0.85
        profiles = try c.decode([ProfileItem].self, forKey: .profiles)
        glass = try c.decode([GlassItem].self, forKey: .glass)
        hardware = try c.decodeIfPresent([HardwareItem].self, forKey: .hardware) ?? []
        colors = try c.decodeIfPresent([ColorItem].self, forKey: .colors) ?? []
        extras = try c.decodeIfPresent([ExtraItem].self, forKey: .extras) ?? []
        installation = try c.decodeIfPresent(Installation.self, forKey: .installation)
            ?? Installation(pricePerM2: 0, minPrice: 0)
    }
}

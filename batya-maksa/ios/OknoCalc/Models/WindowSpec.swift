import Foundation

enum WindowType: String, Codable, CaseIterable, Identifiable {
    case fixed, single_sash, double_sash, triple_sash
    case balcony_door, balcony_block, sliding, entrance_door, interior_door

    var id: String { rawValue }

    var title: String {
        switch self {
        case .fixed: return "Глухое окно"
        case .single_sash: return "Одностворчатое"
        case .double_sash: return "Двухстворчатое"
        case .triple_sash: return "Трёхстворчатое"
        case .balcony_door: return "Балконная дверь"
        case .balcony_block: return "Балконный блок"
        case .sliding: return "Раздвижное"
        case .entrance_door: return "Входная дверь"
        case .interior_door: return "Межкомнатная дверь"
        }
    }
}

enum OpeningKind: String, Codable, CaseIterable, Identifiable {
    case fixed, tilt, turn, tilt_turn, slide

    var id: String { rawValue }

    var title: String {
        switch self {
        case .fixed: return "Глухая"
        case .tilt: return "Откидная"
        case .turn: return "Поворотная"
        case .tilt_turn: return "Поворотно-откидная"
        case .slide: return "Раздвижная"
        }
    }
}

struct Sash: Codable, Identifiable, Hashable {
    var id = UUID()
    var widthMm: Int?
    var opening: OpeningKind

    enum CodingKeys: String, CodingKey { case widthMm, opening }
}

/// Параметры изделия: приходят из ИИ-анализа чертежа и редактируются вручную.
struct WindowSpec: Codable, Equatable {
    var widthMm: Int = 1300
    var heightMm: Int = 1400
    var type: WindowType = .single_sash
    var sashes: [Sash] = [Sash(widthMm: nil, opening: .tilt_turn)]
    var profile: String = ""
    var glass: String = ""
    var color: String = "white"
    var extras: [String] = []
    var notes: String = ""
    var confidence: Double = 1

    var areaM2: Double { Double(widthMm) * Double(heightMm) / 1_000_000 }
    var perimeterM: Double { 2 * Double(widthMm + heightMm) / 1000 }
}

extension JSONDecoder {
    static let snake: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()
}

extension JSONEncoder {
    static let snake: JSONEncoder = {
        let e = JSONEncoder()
        e.keyEncodingStrategy = .convertToSnakeCase
        e.outputFormatting = [.prettyPrinted, .sortedKeys]
        return e
    }()
}

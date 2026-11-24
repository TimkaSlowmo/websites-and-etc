import Foundation

// MARK: - Перечисления (терпимы к неизвестным значениям от ИИ и старым данным)

enum Product: String, Codable, CaseIterable, Identifiable {
    case window, door, balcony_block, facade, partition

    var id: String { rawValue }
    var title: String {
        switch self {
        case .window: return "Окно"
        case .door: return "Дверь"
        case .balcony_block: return "Балконный блок"
        case .facade: return "Витраж / фасад"
        case .partition: return "Перегородка"
        }
    }

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = Product(rawValue: raw) ?? .window
    }
}

enum Opening: String, Codable, CaseIterable, Identifiable {
    case fixed, turn, tilt, tilt_turn, top_hung, slide

    var id: String { rawValue }
    var title: String {
        switch self {
        case .fixed: return "Глухая"
        case .turn: return "Поворотная"
        case .tilt: return "Откидная"
        case .tilt_turn: return "Поворотно-откидная"
        case .top_hung: return "Верхнеподвесная"
        case .slide: return "Раздвижная"
        }
    }
    /// Возможные стороны петель для этого открывания.
    var hinges: [Hinge] {
        switch self {
        case .turn, .tilt_turn: return [.left, .right]
        case .tilt: return [.bottom]
        case .top_hung: return [.top]
        case .fixed, .slide: return [.none]
        }
    }

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = Opening(rawValue: raw) ?? .fixed
    }
}

enum Hinge: String, Codable, CaseIterable, Identifiable {
    case left, right, top, bottom, none

    var id: String { rawValue }
    var title: String {
        switch self {
        case .left: return "Петли слева"
        case .right: return "Петли справа"
        case .top: return "Петли сверху"
        case .bottom: return "Петли снизу"
        case .none: return "—"
        }
    }

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = Hinge(rawValue: raw) ?? .none
    }
}

enum Split: String, Codable, CaseIterable, Identifiable {
    case columns, rows
    var id: String { rawValue }
    var title: String { self == .columns ? "Колонки (слева направо)" : "Ряды (сверху вниз)" }
    var sectionName: String { self == .columns ? "Колонка" : "Ряд" }

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = Split(rawValue: raw) ?? .columns
    }
}

// MARK: - Сетка изделия

/// Ячейка секции. В колонке size = высота (сверху вниз), в ряду size = ширина (слева направо).
struct Cell: Codable, Identifiable, Hashable {
    var id = UUID()
    var sizeMm: Int
    var opening: Opening = .fixed
    var hinge: Hinge = .none
    var inward: Bool = true
    var door: Bool = false

    enum CodingKeys: String, CodingKey { case sizeMm, opening, hinge, inward, door }

    init(sizeMm: Int, opening: Opening = .fixed, hinge: Hinge = .none, inward: Bool = true, door: Bool = false) {
        self.sizeMm = sizeMm; self.opening = opening; self.hinge = hinge; self.inward = inward; self.door = door
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        sizeMm = (try? c.decode(Int.self, forKey: .sizeMm)) ?? Int((try? c.decode(Double.self, forKey: .sizeMm)) ?? 0)
        opening = (try? c.decode(Opening.self, forKey: .opening)) ?? .fixed
        hinge = (try? c.decode(Hinge.self, forKey: .hinge)) ?? .none
        inward = (try? c.decode(Bool.self, forKey: .inward)) ?? true
        door = (try? c.decode(Bool.self, forKey: .door)) ?? false
    }

    /// Приводит петли и признак двери в соответствие с открыванием.
    mutating func normalize() {
        if !opening.hinges.contains(hinge) { hinge = opening.hinges.first ?? .none }
        if opening == .fixed { door = false }
    }
}

/// Секция сетки: колонка (size = ширина) или ряд (size = высота).
struct GridSection: Codable, Identifiable, Hashable {
    var id = UUID()
    var sizeMm: Int
    var cells: [Cell]

    enum CodingKeys: String, CodingKey { case sizeMm, cells }

    init(sizeMm: Int, cells: [Cell]) { self.sizeMm = sizeMm; self.cells = cells }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        sizeMm = (try? c.decode(Int.self, forKey: .sizeMm)) ?? Int((try? c.decode(Double.self, forKey: .sizeMm)) ?? 0)
        cells = (try? c.decode([Cell].self, forKey: .cells)) ?? []
    }
}

struct Layout: Codable, Hashable {
    var split: Split = .columns
    var sections: [GridSection]

    static func single(width: Int, height: Int, opening: Opening = .tilt_turn) -> Layout {
        var cell = Cell(sizeMm: height, opening: opening)
        cell.normalize()
        return Layout(split: .columns, sections: [GridSection(sizeMm: width, cells: [cell])])
    }
}

/// Прямоугольник ячейки в мм от левого верхнего угла.
struct CellRect: Identifiable {
    var section: Int
    var index: Int
    var x: Double, y: Double, w: Double, h: Double
    var cell: Cell
    var id: String { "\(section)-\(index)" }
}

struct InfoItem: Codable, Hashable, Identifiable {
    var label: String
    var value: String
    var id: String { label + "|" + value }
}

/// Другие значения, которые увидела вторая модель ИИ.
struct Alternatives: Codable, Equatable, Hashable {
    var widthMm: [Int]? = nil
    var heightMm: [Int]? = nil
}

// MARK: - Изделие

struct WindowSpec: Codable, Equatable, Hashable {
    var widthMm: Int = 1300
    var heightMm: Int = 1400
    var product: Product = .window
    var quantity: Int = 1
    var layout: Layout = .single(width: 1300, height: 1400)
    var system: String = ""
    var fillMm: Int? = nil
    var glass: String = ""
    var color: String = ""
    var colorInside: String = ""
    var inward: Bool = true
    var info: [InfoItem] = []
    var notes: String = ""
    var confidence: Double = 1
    var alternatives: Alternatives? = nil

    enum CodingKeys: String, CodingKey {
        case widthMm, heightMm, product, quantity, layout, system, fillMm, glass, color, colorInside, inward, info, notes, confidence, alternatives
        case sashes, type // старый формат
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func int(_ k: CodingKeys) -> Int? {
            if let v = try? c.decode(Int.self, forKey: k) { return v }
            if let v = try? c.decode(Double.self, forKey: k) { return Int(v.rounded()) }
            return nil
        }
        widthMm = int(.widthMm) ?? 1300
        heightMm = int(.heightMm) ?? 1400
        quantity = max(1, int(.quantity) ?? 1)
        if let p = try? c.decode(Product.self, forKey: .product) {
            product = p
        } else {
            let t = (try? c.decode(String.self, forKey: .type)) ?? ""
            product = ["balcony_door", "entrance_door", "interior_door"].contains(t) ? .door : (t == "balcony_block" ? .balcony_block : .window)
        }
        system = (try? c.decode(String.self, forKey: .system)) ?? ""
        fillMm = int(.fillMm)
        glass = (try? c.decode(String.self, forKey: .glass)) ?? ""
        color = (try? c.decode(String.self, forKey: .color)) ?? ""
        colorInside = (try? c.decode(String.self, forKey: .colorInside)) ?? ""
        inward = (try? c.decode(Bool.self, forKey: .inward)) ?? true
        info = (try? c.decode([InfoItem].self, forKey: .info)) ?? []
        notes = (try? c.decode(String.self, forKey: .notes)) ?? ""
        confidence = (try? c.decode(Double.self, forKey: .confidence)) ?? 1
        alternatives = try? c.decode(Alternatives.self, forKey: .alternatives)

        if let l = try? c.decode(Layout.self, forKey: .layout), !l.sections.isEmpty {
            layout = l
        } else {
            // Старый формат: створки слева направо.
            struct OldSash: Decodable { var widthMm: Int?; var opening: Opening? }
            let old = (try? c.decode([OldSash].self, forKey: .sashes)) ?? []
            if old.isEmpty {
                layout = .single(width: widthMm, height: heightMm)
            } else {
                let each = widthMm / old.count
                layout = Layout(split: .columns, sections: old.map { s in
                    var cell = Cell(sizeMm: heightMm, opening: s.opening ?? .fixed)
                    cell.normalize()
                    return GridSection(sizeMm: s.widthMm ?? each, cells: [cell])
                })
            }
        }
        fitLayout()
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(widthMm, forKey: .widthMm)
        try c.encode(heightMm, forKey: .heightMm)
        try c.encode(product, forKey: .product)
        try c.encode(quantity, forKey: .quantity)
        try c.encode(layout, forKey: .layout)
        try c.encode(system, forKey: .system)
        try c.encodeIfPresent(fillMm, forKey: .fillMm)
        try c.encode(glass, forKey: .glass)
        try c.encode(color, forKey: .color)
        try c.encode(colorInside, forKey: .colorInside)
        try c.encode(inward, forKey: .inward)
        try c.encode(info, forKey: .info)
        try c.encode(notes, forKey: .notes)
        try c.encode(confidence, forKey: .confidence)
        try c.encodeIfPresent(alternatives, forKey: .alternatives)
    }

    var areaM2: Double { Double(widthMm) * Double(heightMm) / 1_000_000 }

    /// Размер секций вдоль главной оси и размер ячеек поперёк.
    var mainTotal: Int { layout.split == .columns ? widthMm : heightMm }
    var crossTotal: Int { layout.split == .columns ? heightMm : widthMm }

    var cellRects: [CellRect] {
        var out: [CellRect] = []
        var a = 0.0
        for (si, s) in layout.sections.enumerated() {
            var b = 0.0
            for (ci, c) in s.cells.enumerated() {
                if layout.split == .columns {
                    out.append(CellRect(section: si, index: ci, x: a, y: b, w: Double(s.sizeMm), h: Double(c.sizeMm), cell: c))
                } else {
                    out.append(CellRect(section: si, index: ci, x: b, y: a, w: Double(c.sizeMm), h: Double(s.sizeMm), cell: c))
                }
                b += Double(c.sizeMm)
            }
            a += Double(s.sizeMm)
        }
        return out
    }

    /// Сумма секций и ячеек в каждой секции совпадает с общими размерами?
    var layoutMismatch: String? {
        let main = layout.sections.reduce(0) { $0 + $1.sizeMm }
        if main != mainTotal {
            return "Сумма \(layout.split == .columns ? "колонок" : "рядов") \(main) мм, а \(layout.split == .columns ? "ширина" : "высота") \(mainTotal) мм"
        }
        for (i, s) in layout.sections.enumerated() {
            let sum = s.cells.reduce(0) { $0 + $1.sizeMm }
            if sum != crossTotal {
                return "\(layout.split.sectionName) \(i + 1): сумма ячеек \(sum) мм, а должно быть \(crossTotal) мм"
            }
        }
        return nil
    }

    /// Подгоняет размеры секций и ячеек под общие размеры (пропорционально, остаток — в последнюю).
    mutating func fitLayout() {
        if layout.sections.isEmpty { layout = .single(width: widthMm, height: heightMm) }
        layout.sections = Self.fit(layout.sections.map(\.sizeMm), to: mainTotal).enumerated().map { i, v in
            var s = layout.sections[i]
            s.sizeMm = v
            if s.cells.isEmpty { s.cells = [Cell(sizeMm: crossTotal)] }
            let sizes = Self.fit(s.cells.map(\.sizeMm), to: crossTotal)
            for j in s.cells.indices { s.cells[j].sizeMm = sizes[j]; s.cells[j].normalize() }
            return s
        }
    }

    static func fit(_ values: [Int], to total: Int) -> [Int] {
        guard !values.isEmpty else { return [] }
        let sum = values.reduce(0, +)
        var out: [Int]
        if sum <= 0 {
            out = values.map { _ in total / values.count }
        } else if sum == total {
            return values
        } else {
            out = values.map { max(1, Int((Double($0) * Double(total) / Double(sum)).rounded())) }
        }
        out[out.count - 1] += total - out.reduce(0, +)
        return out
    }

    var openingCount: Int { layout.sections.reduce(0) { $0 + $1.cells.filter { $0.opening != .fixed }.count } }
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
        e.outputFormatting = [.sortedKeys]
        return e
    }()
}

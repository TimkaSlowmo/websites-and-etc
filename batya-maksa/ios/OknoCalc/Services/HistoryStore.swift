import UIKit

/// Сохранённое КП: несколько окон, настройки расчёта и итог.
struct HistoryItem: Codable, Identifiable, Equatable {
    var id = UUID()
    var date = Date()
    var positions: [Position]
    var options: OfferOptions
    var total: Double
    var currency: String

    var windows: Int { positions.reduce(0) { $0 + max(1, $1.spec.quantity) } }
    var title: String {
        guard let p = positions.first else { return "КП" }
        let first = "\(p.spec.product.title) \(p.spec.widthMm)×\(p.spec.heightMm)"
        return positions.count == 1 ? first : "\(first) и ещё \(positions.count - 1)"
    }

    init(id: UUID = UUID(), date: Date = Date(), positions: [Position], options: OfferOptions, total: Double, currency: String) {
        self.id = id; self.date = date; self.positions = positions; self.options = options; self.total = total; self.currency = currency
    }

    enum CodingKeys: String, CodingKey { case id, date, positions, options, total, currency, spec, selection }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(UUID.self, forKey: .id)) ?? UUID()
        date = (try? c.decode(Date.self, forKey: .date)) ?? Date()
        total = (try? c.decode(Double.self, forKey: .total)) ?? 0
        currency = (try? c.decode(String.self, forKey: .currency)) ?? "KZT"
        options = (try? c.decode(OfferOptions.self, forKey: .options)) ?? OfferOptions()
        if let list = try? c.decode([Position].self, forKey: .positions) {
            positions = list
        } else {
            // Старый формат — одно окно; id позиции = id записи, чтобы нашлись старые картинки
            let spec = try c.decode(WindowSpec.self, forKey: .spec)
            let sel = (try? c.decode(Selection.self, forKey: .selection)) ?? Selection()
            positions = [Position(id: id, mark: "ОК-1", spec: spec, selection: sel)]
            options.scope = .turnkey
        }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(date, forKey: .date)
        try c.encode(positions, forKey: .positions)
        try c.encode(options, forKey: .options)
        try c.encode(total, forKey: .total)
        try c.encode(currency, forKey: .currency)
    }
}

/// Локальная история КП: JSON + картинки окон в Documents/history.
@MainActor
final class HistoryStore: ObservableObject {
    @Published private(set) var items: [HistoryItem] = []

    private let dir: URL
    private var indexURL: URL { dir.appendingPathComponent("index.json") }

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        dir = docs.appendingPathComponent("history", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        if let data = try? Data(contentsOf: indexURL),
           let list = try? JSONDecoder().decode([HistoryItem].self, from: data) {
            items = list
        }
    }

    /// Сохраняет КП (повторное сохранение того же КП обновляет запись).
    func save(_ item: HistoryItem, drawings: [UUID: UIImage], renders: [UUID: UIImage]) {
        for p in item.positions {
            write(drawings[p.id], name: "\(p.id)_d.jpg")
            write(renders[p.id], name: "\(p.id)_r.jpg")
        }
        items.removeAll { $0.id == item.id }
        items.insert(item, at: 0)
        persist()
    }

    func delete(_ item: HistoryItem) {
        for p in item.positions {
            try? FileManager.default.removeItem(at: file("\(p.id)_d.jpg"))
            try? FileManager.default.removeItem(at: file("\(p.id)_r.jpg"))
        }
        items.removeAll { $0.id == item.id }
        persist()
    }

    func drawing(_ positionId: UUID) -> UIImage? { UIImage(contentsOfFile: file("\(positionId)_d.jpg").path) }
    func render(_ positionId: UUID) -> UIImage? { UIImage(contentsOfFile: file("\(positionId)_r.jpg").path) }
    func thumbnail(for item: HistoryItem) -> UIImage? {
        item.positions.lazy.compactMap { self.render($0.id) ?? self.drawing($0.id) }.first
    }

    private func file(_ name: String) -> URL { dir.appendingPathComponent(name) }

    private func write(_ image: UIImage?, name: String) {
        guard let data = image?.jpegData(compressionQuality: 0.8) else { return }
        try? data.write(to: file(name), options: .atomic)
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(items) { try? data.write(to: indexURL, options: .atomic) }
    }
}

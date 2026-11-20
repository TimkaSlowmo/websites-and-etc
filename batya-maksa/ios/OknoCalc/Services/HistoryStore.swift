import UIKit

struct HistoryItem: Codable, Identifiable, Equatable {
    var id = UUID()
    var date = Date()
    var spec: WindowSpec
    var selection: Selection
    var total: Double
    var currency: String
}

/// Локальная история расчётов: JSON + картинки в Documents/history.
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

    func save(spec: WindowSpec, selection: Selection, total: Double, currency: String,
              drawing: UIImage?, render: UIImage?) {
        let item = HistoryItem(spec: spec, selection: selection, total: total, currency: currency)
        write(drawing, name: "\(item.id)_d.jpg")
        write(render, name: "\(item.id)_r.jpg")
        items.insert(item, at: 0)
        persist()
    }

    func delete(_ item: HistoryItem) {
        try? FileManager.default.removeItem(at: file("\(item.id)_d.jpg"))
        try? FileManager.default.removeItem(at: file("\(item.id)_r.jpg"))
        items.removeAll { $0.id == item.id }
        persist()
    }

    func drawing(for item: HistoryItem) -> UIImage? { UIImage(contentsOfFile: file("\(item.id)_d.jpg").path) }
    func render(for item: HistoryItem) -> UIImage? { UIImage(contentsOfFile: file("\(item.id)_r.jpg").path) }

    private func file(_ name: String) -> URL { dir.appendingPathComponent(name) }

    private func write(_ image: UIImage?, name: String) {
        guard let data = image?.jpegData(compressionQuality: 0.8) else { return }
        try? data.write(to: file(name), options: .atomic)
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(items) { try? data.write(to: indexURL, options: .atomic) }
    }
}

import UIKit

/// Черновик текущего КП (Documents/draft): сохраняется сам при каждом изменении и открывается
/// при следующем запуске — КП не теряется, если приложение закрыли или iOS выгрузила его из памяти.
@MainActor
final class DraftStore {
    struct Draft: Codable {
        var positions: [Position]
        var options: OfferOptions
        var current: Int
        var historyId: UUID?
        /// КП совпадает с сохранённым в историю (кнопка «Сохранено»)
        var saved: Bool
    }

    private let dir: URL
    private var indexURL: URL { dir.appendingPathComponent("draft.json") }
    /// Какая картинка уже лежит в файле — чтобы не перекодировать одно и то же при каждом изменении
    private var written: [String: ObjectIdentifier] = [:]
    private let io = DispatchQueue(label: "kz.okno.draft", qos: .utility)

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        dir = docs.appendingPathComponent("draft", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }

    func load() -> Draft? {
        guard let data = try? Data(contentsOf: indexURL),
              let d = try? JSONDecoder().decode(Draft.self, from: data), !d.positions.isEmpty else { return nil }
        return d
    }

    func image(_ id: UUID, _ kind: String) -> UIImage? {
        let name = "\(id)_\(kind).jpg"
        guard let img = UIImage(contentsOfFile: dir.appendingPathComponent(name).path) else { return nil }
        written[name] = ObjectIdentifier(img)
        return img
    }

    /// nil — КП пустое, черновик удаляется.
    func save(_ d: Draft?) {
        guard let d else { clear(); return }
        guard let data = try? JSONEncoder().encode(d) else { return }
        let url = indexURL
        io.async { try? data.write(to: url, options: .atomic) }
    }

    /// Дописывает новые картинки окон и удаляет картинки окон, которых больше нет в КП.
    func syncImages(drawings: [UUID: UIImage], renders: [UUID: UIImage]) {
        var keep: [String: UIImage] = [:]
        for (id, img) in drawings { keep["\(id)_d.jpg"] = img }
        for (id, img) in renders { keep["\(id)_r.jpg"] = img }
        var toWrite: [(URL, UIImage)] = []
        for (name, img) in keep where written[name] != ObjectIdentifier(img) {
            written[name] = ObjectIdentifier(img)
            toWrite.append((dir.appendingPathComponent(name), img))
        }
        let dir = dir
        let stale = existingImages().filter { keep[$0] == nil }
        stale.forEach { written[$0] = nil }
        guard !toWrite.isEmpty || !stale.isEmpty else { return }
        io.async {
            for (url, img) in toWrite {
                if let data = img.jpegData(compressionQuality: 0.8) { try? data.write(to: url, options: .atomic) }
            }
            for name in stale { try? FileManager.default.removeItem(at: dir.appendingPathComponent(name)) }
        }
    }

    func clear() {
        written = [:]
        let dir = dir
        io.async {
            let files = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
            files.forEach { try? FileManager.default.removeItem(at: $0) }
        }
    }

    private func existingImages() -> [String] {
        ((try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []).filter { $0.hasSuffix(".jpg") }
    }
}

import Foundation

/// Каталог компании: встроенный catalog.json + сохранённые правки цен (Documents/catalog.json).
@MainActor
final class CatalogStore: ObservableObject {
    @Published private(set) var catalog: Catalog

    private static var userFile: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("catalog.json")
    }

    init() {
        if let data = try? Data(contentsOf: Self.userFile), let c = try? Catalog.decode(data) {
            catalog = c
        } else {
            catalog = Self.loadBundled()
        }
    }

    static func loadBundled() -> Catalog {
        guard let url = Bundle.main.url(forResource: "catalog", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let c = try? Catalog.decode(data) else {
            fatalError("catalog.json отсутствует или повреждён в сборке")
        }
        return c
    }

    /// Сохраняет правки (цены, коэффициенты) поверх встроенного каталога.
    func update(_ change: (inout Catalog) -> Void) {
        var c = catalog
        change(&c)
        c.buildIndex()
        catalog = c
        if let data = try? JSONEncoder.snake.encode(c) { try? data.write(to: Self.userFile, options: .atomic) }
    }

    /// Загрузка каталога из файла JSON (формат как catalog.json).
    func importCatalog(from url: URL) throws {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let data = try Data(contentsOf: url)
        let parsed: Catalog
        do { parsed = try Catalog.decode(data) } catch {
            throw CatalogError.invalid("Не удалось прочитать каталог: \(error.localizedDescription)")
        }
        try data.write(to: Self.userFile, options: .atomic)
        catalog = parsed
    }

    func resetToBundled() {
        try? FileManager.default.removeItem(at: Self.userFile)
        catalog = Self.loadBundled()
    }

    var isModified: Bool { FileManager.default.fileExists(atPath: Self.userFile.path) }

    enum CatalogError: LocalizedError {
        case invalid(String)
        var errorDescription: String? { if case .invalid(let m) = self { return m } else { return nil } }
    }
}

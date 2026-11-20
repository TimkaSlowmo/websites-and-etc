import Foundation

@MainActor
final class CatalogStore: ObservableObject {
    @Published private(set) var catalog: Catalog

    private static var userFile: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("catalog.json")
    }

    init() {
        if let data = try? Data(contentsOf: Self.userFile),
           let c = try? JSONDecoder.snake.decode(Catalog.self, from: data) {
            catalog = c
        } else {
            catalog = Self.loadBundled()
        }
    }

    private static func loadBundled() -> Catalog {
        guard let url = Bundle.main.url(forResource: "catalog", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let c = try? JSONDecoder.snake.decode(Catalog.self, from: data) else {
            fatalError("catalog.json отсутствует или повреждён в сборке")
        }
        return c
    }

    /// Импорт каталога (JSON). Бросает ошибку с понятным текстом, если формат неверный.
    func importCatalog(from url: URL) throws {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let data = try Data(contentsOf: url)
        let parsed: Catalog
        do {
            parsed = try JSONDecoder.snake.decode(Catalog.self, from: data)
        } catch {
            throw CatalogError.invalid("Не удалось прочитать каталог: \(error.localizedDescription)")
        }
        guard !parsed.profiles.isEmpty, !parsed.glass.isEmpty else {
            throw CatalogError.invalid("В каталоге нет профилей или стеклопакетов")
        }
        try data.write(to: Self.userFile, options: .atomic)
        catalog = parsed
    }

    func resetToBundled() {
        try? FileManager.default.removeItem(at: Self.userFile)
        catalog = Self.loadBundled()
    }

    enum CatalogError: LocalizedError {
        case invalid(String)
        var errorDescription: String? { if case .invalid(let m) = self { return m } else { return nil } }
    }
}

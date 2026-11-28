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
        } else if let data = try? Data(contentsOf: Self.userFile) {
            // Правки из каталога старой версии: переносим ставки на новый встроенный каталог
            catalog = Self.migrate(old: data, into: Self.loadBundled())
            if let out = try? JSONEncoder.snake.encode(catalog) { try? out.write(to: Self.userFile, options: .atomic) }
        } else {
            catalog = Self.loadBundled()
        }
    }

    /// Переносит изменённые пользователем ставки и цены (наценка, работа, стеклопакеты, фурнитура, надбавки за цвет).
    static func migrate(old data: Data, into fresh: Catalog) -> Catalog {
        guard let o = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return fresh }
        var c = fresh
        func num(_ k: String) -> Double? { (o[k] as? NSNumber)?.doubleValue }
        if let v = num("markup_percent") { c.markupPercent = v }
        if let v = num("vat_percent") { c.vatPercent = v }
        if let v = num("waste_percent") { c.wastePercent = v }
        if let v = num("labor_per_m2") { c.laborPerM2 = v }
        if let v = num("install_per_m2") { c.installPerM2 = v }
        if let v = num("install_min") { c.installMin = v }
        if let v = num("two_color_percent") { c.twoColorPercent = v }
        for g in (o["glass"] as? [[String: Any]]) ?? [] {
            if let f = (g["fill_mm"] as? NSNumber)?.intValue, let p = (g["price_per_m2"] as? NSNumber)?.doubleValue,
               let i = c.glass.firstIndex(where: { $0.fillMm == f }) { c.glass[i].pricePerM2 = p }
        }
        for h in (o["hardware"] as? [[String: Any]]) ?? [] {
            if let k = h["opening"] as? String, let p = (h["price"] as? NSNumber)?.doubleValue,
               let i = c.hardware.firstIndex(where: { $0.opening == k }) { c.hardware[i].price = p }
        }
        for g in (o["color_groups"] as? [[String: Any]]) ?? [] {
            if let k = g["id"] as? String, let p = (g["surcharge_percent"] as? NSNumber)?.doubleValue,
               let i = c.colorGroups.firstIndex(where: { $0.id == k }) { c.colorGroups[i].surchargePercent = p }
        }
        c.buildIndex()
        return c
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

    func resetToBundled() {
        try? FileManager.default.removeItem(at: Self.userFile)
        catalog = Self.loadBundled()
    }

    var isModified: Bool { FileManager.default.fileExists(atPath: Self.userFile.path) }
}

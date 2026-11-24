import SwiftUI

@MainActor
final class EstimateViewModel: ObservableObject {
    enum Phase: Equatable {
        case idle
        case analyzing
        case ready
        case failed(String)
    }

    @Published var phase: Phase = .idle
    @Published var tab: AppTab = .estimate
    @Published var drawing: UIImage?
    @Published var renderImage: UIImage?
    @Published var isRendering = false
    @Published var renderError: String?
    @Published var spec = WindowSpec()
    @Published var selection = Selection()

    private let settings: AppSettings
    private let catalogStore: CatalogStore
    private let history: HistoryStore

    init(settings: AppSettings, catalogStore: CatalogStore, history: HistoryStore) {
        self.settings = settings
        self.catalogStore = catalogStore
        self.history = history
        applyLaunchArguments()
    }

    var companyName: String { settings.companyName }
    var catalog: Catalog { catalogStore.catalog }
    var breakdown: PriceBreakdown { PricingEngine.calculate(spec: spec, selection: selection, catalog: catalog) }

    func saveToHistory() {
        history.save(spec: spec, selection: selection, total: breakdown.total, currency: breakdown.currency,
                     drawing: drawing, render: renderImage)
    }

    func load(_ item: HistoryItem) {
        spec = item.spec
        selection = item.selection
        if catalog.system(selection.systemId) == nil { selection = PricingEngine.autoSelection(spec: spec, catalog: catalog) }
        drawing = history.drawing(for: item)
        renderImage = history.render(for: item)
        renderError = nil
        phase = .ready
        tab = .estimate
    }

    func start(with image: UIImage) {
        drawing = image
        renderImage = nil
        renderError = nil
        withAnimation(.spring(response: 0.45, dampingFraction: 0.9)) { phase = .analyzing }
        Task { await analyze(image) }
    }

    func retry() {
        guard let drawing else { return }
        start(with: drawing)
    }

    func reset() {
        phase = .idle
        drawing = nil
        renderImage = nil
        renderError = nil
        spec = WindowSpec()
    }

    /// Ручной режим: если ИИ недоступен, можно ввести параметры вручную.
    func startManual() {
        spec = WindowSpec()
        drawing = nil
        renderImage = nil
        selection = PricingEngine.autoSelection(spec: spec, catalog: catalog)
        phase = .ready
    }

    private func analyze(_ image: UIImage) async {
        do {
            let client = try APIClient(settings: settings)
            let result = try await client.analyze(drawing: image)
            selection = PricingEngine.autoSelection(spec: result, catalog: catalog)
            UINotificationFeedbackGenerator().notificationOccurred(result.confidence < 0.6 ? .warning : .success)
            withAnimation(.spring(response: 0.5, dampingFraction: 0.85)) {
                spec = result
                phase = .ready
            }
            await renderPhoto()
        } catch {
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            withAnimation(.spring) { phase = .failed(error.localizedDescription) }
        }
    }

    func renderPhoto() async {
        isRendering = true
        renderError = nil
        defer { isRendering = false }
        do {
            let client = try APIClient(settings: settings)
            var s = spec
            if let col = catalog.color(selection.colorId) { s.color = "\(col.code) \(col.name)" }
            let img = try await client.render(spec: s)
            withAnimation(.easeOut(duration: 0.5)) { renderImage = img }
        } catch {
            renderError = error.localizedDescription
        }
    }

    // MARK: Редактирование

    func setSize(width: Int? = nil, height: Int? = nil) {
        if let width { spec.widthMm = max(100, width) }
        if let height { spec.heightMm = max(100, height) }
        spec.fitLayout()
    }

    func setSplit(_ split: Split) {
        guard split != spec.layout.split else { return }
        // Переворачиваем сетку: колонки ⇄ ряды, сохраняя ячейки там, где это однозначно.
        let main = split == .columns ? spec.widthMm : spec.heightMm
        let cross = split == .columns ? spec.heightMm : spec.widthMm
        let n = spec.layout.sections.count
        let opened = spec.layout.sections.flatMap(\.cells).first { $0.opening != .fixed }
        spec.layout = Layout(split: split, sections: (0..<max(1, n)).map { i in
            var c = Cell(sizeMm: cross, opening: i == 0 ? (opened?.opening ?? .fixed) : .fixed, hinge: i == 0 ? (opened?.hinge ?? .none) : .none)
            c.normalize()
            return Section(sizeMm: main / max(1, n), cells: [c])
        })
        spec.fitLayout()
    }

    func addSection() {
        let n = spec.layout.sections.count + 1
        let size = spec.mainTotal / n
        for i in spec.layout.sections.indices { spec.layout.sections[i].sizeMm = Int(Double(spec.layout.sections[i].sizeMm) * Double(n - 1) / Double(n)) }
        spec.layout.sections.append(Section(sizeMm: size, cells: [Cell(sizeMm: spec.crossTotal)]))
        spec.fitLayout()
    }

    func removeSection(_ i: Int) {
        guard spec.layout.sections.count > 1, spec.layout.sections.indices.contains(i) else { return }
        let freed = spec.layout.sections[i].sizeMm
        spec.layout.sections.remove(at: i)
        spec.layout.sections[max(0, i - 1)].sizeMm += freed
        spec.fitLayout()
    }

    /// Делит ячейку пополам в пределах своей секции.
    func splitCell(section: Int, index: Int) {
        guard spec.layout.sections.indices.contains(section), spec.layout.sections[section].cells.indices.contains(index) else { return }
        let c = spec.layout.sections[section].cells[index]
        let a = c.sizeMm / 2
        spec.layout.sections[section].cells[index].sizeMm = c.sizeMm - a
        spec.layout.sections[section].cells.insert(Cell(sizeMm: a), at: index + 1)
        spec.fitLayout()
    }

    func removeCell(section: Int, index: Int) {
        guard spec.layout.sections.indices.contains(section), spec.layout.sections[section].cells.count > 1 else { return }
        let freed = spec.layout.sections[section].cells.remove(at: index).sizeMm
        spec.layout.sections[section].cells[max(0, index - 1)].sizeMm += freed
        spec.fitLayout()
    }

    func updateCell(section: Int, index: Int, _ change: (inout Cell) -> Void) {
        guard spec.layout.sections.indices.contains(section), spec.layout.sections[section].cells.indices.contains(index) else { return }
        change(&spec.layout.sections[section].cells[index])
        spec.layout.sections[section].cells[index].normalize()
    }

    /// Меняет размер секции, забирая разницу у соседней.
    func setSectionSize(_ i: Int, _ value: Int) {
        guard spec.layout.sections.indices.contains(i), spec.layout.sections.count > 1 else { return }
        let j = i == spec.layout.sections.count - 1 ? i - 1 : i + 1
        let total = spec.layout.sections[i].sizeMm + spec.layout.sections[j].sizeMm
        let v = min(max(100, value), total - 100)
        spec.layout.sections[i].sizeMm = v
        spec.layout.sections[j].sizeMm = total - v
    }

    func setCellSize(section: Int, index: Int, _ value: Int) {
        guard spec.layout.sections.indices.contains(section) else { return }
        var cells = spec.layout.sections[section].cells
        guard cells.count > 1, cells.indices.contains(index) else { return }
        let j = index == cells.count - 1 ? index - 1 : index + 1
        let total = cells[index].sizeMm + cells[j].sizeMm
        let v = min(max(100, value), total - 100)
        cells[index].sizeMm = v
        cells[j].sizeMm = total - v
        spec.layout.sections[section].cells = cells
    }

    var summaryText: String {
        let b = breakdown
        let sys = catalog.system(selection.systemId)?.name ?? ""
        let col = catalog.color(selection.colorId)?.title ?? ""
        var t = "\(spec.product.title) \(spec.widthMm)×\(spec.heightMm) мм, \(sys), заполнение \(selection.fillMm) мм, \(col)\n"
        for r in spec.cellRects {
            t += "• \(spec.layout.split.sectionName) \(r.section + 1), ячейка \(r.index + 1): \(Int(r.w))×\(Int(r.h)) — \(r.cell.door ? "дверь, " : "")\(r.cell.opening.title)\(r.cell.hinge != .none ? ", " + r.cell.hinge.title.lowercased() : "")\n"
        }
        t += "Цена за 1 шт: \(Money.format(b.unitTotal, currency: b.currency))"
        if b.quantity > 1 { t += "\nИтого за \(b.quantity) шт: \(Money.format(b.total, currency: b.currency))" }
        return t
    }

    // MARK: Демо для проверки (запуск с аргументом -demo)

    private func applyLaunchArguments() {
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-demo") {
            var s = WindowSpec()
            s.widthMm = 1200; s.heightMm = 2700; s.product = .window
            s.layout = Layout(split: .columns, sections: [
                Section(sizeMm: 800, cells: [Cell(sizeMm: 1700, opening: .tilt_turn, hinge: .left), Cell(sizeMm: 1000)]),
                Section(sizeMm: 400, cells: [Cell(sizeMm: 2700)]),
            ])
            s.system = "BKT 77"; s.fillMm = 32; s.color = "RAL 7024"; s.confidence = 0.9
            s.info = [InfoItem(label: "Система", value: "BKT 77"), InfoItem(label: "Заполнение", value: "32 мм"),
                      InfoItem(label: "Цвет", value: "RAL 7024"), InfoItem(label: "Схема открывания", value: "поворотно-откидная, петли слева")]
            s.notes = "1200 2700 1700 1000 800 400. Система BKT 77, заполнение 32, цвет RAL 7024"
            spec = s
            selection = PricingEngine.autoSelection(spec: s, catalog: catalog)
            phase = .ready
        }
        if let i = args.firstIndex(of: "-tab"), i + 1 < args.count {
            let map: [String: AppTab] = ["history": .history, "catalog": .catalog, "settings": .settings]
            tab = map[args[i + 1]] ?? .estimate
        }
    }
}

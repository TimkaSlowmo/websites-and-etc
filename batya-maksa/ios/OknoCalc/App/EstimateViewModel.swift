import SwiftUI

/// Коммерческое предложение из нескольких окон. Редактируется «текущее» окно; «Добавить» — новое окно в КП,
/// «Новый» — начать КП заново.
@MainActor
final class EstimateViewModel: ObservableObject {
    enum Phase: Equatable {
        case idle
        case analyzing
        case ready
        case failed(String)
    }

    /// Стиль ИИ-фото
    enum RenderStyle: String, CaseIterable, Identifiable {
        case facade, interior, studio
        var id: String { rawValue }
        var title: String { ["facade": "Фасад", "interior": "Интерьер", "studio": "Студия"][rawValue] ?? rawValue }
    }

    @Published var phase: Phase = .idle
    @Published var tab: AppTab = .estimate
    @Published var positions: [Position] = []
    @Published var current: Int = 0
    @Published var options = OfferOptions()
    @Published var drawings: [UUID: UIImage] = [:]
    @Published var renders: [UUID: UIImage] = [:]
    @Published var rendering: Set<UUID> = []
    @Published var renderErrors: [UUID: String] = [:]
    @Published var renderStyle: RenderStyle = .facade
    /// Фото, которое сейчас распознаётся (для экрана анализа и «Повторить»)
    @Published var pendingImage: UIImage?

    private var historyId: UUID?
    private var savedState: [Position]?
    private var savedOptions: OfferOptions?

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

    // MARK: Текущее окно

    private var cur: Int? { positions.indices.contains(current) ? current : nil }

    var spec: WindowSpec {
        get { cur.map { positions[$0].spec } ?? WindowSpec() }
        set { if let i = cur { positions[i].spec = newValue } }
    }
    var selection: Selection {
        get { cur.map { positions[$0].selection } ?? Selection() }
        set { if let i = cur { positions[i].selection = newValue } }
    }
    var mark: String {
        get { cur.map { positions[$0].mark } ?? "" }
        set { if let i = cur { positions[i].mark = newValue } }
    }
    var currentId: UUID? { cur.map { positions[$0].id } }
    var drawing: UIImage? { currentId.flatMap { drawings[$0] } }
    var renderImage: UIImage? { currentId.flatMap { renders[$0] } }
    var isRendering: Bool { currentId.map { rendering.contains($0) } ?? false }
    var renderError: String? { currentId.flatMap { renderErrors[$0] } }

    /// Расчёт всего КП (общий раскрой и уплотнители).
    var offer: OfferBreakdown { PricingEngine.offer(positions, options: options, catalog: catalog) }

    var isSaved: Bool { savedState == positions && savedOptions == options && !positions.isEmpty }

    // MARK: Добавление окон

    /// Новое окно по фото чертежа.
    func start(with image: UIImage) {
        pendingImage = image
        withAnimation(.spring(response: 0.45, dampingFraction: 0.9)) { phase = .analyzing }
        Task { await analyze(image) }
    }

    func retry() {
        guard let pendingImage else { return }
        start(with: pendingImage)
    }

    /// Вернуться к КП после ошибки распознавания (если в КП уже есть окна).
    func cancelAdding() {
        pendingImage = nil
        withAnimation { phase = positions.isEmpty ? .idle : .ready }
    }

    /// Новое окно вручную (параметры по умолчанию или как у последнего окна КП).
    func addManual() {
        var p = Position(mark: nextMark())
        if let last = positions.last {
            p.selection = last.selection
            p.selection.overrides = []
        } else {
            p.selection = PricingEngine.autoSelection(spec: p.spec, catalog: catalog)
        }
        append(p)
    }

    /// Копия текущего окна (например, такое же окно другого размера).
    func duplicateCurrent() {
        guard let i = cur else { return }
        var p = positions[i]
        p.id = UUID()
        p.mark = nextMark()
        if let d = drawings[positions[i].id] { drawings[p.id] = d }
        if let r = renders[positions[i].id] { renders[p.id] = r }
        append(p)
    }

    func removePosition(_ i: Int) {
        guard positions.indices.contains(i) else { return }
        let id = positions[i].id
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
            positions.remove(at: i)
            drawings[id] = nil; renders[id] = nil; renderErrors[id] = nil
            current = min(current, max(0, positions.count - 1))
            if positions.isEmpty { phase = .idle }
        }
    }

    func select(_ i: Int) {
        guard positions.indices.contains(i) else { return }
        withAnimation(.easeInOut(duration: 0.25)) { current = i }
    }

    private func append(_ p: Position) {
        withAnimation(.spring(response: 0.45, dampingFraction: 0.88)) {
            positions.append(p)
            current = positions.count - 1
            phase = .ready
        }
    }

    private func nextMark() -> String {
        let used = positions.compactMap { Int($0.mark.filter(\.isNumber)) }
        return "ОК-\((used.max() ?? 0) + 1)"
    }

    /// «Новый»: начать КП заново.
    func reset() {
        withAnimation(.spring(response: 0.45, dampingFraction: 0.9)) {
            phase = .idle
            positions = []
            current = 0
            options = OfferOptions()
            drawings = [:]; renders = [:]; renderErrors = [:]; rendering = []
            pendingImage = nil
            historyId = nil; savedState = nil; savedOptions = nil
        }
    }

    /// Первое окно вручную с главного экрана.
    func startManual() { addManual() }

    // MARK: История

    func saveToHistory() {
        guard !positions.isEmpty else { return }
        let o = offer
        let item = HistoryItem(id: historyId ?? UUID(), positions: positions, options: options, total: o.total, currency: o.currency)
        history.save(item, drawings: drawings, renders: renders)
        historyId = item.id
        savedState = positions
        savedOptions = options
    }

    func load(_ item: HistoryItem) {
        positions = item.positions.map { p in
            var p = p
            if catalog.system(p.selection.systemId) == nil { p.selection = PricingEngine.autoSelection(spec: p.spec, catalog: catalog) }
            return p
        }
        options = item.options
        drawings = [:]; renders = [:]; renderErrors = [:]
        for p in positions {
            if let d = history.drawing(p.id) { drawings[p.id] = d }
            if let r = history.render(p.id) { renders[p.id] = r }
        }
        current = 0
        historyId = item.id
        savedState = positions
        savedOptions = options
        phase = positions.isEmpty ? .idle : .ready
        tab = .estimate
    }

    // MARK: ИИ

    private func analyze(_ image: UIImage) async {
        do {
            let client = try APIClient(settings: settings)
            let result = try await client.analyze(drawing: image)
            var p = Position(mark: nextMark(), spec: result, selection: PricingEngine.autoSelection(spec: result, catalog: catalog))
            p.spec.quantity = max(1, result.quantity)
            drawings[p.id] = image
            pendingImage = nil
            UINotificationFeedbackGenerator().notificationOccurred(result.confidence < 0.6 ? .warning : .success)
            withAnimation(.spring(response: 0.5, dampingFraction: 0.85)) {
                positions.append(p)
                current = positions.count - 1
                phase = .ready
            }
            await renderPhoto()
        } catch {
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            withAnimation(.spring) { phase = .failed(error.localizedDescription) }
        }
    }

    /// ИИ-фото текущего окна: по картинке схемы (точная сетка и створки) в выбранном стиле.
    func renderPhoto() async {
        guard let i = cur else { return }
        let p = positions[i]
        rendering.insert(p.id)
        renderErrors[p.id] = nil
        defer { rendering.remove(p.id) }
        do {
            let client = try APIClient(settings: settings)
            var s = p.spec
            let col = catalog.color(p.selection.colorId)
            if let col { s.color = "\(col.code) \(col.name)" }
            let scheme = SchemeRender.cleanPNG(spec: p.spec, frameHex: col?.hex ?? "#45494E")
            let img = try await client.render(spec: s, scheme: scheme, style: renderStyle.rawValue)
            withAnimation(.easeOut(duration: 0.5)) { renders[p.id] = img }
        } catch {
            renderErrors[p.id] = error.localizedDescription
        }
    }

    // MARK: Рекомендации для нестандарта

    func applyRecommendation(_ w: LimitWarning) {
        guard !w.role.isEmpty else { return }
        selection.setOverride(w.role, w.applied ? nil : w.code)
    }

    // MARK: Редактирование сетки

    func setSize(width: Int? = nil, height: Int? = nil) {
        if let width { spec.widthMm = min(max(100, width), 20000) }
        if let height { spec.heightMm = min(max(100, height), 20000) }
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
            return GridSection(sizeMm: main / max(1, n), cells: [c])
        })
        spec.fitLayout()
    }

    func addSection() {
        let n = spec.layout.sections.count + 1
        let size = spec.mainTotal / n
        for i in spec.layout.sections.indices { spec.layout.sections[i].sizeMm = Int(Double(spec.layout.sections[i].sizeMm) * Double(n - 1) / Double(n)) }
        spec.layout.sections.append(GridSection(sizeMm: size, cells: [Cell(sizeMm: spec.crossTotal)]))
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

    /// Изменение размера, нажатого на схеме.
    func applyDimension(_ d: SchemeDimension, value: Int) {
        switch d.kind {
        case .width: setSize(width: value)
        case .height: setSize(height: value)
        case .section: setSectionSize(d.section, value)
        case .cell: setCellSize(section: d.section, index: d.index, value)
        }
    }

    // MARK: Текст КП

    var summaryText: String {
        let o = offer
        var t = options.objectName.isEmpty ? "Коммерческое предложение\n" : "КП: \(options.objectName)\n"
        for (i, p) in positions.enumerated() {
            let b = o.items[i]
            let sys = catalog.system(p.selection.systemId)?.name ?? ""
            let col = catalog.color(p.selection.colorId)?.code ?? ""
            t += "\n\(i + 1). \(p.mark): \(p.spec.product.title) \(p.spec.widthMm)×\(p.spec.heightMm) мм, \(sys), \(col)"
            t += "\n   \(b.quantity) шт × \(Money.format(b.unitTotal, currency: b.currency)) = \(Money.format(b.total, currency: b.currency))"
        }
        t += "\n\nПлощадь: \(String(format: "%.2f", o.areaM2)) м²"
        if o.discount > 0 { t += "\nСкидка \(Int(options.discountPercent))%: −\(Money.format(o.discount, currency: o.currency))" }
        t += "\nИтого: \(Money.format(o.total, currency: o.currency)) (в т. ч. НДС \(Int(catalog.vatPercent))%: \(Money.format(o.vatIncluded, currency: o.currency)))"
        t += "\n" + (options.scope == .materials
            ? "В цену входят: алюминиевый профиль, комплектующие, уплотнители."
            : "В цену входят: профиль, комплектующие, уплотнители, стеклопакеты, фурнитура, изготовление\(positions.contains { $0.selection.installation } ? ", монтаж" : "").")
        return t
    }

    // MARK: Демо для проверки (запуск с аргументом -demo)

    private func applyLaunchArguments() {
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-demo") {
            var s = WindowSpec()
            s.widthMm = 1200; s.heightMm = 2700; s.product = .window
            s.layout = Layout(split: .columns, sections: [
                GridSection(sizeMm: 800, cells: [Cell(sizeMm: 1700, opening: .tilt_turn, hinge: .left), Cell(sizeMm: 1000)]),
                GridSection(sizeMm: 400, cells: [Cell(sizeMm: 2700)]),
            ])
            s.system = "BKT 77"; s.fillMm = 32; s.color = "RAL 7024"; s.confidence = 0.9
            s.info = [InfoItem(label: "Система", value: "BKT 77"), InfoItem(label: "Заполнение", value: "32 мм"),
                      InfoItem(label: "Цвет", value: "RAL 7024"), InfoItem(label: "Схема открывания", value: "поворотно-откидная, петли слева")]
            s.notes = "1200 2700 1700 1000 800 400. Система BKT 77, заполнение 32, цвет RAL 7024"
            let sel = PricingEngine.autoSelection(spec: s, catalog: catalog)
            var s2 = WindowSpec()
            s2.widthMm = 1500; s2.heightMm = 2300
            s2.layout = Layout(split: .columns, sections: [
                GridSection(sizeMm: 750, cells: [Cell(sizeMm: 1650, opening: .tilt_turn, hinge: .left), Cell(sizeMm: 650)]),
                GridSection(sizeMm: 750, cells: [Cell(sizeMm: 1650), Cell(sizeMm: 650)]),
            ])
            s2.quantity = 2
            positions = [Position(mark: "ОК-1", spec: s, selection: sel), Position(mark: "ОК-2", spec: s2, selection: sel)]
            options.objectName = "Демо-объект"
            current = 0
            phase = .ready
            // Для проверки в CI: КП демо в PDF → Documents/demo-kp.pdf
            if args.contains("-pdf") {
                let imgs = Dictionary(uniqueKeysWithValues: positions.map { ($0.id, PDFExporter.Images(drawing: nil, render: nil)) })
                if let url = PDFExporter.makeOffer(positions: positions, options: options, breakdown: offer, catalog: catalog,
                                                   company: "BENKAM", images: imgs) {
                    let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("demo-kp.pdf")
                    try? FileManager.default.removeItem(at: docs)
                    try? FileManager.default.copyItem(at: url, to: docs)
                }
            }
        }
        if let i = args.firstIndex(of: "-tab"), i + 1 < args.count {
            let map: [String: AppTab] = ["history": .history, "catalog": .catalog, "settings": .settings]
            tab = map[args[i + 1]] ?? .estimate
        }
    }
}

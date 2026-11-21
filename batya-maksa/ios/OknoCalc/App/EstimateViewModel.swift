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
    @Published var selection = Selection(profileId: "", glassId: "", colorId: "")

    private let settings: AppSettings
    private let catalogStore: CatalogStore
    private let history: HistoryStore

    init(settings: AppSettings, catalogStore: CatalogStore, history: HistoryStore) {
        self.settings = settings
        self.catalogStore = catalogStore
        self.history = history
    }

    var companyName: String { settings.companyName }

    func saveToHistory() {
        history.save(spec: spec, selection: selection, total: breakdown.total, currency: breakdown.currency,
                     drawing: drawing, render: renderImage)
    }

    func load(_ item: HistoryItem) {
        spec = item.spec
        selection = item.selection
        drawing = history.drawing(for: item)
        renderImage = history.render(for: item)
        renderError = nil
        phase = .ready
        tab = .estimate
    }

    var catalog: Catalog { catalogStore.catalog }

    var breakdown: PriceBreakdown {
        PricingEngine.calculate(spec: spec, selection: selection, catalog: catalog)
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
            let img = try await client.render(spec: spec)
            withAnimation(.easeOut(duration: 0.5)) { renderImage = img }
        } catch {
            renderError = error.localizedDescription
        }
    }

    var summaryText: String {
        let b = breakdown
        var t = "\(spec.type.title) \(spec.widthMm)×\(spec.heightMm) мм\n"
        t += b.lines.map { "• \($0.title): \(Money.format($0.amount, currency: b.currency))" }.joined(separator: "\n")
        t += "\nИтого (ориентировочно): \(Money.format(b.total, currency: b.currency))"
        return t
    }
}

import XCTest
@testable import OknoCalc

final class PricingEngineTests: XCTestCase {

    private func loadCatalog() throws -> Catalog {
        let url = Bundle(for: CatalogStore.self).url(forResource: "catalog", withExtension: "json")
        let data = try Data(contentsOf: try XCTUnwrap(url))
        return try JSONDecoder.snake.decode(Catalog.self, from: data)
    }

    func testAutoSelectionByKeywords() throws {
        let c = try loadCatalog()
        var spec = WindowSpec()
        spec.profile = "aluminium"
        spec.glass = "triple glazing"
        spec.extras = ["mosquito net"]
        let sel = PricingEngine.autoSelection(spec: spec, catalog: c)
        XCTAssertEqual(sel.profileId, "alu_cold")
        XCTAssertEqual(sel.glassId, "triple")
        XCTAssertTrue(sel.extraIds.contains("net"))
    }

    func testPriceMatchesManualCalculation() throws {
        var c = try loadCatalog()
        c.markupPercent = 0
        c.vatPercent = 0
        var spec = WindowSpec()
        spec.widthMm = 1000
        spec.heightMm = 1000
        spec.sashes = [Sash(widthMm: nil, opening: .tilt_turn)]
        let sel = Selection(profileId: "pvc5", glassId: "single", colorId: "white")
        let b = PricingEngine.calculate(spec: spec, selection: sel, catalog: c)
        // 1 м² × 28000 + 0.85 × 9000 + 14000 = 49650 -> округление до 100 = 49700
        XCTAssertEqual(b.subtotal, 49650, accuracy: 0.5)
        XCTAssertEqual(b.total, 49700, accuracy: 0.5)
    }

    func testMinimumAreaApplied() throws {
        var c = try loadCatalog()
        c.markupPercent = 0
        c.vatPercent = 0
        var spec = WindowSpec()
        spec.widthMm = 300
        spec.heightMm = 300
        let sel = Selection(profileId: "pvc5", glassId: "single", colorId: "white")
        let b = PricingEngine.calculate(spec: spec, selection: sel, catalog: c)
        XCTAssertEqual(b.lines.first?.amount ?? 0, 0.5 * 28000, accuracy: 0.5)
    }
}

import XCTest
@testable import OknoCalc

/// Сверка с эталонным расчётом tools/pricing_ref.py (catalog/test-cases.json).
final class PricingEngineTests: XCTestCase {

    struct Item: Decodable { let spec: WindowSpec; let selection: Selection }
    struct Offer: Decodable { let options: OfferOptions; let items: [Item] }
    struct Case: Decodable { let name: String; let offer: Offer }

    private func bundle() -> Bundle { Bundle(for: PricingEngineTests.self) }

    private func catalog() throws -> Catalog {
        let url = try XCTUnwrap(bundle().url(forResource: "catalog", withExtension: "json"))
        return try Catalog.decode(Data(contentsOf: url))
    }

    func testCatalogLoads() throws {
        let c = try catalog()
        XCTAssertEqual(c.systems.map(\.id), ["bkt77", "bkf50", "bko45", "opc40", "wdc47"])
        XCTAssertGreaterThan(c.articles.count, 500)
        XCTAssertEqual(c.vatPercent, 16)
        XCTAssertNotNil(c.color("ral7024"))
        // Все артикулы из правил есть в прайсе.
        for s in c.systems {
            for r in s.rules {
                for code in [r.code, r.doorCode].compactMap({ $0 }) + (r.byFill ?? []).map(\.code) {
                    XCTAssertNotNil(c.article(code), "\(s.id): \(code)")
                }
            }
        }
    }

    func testMatchesReferenceCalculation() throws {
        let c = try catalog()
        let url = try XCTUnwrap(bundle().url(forResource: "test-cases", withExtension: "json"))
        let data = try Data(contentsOf: url)
        let cases = try JSONDecoder.snake.decode([Case].self, from: data)
        let raw = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [[String: Any]])
        XCTAssertEqual(cases.count, 8)
        for (t, r) in zip(cases, raw) {
            let e = try XCTUnwrap(r["expected"] as? [String: Any])
            let positions = t.offer.items.map { Position(spec: $0.spec, selection: $0.selection) }
            let b = PricingEngine.offer(positions, options: t.offer.options, catalog: c)
            XCTAssertEqual(b.items.map(\.unitTotal), (e["unit_totals"] as? [Double]) ?? [], t.name)
            XCTAssertEqual(b.items.map(\.total), (e["totals"] as? [Double]) ?? [], t.name)
            XCTAssertEqual(b.subtotal, e["subtotal"] as? Double ?? -1, t.name)
            XCTAssertEqual(b.discount, e["discount"] as? Double ?? -1, t.name)
            XCTAssertEqual(b.total, e["total"] as? Double ?? -1, t.name)
            XCTAssertEqual(b.vatIncluded, e["vat_included"] as? Double ?? -1, t.name)
            XCTAssertEqual(b.bars, (e["bars"] as? [String: Int]) ?? [:], t.name)
            XCTAssertEqual(b.items.map { $0.limits.map(\.kind) }, (e["warnings"] as? [[String]]) ?? [], t.name)
        }
    }

    /// Карты раскроя ORGADATA (окно 1500×2300): длины деталей до миллиметра.
    func testGeometryMatchesOrgadata() throws {
        let c = try catalog()
        var s = WindowSpec()
        s.widthMm = 1500; s.heightMm = 2300
        s.layout = Layout(split: .columns, sections: [
            GridSection(sizeMm: 750, cells: [Cell(sizeMm: 1650, opening: .tilt_turn, hinge: .left), Cell(sizeMm: 650)]),
            GridSection(sizeMm: 750, cells: [Cell(sizeMm: 1650), Cell(sizeMm: 650)]),
        ])
        let g = PricingEngine.geometry(s, try XCTUnwrap(c.system("bkt77")))
        XCTAssertEqual(g.pieces["frame"]?.sorted(), [1500, 1500, 2300, 2300])
        XCTAssertEqual(g.pieces["impost"]?.sorted(), [687, 687, 2216])
        XCTAssertEqual(g.pieces["sash"]?.sorted(), [699, 699, 1599, 1599])
        XCTAssertEqual(g.pieces["bead"]?.reduce(0, +), 13416)
    }

    func testWorkerResponseDecodes() throws {
        let json = """
        {"width_mm":1200,"height_mm":2700,"product":"window","quantity":1,
         "layout":{"split":"columns","sections":[{"size_mm":800,"cells":[{"size_mm":1700,"opening":"tilt_turn","hinge":"left","inward":true,"door":false},{"size_mm":1000,"opening":"fixed","hinge":"none","inward":true,"door":false}]},{"size_mm":400,"cells":[{"size_mm":2700,"opening":"fixed","hinge":"none","inward":true,"door":false}]}]},
         "system":"BKT 77, BKF 50","fill_mm":32,"glass":"","color":"RAL 7024","color_inside":"","inward":true,
         "info":[{"label":"Система","value":"BKT 77"}],"notes":"","confidence":0.9,"type":"double_sash","sashes":[],"model":"x","ms":100}
        """
        let s = try JSONDecoder.snake.decode(WindowSpec.self, from: Data(json.utf8))
        XCTAssertEqual(s.layout.sections.map(\.sizeMm), [800, 400])
        XCTAssertEqual(s.layout.sections[0].cells[0].opening, .tilt_turn)
        XCTAssertEqual(s.fillMm, 32)
        XCTAssertNil(s.layoutMismatch)
        let sel = PricingEngine.autoSelection(spec: s, catalog: try catalog())
        XCTAssertEqual(sel.systemId, "bkt77")
        XCTAssertEqual(sel.colorId, "ral7024")
        XCTAssertEqual(sel.fillMm, 32)
        // Обратно в JSON для /render — в snake_case.
        let out = String(data: try JSONEncoder.snake.encode(s), encoding: .utf8) ?? ""
        XCTAssertTrue(out.contains("\"size_mm\":800"))
        XCTAssertTrue(out.contains("\"width_mm\":1200"))
    }

    func testLegacyHistoryDecodes() throws {
        let old = """
        {"widthMm":1800,"heightMm":1400,"type":"double_sash","sashes":[{"widthMm":900,"opening":"tilt_turn"},{"opening":"fixed"}],
         "profile":"","glass":"","color":"white","extras":[],"notes":"","confidence":1}
        """
        let s = try JSONDecoder().decode(WindowSpec.self, from: Data(old.utf8))
        XCTAssertEqual(s.layout.sections.count, 2)
        XCTAssertEqual(s.layout.sections.map(\.sizeMm).reduce(0, +), 1800)
        XCTAssertEqual(s.layout.sections[0].cells[0].opening, .tilt_turn)
        let sel = try JSONDecoder().decode(Selection.self, from: Data(#"{"profileId":"pvc5","glassId":"single"}"#.utf8))
        XCTAssertEqual(sel.systemId, "")
    }

    func testLayoutEditing() {
        var s = WindowSpec()
        s.widthMm = 1000; s.heightMm = 2000
        s.layout = Layout(split: .rows, sections: [GridSection(sizeMm: 700, cells: [Cell(sizeMm: 1000, opening: .tilt)]),
                                                   GridSection(sizeMm: 1300, cells: [Cell(sizeMm: 1000, opening: .turn, hinge: .left, door: true)])])
        XCTAssertNil(s.layoutMismatch)
        let rects = s.cellRects
        XCTAssertEqual(rects.map { [$0.x, $0.y, $0.w, $0.h] }, [[0, 0, 1000, 700], [0, 700, 1000, 1300]])
        s.heightMm = 2100
        XCTAssertNotNil(s.layoutMismatch)
        s.fitLayout()
        XCTAssertNil(s.layoutMismatch)
        XCTAssertEqual(s.layout.sections.map(\.sizeMm).reduce(0, +), 2100)
        // Петли приводятся к открыванию.
        var c = Cell(sizeMm: 100, opening: .tilt, hinge: .left)
        c.normalize()
        XCTAssertEqual(c.hinge, .bottom)
    }
}

/// КП в PDF создаётся и не пустое (схема, фото, параметры, стоимость).
final class PDFExporterTests: XCTestCase {
    @MainActor
    func testOfferPdf() throws {
        let url = try XCTUnwrap(Bundle(for: PDFExporterTests.self).url(forResource: "catalog", withExtension: "json"))
        let c = try Catalog.decode(Data(contentsOf: url))
        var s = WindowSpec()
        s.widthMm = 1200; s.heightMm = 2700
        s.layout = Layout(split: .columns, sections: [
            GridSection(sizeMm: 800, cells: [Cell(sizeMm: 1700, opening: .tilt_turn, hinge: .left), Cell(sizeMm: 1000)]),
            GridSection(sizeMm: 400, cells: [Cell(sizeMm: 2700)]),
        ])
        s.info = [InfoItem(label: "Система", value: "BKT 77")]
        let sel = PricingEngine.autoSelection(spec: s, catalog: c)
        let positions = [Position(mark: "ОК-1", spec: s, selection: sel), Position(mark: "ОК-2", spec: s, selection: sel)]
        let b = PricingEngine.offer(positions, options: OfferOptions(), catalog: c)
        let pdf = try XCTUnwrap(PDFExporter.makeOffer(positions: positions, options: OfferOptions(), breakdown: b, catalog: c,
                                                      company: "Тест", images: [:]))
        let size = try XCTUnwrap(try FileManager.default.attributesOfItem(atPath: pdf.path)[.size] as? Int)
        XCTAssertGreaterThan(size, 10_000)
    }
}

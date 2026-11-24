import XCTest
@testable import OknoCalc

/// Сверка с эталонным расчётом tools/pricing_ref.py (catalog/test-cases.json).
final class PricingEngineTests: XCTestCase {

    struct Expected: Decodable { let subtotal: Double; let unitTotal: Double; let total: Double; let lines: Int }
    struct Case: Decodable { let name: String; let spec: WindowSpec; let selection: Selection; let expected: Expected }

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
        let cases = try JSONDecoder.snake.decode([Case].self, from: Data(contentsOf: url))
        XCTAssertEqual(cases.count, 5)
        for t in cases {
            let b = PricingEngine.calculate(spec: t.spec, selection: t.selection, catalog: c)
            XCTAssertEqual(b.subtotal, t.expected.subtotal, accuracy: 0.5, t.name)
            XCTAssertEqual(b.unitTotal, t.expected.unitTotal, t.name)
            XCTAssertEqual(b.total, t.expected.total, t.name)
            XCTAssertEqual(b.lines.count, t.expected.lines, t.name)
        }
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
        let b = PricingEngine.calculate(spec: s, selection: sel, catalog: c)
        let pdf = try XCTUnwrap(PDFExporter.makeOffer(spec: s, selection: sel, breakdown: b, catalog: c, company: "Тест", drawing: nil, render: nil))
        let size = try XCTUnwrap(try FileManager.default.attributesOfItem(atPath: pdf.path)[.size] as? Int)
        XCTAssertGreaterThan(size, 10_000)
    }
}

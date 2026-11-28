package kz.okno.proyom

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Сверка с эталонным расчётом tools/pricing_ref.py (test-cases.json) — те же суммы, что в iOS. */
class PricingTest {
    private val catalog = Catalog.parse(File("src/main/assets/catalog.json").readText())

    @Test fun catalogLoads() {
        assertEquals(listOf("bkt77", "bkf50", "bko45", "opc40", "wdc47"), catalog.systems.map { it.id })
        assertTrue(catalog.articles.size > 500)
        assertEquals(16.0, catalog.vatPercent, 0.0)
        for (s in catalog.systems) for (r in s.rules) {
            (listOfNotNull(r.code, r.doorCode) + (r.byFill ?: emptyList()).map { it.code }).forEach {
                assertNotNull("${s.id}: $it", catalog.article(it))
            }
        }
    }

    @Test fun matchesReferenceCalculation() {
        val text = javaClass.classLoader!!.getResource("test-cases.json").readText()
        val cases = JSONArray(text)
        assertEquals(8, cases.length())
        for (i in 0 until cases.length()) {
            val t = cases.getJSONObject(i)
            val offer = t.getJSONObject("offer")
            val opt = OfferOptions.fromJson(offer.getJSONObject("options"))
            val positions = offer.getJSONArray("items").objects().map {
                Position(spec = WindowSpec.fromJson(it.getJSONObject("spec")), selection = Selection.fromJson(it.getJSONObject("selection")))
            }
            val e = t.getJSONObject("expected")
            val b = Pricing.offer(positions, opt, catalog)
            val name = t.getString("name")
            fun doubles(k: String) = e.getJSONArray(k).let { a -> (0 until a.length()).map { a.getDouble(it) } }
            assertEquals(name, doubles("unit_totals"), b.items.map { it.unitTotal })
            assertEquals(name, doubles("totals"), b.items.map { it.total })
            assertEquals(name, e.getDouble("subtotal"), b.subtotal, 0.0)
            assertEquals(name, e.getDouble("discount"), b.discount, 0.0)
            assertEquals(name, e.getDouble("total"), b.total, 0.0)
            assertEquals(name, e.getDouble("vat_included"), b.vatIncluded, 0.0)
            val bars = e.getJSONObject("bars").let { o -> o.keys().asSequence().associateWith { o.getInt(it) } }
            assertEquals(name, bars, b.bars)
            val warns = e.getJSONArray("warnings").let { a -> (0 until a.length()).map { a.getJSONArray(it).strings() } }
            assertEquals(name, warns, b.items.map { it.limits.map { w -> w.kind } })
        }
    }

    /** Карты раскроя ORGADATA (окно 1500×2300): длины деталей до миллиметра. */
    @Test fun geometryMatchesOrgadata() {
        val s = WindowSpec(widthMm = 1500, heightMm = 2300, layout = Layout(Split.COLUMNS, listOf(
            Section(750, listOf(Cell(1650, Opening.TILT_TURN, Hinge.LEFT), Cell(650))),
            Section(750, listOf(Cell(1650), Cell(650))))))
        val g = Pricing.geometry(s, catalog.system("bkt77")!!)
        assertEquals(listOf(1500, 1500, 2300, 2300), g.pieces["frame"]!!.sorted())
        assertEquals(listOf(687, 687, 2216), g.pieces["impost"]!!.sorted())
        assertEquals(listOf(699, 699, 1599, 1599), g.pieces["sash"]!!.sorted())
        assertEquals(13416, g.pieces["bead"]!!.sum())
    }

    @Test fun workerResponseAndAutoSelection() {
        val o = JSONObject("""
            {"width_mm":1200,"height_mm":2700,"product":"window","quantity":1,
             "layout":{"split":"columns","sections":[{"size_mm":800,"cells":[{"size_mm":1700,"opening":"tilt_turn","hinge":"left","inward":true,"door":false},{"size_mm":1000,"opening":"fixed","hinge":"none"}]},{"size_mm":400,"cells":[{"size_mm":2700,"opening":"fixed"}]}]},
             "system":"BKT 77, BKF 50","fill_mm":32,"color":"RAL 7024","info":[{"label":"Система","value":"BKT 77"}],"confidence":0.9}
        """)
        val s = WindowSpec.fromJson(o)
        assertEquals(listOf(800, 400), s.layout.sections.map { it.sizeMm })
        assertEquals(Opening.TILT_TURN, s.layout.sections[0].cells[0].opening)
        assertNull(s.layoutMismatch)
        val sel = Pricing.autoSelection(s, catalog)
        assertEquals("bkt77", sel.systemId)
        assertEquals("ral7024", sel.colorId)
        assertEquals(32, sel.fillMm)
        // Обратно в JSON и снова — без потерь.
        assertEquals(s, WindowSpec.fromJson(s.toJson()))
    }

    @Test fun legacyHistoryAndGridEditing() {
        val old = WindowSpec.fromJson(JSONObject("""{"width_mm":1800,"height_mm":1400,"type":"double_sash","sashes":[{"width_mm":900,"opening":"tilt_turn"},{"opening":"fixed"}]}"""))
        assertEquals(2, old.layout.sections.size)
        assertEquals(1800, old.layout.sections.sumOf { it.sizeMm })
        val door = WindowSpec(widthMm = 1000, heightMm = 2000, layout = Layout(Split.ROWS, listOf(
            Section(700, listOf(Cell(1000, Opening.TILT))), Section(1300, listOf(Cell(1000, Opening.TURN, Hinge.LEFT, door = true))))))
        assertEquals(listOf(listOf(0.0, 0.0, 1000.0, 700.0), listOf(0.0, 700.0, 1000.0, 1300.0)), door.cellRects.map { listOf(it.x, it.y, it.w, it.h) })
        val bigger = door.copy(heightMm = 2100)
        assertNotNull(bigger.layoutMismatch)
        assertNull(bigger.fitted().layoutMismatch)
        assertEquals(Hinge.BOTTOM, Cell(100, Opening.TILT, Hinge.LEFT).normalized().hinge)
    }
}

package kz.okno.proyom

import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.max

/** Выбор по каталогу для конкретного изделия. */
data class Selection(
    val systemId: String = "",
    val fillMm: Int = 32,
    val colorId: String = "",
    val colorInsideId: String = "",
    val installation: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("system_id", systemId).put("fill_mm", fillMm).put("color_id", colorId)
        .put("color_inside_id", colorInsideId).put("installation", installation)

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) Selection() else Selection(
            o.optString("system_id"), o.optInt("fill_mm", 32), o.optString("color_id"),
            o.optString("color_inside_id"), o.optBoolean("installation"),
        )
    }
}

enum class LineGroup(val title: String) {
    PROFILE("Профили"), GASKET("Уплотнители"), ACCESSORY("Комплектующие"), GLASS("Заполнение"),
    HARDWARE("Фурнитура"), COLOR("Цвет"), LABOR("Изготовление"), INSTALL("Монтаж"),
}

data class PriceLine(val group: LineGroup, val title: String, val code: String, val qty: Double, val unit: String, val price: Double, val amount: Double) {
    val detail: String
        get() = if (group == LineGroup.COLOR) "+%.0f%% к профилям".format(qty)
        else (if (unit == "шт") "%.0f".format(qty) else "%.2f".format(qty)) + " $unit × " + Money.format(price, "KZT")
}

data class Breakdown(
    val lines: List<PriceLine>, val warnings: List<String>, val subtotal: Double, val markup: Double, val vat: Double,
    val unitTotal: Double, val quantity: Int, val total: Double, val currency: String,
) {
    fun sum(g: LineGroup) = lines.filter { it.group == g }.sumOf { it.amount }
}

/** Та же логика, что в tools/pricing_ref.py и в iOS (PricingEngine.swift). */
object Pricing {

    fun geometry(spec: WindowSpec, system: SystemDef): Map<String, Double> {
        val g = HashMap<String, Double>()
        fun add(k: String, v: Double) { g[k] = (g[k] ?: 0.0) + v }
        val w = spec.widthMm.toDouble()
        val h = spec.heightMm.toDouble()
        val cols = spec.layout.split == Split.COLUMNS
        g["frame_perimeter_m"] = 2 * (w + h) / 1000
        g["area_m2"] = w * h / 1_000_000
        g["frame_corners"] = 4.0
        var vert = 2 * h
        var horiz = 2 * w
        var impLen = 0.0
        var impN = 0.0
        spec.layout.sections.drop(1).forEach { _ ->
            impN += 1
            if (cols) { impLen += h; vert += h } else { impLen += w; horiz += w }
        }
        spec.layout.sections.forEach { s ->
            s.cells.drop(1).forEach { _ ->
                impN += 1
                impLen += s.sizeMm
                if (cols) horiz += s.sizeMm else vert += s.sizeMm
            }
        }
        g["impost_length_m"] = impLen / 1000
        g["imposts"] = impN
        g["vert_len_m"] = vert / 1000
        g["horiz_len_m"] = horiz / 1000
        g["grid_len_m"] = (vert + horiz) / 1000
        for (r in spec.cellRects) {
            val c = r.cell
            val ded = when {
                c.door && c.opening != Opening.FIXED -> {
                    add("door_perimeter_m", 2 * (r.w + r.h) / 1000); add("doors", 1.0); add("door_width_m", r.w / 1000)
                    system.deductionDoorMm
                }
                c.opening != Opening.FIXED -> {
                    add("sash_perimeter_m", 2 * (r.w + r.h) / 1000); add("sashes", 1.0)
                    system.deductionSashMm
                }
                else -> system.deductionFixedMm
            }
            val gw = max(r.w - ded, 100.0)
            val gh = max(r.h - ded, 100.0)
            add("glass_area_m2", gw * gh / 1_000_000)
            add("glass_perimeter_m", 2 * (gw + gh) / 1000)
            add("panes", 1.0)
        }
        return g
    }

    fun calculate(spec: WindowSpec, sel: Selection, c: Catalog): Breakdown {
        val lines = mutableListOf<PriceLine>()
        val warnings = mutableListOf<String>()
        val system = c.system(sel.systemId) ?: c.systems.firstOrNull()
            ?: return Breakdown(emptyList(), listOf("Нет систем в каталоге"), 0.0, 0.0, 0.0, 0.0, 1, 0.0, c.currency)
        val g = geometry(spec, system)
        val fill = sel.fillMm
        val doorProduct = spec.product == Product.DOOR
        val waste = 1 + c.wastePercent / 100
        var profiles = 0.0
        if (fill < system.fillMin || fill > system.fillMax) {
            warnings += "Заполнение $fill мм вне диапазона ${system.name} (${system.fillMin}–${system.fillMax} мм)"
        }
        spec.layoutMismatch?.let { warnings += it }

        for (r in system.rules) {
            var code = r.code
            if (doorProduct && r.doorCode != null) code = r.doorCode
            if (r.byFill != null) code = r.byFill.firstOrNull { it.min <= fill && fill <= it.max }?.code
            var qty = (g[r.base] ?: 0.0) * r.qty
            if (qty <= 0) continue
            if (code == null) { warnings += "${r.role}: нет позиции под заполнение $fill мм"; continue }
            val a = c.article(code)
            if (a == null) { warnings += "${r.role}: артикул $code не найден в прайсе"; continue }
            if (a.price <= 0) warnings += "$code: цена в прайсе не указана"
            if (r.profile && a.unit == "m") qty *= waste
            val amount = qty * a.price
            if (r.profile) profiles += amount
            val group = if (r.profile) LineGroup.PROFILE else if (a.kind == "gasket") LineGroup.GASKET else LineGroup.ACCESSORY
            lines += PriceLine(group, r.role, code, qty, a.unitTitle, a.price, amount)
        }

        var pct = 0.0
        var colorTitle = ""
        c.color(sel.colorId)?.let { pct += c.group(it.group)?.surchargePercent ?: 0.0; colorTitle = it.code }
        if (sel.colorInsideId.isNotEmpty() && sel.colorInsideId != sel.colorId) {
            pct += c.twoColorPercent
            colorTitle += " / внутри " + (c.color(sel.colorInsideId)?.code ?: "")
        }
        if (pct > 0) lines += PriceLine(LineGroup.COLOR, "Цвет: $colorTitle", "", pct, "%", 0.0, profiles * pct / 100)

        c.glassItem(fill)?.let {
            val area = g["glass_area_m2"] ?: 0.0
            lines += PriceLine(LineGroup.GLASS, it.name, "", area, "м²", it.pricePerM2, area * it.pricePerM2)
        }

        val counts = HashMap<String, Int>()
        for (r in spec.cellRects) if (r.cell.opening != Opening.FIXED) {
            val key = if (r.cell.door) "door" else r.cell.opening.key
            counts[key] = (counts[key] ?: 0) + 1
        }
        for (key in listOf("tilt_turn", "turn", "tilt", "top_hung", "slide", "door")) {
            val n = counts[key] ?: 0
            val hw = c.hardware.firstOrNull { it.opening == key } ?: continue
            if (n > 0) lines += PriceLine(LineGroup.HARDWARE, hw.name, "", n.toDouble(), "шт", hw.price, n * hw.price)
        }

        val area = g["area_m2"] ?: 0.0
        lines += PriceLine(LineGroup.LABOR, "Изготовление", "", area, "м²", c.laborPerM2, area * c.laborPerM2)
        if (sel.installation) lines += PriceLine(LineGroup.INSTALL, "Монтаж", "", area, "м²", c.installPerM2, max(c.installMin, area * c.installPerM2))

        val subtotal = lines.sumOf { it.amount }
        val markup = subtotal * c.markupPercent / 100
        val vat = (subtotal + markup) * c.vatPercent / 100
        val unitTotal = floor((subtotal + markup + vat) / 100 + 0.5) * 100
        val q = max(1, spec.quantity)
        return Breakdown(lines, warnings, subtotal, markup, vat, unitTotal, q, unitTotal * q, c.currency)
    }

    // ---------- Автоподбор по тексту с чертежа ----------

    fun autoSelection(spec: WindowSpec, c: Catalog): Selection {
        val system = matchSystem(spec, c)
        val fill = spec.fillMm ?: system?.fillDefault ?: 32
        val outside = matchColor(spec.color, c) ?: matchColor(spec.notes, c) ?: c.colors.firstOrNull()?.id ?: ""
        var inside = matchColor(spec.colorInside, c) ?: ""
        if (inside == outside) inside = ""
        return Selection(system?.id ?: "", fill, outside, inside)
    }

    fun matchSystem(spec: WindowSpec, c: Catalog): SystemDef? {
        val text = (spec.system + " " + spec.info.joinToString(" ") { "${it.label} ${it.value}" }).lowercase()
        val hits = mutableListOf<Pair<Int, SystemDef>>()
        for (s in c.systems) {
            for (k in s.keywords) {
                if (k.isEmpty()) continue
                val pos = text.indexOf(k.lowercase())
                if (pos >= 0) { hits += pos to s; break }
            }
        }
        hits.sortBy { it.first }
        val hit = hits.firstOrNull { spec.product.key in it.second.products } ?: hits.firstOrNull()
        if (hit != null) return hit.second
        if (spec.product == Product.FACADE) return c.system("bkf50") ?: c.systems.firstOrNull()
        return c.system("bkt77") ?: c.systems.firstOrNull()
    }

    fun matchColor(text: String, c: Catalog): String? {
        val t = text.lowercase()
        if (t.isBlank()) return null
        Regex("\\d{4}").find(t)?.let { m -> c.color("ral" + m.value)?.let { return it.id } }
        var best: Pair<Int, String>? = null
        for (col in c.colors) for (k in col.keywords) {
            if (k.length >= 3 && t.contains(k.lowercase()) && (best == null || k.length > best.first)) best = k.length to col.id
        }
        return best?.second
    }
}

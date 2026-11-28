package kz.okno.proyom

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Выбор по каталогу для конкретного изделия. overrides — замены профилей по рекомендации (роль → артикул). */
data class Selection(
    val systemId: String = "",
    val fillMm: Int = 32,
    val colorId: String = "",
    val colorInsideId: String = "",
    val installation: Boolean = false,
    val overrides: List<Pair<String, String>> = emptyList(),
) {
    fun override(role: String): String? = overrides.firstOrNull { it.first == role }?.second
    fun withOverride(role: String, code: String?): Selection =
        copy(overrides = overrides.filter { it.first != role } + listOfNotNull(code?.let { role to it }))

    fun toJson(): JSONObject = JSONObject().put("system_id", systemId).put("fill_mm", fillMm).put("color_id", colorId)
        .put("color_inside_id", colorInsideId).put("installation", installation)
        .put("overrides", JSONArray(overrides.map { JSONObject().put("role", it.first).put("code", it.second) }))

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) Selection() else Selection(
            o.optString("system_id"), o.optInt("fill_mm", 32), o.optString("color_id"),
            o.optString("color_inside_id"), o.optBoolean("installation"),
            o.optJSONArray("overrides").objects().map { it.optString("role") to it.optString("code") },
        )
    }
}

/** Что входит в КП: материалы (как КП BKM Aluminium) или изделие под ключ. */
enum class OfferScope(val key: String, val title: String) {
    MATERIALS("materials", "Материалы"), TURNKEY("turnkey", "Под ключ");
    companion object { fun of(k: String?) = if (k == "turnkey") TURNKEY else MATERIALS }
}

data class OfferOptions(
    val scope: OfferScope = OfferScope.MATERIALS,
    val cutting: Boolean = true,
    val gasketRound: Boolean = true,
    val discountPercent: Double = 0.0,
    val objectName: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("scope", scope.key).put("cutting", cutting).put("gasket_round", gasketRound)
        .put("discount_percent", discountPercent).put("object_name", objectName)

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) OfferOptions() else OfferOptions(
            OfferScope.of(o.optString("scope")), o.optBoolean("cutting", true), o.optBoolean("gasket_round", true),
            o.optDouble("discount_percent", 0.0), o.optString("object_name"),
        )
    }
}

/** Окно в КП. */
data class Position(
    val id: String = UUID.randomUUID().toString(),
    val mark: String = "ОК-1",
    val spec: WindowSpec = WindowSpec(),
    val selection: Selection = Selection(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("mark", mark).put("spec", spec.toJson()).put("selection", selection.toJson())

    companion object {
        fun fromJson(o: JSONObject) = Position(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("mark", "ОК-1"),
            WindowSpec.fromJson(o.optJSONObject("spec") ?: JSONObject()), Selection.fromJson(o.optJSONObject("selection")),
        )
    }
}

enum class LineGroup(val title: String) {
    PROFILE("Профили"), GASKET("Уплотнители"), ACCESSORY("Комплектующие"), GLASS("Заполнение"),
    HARDWARE("Фурнитура"), COLOR("Цвет"), LABOR("Изготовление"), INSTALL("Монтаж"),
}

data class PriceLine(
    val group: LineGroup, val title: String, val code: String, val qty: Double, val unit: String, val price: Double,
    val amount: Double, val note: String = "",
) {
    val detail: String
        get() = if (group == LineGroup.COLOR) "+%.0f%% к профилям".format(qty)
        else (if (unit == "шт") "%.0f".format(qty) else "%.2f".format(qty)) + " $unit × " + Money.format(price, "KZT") +
            (if (note.isEmpty()) "" else " · $note")
}

/** Нестандартный размер: предупреждение и рекомендуемый профиль. */
data class LimitWarning(val kind: String, val text: String, val role: String, val code: String, val rec: String, val applied: Boolean)

/** Расчёт одного окна в составе КП (цены за 1 шт). */
data class Breakdown(
    val lines: List<PriceLine>, val warnings: List<String>, val limits: List<LimitWarning>, val subtotal: Double, val markup: Double,
    val unitTotal: Double, val quantity: Int, val total: Double, val areaM2: Double, val currency: String,
) {
    fun sum(g: LineGroup) = lines.filter { it.group == g }.sumOf { it.amount }
    val pricePerM2: Double get() = if (areaM2 > 0) unitTotal / areaM2 else 0.0
}

/** Строка спецификации материалов по всему КП. */
data class MaterialLine(
    val group: LineGroup, val code: String, val name: String, val colorKey: String, val unit: String,
    val need: Double, val bought: Double, val bars: Int?, val barMm: Double?, val packM: Double?,
    val price: Double, val amount: Double, val kg: Double,
) {
    val wastePercent: Double get() = if (bought > 0) max(0.0, (bought - need) / bought * 100) else 0.0
}

data class OfferBreakdown(
    val items: List<Breakdown>, val materials: List<MaterialLine>, val subtotal: Double, val discount: Double,
    val total: Double, val vatIncluded: Double, val areaM2: Double, val weightKg: Double, val currency: String,
) {
    val bars: Map<String, Int>
        get() = materials.filter { it.group == LineGroup.PROFILE && it.bars != null }
            .groupBy { it.code }.mapValues { e -> e.value.sumOf { it.bars ?: 0 } }
}

/** Детали изделия (мм) и величины для расчёта. */
class Geometry(
    val v: Map<String, Double>,
    val pieces: Map<String, List<Int>>,
    val sashes: List<SashInfo>,
) {
    operator fun get(k: String) = v[k] ?: 0.0
}

data class SashInfo(val ow: Int, val oh: Int, val gw: Int, val gh: Int, val section: Int, val index: Int, val door: Boolean)

/** Та же логика, что в tools/pricing_ref.py и в iOS (PricingEngine.swift). */
object Pricing {

    private val piecesOf = mapOf(
        "frame_perimeter_m" to "frame", "impost_length_m" to "impost", "sash_perimeter_m" to "sash", "door_perimeter_m" to "door",
        "door_width_m" to "threshold", "bead_length_m" to "bead", "vert_len_m" to "vert", "horiz_len_m" to "horiz", "grid_len_m" to "grid",
    )

    private fun r0(x: Double) = floor(x + 0.5)

    fun geometry(spec: WindowSpec, system: SystemDef): Geometry {
        val geo = system.geo ?: Geo()
        val f = geo.frameFace
        val i2 = geo.impostFace / 2
        val ov = geo.sashOverlap.toInt()
        val sf = geo.sashFace.toInt()
        val df = geo.doorFace.toInt()
        val bd = geo.beadDeduct.toInt()
        val gap = geo.glassGap.toInt()
        val w = spec.widthMm
        val h = spec.heightMm
        val cols = spec.layout.split == Split.COLUMNS
        val secs = spec.layout.sections
        val crossTotal = if (cols) h else w
        val g = HashMap<String, Double>()
        fun add(k: String, v: Double) { g[k] = (g[k] ?: 0.0) + v }
        val p = HashMap<String, MutableList<Int>>()
        fun pc(k: String) = p.getOrPut(k) { mutableListOf() }
        pc("frame").addAll(if (spec.product == Product.DOOR) listOf(w, h, h) else listOf(w, w, h, h))
        g["frame_corners"] = 4.0
        g["area_m2"] = w.toDouble() * h / 1_000_000
        val n = secs.size
        val panes = mutableListOf<Triple<Int, Int, Boolean>>()
        val sashes = mutableListOf<SashInfo>()
        secs.forEachIndexed { si, sec ->
            val a = if (si == 0) f else i2
            val b = if (si == n - 1) f else i2
            val lightMain = sec.sizeMm - a - b
            if (si > 0) pc("impost").add(r0(crossTotal - 2 * f).toInt())
            val m = sec.cells.size
            sec.cells.forEachIndexed { ci, cell ->
                val c1 = if (ci == 0) f else i2
                val c2 = if (ci == m - 1) f else i2
                val lightCross = cell.sizeMm - c1 - c2
                if (ci > 0) pc("impost").add(r0(lightMain).toInt())
                val lw = r0(if (cols) lightMain else lightCross).toInt()
                val lh = r0(if (cols) lightCross else lightMain).toInt()
                val glw: Int
                val glh: Int
                if (cell.opening == Opening.FIXED) {
                    glw = lw; glh = lh
                } else {
                    val ow = lw + 2 * ov
                    val oh = lh + 2 * ov
                    val face = if (cell.door) df else sf
                    glw = ow - 2 * face; glh = oh - 2 * face
                    if (cell.door) {
                        pc("door").addAll(listOf(ow, ow, oh, oh)); pc("threshold").add(lw); add("doors", 1.0)
                    } else {
                        pc("sash").addAll(listOf(ow, ow, oh, oh)); add("sashes", 1.0)
                    }
                    sashes += SashInfo(ow, oh, glw - 2 * gap, glh - 2 * gap, si, ci, cell.door)
                }
                pc("bead").addAll(listOf(glw, glw, glh - 2 * bd, glh - 2 * bd))
                panes += Triple(max(glw - 2 * gap, 50), max(glh - 2 * gap, 50), cell.opening != Opening.FIXED)
            }
        }
        val inner = secs.flatMap { s -> s.cells.drop(1).map { s.sizeMm } }
        if (cols) {
            p["vert"] = (listOf(h, h) + List(max(0, n - 1)) { h }).toMutableList()
            p["horiz"] = (listOf(w, w) + inner).toMutableList()
        } else {
            p["horiz"] = (listOf(w, w) + List(max(0, n - 1)) { w }).toMutableList()
            p["vert"] = (listOf(h, h) + inner).toMutableList()
        }
        p["grid"] = (p["vert"]!! + p["horiz"]!!).toMutableList()
        g["imposts"] = (p["impost"]?.size ?: 0).toDouble()
        for ((k, base) in listOf("frame" to "frame_perimeter_m", "impost" to "impost_length_m", "sash" to "sash_perimeter_m",
            "door" to "door_perimeter_m", "threshold" to "door_width_m", "bead" to "bead_length_m",
            "vert" to "vert_len_m", "horiz" to "horiz_len_m", "grid" to "grid_len_m")) {
            g[base] = (p[k]?.sum() ?: 0) / 1000.0
        }
        for ((gw, gh, inSash) in panes) {
            add("glass_area_m2", gw.toDouble() * gh / 1_000_000)
            add("glass_perimeter_m", 2.0 * (gw + gh) / 1000)
            add("panes", 1.0)
            if (inSash) add("sash_panes", 1.0)
        }
        return Geometry(g, p, sashes)
    }

    fun ruleCode(r: Rule, spec: WindowSpec, sel: Selection): String? {
        var code = r.code
        if (spec.product == Product.DOOR && r.doorCode != null) code = r.doorCode
        if (r.byFill != null) code = r.byFill.firstOrNull { sel.fillMm in it.min..it.max }?.code
        return sel.override(r.role) ?: code
    }

    /** Предупреждения о нестандартных размерах. Считаем всё равно стандартными профилями (или применённой заменой). */
    fun checkLimits(spec: WindowSpec, sel: Selection, system: SystemDef, c: Catalog, g: Geometry): List<LimitWarning> {
        val out = mutableListOf<LimitWarning>()
        val lim = system.limits
        val recs = system.recommend.associateBy { it.`when` }
        fun add(kind: String, text: String) {
            val r = recs[kind]
            out += LimitWarning(kind, text, r?.role ?: "", r?.code ?: "", r?.text ?: "", r != null && sel.override(r.role) == r.code)
        }
        fun i(x: Double?) = (x ?: 0.0).toInt()
        val fr = system.rules.firstOrNull { it.base == "frame_perimeter_m" }
        val frameCode = fr?.let { ruleCode(it, spec, sel) }
        val longestFrame = g.pieces["frame"]?.maxOrNull()
        if (frameCode != null && longestFrame != null) {
            val usable = (c.barLength(frameCode) - c.trim - c.kerf).toInt()
            if (longestFrame > usable) add("frame_long", "Сторона рамы $longestFrame мм длиннее хлыста ($usable мм) — нужна стыковка, нестандарт")
        }
        val maxI = lim?.impostMaxMm
        val longestImpost = g.pieces["impost"]?.maxOrNull()
        if (maxI != null && longestImpost != null && longestImpost > maxI) {
            add("impost_long", "Импост $longestImpost мм длиннее стандартного (${maxI.toInt()} мм)")
        }
        val kgM2 = c.glassItem(sel.fillMm)?.kgM2 ?: 25.0
        for (s in g.sashes) {
            val name = "${if (s.door) "Дверь" else "Створка"} ${s.section + 1}.${s.index + 1} (${s.ow}×${s.oh} мм)"
            if (s.door) {
                if (lim != null && ((lim.doorMaxW != null && s.ow > lim.doorMaxW) || (lim.doorMaxH != null && s.oh > lim.doorMaxH))) {
                    add("door_big", "$name больше стандартной двери (${i(lim.doorMaxW)}×${i(lim.doorMaxH)} мм)")
                }
                continue
            }
            val l = lim ?: continue
            val kg = s.gw.toDouble() * s.gh / 1_000_000 * kgM2 + 2.0 * (s.ow + s.oh) / 1000 * 1.65
            if (s.ow > (l.sashMaxW ?: Double.MAX_VALUE) || s.oh > (l.sashMaxH ?: Double.MAX_VALUE) || kg > (l.sashMaxKg ?: Double.MAX_VALUE)) {
                add("sash_big", "$name, ≈${Math.round(kg)} кг — больше стандартной (${i(l.sashMaxW)}×${i(l.sashMaxH)} мм, до ${i(l.sashMaxKg)} кг)")
            } else if (s.ow < (l.sashMinW ?: 0.0) || s.oh < (l.sashMinH ?: 0.0)) {
                add("sash_small", "$name меньше минимальной для фурнитуры (${i(l.sashMinW)}×${i(l.sashMinH)} мм)")
            }
        }
        return out
    }

    class Need(
        val group: LineGroup, val role: String, val code: String, val unit: String, val qty: Double, val price: Double,
        val pieces: List<Int>?, val colorKey: String, var pool: String? = null,
    )

    private class ItemNeeds(val needs: List<Need>, val g: Geometry, val warnings: List<String>, val limits: List<LimitWarning>, val q: Int)

    private fun needs(spec: WindowSpec, sel: Selection, c: Catalog, scope: OfferScope): ItemNeeds {
        val warnings = mutableListOf<String>()
        val system = c.system(sel.systemId) ?: c.systems.firstOrNull()
            ?: return ItemNeeds(emptyList(), Geometry(emptyMap(), emptyMap(), emptyList()), listOf("Нет систем в каталоге"), emptyList(), 1)
        val g = geometry(spec, system)
        if (sel.fillMm < system.fillMin || sel.fillMm > system.fillMax) {
            warnings += "Заполнение ${sel.fillMm} мм вне диапазона ${system.name} (${system.fillMin}–${system.fillMax} мм)"
        }
        spec.layoutMismatch?.let { warnings += it }
        val colorKey = sel.colorId + "/" + sel.colorInsideId
        val out = mutableListOf<Need>()
        for (r in system.rules) {
            val qty = g[r.base] * r.qty + r.plus.sumOf { g[it.base] * it.qty }
            if (qty <= 0) continue
            val code = ruleCode(r, spec, sel)
            if (code == null) { warnings += "${r.role}: нет позиции под заполнение ${sel.fillMm} мм"; continue }
            val a = c.article(code)
            if (a == null) { warnings += "${r.role}: артикул $code не найден в прайсе"; continue }
            if (a.price <= 0) warnings += "$code: цена в прайсе не указана"
            val group = if (r.profile) LineGroup.PROFILE else if (a.kind == "gasket") LineGroup.GASKET else LineGroup.ACCESSORY
            val pieces = if (group == LineGroup.PROFILE && a.unit == "m" && r.plus.isEmpty() && r.qty == 1.0 && r.base in piecesOf)
                g.pieces[piecesOf[r.base]] ?: emptyList() else null
            out += Need(group, r.role, code, a.unit, qty, a.price, pieces, if (group == LineGroup.PROFILE) colorKey else "")
        }
        if (scope == OfferScope.TURNKEY) {
            c.glassItem(sel.fillMm)?.let { out += Need(LineGroup.GLASS, it.name, "", "m2", g["glass_area_m2"], it.pricePerM2, null, "") }
            val counts = HashMap<String, Int>()
            for (s in spec.layout.sections) for (cell in s.cells) if (cell.opening != Opening.FIXED) {
                val key = if (cell.door) "door" else cell.opening.key
                counts[key] = (counts[key] ?: 0) + 1
            }
            for (key in listOf("tilt_turn", "turn", "tilt", "top_hung", "slide", "door")) {
                val n = counts[key] ?: 0
                val hw = c.hardware.firstOrNull { it.opening == key } ?: continue
                if (n > 0) out += Need(LineGroup.HARDWARE, hw.name, "", "pc", n.toDouble(), hw.price, null, "")
            }
            val area = g["area_m2"]
            out += Need(LineGroup.LABOR, "Изготовление", "", "m2", area, c.laborPerM2, null, "")
            if (sel.installation) {
                val amount = max(c.installMin, area * c.installPerM2)
                out += Need(LineGroup.INSTALL, "Монтаж", "", "m2", area, if (area > 0) amount / area else 0.0, null, "")
            }
        }
        return ItemNeeds(out, g, warnings, checkLimits(spec, sel, system, c, g), max(1, spec.quantity))
    }

    /** Раскрой: первый подходящий хлыст, детали от длинных к коротким (пропил и обрезка концов). */
    fun cutBars(pieces: List<Int>, barMm: Double, kerf: Double, trim: Double): Pair<Int, Int> {
        val cap = barMm - trim
        val free = mutableListOf<Double>()
        var over = 0
        var extra = 0
        for (len in pieces.sortedDescending()) {
            val need = len + kerf
            if (need > cap) { over++; extra += ceil(need / cap).toInt(); continue }
            val i = free.indexOfFirst { it >= need }
            if (i >= 0) free[i] = free[i] - need else free += cap - need
        }
        return (free.size + extra) to over
    }

    private class Pool(val group: LineGroup, val code: String, val colorKey: String) {
        var need = 0.0
        val pieces = mutableListOf<Int>()
        val by = HashMap<String, Double>()
        var bought = 0.0
        var bars: Int? = null
        var barMm: Double? = null
    }

    /** Расчёт КП: все окна вместе (общий раскрой и округление уплотнителей). */
    fun offer(positions: List<Position>, opt: OfferOptions, c: Catalog): OfferBreakdown {
        val waste = 1 + c.wastePercent / 100
        val per = positions.map { needs(it.spec, it.selection, c, opt.scope) }
        val pools = LinkedHashMap<String, Pool>()
        per.forEachIndexed { i, x ->
            for (n in x.needs) {
                val key = when {
                    n.group == LineGroup.PROFILE && opt.cutting && !n.pieces.isNullOrEmpty() -> "profile|${n.code}|${n.colorKey}"
                    n.group == LineGroup.GASKET && opt.gasketRound && c.gasketStep > 0 && n.unit == "m" -> "gasket|${n.code}|"
                    else -> null
                } ?: continue
                val pool = pools.getOrPut(key) { Pool(n.group, n.code, n.colorKey) }
                val m = n.qty * x.q
                pool.need += m
                pool.by["$i|${n.role}"] = (pool.by["$i|${n.role}"] ?: 0.0) + m
                if (n.group == LineGroup.PROFILE) repeat(x.q) { pool.pieces.addAll(n.pieces!!) }
                n.pool = key
            }
        }
        for (pool in pools.values) {
            if (pool.group == LineGroup.PROFILE) {
                val bar = c.barLength(pool.code)
                val (bars, _) = cutBars(pool.pieces, bar, c.kerf, c.trim)
                pool.bars = bars; pool.barMm = bar
                pool.bought = bars * bar / 1000
            } else {
                pool.bought = ceil(pool.need / c.gasketStep - 1e-9) * c.gasketStep
            }
        }
        val items = mutableListOf<Breakdown>()
        val mats = LinkedHashMap<String, MaterialLine>()
        val groups = c.colorGroups.associate { it.id to it.surchargePercent }
        per.forEachIndexed { i, x ->
            val lines = mutableListOf<PriceLine>()
            var profiles = 0.0
            for (n in x.needs) {
                val pool = n.pool?.let { pools[it] }
                val qty: Double
                var note = ""
                if (pool != null) {
                    val share = if (pool.need > 0) (pool.by["$i|${n.role}"] ?: 0.0) / pool.need else 0.0
                    qty = pool.bought * share / x.q
                    note = if (pool.group == LineGroup.PROFILE) "раскрой по КП" else "кратно ${c.gasketStep.toInt()} м"
                } else if (n.group == LineGroup.PROFILE && n.unit == "m") {
                    qty = n.qty * waste
                    note = "+${c.wastePercent.toInt()}% отход"
                } else {
                    qty = n.qty
                }
                val amount = qty * n.price
                if (n.group == LineGroup.PROFILE) profiles += amount
                val unitTitle = when (n.unit) { "m" -> "м"; "m2" -> "м²"; else -> "шт" }
                lines += PriceLine(n.group, n.role, n.code, qty, unitTitle, n.price, amount, note)
                if (n.group == LineGroup.PROFILE || n.group == LineGroup.GASKET || n.group == LineGroup.ACCESSORY) {
                    val key = "${n.group}|${n.code}|${n.colorKey}"
                    val a = c.article(n.code)
                    val m = mats[key] ?: MaterialLine(n.group, n.code, a?.name ?: n.role, n.colorKey, unitTitle, 0.0, 0.0, null, null, a?.packM, n.price, 0.0, 0.0)
                    val bought = if (pool != null) pool.bought else m.bought + qty * x.q
                    mats[key] = m.copy(
                        need = m.need + n.qty * x.q, amount = m.amount + amount * x.q, bought = bought,
                        bars = pool?.bars ?: m.bars, barMm = pool?.barMm ?: m.barMm, kg = (a?.kgM ?: 0.0) * bought,
                    )
                }
            }
            val sel = positions[i].selection
            var pct = 0.0
            var colorTitle = ""
            c.color(sel.colorId)?.let { pct += groups[it.group] ?: 0.0; colorTitle = it.code }
            if (sel.colorInsideId.isNotEmpty() && sel.colorInsideId != sel.colorId) {
                pct += c.twoColorPercent
                colorTitle += " / внутри " + (c.color(sel.colorInsideId)?.code ?: "")
            }
            if (pct > 0) lines += PriceLine(LineGroup.COLOR, "Цвет: $colorTitle", "", pct, "%", 0.0, profiles * pct / 100)
            val subtotal = lines.sumOf { it.amount }
            val markup = if (opt.scope == OfferScope.TURNKEY) subtotal * c.markupPercent / 100 else 0.0
            val unitTotal = r0(subtotal + markup)
            items += Breakdown(lines, x.warnings, x.limits, subtotal, markup, unitTotal, x.q, unitTotal * x.q, x.g["area_m2"], c.currency)
        }
        val subtotal = items.sumOf { it.total }
        val discount = r0(subtotal * opt.discountPercent.coerceIn(0.0, 100.0) / 100)
        val total = subtotal - discount
        val vat = r0(total * c.vatPercent / (100 + c.vatPercent))
        val area = items.sumOf { it.areaM2 * it.quantity }
        val order = listOf(LineGroup.PROFILE, LineGroup.ACCESSORY, LineGroup.GASKET)
        val materials = mats.values.sortedWith(compareBy({ order.indexOf(it.group) }, { it.code }, { it.colorKey }))
        return OfferBreakdown(items, materials, subtotal, discount, total, vat, area, materials.sumOf { it.kg }, c.currency)
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

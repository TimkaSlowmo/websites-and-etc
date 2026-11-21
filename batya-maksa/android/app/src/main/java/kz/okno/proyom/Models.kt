package kz.okno.proyom

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

enum class WindowType(val key: String, val title: String) {
    FIXED("fixed", "Глухое окно"),
    SINGLE("single_sash", "Одностворчатое"),
    DOUBLE("double_sash", "Двухстворчатое"),
    TRIPLE("triple_sash", "Трёхстворчатое"),
    BALCONY_DOOR("balcony_door", "Балконная дверь"),
    BALCONY_BLOCK("balcony_block", "Балконный блок"),
    SLIDING("sliding", "Раздвижное"),
    ENTRANCE_DOOR("entrance_door", "Входная дверь"),
    INTERIOR_DOOR("interior_door", "Межкомнатная дверь");

    companion object {
        fun of(k: String?) = entries.firstOrNull { it.key == k } ?: SINGLE
    }
}

enum class Opening(val key: String, val title: String) {
    FIXED("fixed", "Глухая"),
    TILT("tilt", "Откидная"),
    TURN("turn", "Поворотная"),
    TILT_TURN("tilt_turn", "Поворотно-откидная"),
    SLIDE("slide", "Раздвижная");

    companion object {
        fun of(k: String?) = entries.firstOrNull { it.key == k } ?: FIXED
    }
}

data class Sash(val widthMm: Int? = null, val opening: Opening = Opening.FIXED)

data class WindowSpec(
    val widthMm: Int = 1300,
    val heightMm: Int = 1400,
    val type: WindowType = WindowType.SINGLE,
    val sashes: List<Sash> = listOf(Sash(null, Opening.TILT_TURN)),
    val profile: String = "",
    val glass: String = "",
    val color: String = "white",
    val extras: List<String> = emptyList(),
    val notes: String = "",
    val confidence: Double = 1.0,
    val altWidths: List<Int> = emptyList(),
    val altHeights: List<Int> = emptyList(),
) {
    val areaM2: Double get() = widthMm.toDouble() * heightMm / 1_000_000
    val perimeterM: Double get() = 2.0 * (widthMm + heightMm) / 1000

    fun toJson(): JSONObject = JSONObject().apply {
        put("width_mm", widthMm); put("height_mm", heightMm); put("type", type.key)
        put("sashes", JSONArray().apply {
            sashes.forEach { s ->
                put(JSONObject().apply { put("width_mm", s.widthMm ?: JSONObject.NULL); put("opening", s.opening.key) })
            }
        })
        put("profile", profile); put("glass", glass); put("color", color)
        put("extras", JSONArray(extras)); put("notes", notes); put("confidence", confidence)
    }

    companion object {
        fun fromJson(o: JSONObject): WindowSpec {
            val sashes = o.optJSONArray("sashes")?.let { a ->
                (0 until a.length()).map { i ->
                    val s = a.getJSONObject(i)
                    Sash(if (s.isNull("width_mm")) null else s.optInt("width_mm"), Opening.of(s.optString("opening")))
                }
            }.orEmpty().ifEmpty { listOf(Sash(null, Opening.TILT_TURN)) }
            val alt = o.optJSONObject("alternatives")
            return WindowSpec(
                widthMm = o.optInt("width_mm", 1300),
                heightMm = o.optInt("height_mm", 1400),
                type = WindowType.of(o.optString("type")),
                sashes = sashes,
                profile = o.optString("profile"),
                glass = o.optString("glass"),
                color = o.optString("color", "white"),
                extras = o.optJSONArray("extras").strings(),
                notes = o.optString("notes"),
                confidence = o.optDouble("confidence", 1.0),
                altWidths = alt?.optJSONArray("width_mm").ints(),
                altHeights = alt?.optJSONArray("height_mm").ints(),
            )
        }
    }
}

fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map { optInt(it) }.filter { it > 0 }

// ---------- Каталог ----------

data class PricedItem(val id: String, val name: String, val keywords: List<String>, val price: Double, val isDefault: Boolean)
data class HardwareItem(val opening: Opening, val name: String, val price: Double)
data class ColorItem(val id: String, val name: String, val keywords: List<String>, val surchargePercent: Double, val isDefault: Boolean)
enum class ExtraUnit(val key: String, val title: String) {
    PC("pc", "шт"), WIDTH("width_m", "за м ширины"), HEIGHT("height_m", "за м высоты"),
    PERIMETER("perimeter_m", "за м периметра"), AREA("area_m2", "за м²");
    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } ?: PC }
}
data class ExtraItem(val id: String, val name: String, val keywords: List<String>, val price: Double, val unit: ExtraUnit)

data class Catalog(
    val name: String,
    val currency: String,
    val vatPercent: Double,
    val markupPercent: Double,
    val minAreaM2: Double,
    val glassAreaRatio: Double,
    val profiles: List<PricedItem>,
    val glass: List<PricedItem>,
    val hardware: List<HardwareItem>,
    val colors: List<ColorItem>,
    val extras: List<ExtraItem>,
    val installPerM2: Double,
    val installMin: Double,
) {
    companion object {
        fun parse(text: String): Catalog {
            val o = JSONObject(text)
            fun priced(key: String) = o.optJSONArray(key).objects().map {
                PricedItem(it.getString("id"), it.getString("name"), it.optJSONArray("keywords").strings(),
                    it.getDouble("price_per_m2"), it.optBoolean("is_default"))
            }
            val profiles = priced("profiles")
            val glass = priced("glass")
            require(profiles.isNotEmpty() && glass.isNotEmpty()) { "В каталоге нет профилей или стеклопакетов" }
            val inst = o.optJSONObject("installation")
            return Catalog(
                name = o.optString("name", "Каталог"),
                currency = o.optString("currency", "KZT"),
                vatPercent = o.optDouble("vat_percent", 0.0),
                markupPercent = o.optDouble("markup_percent", 0.0),
                minAreaM2 = o.optDouble("min_area_m2", 0.5),
                glassAreaRatio = o.optDouble("glass_area_ratio", 0.85),
                profiles = profiles,
                glass = glass,
                hardware = o.optJSONArray("hardware").objects().map {
                    HardwareItem(Opening.of(it.optString("opening")), it.optString("name"), it.optDouble("price", 0.0))
                },
                colors = o.optJSONArray("colors").objects().map {
                    ColorItem(it.getString("id"), it.getString("name"), it.optJSONArray("keywords").strings(),
                        it.optDouble("surcharge_percent", 0.0), it.optBoolean("is_default"))
                },
                extras = o.optJSONArray("extras").objects().map {
                    ExtraItem(it.getString("id"), it.getString("name"), it.optJSONArray("keywords").strings(),
                        it.optDouble("price", 0.0), ExtraUnit.of(it.optString("unit")))
                },
                installPerM2 = inst?.optDouble("price_per_m2", 0.0) ?: 0.0,
                installMin = inst?.optDouble("min_price", 0.0) ?: 0.0,
            )
        }
    }
}

fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

// ---------- Расчёт ----------

data class Selection(
    val profileId: String = "",
    val glassId: String = "",
    val colorId: String = "",
    val extraIds: Set<String> = emptySet(),
    val installation: Boolean = false,
) {
    fun toJson() = JSONObject().apply {
        put("profile", profileId); put("glass", glassId); put("color", colorId)
        put("extras", JSONArray(extraIds.toList())); put("installation", installation)
    }
    companion object {
        fun fromJson(o: JSONObject) = Selection(
            o.optString("profile"), o.optString("glass"), o.optString("color"),
            o.optJSONArray("extras").strings().toSet(), o.optBoolean("installation"),
        )
    }
}

data class PriceLine(val title: String, val detail: String, val amount: Double)
data class Breakdown(val lines: List<PriceLine>, val subtotal: Double, val markup: Double, val vat: Double, val total: Double, val currency: String)

object Pricing {
    private fun <T> match(text: String, items: List<T>, kw: (T) -> List<String>, def: (T) -> Boolean): T? {
        val t = text.lowercase()
        var best: T? = null
        var len = 0
        if (t.isNotBlank()) for (item in items) for (k in kw(item)) {
            if (k.isNotBlank() && t.contains(k.lowercase()) && k.length > len) { best = item; len = k.length }
        }
        return best ?: items.firstOrNull(def) ?: items.firstOrNull()
    }

    fun autoSelection(spec: WindowSpec, c: Catalog): Selection {
        val all = listOf(spec.profile, spec.glass, spec.color, spec.notes, spec.extras.joinToString(" ")).joinToString(" ")
        val p = match(spec.profile + " " + spec.notes, c.profiles, { it.keywords }, { it.isDefault })
        val g = match(spec.glass + " " + spec.notes, c.glass, { it.keywords }, { it.isDefault })
        val col = match(all, c.colors, { it.keywords }, { it.isDefault })
        val hay = all.lowercase()
        val extras = c.extras.filter { e -> e.keywords.any { it.isNotBlank() && hay.contains(it.lowercase()) } }.map { it.id }.toSet()
        return Selection(p?.id ?: "", g?.id ?: "", col?.id ?: "", extras)
    }

    fun calculate(spec: WindowSpec, sel: Selection, c: Catalog): Breakdown {
        val lines = mutableListOf<PriceLine>()
        val area = max(spec.areaM2, c.minAreaM2)
        var frame = 0.0
        c.profiles.firstOrNull { it.id == sel.profileId }?.let {
            frame = area * it.price
            lines += PriceLine(it.name, "%.2f м² × %d".format(area, it.price.toInt()), frame)
        }
        c.colors.firstOrNull { it.id == sel.colorId }?.takeIf { it.surchargePercent > 0 }?.let {
            lines += PriceLine("Цвет: ${it.name}", "+${it.surchargePercent.toInt()}% к профилю", frame * it.surchargePercent / 100)
        }
        c.glass.firstOrNull { it.id == sel.glassId }?.let {
            val ga = area * c.glassAreaRatio
            lines += PriceLine(it.name, "%.2f м² × %d".format(ga, it.price.toInt()), ga * it.price)
        }
        val counts = spec.sashes.groupingBy { it.opening }.eachCount()
        for (o in Opening.entries) {
            val n = counts[o] ?: 0
            val h = c.hardware.firstOrNull { it.opening == o } ?: continue
            if (n > 0 && h.price > 0) lines += PriceLine(h.name, "$n шт × ${h.price.toInt()}", n * h.price)
        }
        for (e in c.extras) if (e.id in sel.extraIds) {
            val q = when (e.unit) {
                ExtraUnit.PC -> 1.0
                ExtraUnit.WIDTH -> spec.widthMm / 1000.0
                ExtraUnit.HEIGHT -> spec.heightMm / 1000.0
                ExtraUnit.PERIMETER -> spec.perimeterM
                ExtraUnit.AREA -> area
            }
            lines += PriceLine(e.name, "%.2f × %d (%s)".format(q, e.price.toInt(), e.unit.title), q * e.price)
        }
        if (sel.installation) {
            lines += PriceLine("Монтаж", "от ${c.installMin.toInt()}", max(c.installMin, area * c.installPerM2))
        }
        val sub = lines.sumOf { it.amount }
        val markup = sub * c.markupPercent / 100
        val vat = (sub + markup) * c.vatPercent / 100
        val total = Math.round((sub + markup + vat) / 100.0) * 100.0
        return Breakdown(lines, sub, markup, vat, total, c.currency)
    }
}

object Money {
    fun format(v: Double, currency: String): String {
        val s = String.format(java.util.Locale.US, "%,d", Math.round(v)).replace(",", " ")
        val sym = mapOf("KZT" to "₸", "RUB" to "₽", "USD" to "$", "EUR" to "€")[currency] ?: currency
        return "$s $sym"
    }
}

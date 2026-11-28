package kz.okno.proyom

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

enum class Product(val key: String, val title: String) {
    WINDOW("window", "Окно"), DOOR("door", "Дверь"), BALCONY_BLOCK("balcony_block", "Балконный блок"),
    FACADE("facade", "Витраж / фасад"), PARTITION("partition", "Перегородка");

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } }
}

enum class Hinge(val key: String, val title: String) {
    LEFT("left", "Петли слева"), RIGHT("right", "Петли справа"), TOP("top", "Петли сверху"),
    BOTTOM("bottom", "Петли снизу"), NONE("none", "—");

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } ?: NONE }
}

enum class Opening(val key: String, val title: String) {
    FIXED("fixed", "Глухая"), TURN("turn", "Поворотная"), TILT("tilt", "Откидная"),
    TILT_TURN("tilt_turn", "Поворотно-откидная"), TOP_HUNG("top_hung", "Верхнеподвесная"), SLIDE("slide", "Раздвижная");

    /** Возможные стороны петель для этого открывания. */
    val hinges: List<Hinge>
        get() = when (this) {
            TURN, TILT_TURN -> listOf(Hinge.LEFT, Hinge.RIGHT)
            TILT -> listOf(Hinge.BOTTOM)
            TOP_HUNG -> listOf(Hinge.TOP)
            FIXED, SLIDE -> listOf(Hinge.NONE)
        }

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } ?: FIXED }
}

enum class Split(val key: String, val title: String, val sectionName: String, val short: String) {
    COLUMNS("columns", "Колонки", "Колонка", "Колонки"), ROWS("rows", "Ряды", "Ряд", "Ряды");

    companion object { fun of(k: String?) = if (k == "rows") ROWS else COLUMNS }
}

/** Ячейка секции. В колонке size = высота (сверху вниз), в ряду size = ширина (слева направо). */
data class Cell(
    val sizeMm: Int,
    val opening: Opening = Opening.FIXED,
    val hinge: Hinge = Hinge.NONE,
    val inward: Boolean = true,
    val door: Boolean = false,
) {
    fun normalized(): Cell {
        val h = if (hinge in opening.hinges) hinge else opening.hinges.first()
        return copy(hinge = h, door = door && opening != Opening.FIXED)
    }
}

data class Section(val sizeMm: Int, val cells: List<Cell>)

data class Layout(val split: Split = Split.COLUMNS, val sections: List<Section>) {
    companion object {
        fun single(width: Int, height: Int, opening: Opening = Opening.TILT_TURN) =
            Layout(Split.COLUMNS, listOf(Section(width, listOf(Cell(height, opening).normalized()))))
    }
}

data class CellRect(val section: Int, val index: Int, val x: Double, val y: Double, val w: Double, val h: Double, val cell: Cell) {
    val id: String get() = "$section-$index"
}

data class InfoItem(val label: String, val value: String)

data class WindowSpec(
    val widthMm: Int = 1300,
    val heightMm: Int = 1400,
    val product: Product = Product.WINDOW,
    val quantity: Int = 1,
    val layout: Layout = Layout.single(1300, 1400),
    val system: String = "",
    val fillMm: Int? = null,
    val glass: String = "",
    val color: String = "",
    val colorInside: String = "",
    val inward: Boolean = true,
    val info: List<InfoItem> = emptyList(),
    val notes: String = "",
    val confidence: Double = 1.0,
    val altWidths: List<Int> = emptyList(),
    val altHeights: List<Int> = emptyList(),
) {
    val areaM2: Double get() = widthMm.toDouble() * heightMm / 1_000_000
    val mainTotal: Int get() = if (layout.split == Split.COLUMNS) widthMm else heightMm
    val crossTotal: Int get() = if (layout.split == Split.COLUMNS) heightMm else widthMm

    val cellRects: List<CellRect>
        get() {
            val out = mutableListOf<CellRect>()
            var a = 0.0
            layout.sections.forEachIndexed { si, s ->
                var b = 0.0
                s.cells.forEachIndexed { ci, c ->
                    out += if (layout.split == Split.COLUMNS) CellRect(si, ci, a, b, s.sizeMm.toDouble(), c.sizeMm.toDouble(), c)
                    else CellRect(si, ci, b, a, c.sizeMm.toDouble(), s.sizeMm.toDouble(), c)
                    b += c.sizeMm
                }
                a += s.sizeMm
            }
            return out
        }

    /** Текст расхождения сетки с общими размерами или null. */
    val layoutMismatch: String?
        get() {
            val main = layout.sections.sumOf { it.sizeMm }
            val cols = layout.split == Split.COLUMNS
            if (main != mainTotal) return "Сумма ${if (cols) "колонок" else "рядов"} $main мм, а ${if (cols) "ширина" else "высота"} $mainTotal мм"
            layout.sections.forEachIndexed { i, s ->
                val sum = s.cells.sumOf { it.sizeMm }
                if (sum != crossTotal) return "${layout.split.sectionName} ${i + 1}: сумма ячеек $sum мм, а должно быть $crossTotal мм"
            }
            return null
        }

    /** Подгоняет секции и ячейки под общие размеры (пропорционально, остаток — в последнюю). */
    fun fitted(): WindowSpec {
        val secs = layout.sections.ifEmpty { Layout.single(widthMm, heightMm).sections }
        val sizes = fit(secs.map { it.sizeMm }, mainTotal)
        return copy(layout = layout.copy(sections = secs.mapIndexed { i, s ->
            val cells = s.cells.ifEmpty { listOf(Cell(crossTotal)) }
            val cs = fit(cells.map { it.sizeMm }, crossTotal)
            Section(sizes[i], cells.mapIndexed { j, c -> c.copy(sizeMm = cs[j]).normalized() })
        }))
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("width_mm", widthMm); put("height_mm", heightMm); put("product", product.key); put("quantity", quantity)
        put("layout", JSONObject().apply {
            put("split", layout.split.key)
            put("sections", JSONArray().apply {
                layout.sections.forEach { s ->
                    put(JSONObject().apply {
                        put("size_mm", s.sizeMm)
                        put("cells", JSONArray().apply {
                            s.cells.forEach { c ->
                                put(JSONObject().put("size_mm", c.sizeMm).put("opening", c.opening.key).put("hinge", c.hinge.key)
                                    .put("inward", c.inward).put("door", c.door))
                            }
                        })
                    })
                }
            })
        })
        put("system", system); put("fill_mm", fillMm ?: JSONObject.NULL); put("glass", glass)
        put("color", color); put("color_inside", colorInside); put("inward", inward)
        put("info", JSONArray().apply { info.forEach { put(JSONObject().put("label", it.label).put("value", it.value)) } })
        put("notes", notes); put("confidence", confidence)
    }

    companion object {
        fun fit(values: List<Int>, total: Int): List<Int> {
            if (values.isEmpty()) return emptyList()
            val sum = values.sum()
            if (sum == total) return values
            val out = if (sum <= 0) MutableList(values.size) { total / values.size }
            else values.map { max(1, (it.toDouble() * total / sum).roundToInt()) }.toMutableList()
            out[out.lastIndex] += total - out.sum()
            return out
        }

        private fun JSONObject.int(vararg keys: String): Int? {
            for (k in keys) if (has(k) && !isNull(k)) {
                val d = optDouble(k, Double.NaN)
                if (!d.isNaN()) return d.roundToInt()
            }
            return null
        }

        /** Разбор ответа сервера (snake_case) и сохранённой истории, включая старый формат со «sashes». */
        fun fromJson(o: JSONObject): WindowSpec {
            val w = o.int("width_mm") ?: 1300
            val h = o.int("height_mm") ?: 1400
            val product = Product.of(o.optString("product")) ?: when (o.optString("type")) {
                "balcony_door", "entrance_door", "interior_door" -> Product.DOOR
                "balcony_block" -> Product.BALCONY_BLOCK
                else -> Product.WINDOW
            }
            val lay = o.optJSONObject("layout")
            val layout = if (lay != null && (lay.optJSONArray("sections")?.length() ?: 0) > 0) {
                Layout(Split.of(lay.optString("split")), lay.optJSONArray("sections").objects().map { s ->
                    Section(s.int("size_mm") ?: 0, s.optJSONArray("cells").objects().map { c ->
                        Cell(c.int("size_mm") ?: 0, Opening.of(c.optString("opening")), Hinge.of(c.optString("hinge")),
                            c.optBoolean("inward", true), c.optBoolean("door", false)).normalized()
                    })
                })
            } else {
                val old = o.optJSONArray("sashes").objects()
                if (old.isEmpty()) Layout.single(w, h)
                else Layout(Split.COLUMNS, old.map { s ->
                    Section(s.int("width_mm") ?: (w / old.size), listOf(Cell(h, Opening.of(s.optString("opening"))).normalized()))
                })
            }
            val alt = o.optJSONObject("alternatives")
            return WindowSpec(
                widthMm = w, heightMm = h, product = product, quantity = max(1, o.int("quantity") ?: 1), layout = layout,
                system = o.optString("system"), fillMm = o.int("fill_mm"), glass = o.optString("glass"),
                color = o.optString("color"), colorInside = o.optString("color_inside"), inward = o.optBoolean("inward", true),
                info = o.optJSONArray("info").objects().map { InfoItem(it.optString("label"), it.optString("value")) },
                notes = o.optString("notes"), confidence = o.optDouble("confidence", 1.0),
                altWidths = alt?.optJSONArray("width_mm").ints(), altHeights = alt?.optJSONArray("height_mm").ints(),
            ).fitted()
        }
    }
}

fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map { optInt(it) }.filter { it > 0 }
fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

// ---------- Каталог компании (catalog.json, tools/build_catalog.py) ----------

data class Article(
    val code: String, val name: String, val price: Double, val unit: String, val kind: String,
    val packM: Double? = null, val kgM: Double? = null,
) {
    val unitTitle: String get() = if (unit == "m") "м" else "шт"
}

data class FillCode(val min: Int, val max: Int, val code: String)

data class RulePlus(val base: String, val qty: Double)

data class Rule(
    val role: String, val code: String?, val base: String, val qty: Double, val profile: Boolean,
    val doorCode: String?, val byFill: List<FillCode>?, val plus: List<RulePlus> = emptyList(),
)

/** Вычеты профилей системы, мм (как в ORGADATA). */
data class Geo(
    val frameFace: Double = 42.0, val impostFace: Double = 42.0, val sashOverlap: Double = 6.0, val sashFace: Double = 59.0,
    val doorFace: Double = 73.0, val beadDeduct: Double = 22.0, val glassGap: Double = 6.0,
)

/** Стандартные размеры для утверждённых профилей. */
data class Limits(
    val sashMinW: Double?, val sashMinH: Double?, val sashMaxW: Double?, val sashMaxH: Double?, val sashMaxKg: Double?,
    val doorMaxW: Double?, val doorMaxH: Double?, val impostMaxMm: Double?,
)

data class Recommendation(val `when`: String, val role: String, val code: String, val text: String)

data class SystemDef(
    val id: String, val name: String, val description: String, val keywords: List<String>, val products: List<String>,
    val fillMin: Int, val fillMax: Int, val fillDefault: Int,
    val geo: Geo?, val limits: Limits?, val recommend: List<Recommendation>, val rules: List<Rule>,
)

data class BarLength(val prefix: String, val mm: Double)

data class GlassItem(val fillMm: Int, val name: String, val pricePerM2: Double, val kgM2: Double? = null)
data class HardwareItem(val opening: String, val name: String, val price: Double)
data class ColorGroup(val id: String, val name: String, val surchargePercent: Double)
data class ColorItem(val id: String, val code: String, val name: String, val hex: String, val group: String, val keywords: List<String>) {
    val title: String get() = "$code · $name"
}

data class Catalog(
    val raw: JSONObject,
    val name: String,
    val currency: String,
    val vatPercent: Double,
    val markupPercent: Double,
    val wastePercent: Double,
    val laborPerM2: Double,
    val installPerM2: Double,
    val installMin: Double,
    val twoColorPercent: Double,
    val barDefaultMm: Double,
    val barLengths: List<BarLength>,
    val kerf: Double,
    val trim: Double,
    val gasketStep: Double,
    val validDays: Int,
    val colorGroups: List<ColorGroup>,
    val glass: List<GlassItem>,
    val hardware: List<HardwareItem>,
    val colors: List<ColorItem>,
    val systems: List<SystemDef>,
    val articles: List<Article>,
) {
    private val index: Map<String, Article> by lazy { articles.associateBy { it.code } }

    fun article(code: String) = index[code]
    fun system(id: String) = systems.firstOrNull { it.id == id }
    fun color(id: String) = colors.firstOrNull { it.id == id }
    fun group(id: String) = colorGroups.firstOrNull { it.id == id }

    /** Длина хлыста: самое длинное совпадение начала артикула, иначе по умолчанию. */
    fun barLength(code: String): Double =
        barLengths.filter { code.startsWith(it.prefix) }.maxByOrNull { it.prefix.length }?.mm ?: barDefaultMm

    /** Стеклопакет под заполнение: ближайший не тоньше, иначе самый толстый. */
    fun glassItem(fill: Int): GlassItem? {
        val sorted = glass.sortedBy { it.fillMm }
        return sorted.firstOrNull { it.fillMm >= fill } ?: sorted.lastOrNull()
    }

    /** Каталог с изменёнными ставками — правки пишутся в исходный JSON, чтобы сохранить файл целиком. */
    fun edited(change: (JSONObject) -> Unit): Catalog {
        val copy = JSONObject(raw.toString())
        change(copy)
        return parse(copy.toString())
    }

    fun toJsonString(): String = raw.toString()

    companion object {
        fun parse(text: String): Catalog {
            val o = JSONObject(text)
            require(o.optInt("version", 1) >= 3) { "Каталог старого формата" }
            fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k) else null
            val systems = o.optJSONArray("systems").objects().map { s ->
                SystemDef(
                    s.getString("id"), s.getString("name"), s.optString("description"), s.optJSONArray("keywords").strings(),
                    s.optJSONArray("products").strings(), s.optInt("fill_min"), s.optInt("fill_max"), s.optInt("fill_default", 32),
                    s.optJSONObject("geo")?.let { g ->
                        Geo(g.optDouble("frame_face", 42.0), g.optDouble("impost_face", 42.0), g.optDouble("sash_overlap", 6.0),
                            g.optDouble("sash_face", 59.0), g.optDouble("door_face", 73.0), g.optDouble("bead_deduct", 22.0), g.optDouble("glass_gap", 6.0))
                    },
                    s.optJSONObject("limits")?.let { l ->
                        Limits(l.dbl("sash_min_w"), l.dbl("sash_min_h"), l.dbl("sash_max_w"), l.dbl("sash_max_h"), l.dbl("sash_max_kg"),
                            l.dbl("door_max_w"), l.dbl("door_max_h"), l.dbl("impost_max_mm"))
                    },
                    s.optJSONArray("recommend").objects().map { Recommendation(it.optString("when"), it.optString("role"), it.optString("code"), it.optString("text")) },
                    s.optJSONArray("rules").objects().map { r ->
                        Rule(
                            r.getString("role"), if (r.isNull("code") || !r.has("code")) null else r.getString("code"),
                            r.getString("base"), r.optDouble("qty", 1.0), r.optBoolean("profile"),
                            if (r.has("door_code")) r.getString("door_code") else null,
                            r.optJSONArray("by_fill")?.objects()?.map { FillCode(it.getInt("min"), it.getInt("max"), it.getString("code")) },
                            r.optJSONArray("plus").objects().map { RulePlus(it.getString("base"), it.optDouble("qty", 1.0)) },
                        )
                    },
                )
            }
            val articles = o.optJSONArray("articles").objects().map {
                Article(it.getString("code"), it.optString("name"), it.optDouble("price", 0.0), it.optString("unit"), it.optString("kind"),
                    it.dbl("pack_m"), it.dbl("kg_m"))
            }
            require(systems.isNotEmpty() && articles.isNotEmpty()) { "В каталоге нет систем или прайса" }
            return Catalog(
                raw = o,
                name = o.optString("name", "Каталог"),
                currency = o.optString("currency", "KZT"),
                vatPercent = o.optDouble("vat_percent", 0.0),
                markupPercent = o.optDouble("markup_percent", 0.0),
                wastePercent = o.optDouble("waste_percent", 0.0),
                laborPerM2 = o.optDouble("labor_per_m2", 0.0),
                installPerM2 = o.optDouble("install_per_m2", 0.0),
                installMin = o.optDouble("install_min", 0.0),
                twoColorPercent = o.optDouble("two_color_percent", 0.0),
                barDefaultMm = o.optDouble("bar_default_mm", 6500.0),
                barLengths = o.optJSONArray("bar_lengths").objects().map { BarLength(it.getString("prefix"), it.optDouble("mm", 6500.0)) },
                kerf = o.optDouble("saw_kerf_mm", 10.0),
                trim = o.optDouble("bar_trim_mm", 20.0),
                gasketStep = o.optDouble("gasket_step_m", 20.0),
                validDays = o.optInt("offer_valid_days", 5),
                colorGroups = o.optJSONArray("color_groups").objects().map { ColorGroup(it.getString("id"), it.optString("name"), it.optDouble("surcharge_percent", 0.0)) },
                glass = o.optJSONArray("glass").objects().map { GlassItem(it.getInt("fill_mm"), it.optString("name"), it.optDouble("price_per_m2", 0.0), it.dbl("kg_m2")) },
                hardware = o.optJSONArray("hardware").objects().map { HardwareItem(it.getString("opening"), it.optString("name"), it.optDouble("price", 0.0)) },
                colors = o.optJSONArray("colors").objects().map {
                    ColorItem(it.getString("id"), it.optString("code"), it.optString("name"), it.optString("hex", "#45494E"), it.optString("group"), it.optJSONArray("keywords").strings())
                },
                systems = systems,
                articles = articles,
            )
        }
    }
}

object Money {
    fun format(v: Double, currency: String): String {
        val s = String.format(java.util.Locale.US, "%,d", Math.round(v)).replace(",", " ")
        val sym = mapOf("KZT" to "₸", "RUB" to "₽", "USD" to "$", "EUR" to "€")[currency] ?: currency
        return "$s $sym"
    }
}

package kz.okno.proyom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("proyom", Context.MODE_PRIVATE)
    var company: String
        get() = p.getString("company", "") ?: ""
        set(v) { p.edit().putString("company", v).apply() }
    var theme: String
        get() = p.getString("theme", "system") ?: "system"
        set(v) { p.edit().putString("theme", v).apply() }
}

/** Каталог компании: встроенный catalog.json + сохранённые правки цен (files/catalog.json). */
class CatalogStore(private val ctx: Context) {
    private val userFile = File(ctx.filesDir, "catalog.json")

    fun load(): Catalog {
        if (!userFile.exists()) return bundled()
        val text = runCatching { userFile.readText() }.getOrNull() ?: return bundled()
        runCatching { return Catalog.parse(text) }
        // Правки из каталога старой версии — переносим ставки на новый встроенный каталог
        return runCatching { migrate(JSONObject(text)).also { save(it) } }.getOrElse { bundled() }
    }

    fun bundled(): Catalog = Catalog.parse(ctx.assets.open("catalog.json").bufferedReader().use { it.readText() })

    private fun migrate(old: JSONObject): Catalog = bundled().edited { o ->
        for (k in listOf("markup_percent", "vat_percent", "waste_percent", "labor_per_m2", "install_per_m2", "install_min", "two_color_percent")) {
            if (old.has(k)) o.put(k, old.optDouble(k))
        }
        fun copy(arr: String, key: String, field: String) {
            val src = old.optJSONArray(arr).objects()
            o.optJSONArray(arr).objects().forEach { dst ->
                src.firstOrNull { it.opt(key)?.toString() == dst.opt(key)?.toString() }?.let { if (it.has(field)) dst.put(field, it.optDouble(field)) }
            }
        }
        copy("glass", "fill_mm", "price_per_m2")
        copy("hardware", "opening", "price")
        copy("color_groups", "id", "surcharge_percent")
    }

    fun save(c: Catalog) { userFile.writeText(c.toJsonString()) }

    val isModified: Boolean get() = userFile.exists()

    fun reset(): Catalog { userFile.delete(); return bundled() }
}

/** Сохранённое КП: несколько окон, настройки расчёта и итог. */
data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val positions: List<Position>,
    val options: OfferOptions,
    val total: Double,
    val currency: String,
) {
    val windows: Int get() = positions.sumOf { maxOf(1, it.spec.quantity) }
    val title: String
        get() {
            val p = positions.firstOrNull() ?: return "КП"
            val first = "${p.spec.product.title} ${p.spec.widthMm}×${p.spec.heightMm}"
            return if (positions.size == 1) first else "$first и ещё ${positions.size - 1}"
        }
}

class HistoryStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "history").apply { mkdirs() }
    private val index = File(dir, "index.json")

    fun load(): List<HistoryItem> = runCatching {
        JSONArray(index.readText()).objects().mapNotNull {
            runCatching {
                val id = it.getString("id")
                val positions = it.optJSONArray("positions")?.objects()?.map { p -> Position.fromJson(p) }
                    // Старый формат — одно окно; id позиции = id записи, чтобы нашлись старые картинки
                    ?: listOf(Position(id, "ОК-1", WindowSpec.fromJson(it.getJSONObject("spec")), selectionOf(it.optJSONObject("selection"))))
                val options = it.optJSONObject("options")?.let { o -> OfferOptions.fromJson(o) } ?: OfferOptions(scope = OfferScope.TURNKEY)
                HistoryItem(id, it.getLong("date"), positions, options, it.getDouble("total"), it.getString("currency"))
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    /** Новый формат (system_id…) или старый (profile…) — старый даёт пустой выбор, его подберёт автоподбор. */
    private fun selectionOf(o: JSONObject?) = Selection.fromJson(o?.takeIf { it.has("system_id") })

    fun save(items: List<HistoryItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("date", it.date).put("positions", JSONArray(it.positions.map { p -> p.toJson() }))
                .put("options", it.options.toJson()).put("total", it.total).put("currency", it.currency))
        }
        index.writeText(arr.toString())
    }

    fun writeImage(id: String, kind: String, bmp: Bitmap?) {
        bmp ?: return
        File(dir, "${id}_$kind.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }

    fun image(id: String, kind: String): Bitmap? =
        File(dir, "${id}_$kind.jpg").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun deleteImages(id: String) {
        File(dir, "${id}_d.jpg").delete(); File(dir, "${id}_r.jpg").delete()
    }
}

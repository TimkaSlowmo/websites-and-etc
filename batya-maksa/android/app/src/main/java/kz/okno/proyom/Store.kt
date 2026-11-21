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
    var workerUrl: String
        get() = p.getString("workerUrl", null) ?: BuildConfig.WORKER_URL
        set(v) { p.edit().putString("workerUrl", v).apply() }
}

class CatalogStore(private val ctx: Context) {
    private val userFile = File(ctx.filesDir, "catalog.json")

    fun load(): Catalog =
        runCatching { Catalog.parse(userFile.readText()) }.getOrNull() ?: bundled()

    fun bundled(): Catalog = Catalog.parse(ctx.assets.open("catalog.json").bufferedReader().use { it.readText() })

    /** Проверяет и сохраняет каталог; бросает исключение с понятным текстом. */
    fun import(text: String): Catalog {
        val c = runCatching { Catalog.parse(text) }.getOrElse { throw IllegalArgumentException("Не удалось прочитать каталог: ${it.message}") }
        userFile.writeText(text)
        return c
    }

    fun reset(): Catalog { userFile.delete(); return bundled() }
}

data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val spec: WindowSpec,
    val selection: Selection,
    val total: Double,
    val currency: String,
)

class HistoryStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "history").apply { mkdirs() }
    private val index = File(dir, "index.json")

    fun load(): List<HistoryItem> = runCatching {
        JSONArray(index.readText()).objects().map {
            HistoryItem(it.getString("id"), it.getLong("date"), WindowSpec.fromJson(it.getJSONObject("spec")),
                Selection.fromJson(it.getJSONObject("selection")), it.getDouble("total"), it.getString("currency"))
        }
    }.getOrDefault(emptyList())

    fun save(items: List<HistoryItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("date", it.date).put("spec", it.spec.toJson())
                .put("selection", it.selection.toJson()).put("total", it.total).put("currency", it.currency))
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

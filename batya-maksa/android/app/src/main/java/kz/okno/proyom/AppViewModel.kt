package kz.okno.proyom

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

enum class Tab { ESTIMATE, HISTORY, CATALOG, SETTINGS }

sealed interface Phase {
    data object Idle : Phase
    data object Analyzing : Phase
    data object Ready : Phase
    data class Failed(val message: String) : Phase
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val catalogStore = CatalogStore(app)
    private val historyStore = HistoryStore(app)

    var tab by mutableStateOf(Tab.ESTIMATE)
    var phase by mutableStateOf<Phase>(Phase.Idle)
    var drawing by mutableStateOf<Bitmap?>(null)
    var render by mutableStateOf<Bitmap?>(null)
    var isRendering by mutableStateOf(false)
    var renderError by mutableStateOf<String?>(null)
    var spec by mutableStateOf(WindowSpec())
    var selection by mutableStateOf(Selection())
    var catalog by mutableStateOf(catalogStore.load())
        private set
    var catalogModified by mutableStateOf(catalogStore.isModified)
        private set
    var history by mutableStateOf(historyStore.load())
        private set
    var company by mutableStateOf(prefs.company)
        private set
    /** Тема: "system" | "light" | "dark" — как на iPhone. */
    var theme by mutableStateOf(prefs.theme)
        private set

    val breakdown: Breakdown get() = Pricing.calculate(spec, selection, catalog)

    private fun api() = Api(BuildConfig.WORKER_URL)

    fun start(image: Bitmap) {
        drawing = image; render = null; renderError = null
        phase = Phase.Analyzing
        viewModelScope.launch {
            try {
                val result = api().analyze(image)
                selection = Pricing.autoSelection(result, catalog)
                spec = result
                phase = Phase.Ready
                renderPhoto()
            } catch (e: Exception) {
                phase = Phase.Failed(e.message ?: "Ошибка")
            }
        }
    }

    fun retry() { drawing?.let { start(it) } }

    fun reset() {
        phase = Phase.Idle; drawing = null; render = null; renderError = null; spec = WindowSpec()
    }

    fun manual() {
        spec = WindowSpec(); drawing = null; render = null
        selection = Pricing.autoSelection(spec, catalog)
        phase = Phase.Ready
    }

    fun renderPhoto() {
        if (isRendering) return
        isRendering = true; renderError = null
        viewModelScope.launch {
            try {
                val col = catalog.color(selection.colorId)
                render = api().render(if (col != null) spec.copy(color = "${col.code} ${col.name}") else spec)
            } catch (e: Exception) { renderError = e.message }
            isRendering = false
        }
    }

    fun updateSpec(f: (WindowSpec) -> WindowSpec) { spec = f(spec) }
    fun updateSelection(f: (Selection) -> Selection) { selection = f(selection) }

    // ---------- Редактирование сетки ----------

    fun setSize(width: Int? = null, height: Int? = null) {
        spec = spec.copy(widthMm = max(100, width ?: spec.widthMm), heightMm = max(100, height ?: spec.heightMm)).fitted()
    }

    fun setSplit(split: Split) {
        if (split == spec.layout.split) return
        val main = if (split == Split.COLUMNS) spec.widthMm else spec.heightMm
        val cross = if (split == Split.COLUMNS) spec.heightMm else spec.widthMm
        val n = max(1, spec.layout.sections.size)
        val opened = spec.layout.sections.flatMap { it.cells }.firstOrNull { it.opening != Opening.FIXED }
        spec = spec.copy(layout = Layout(split, (0 until n).map { i ->
            val c = if (i == 0 && opened != null) Cell(cross, opened.opening, opened.hinge) else Cell(cross)
            Section(main / n, listOf(c.normalized()))
        })).fitted()
    }

    private fun sections(f: (MutableList<Section>) -> Unit) {
        val list = spec.layout.sections.toMutableList()
        f(list)
        spec = spec.copy(layout = spec.layout.copy(sections = list)).fitted()
    }

    fun addSection() = sections { list ->
        val n = list.size + 1
        for (i in list.indices) list[i] = list[i].copy(sizeMm = list[i].sizeMm * (n - 1) / n)
        list += Section(spec.mainTotal / n, listOf(Cell(spec.crossTotal)))
    }

    fun removeSection(i: Int) = sections { list ->
        if (list.size > 1 && i in list.indices) {
            val freed = list.removeAt(i).sizeMm
            val j = max(0, i - 1)
            list[j] = list[j].copy(sizeMm = list[j].sizeMm + freed)
        }
    }

    fun splitCell(section: Int, index: Int) = sections { list ->
        val s = list.getOrNull(section) ?: return@sections
        val c = s.cells.getOrNull(index) ?: return@sections
        val a = c.sizeMm / 2
        val cells = s.cells.toMutableList()
        cells[index] = c.copy(sizeMm = c.sizeMm - a)
        cells.add(index + 1, Cell(a))
        list[section] = s.copy(cells = cells)
    }

    fun removeCell(section: Int, index: Int) = sections { list ->
        val s = list.getOrNull(section) ?: return@sections
        if (s.cells.size < 2 || index !in s.cells.indices) return@sections
        val cells = s.cells.toMutableList()
        val freed = cells.removeAt(index).sizeMm
        val j = max(0, index - 1)
        cells[j] = cells[j].copy(sizeMm = cells[j].sizeMm + freed)
        list[section] = s.copy(cells = cells)
    }

    fun updateCell(section: Int, index: Int, f: (Cell) -> Cell) = sections { list ->
        val s = list.getOrNull(section) ?: return@sections
        if (index !in s.cells.indices) return@sections
        list[section] = s.copy(cells = s.cells.mapIndexed { j, c -> if (j == index) f(c).normalized() else c })
    }

    /** Меняет размер секции, забирая разницу у соседней. */
    fun setSectionSize(i: Int, value: Int) = sections { list ->
        if (list.size < 2 || i !in list.indices) return@sections
        val j = if (i == list.lastIndex) i - 1 else i + 1
        val total = list[i].sizeMm + list[j].sizeMm
        val v = min(max(100, value), total - 100)
        list[i] = list[i].copy(sizeMm = v)
        list[j] = list[j].copy(sizeMm = total - v)
    }

    fun setCellSize(section: Int, index: Int, value: Int) = sections { list ->
        val s = list.getOrNull(section) ?: return@sections
        if (s.cells.size < 2 || index !in s.cells.indices) return@sections
        val j = if (index == s.cells.lastIndex) index - 1 else index + 1
        val total = s.cells[index].sizeMm + s.cells[j].sizeMm
        val v = min(max(100, value), total - 100)
        val cells = s.cells.toMutableList()
        cells[index] = cells[index].copy(sizeMm = v)
        cells[j] = cells[j].copy(sizeMm = total - v)
        list[section] = s.copy(cells = cells)
    }

    // ---------- История ----------

    fun saveToHistory() {
        val b = breakdown
        val item = HistoryItem(spec = spec, selection = selection, total = b.total, currency = b.currency)
        historyStore.writeImage(item.id, "d", drawing)
        historyStore.writeImage(item.id, "r", render)
        history = listOf(item) + history
        historyStore.save(history)
    }

    fun open(item: HistoryItem) {
        spec = item.spec
        selection = if (catalog.system(item.selection.systemId) != null) item.selection else Pricing.autoSelection(item.spec, catalog)
        drawing = historyStore.image(item.id, "d"); render = historyStore.image(item.id, "r")
        renderError = null; phase = Phase.Ready; tab = Tab.ESTIMATE
    }

    fun delete(item: HistoryItem) {
        historyStore.deleteImages(item.id)
        history = history.filter { it.id != item.id }
        historyStore.save(history)
    }

    fun thumbnail(item: HistoryItem): Bitmap? = historyStore.image(item.id, "r") ?: historyStore.image(item.id, "d")

    // ---------- Настройки и каталог ----------

    fun changeCompany(v: String) { company = v; prefs.company = v }
    fun changeTheme(v: String) { theme = v; prefs.theme = v }

    /** Правка ставок: изменения пишутся в JSON каталога и сохраняются. */
    fun editCatalog(change: (JSONObject) -> Unit) {
        catalog = catalog.edited(change)
        catalogStore.save(catalog)
        catalogModified = true
    }

    fun resetCatalog() { catalog = catalogStore.reset(); catalogModified = false }

    fun summary(): String {
        val b = breakdown
        val sys = catalog.system(selection.systemId)?.name ?: ""
        val col = catalog.color(selection.colorId)?.title ?: ""
        return buildString {
            append("${spec.product.title} ${spec.widthMm}×${spec.heightMm} мм, $sys, заполнение ${selection.fillMm} мм, $col\n")
            spec.cellRects.forEach { r ->
                val c = r.cell
                append("• ${spec.layout.split.sectionName} ${r.section + 1}, ячейка ${r.index + 1}: ${r.w.toInt()}×${r.h.toInt()} — ")
                append((if (c.door) "дверь, " else "") + c.opening.title + (if (c.hinge != Hinge.NONE) ", " + c.hinge.title.lowercase() else "") + "\n")
            }
            append("Цена за 1 шт: ${Money.format(b.unitTotal, b.currency)}")
            if (b.quantity > 1) append("\nИтого за ${b.quantity} шт: ${Money.format(b.total, b.currency)}")
        }
    }

    /** Демо для проверки: тот же пример, что в iOS (-demo). */
    fun demo() {
        val s = WindowSpec(
            widthMm = 1200, heightMm = 2700, product = Product.WINDOW,
            layout = Layout(Split.COLUMNS, listOf(
                Section(800, listOf(Cell(1700, Opening.TILT_TURN, Hinge.LEFT), Cell(1000))),
                Section(400, listOf(Cell(2700))),
            )),
            system = "BKT 77", fillMm = 32, color = "RAL 7024", confidence = 0.9,
            info = listOf(InfoItem("Система", "BKT 77"), InfoItem("Заполнение", "32 мм"), InfoItem("Цвет", "RAL 7024"),
                InfoItem("Схема открывания", "поворотно-откидная, петли слева")),
            notes = "1200 2700 1700 1000 800 400. Система BKT 77, заполнение 32, цвет RAL 7024",
        )
        spec = s
        selection = Pricing.autoSelection(s, catalog)
        phase = Phase.Ready
        tab = Tab.ESTIMATE
    }
}

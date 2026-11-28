package kz.okno.proyom

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** Стиль ИИ-фото. */
enum class RenderStyle(val key: String, val title: String) { FACADE("facade", "Фасад"), INTERIOR("interior", "Интерьер"), STUDIO("studio", "Студия") }

/** Размер на схеме, который можно изменить нажатием (выделен голубым). */
data class SchemeDimension(val kind: Kind, val section: Int = 0, val index: Int = 0, val value: Int, val title: String) {
    enum class Kind { WIDTH, HEIGHT, SECTION, CELL }
}

/**
 * Коммерческое предложение из нескольких окон. Редактируется «текущее» окно;
 * «Добавить» — новое окно в КП, «Новый» — начать КП заново. Логика как в iOS (EstimateViewModel).
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val catalogStore = CatalogStore(app)
    private val historyStore = HistoryStore(app)

    var tab by mutableStateOf(Tab.ESTIMATE)
    var phase by mutableStateOf<Phase>(Phase.Idle)
    var positions by mutableStateOf(listOf<Position>())
        private set
    var current by mutableStateOf(0)
        private set
    var options by mutableStateOf(OfferOptions())
    var drawings by mutableStateOf(mapOf<String, Bitmap>())
        private set
    var renders by mutableStateOf(mapOf<String, Bitmap>())
        private set
    var rendering by mutableStateOf(setOf<String>())
        private set
    var renderErrors by mutableStateOf(mapOf<String, String>())
        private set
    var renderStyle by mutableStateOf(RenderStyle.FACADE)
    /** Фото, которое сейчас распознаётся (экран анализа и «Повторить»). */
    var pendingImage by mutableStateOf<Bitmap?>(null)
        private set
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

    private var historyId: String? = null
    private var savedState by mutableStateOf<Pair<List<Position>, OfferOptions>?>(null)

    private fun api() = Api(BuildConfig.WORKER_URL)

    // ---------- Текущее окно ----------

    private val cur: Int? get() = current.takeIf { it in positions.indices }
    var spec: WindowSpec
        get() = cur?.let { positions[it].spec } ?: WindowSpec()
        set(v) { cur?.let { i -> positions = positions.toMutableList().also { it[i] = it[i].copy(spec = v) } } }
    var selection: Selection
        get() = cur?.let { positions[it].selection } ?: Selection()
        set(v) { cur?.let { i -> positions = positions.toMutableList().also { it[i] = it[i].copy(selection = v) } } }
    var mark: String
        get() = cur?.let { positions[it].mark } ?: ""
        set(v) { cur?.let { i -> positions = positions.toMutableList().also { it[i] = it[i].copy(mark = v) } } }
    val currentId: String? get() = cur?.let { positions[it].id }
    val drawing: Bitmap? get() = currentId?.let { drawings[it] }
    val render: Bitmap? get() = currentId?.let { renders[it] }
    val isRendering: Boolean get() = currentId?.let { it in rendering } ?: false
    val renderError: String? get() = currentId?.let { renderErrors[it] }

    /** Расчёт всего КП (общий раскрой и уплотнители). */
    val offer: OfferBreakdown get() = Pricing.offer(positions, options, catalog)
    val isSaved: Boolean get() = positions.isNotEmpty() && savedState == (positions to options)

    fun updateSpec(f: (WindowSpec) -> WindowSpec) { spec = f(spec) }
    fun updateSelection(f: (Selection) -> Selection) { selection = f(selection) }
    fun updateOptions(f: (OfferOptions) -> OfferOptions) { options = f(options) }

    // ---------- Добавление окон ----------

    /** Новое окно по фото чертежа. */
    fun start(image: Bitmap) {
        pendingImage = image
        phase = Phase.Analyzing
        viewModelScope.launch {
            try {
                val result = api().analyze(image)
                val p = Position(mark = nextMark(), spec = result.copy(quantity = max(1, result.quantity)),
                    selection = Pricing.autoSelection(result, catalog))
                drawings = drawings + (p.id to image)
                pendingImage = null
                positions = positions + p
                current = positions.lastIndex
                phase = Phase.Ready
                renderPhoto()
            } catch (e: Exception) {
                phase = Phase.Failed(e.message ?: "Ошибка")
            }
        }
    }

    fun retry() { pendingImage?.let { start(it) } }

    /** Вернуться к КП после ошибки распознавания. */
    fun cancelAdding() { pendingImage = null; phase = if (positions.isEmpty()) Phase.Idle else Phase.Ready }

    /** Новое окно вручную (с выбором как у последнего окна КП). */
    fun addManual() {
        val last = positions.lastOrNull()
        val sel = last?.selection?.copy(overrides = emptyList()) ?: Pricing.autoSelection(WindowSpec(), catalog)
        append(Position(mark = nextMark(), selection = sel))
    }

    fun manual() = addManual()

    /** Копия текущего окна. */
    fun duplicateCurrent() {
        val i = cur ?: return
        val src = positions[i]
        val p = src.copy(id = java.util.UUID.randomUUID().toString(), mark = nextMark())
        drawings[src.id]?.let { drawings = drawings + (p.id to it) }
        renders[src.id]?.let { renders = renders + (p.id to it) }
        append(p)
    }

    fun removePosition(i: Int) {
        if (i !in positions.indices) return
        val id = positions[i].id
        positions = positions.filterIndexed { j, _ -> j != i }
        drawings = drawings - id; renders = renders - id; renderErrors = renderErrors - id
        current = min(current, max(0, positions.lastIndex))
        if (positions.isEmpty()) phase = Phase.Idle
    }

    fun select(i: Int) { if (i in positions.indices) current = i }

    private fun append(p: Position) {
        positions = positions + p
        current = positions.lastIndex
        phase = Phase.Ready
        tab = Tab.ESTIMATE
    }

    private fun nextMark(): String {
        val used = positions.mapNotNull { it.mark.filter(Char::isDigit).toIntOrNull() }
        return "ОК-${(used.maxOrNull() ?: 0) + 1}"
    }

    /** «Новый»: начать КП заново. */
    fun reset() {
        phase = Phase.Idle
        positions = emptyList(); current = 0; options = OfferOptions()
        drawings = emptyMap(); renders = emptyMap(); renderErrors = emptyMap(); rendering = emptySet()
        pendingImage = null; historyId = null; savedState = null
    }

    // ---------- ИИ-фото по схеме ----------

    fun renderPhoto() {
        val i = cur ?: return
        val p = positions[i]
        if (p.id in rendering) return
        rendering = rendering + p.id
        renderErrors = renderErrors - p.id
        viewModelScope.launch {
            try {
                val col = catalog.color(p.selection.colorId)
                val scheme = withContext(Dispatchers.Default) { cleanSchemePng(p.spec, col?.hex ?: "#45494E") }
                val s = if (col != null) p.spec.copy(color = "${col.code} ${col.name}") else p.spec
                val bmp = api().render(s, scheme, renderStyle.key)
                renders = renders + (p.id to bmp)
            } catch (e: Exception) {
                renderErrors = renderErrors + (p.id to (e.message ?: "Ошибка"))
            }
            rendering = rendering - p.id
        }
    }

    // ---------- Нестандарт ----------

    fun applyRecommendation(w: LimitWarning) {
        if (w.role.isEmpty()) return
        selection = selection.withOverride(w.role, if (w.applied) null else w.code)
    }

    /** Изменение размера, нажатого на схеме. */
    fun applyDimension(d: SchemeDimension, value: Int) {
        when (d.kind) {
            SchemeDimension.Kind.WIDTH -> setSize(width = value)
            SchemeDimension.Kind.HEIGHT -> setSize(height = value)
            SchemeDimension.Kind.SECTION -> setSectionSize(d.section, value)
            SchemeDimension.Kind.CELL -> setCellSize(d.section, d.index, value)
        }
    }

    // ---------- Редактирование сетки ----------

    fun setSize(width: Int? = null, height: Int? = null) {
        spec = spec.copy(widthMm = min(20000, max(100, width ?: spec.widthMm)), heightMm = min(20000, max(100, height ?: spec.heightMm))).fitted()
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
        if (positions.isEmpty()) return
        val o = offer
        val item = HistoryItem(id = historyId ?: java.util.UUID.randomUUID().toString(), positions = positions, options = options,
            total = o.total, currency = o.currency)
        for (p in positions) {
            historyStore.writeImage(p.id, "d", drawings[p.id])
            historyStore.writeImage(p.id, "r", renders[p.id])
        }
        history = listOf(item) + history.filter { it.id != item.id }
        historyStore.save(history)
        historyId = item.id
        savedState = positions to options
    }

    fun open(item: HistoryItem) {
        positions = item.positions.map { p ->
            if (catalog.system(p.selection.systemId) != null) p else p.copy(selection = Pricing.autoSelection(p.spec, catalog))
        }
        options = item.options
        drawings = positions.mapNotNull { p -> historyStore.image(p.id, "d")?.let { p.id to it } }.toMap()
        renders = positions.mapNotNull { p -> historyStore.image(p.id, "r")?.let { p.id to it } }.toMap()
        renderErrors = emptyMap()
        current = 0
        historyId = item.id
        savedState = positions to options
        phase = if (positions.isEmpty()) Phase.Idle else Phase.Ready
        tab = Tab.ESTIMATE
    }

    fun delete(item: HistoryItem) {
        item.positions.forEach { historyStore.deleteImages(it.id) }
        history = history.filter { it.id != item.id }
        historyStore.save(history)
    }

    fun thumbnail(item: HistoryItem): Bitmap? =
        item.positions.firstNotNullOfOrNull { historyStore.image(it.id, "r") ?: historyStore.image(it.id, "d") }

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
        val o = offer
        return buildString {
            append(if (options.objectName.isEmpty()) "Коммерческое предложение\n" else "КП: ${options.objectName}\n")
            positions.forEachIndexed { i, p ->
                val b = o.items[i]
                val sys = catalog.system(p.selection.systemId)?.name ?: ""
                val col = catalog.color(p.selection.colorId)?.code ?: ""
                append("\n${i + 1}. ${p.mark}: ${p.spec.product.title} ${p.spec.widthMm}×${p.spec.heightMm} мм, $sys, $col")
                append("\n   ${b.quantity} шт × ${Money.format(b.unitTotal, b.currency)} = ${Money.format(b.total, b.currency)}")
            }
            append("\n\nПлощадь: ${"%.2f".format(o.areaM2)} м²")
            if (o.discount > 0) append("\nСкидка ${options.discountPercent.toInt()}%: −${Money.format(o.discount, o.currency)}")
            append("\nИтого: ${Money.format(o.total, o.currency)} (в т. ч. НДС ${catalog.vatPercent.toInt()}%: ${Money.format(o.vatIncluded, o.currency)})")
            append("\n" + if (options.scope == OfferScope.MATERIALS) "В цену входят: алюминиевый профиль, комплектующие, уплотнители."
                else "В цену входят: профиль, комплектующие, уплотнители, стеклопакеты, фурнитура, изготовление" +
                    (if (positions.any { it.selection.installation }) ", монтаж" else "") + ".")
        }
    }

    /** Демо для проверки: тот же пример, что в iOS (-demo) — КП из двух окон. */
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
        val s2 = WindowSpec(
            widthMm = 1500, heightMm = 2300, quantity = 2,
            layout = Layout(Split.COLUMNS, listOf(
                Section(750, listOf(Cell(1650, Opening.TILT_TURN, Hinge.LEFT), Cell(650))),
                Section(750, listOf(Cell(1650), Cell(650))),
            )),
        )
        val sel = Pricing.autoSelection(s, catalog)
        positions = listOf(Position(mark = "ОК-1", spec = s, selection = sel), Position(mark = "ОК-2", spec = s2, selection = sel))
        options = OfferOptions(objectName = "Демо-объект")
        current = 0
        phase = Phase.Ready
        tab = Tab.ESTIMATE
    }
}

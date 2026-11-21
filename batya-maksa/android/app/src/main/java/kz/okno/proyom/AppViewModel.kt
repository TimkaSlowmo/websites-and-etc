package kz.okno.proyom

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

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
    var history by mutableStateOf(historyStore.load())
        private set
    var company by mutableStateOf(prefs.company)
        private set
    var workerUrl by mutableStateOf(prefs.workerUrl)
        private set

    val breakdown: Breakdown get() = Pricing.calculate(spec, selection, catalog)

    private fun api() = Api(workerUrl)

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
            try { render = api().render(spec) } catch (e: Exception) { renderError = e.message }
            isRendering = false
        }
    }

    fun updateSpec(f: (WindowSpec) -> WindowSpec) { spec = f(spec) }
    fun updateSelection(f: (Selection) -> Selection) { selection = f(selection) }

    fun saveToHistory() {
        val b = breakdown
        val item = HistoryItem(spec = spec, selection = selection, total = b.total, currency = b.currency)
        historyStore.writeImage(item.id, "d", drawing)
        historyStore.writeImage(item.id, "r", render)
        history = listOf(item) + history
        historyStore.save(history)
    }

    fun open(item: HistoryItem) {
        spec = item.spec; selection = item.selection
        drawing = historyStore.image(item.id, "d"); render = historyStore.image(item.id, "r")
        renderError = null; phase = Phase.Ready; tab = Tab.ESTIMATE
    }

    fun delete(item: HistoryItem) {
        historyStore.deleteImages(item.id)
        history = history.filter { it.id != item.id }
        historyStore.save(history)
    }

    fun thumbnail(item: HistoryItem): Bitmap? = historyStore.image(item.id, "r") ?: historyStore.image(item.id, "d")

    fun changeCompany(v: String) { company = v; prefs.company = v }
    fun changeWorkerUrl(v: String) { workerUrl = v; prefs.workerUrl = v }

    fun importCatalog(text: String): String = try {
        catalog = catalogStore.import(text); "Каталог загружен"
    } catch (e: Exception) { e.message ?: "Ошибка" }

    fun resetCatalog() { catalog = catalogStore.reset() }

    fun summary(): String {
        val b = breakdown
        return buildString {
            append("${spec.type.title} ${spec.widthMm}×${spec.heightMm} мм\n")
            b.lines.forEach { append("• ${it.title}: ${Money.format(it.amount, b.currency)}\n") }
            append("Итого (ориентировочно): ${Money.format(b.total, b.currency)}")
        }
    }
}

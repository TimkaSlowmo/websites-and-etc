package kz.okno.proyom

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- История ----------

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val p = pal()
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")) }
    Column(Modifier.fillMaxSize()) {
        LargeTitle("История")
        if (vm.history.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(bottom = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.Schedule, null, tint = p.secondary, modifier = Modifier.size(52.dp))
                Text("Пока пусто", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("Сохраняйте КП кнопкой «Сохранить в историю».", color = p.secondary, fontSize = 15.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp, vertical = 6.dp))
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                itemsIndexed(vm.history, key = { _, h -> h.id }) { i, item ->
                    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                        if (v == SwipeToDismissBoxValue.EndToStart) { vm.delete(item); true } else false
                    })
                    Box(Modifier.animateItem()) {
                        GroupItem(i, vm.history.size, inset = 84.dp) {
                            SwipeToDismissBox(
                                state = state, enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(Modifier.fillMaxSize().background(Danger).padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                                        Text("Удалить", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                },
                            ) {
                                RowBase(onClick = { vm.open(item) }, minHeight = 72.dp) {
                                    val thumb by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, item.id, item.date) {
                                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.thumbnail(item) }
                                    }
                                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(p.fill), contentAlignment = Alignment.Center) {
                                        if (thumb != null) Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        else Icon(Icons.Outlined.Window, null, tint = p.secondary)
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(item.options.objectName.ifEmpty { item.title }, color = p.text, fontSize = 17.sp, maxLines = 1)
                                        Text(fmt.format(Date(item.date)) + " · окон: ${item.windows}", fontSize = 12.sp, color = p.secondary)
                                    }
                                    Text(Money.format(item.total, item.currency), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                item { GroupFooter("Смахните строку влево, чтобы удалить.") }
            }
        }
    }
}

// ---------- Каталог ----------

private val baseNames = mapOf(
    "frame_perimeter_m" to "периметр рамы", "impost_length_m" to "длина импостов", "imposts" to "на импост",
    "sash_perimeter_m" to "периметр створок", "sashes" to "на створку", "door_perimeter_m" to "периметр дверных створок",
    "doors" to "на дверь", "glass_perimeter_m" to "периметр стёкол", "glass_area_m2" to "площадь стёкол", "panes" to "на стекло",
    "door_width_m" to "ширина двери", "vert_len_m" to "длина стоек", "horiz_len_m" to "длина ригелей",
    "grid_len_m" to "длина стоек и ригелей", "area_m2" to "площадь", "frame_corners" to "на угол рамы",
    "bead_length_m" to "длина штапиков", "sash_panes" to "на стекло в створке",
)

private fun plusTitle(r: Rule): String = r.plus.joinToString("") { p ->
    if (p.qty < 1) " + ${Math.round(p.qty * 1000)} мм ${baseTitle(p.base, 1.0)}" else " + ${baseTitle(p.base, p.qty)}"
}

private fun baseTitle(base: String, qty: Double): String {
    val n = baseNames[base] ?: base
    return if (qty == 1.0) n else (if (qty < 1) "%.2f".format(qty) else "%.0f".format(qty)) + " × " + n
}

@Composable
fun CatalogScreen(vm: AppViewModel) {
    val c = vm.catalog
    val p = pal()
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var openSystem by remember { mutableStateOf<String?>(null) }
    val opened = openSystem?.let { c.system(it) }
    if (opened != null) {
        BackHandler { openSystem = null }
        SystemDetail(opened, c) { openSystem = null }
        return
    }
    val q = query.trim().lowercase()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { LargeTitle("Каталог") }
        item { SearchField(query, when (tab) { 0 -> "Система"; 1 -> "Артикул или название"; else -> "RAL, название" }) { query = it } }
        item { Segmented(listOf("Системы", "Прайс", "Цвета"), tab, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { tab = it } }
        when (tab) {
            0 -> {
                val list = c.systems.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
                item { Spacer(Modifier.height(18.dp)) }
                itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                    GroupItem(i, list.size) {
                        RowBase(onClick = { openSystem = s.id }, minHeight = 64.dp) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text(s.description, fontSize = 12.sp, color = p.secondary)
                                Text("Заполнение ${s.fillMin}–${s.fillMax} мм · ${s.rules.size} позиций в расчёте", fontSize = 11.sp, color = p.tertiary)
                            }
                            Text("›", color = p.tertiary, fontSize = 22.sp)
                        }
                    }
                }
            }
            1 -> {
                val list = c.articles.filter { q.isEmpty() || it.code.lowercase().contains(q) || it.name.lowercase().contains(q) }
                val shown = list.take(300)
                item { GroupHeader("Позиций: ${list.size} из ${c.articles.size}") }
                itemsIndexed(shown, key = { _, a -> a.code }) { i, a ->
                    GroupItem(i, shown.size) {
                        RowBase {
                            Column(Modifier.weight(1f)) {
                                Text(a.code, fontSize = 12.sp, color = Accent, fontFamily = FontFamily.Monospace)
                                Text(a.name, fontSize = 15.sp, color = p.text)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (a.price > 0) "${Money.format(a.price, c.currency)}/${a.unitTitle}" else "нет цены",
                                fontSize = 15.sp, textAlign = TextAlign.End, color = if (a.price > 0) p.text else p.secondary)
                        }
                    }
                }
                if (list.size > 300) item { GroupFooter("Показаны первые 300 — уточните поиск.") }
            }
            else -> c.colorGroups.forEach { g ->
                val list = c.colors.filter { it.group == g.id && (q.isEmpty() || it.title.lowercase().contains(q)) }
                if (list.isNotEmpty()) {
                    item(key = "h-${g.id}") { GroupHeader(if (g.surchargePercent > 0) "${g.name} · +${g.surchargePercent.toInt()}%" else g.name) }
                    itemsIndexed(list, key = { _, col -> col.id }) { i, col ->
                        GroupItem(i, list.size, inset = 58.dp) {
                            RowBase {
                                Swatch(col.hex, Modifier.size(30.dp))
                                Text(col.code, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
                                Text(col.name, color = p.secondary, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemDetail(system: SystemDef, c: Catalog, onBack: () -> Unit) {
    val p = pal()
    Column(Modifier.fillMaxSize()) {
        TopBar(system.name, leading = {
            Text("‹ Каталог", color = Accent, fontSize = 17.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 6.dp))
        })
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Group { NoteRow(system.description) } }
            system.geo?.let { g ->
                item {
                    Group("Вычеты профилей, мм") {
                        NoteRow("Рама ${g.frameFace.toInt()} · импост ${g.impostFace.toInt()} · нахлёст створки ${g.sashOverlap.toInt()} · створка до стекла ${g.sashFace.toInt()} · штапик −${g.beadDeduct.toInt()} · стекло −${g.glassGap.toInt()} мм")
                    }
                }
            }
            if (system.recommend.isNotEmpty()) item {
                Group("Рекомендации для нестандарта") {
                    system.recommend.forEachIndexed { i, r ->
                        if (i > 0) Sep()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(r.text, fontSize = 15.sp, color = p.text)
                            Text("${r.role} → ${r.code} · ${c.article(r.code)?.name ?: "нет в прайсе"}", fontSize = 12.sp, color = p.secondary)
                        }
                    }
                }
            }
            item { GroupHeader("Что входит в расчёт") }
            itemsIndexed(system.rules) { i, r ->
                GroupItem(i, system.rules.size) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(r.role, fontSize = 15.sp, color = p.text)
                        if (r.byFill != null) {
                            Text(r.byFill.joinToString("\n") { "${it.min}–${it.max} мм: ${it.code}" }, fontSize = 11.sp, color = p.secondary, fontFamily = FontFamily.Monospace)
                        } else if (r.code != null) {
                            val a = c.article(r.code)
                            Text("${r.code} · ${a?.name ?: "нет в прайсе"}", fontSize = 12.sp, color = p.secondary)
                            if (a != null) Text("${Money.format(a.price, c.currency)}/${a.unitTitle} · ${baseTitle(r.base, r.qty)}${plusTitle(r)}", fontSize = 11.sp, color = p.tertiary)
                            r.doorCode?.let { Text("для дверей: $it", fontSize = 11.sp, color = p.tertiary) }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Настройки ----------

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val c = vm.catalog
    val p = pal()
    var message by remember { mutableStateOf<String?>(null) }
    fun set(key: String, v: Double) = vm.editCatalog { it.put(key, v) }
    fun setIn(arr: String, i: Int, key: String, v: Double) = vm.editCatalog { (it.getJSONArray(arr).get(i) as JSONObject).put(key, v) }
    val themes = listOf("system", "light", "dark")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LargeTitle("Настройки")
        Group("Оформление") {
            Segmented(listOf("Авто", "Светлая", "Тёмная"), themes.indexOf(vm.theme).coerceAtLeast(0), Modifier.padding(12.dp)) { i ->
                vm.changeTheme(themes[i])
            }
        }
        Group("Коммерческое предложение") {
            TextRow("Название компании (для КП)", vm.company) { vm.changeCompany(it) }
            Sep(); RateRow("Срок действия КП", c.validDays.toDouble(), "дн.") { vm.editCatalog { o -> o.put("offer_valid_days", maxOf(1, it.toInt())) } }
        }
        Group("Раскрой и упаковка", footer = "Как в картах раскроя ORGADATA: детали всех окон КП раскладываются по хлыстам, платим за целые хлысты.") {
            RateRow("Хлыст профиля", c.barDefaultMm, "мм") { set("bar_default_mm", maxOf(1000.0, it)) }
            c.barLengths.forEachIndexed { i, b ->
                Sep(); RateRow(b.title ?: "Хлыст ${b.prefix}…", b.mm, "мм") { setIn("bar_lengths", i, "mm", maxOf(1000.0, it)) }
            }
            Sep(); RateRow("Ширина пропила", c.kerf, "мм") { set("saw_kerf_mm", it) }
            Sep(); RateRow("Обрезка концов хлыста", c.trim, "мм") { set("bar_trim_mm", it) }
            Sep(); RateRow("Уплотнитель кратно", c.gasketStep, "м") { set("gasket_step_m", maxOf(1.0, it)) }
        }
        c.systems.forEachIndexed { si, sys ->
            val l = sys.limits ?: return@forEachIndexed
            fun setL(k: String, v: Double) = vm.editCatalog { o -> o.getJSONArray("systems").getJSONObject(si).getJSONObject("limits").put(k, v) }
            Group("Стандартные размеры · ${sys.name}", footer = "Больше — нестандарт: расчёт по стандартным профилям с предупреждением и рекомендацией профиля.") {
                RateRow("Створка, макс. ширина", l.sashMaxW ?: 0.0, "мм") { setL("sash_max_w", it) }
                Sep(); RateRow("Створка, макс. высота", l.sashMaxH ?: 0.0, "мм") { setL("sash_max_h", it) }
                Sep(); RateRow("Створка, макс. вес", l.sashMaxKg ?: 0.0, "кг") { setL("sash_max_kg", it) }
                Sep(); RateRow("Импост, макс. длина", l.impostMaxMm ?: 0.0, "мм") { setL("impost_max_mm", it) }
                Sep(); RateRow("Дверь, макс. ширина", l.doorMaxW ?: 0.0, "мм") { setL("door_max_w", it) }
                Sep(); RateRow("Дверь, макс. высота", l.doorMaxH ?: 0.0, "мм") { setL("door_max_h", it) }
            }
        }
        Group("Коэффициенты расчёта") {
            RateRow("Наценка", c.markupPercent, "%") { set("markup_percent", it) }
            Sep(); RateRow("НДС", c.vatPercent, "%") { set("vat_percent", it) }
            Sep(); RateRow("Отходы профиля", c.wastePercent, "%") { set("waste_percent", it) }
            Sep(); RateRow("Изготовление, за м²", c.laborPerM2, "₸") { set("labor_per_m2", it) }
            Sep(); RateRow("Монтаж, за м²", c.installPerM2, "₸") { set("install_per_m2", it) }
            Sep(); RateRow("Монтаж, минимум", c.installMin, "₸") { set("install_min", it) }
            Sep(); RateRow("Двухцветное исполнение", c.twoColorPercent, "%") { set("two_color_percent", it) }
        }
        Group("Стеклопакеты", footer = "Стеклопакетов нет в прайсе профилей — укажите цены поставщика.") {
            c.glass.forEachIndexed { i, g -> if (i > 0) Sep(); RateRow(g.name, g.pricePerM2, "₸/м²") { setIn("glass", i, "price_per_m2", it) } }
        }
        Group("Фурнитура (за створку)") {
            c.hardware.forEachIndexed { i, h -> if (i > 0) Sep(); RateRow(h.name, h.price, "₸") { setIn("hardware", i, "price", it) } }
        }
        Group("Надбавка за цвет к профилям") {
            c.colorGroups.forEachIndexed { i, g -> if (i > 0) Sep(); RateRow(g.name, g.surchargePercent, "%") { setIn("color_groups", i, "surcharge_percent", it) } }
        }
        Group("Каталог") {
            ValueRow("Прайс", c.name)
            Sep(); ValueRow("Позиций / систем", "${c.articles.size} / ${c.systems.size}")
            if (vm.catalogModified) {
                Sep(); ButtonRow("Вернуть цены из прайса", color = Danger) { vm.resetCatalog(); message = "Цены сброшены к прайсу" }
            }
        }
        message?.let { Group { NoteRow(it) } }
        Group(footer = "Проём — ИИ-расчёт окон и дверей по чертежу.") {
            ValueRow("Версия", BuildConfig.VERSION_NAME)
        }
        Spacer(Modifier.height(28.dp))
    }
}

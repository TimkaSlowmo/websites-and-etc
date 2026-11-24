package kz.okno.proyom

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun ScreenTitle(text: String) {
    Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
}

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale("ru")) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("История")
        if (vm.history.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.History, null, tint = Color.LightGray, modifier = Modifier.size(56.dp))
                Text("Пока пусто", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text("Сохраняйте расчёты кнопкой «Сохранить в историю».", color = Color.Gray, fontSize = 14.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.history, key = { it.id }) { item ->
                    Row(
                        Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                            .clickable { vm.open(item) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val thumb = remember(item.id) { vm.thumbnail(item) }
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEFF2F5))) {
                            thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("${item.spec.product.title} ${item.spec.widthMm}×${item.spec.heightMm}")
                            Text(fmt.format(Date(item.date)) + if (item.spec.quantity > 1) " · ${item.spec.quantity} шт" else "", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text(Money.format(item.total, item.currency), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { vm.delete(item) }) { Icon(Icons.Filled.Delete, "Удалить", tint = Color.Gray) }
                    }
                }
            }
        }
    }
}

private val baseNames = mapOf(
    "frame_perimeter_m" to "периметр рамы", "impost_length_m" to "длина импостов", "imposts" to "на импост",
    "sash_perimeter_m" to "периметр створок", "sashes" to "на створку", "door_perimeter_m" to "периметр дверных створок",
    "doors" to "на дверь", "glass_perimeter_m" to "периметр стёкол", "glass_area_m2" to "площадь стёкол", "panes" to "на стекло",
    "door_width_m" to "ширина двери", "vert_len_m" to "длина стоек", "horiz_len_m" to "длина ригелей",
    "grid_len_m" to "длина стоек и ригелей", "area_m2" to "площадь", "frame_corners" to "на угол рамы",
)

@Composable
fun CatalogScreen(vm: AppViewModel) {
    val c = vm.catalog
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var openSystem by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Каталог")
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            listOf("Системы", "Прайс", "Цвета").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        if (tab != 0) {
            OutlinedTextField(query, { query = it }, placeholder = { Text(if (tab == 1) "Артикул или название" else "RAL, название") },
                singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp))
        }
        val q = query.trim().lowercase()
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> items(c.systems, key = { it.id }) { s ->
                    SectionCard {
                        Row(Modifier.fillMaxWidth().clickable { openSystem = if (openSystem == s.id) null else s.id }, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(s.description, fontSize = 12.sp, color = Color.Gray)
                                Text("Заполнение ${s.fillMin}–${s.fillMax} мм · ${s.rules.size} позиций в расчёте", fontSize = 11.sp, color = Color.Gray)
                            }
                            Text(if (openSystem == s.id) "▲" else "▼", color = Color.Gray)
                        }
                        if (openSystem == s.id) s.rules.forEach { r ->
                            Column(Modifier.padding(top = 8.dp)) {
                                Text(r.role, fontSize = 14.sp)
                                if (r.byFill != null) {
                                    Text(r.byFill.joinToString("\n") { "${it.min}–${it.max} мм: ${it.code}" }, fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                } else if (r.code != null) {
                                    val a = c.article(r.code)
                                    Text("${r.code} · ${a?.name ?: "нет в прайсе"}", fontSize = 12.sp, color = Color.Gray)
                                    if (a != null) {
                                        val base = baseNames[r.base] ?: r.base
                                        Text("${Money.format(a.price, c.currency)}/${a.unitTitle} · ${if (r.qty == 1.0) base else "%.1f × %s".format(r.qty, base)}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    r.doorCode?.let { Text("для дверей: $it", fontSize = 11.sp, color = Color.Gray) }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val list = c.articles.filter { q.isEmpty() || it.code.lowercase().contains(q) || it.name.lowercase().contains(q) }
                    item { Text("Позиций: ${list.size} из ${c.articles.size}", fontSize = 13.sp, color = Color.Gray) }
                    items(list.take(300), key = { it.code }) { a ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.code, fontSize = 12.sp, color = Accent, fontFamily = FontFamily.Monospace)
                                Text(a.name, fontSize = 14.sp)
                            }
                            Text(if (a.price > 0) "${Money.format(a.price, c.currency)}/${a.unitTitle}" else "нет цены",
                                fontSize = 14.sp, textAlign = TextAlign.End, color = if (a.price > 0) Color.Black else Color.Gray)
                        }
                    }
                    if (list.size > 300) item { Text("Показаны первые 300 — уточните поиск.", fontSize = 12.sp, color = Color.Gray) }
                }
                else -> c.colorGroups.forEach { g ->
                    val list = c.colors.filter { it.group == g.id && (q.isEmpty() || it.title.lowercase().contains(q)) }
                    if (list.isNotEmpty()) {
                        item { Text(if (g.surchargePercent > 0) "${g.name} · +${g.surchargePercent.toInt()}%" else g.name, fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp)) }
                        items(list, key = { it.id }) { col ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Swatch(col.hex, Modifier.size(32.dp))
                                Text(col.code, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
                                Text(col.name, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RateField(title: String, value: Double, suffix: String, onSave: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp)
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                text = t.filter { it.isDigit() || it == '.' || it == ',' }.take(9)
                text.replace(',', '.').toDoubleOrNull()?.let(onSave)
            },
            singleLine = true, suffix = { Text(suffix) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.width(150.dp),
        )
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val c = vm.catalog
    var message by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        message = if (text == null) "Не удалось открыть файл" else vm.importCatalog(text)
    }
    fun set(key: String, v: Double) = vm.editCatalog { it.put(key, v) }
    fun setIn(arr: String, i: Int, key: String, v: Double) = vm.editCatalog { (it.getJSONArray(arr).get(i) as JSONObject).put(key, v) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Настройки")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("Коммерческое предложение") {
                OutlinedTextField(vm.company, vm::changeCompany, label = { Text("Название компании") },
                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            }
            SectionCard("Коэффициенты расчёта") {
                RateField("Наценка", c.markupPercent, "%") { set("markup_percent", it) }
                RateField("НДС", c.vatPercent, "%") { set("vat_percent", it) }
                RateField("Отходы профиля", c.wastePercent, "%") { set("waste_percent", it) }
                RateField("Изготовление, за м²", c.laborPerM2, "₸") { set("labor_per_m2", it) }
                RateField("Монтаж, за м²", c.installPerM2, "₸") { set("install_per_m2", it) }
                RateField("Монтаж, минимум", c.installMin, "₸") { set("install_min", it) }
                RateField("Двухцветное исполнение", c.twoColorPercent, "%") { set("two_color_percent", it) }
            }
            SectionCard("Стеклопакеты, за м²") {
                c.glass.forEachIndexed { i, g -> RateField(g.name, g.pricePerM2, "₸") { setIn("glass", i, "price_per_m2", it) } }
                Text("Стеклопакетов нет в прайсе профилей — укажите цены поставщика.", fontSize = 12.sp, color = Color.Gray)
            }
            SectionCard("Фурнитура, за створку") {
                c.hardware.forEachIndexed { i, h -> RateField(h.name, h.price, "₸") { setIn("hardware", i, "price", it) } }
            }
            SectionCard("Надбавка за цвет к профилям") {
                c.colorGroups.forEachIndexed { i, g -> RateField(g.name, g.surchargePercent, "%") { setIn("color_groups", i, "surcharge_percent", it) } }
            }
            SectionCard("Каталог") {
                Line2("Прайс", c.name)
                Line2("Позиций / систем", "${c.articles.size} / ${c.systems.size}")
                FilledTonalButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.size(8.dp)); Text("Загрузить каталог (JSON)")
                }
                if (vm.catalogModified) TextButton(onClick = { vm.resetCatalog(); message = "Цены сброшены к прайсу" }) {
                    Text("Вернуть цены из прайса", color = Color(0xFFDC2626))
                }
                message?.let { Text(it, fontSize = 13.sp, color = Accent) }
            }
            SectionCard("ИИ-сервер") {
                OutlinedTextField(vm.workerUrl, vm::changeWorkerUrl, label = { Text("Адрес") },
                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                Text("Уже настроено, менять не нужно.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            }
            SectionCard {
                Line2("Версия", BuildConfig.VERSION_NAME)
                Text("Проём — ИИ-расчёт окон и дверей по чертежу.", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

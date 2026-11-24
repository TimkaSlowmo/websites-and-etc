package kz.okno.proyom

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider

@Composable
fun ResultView(vm: AppViewModel) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val c = vm.catalog
    val spec = vm.spec
    val sel = vm.selection
    val b = vm.breakdown
    val system = c.system(sel.systemId)
    val outside = c.color(sel.colorId)
    var tab by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }   // "out" | "in"
    var expanded by remember { mutableStateOf(setOf<LineGroup>()) }
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Расчёт", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::reset) { Icon(Icons.Filled.Add, null); Text("Новый") }
        }

        Appear(0) {
            SectionCard {
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Схема") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Чертёж") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("ИИ-фото") })
                }
                Spacer(Modifier.height(10.dp))
                Crossfade(targetState = tab, label = "img") { t ->
                    when (t) {
                        0 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SchemeView(spec, outside?.hex ?: "#45494E", editing?.let { "${it.first}-${it.second}" },
                                Modifier.heightIn(max = 420.dp)) { r ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                editing = r.section to r.index
                            }
                            Text("Нажмите на ячейку, чтобы изменить открывание и размер", fontSize = 12.sp, color = Color.Gray)
                        }
                        1 -> {
                            val d = vm.drawing
                            if (d != null) Image(d.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                            else Text("Чертёж не загружен (ручной ввод)", color = Color.Gray, modifier = Modifier.padding(24.dp))
                        }
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val r = vm.render
                            when {
                                r != null -> {
                                    AnimatedVisibility(visible = true, enter = fadeIn(tween(500)) + scaleIn(initialScale = 0.95f)) {
                                        Image(r.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                                    }
                                    Text("Примерный вид, сгенерирован ИИ", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
                                }
                                vm.isRendering -> Shimmer("Рисую готовое изделие…", Modifier.fillMaxWidth().height(240.dp))
                                else -> Text(vm.renderError ?: "Фото ещё не создано", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp))
                            }
                            OutlinedButton(onClick = vm::renderPhoto, enabled = !vm.isRendering, modifier = Modifier.padding(top = 8.dp)) {
                                Icon(Icons.Filled.Refresh, null); Spacer(Modifier.size(6.dp)); Text("Сгенерировать по параметрам")
                            }
                        }
                    }
                }
            }
        }

        val warnings = buildList {
            if (spec.confidence < 0.6 && vm.drawing != null) add("ИИ не уверен в распознавании — сверьте схему с чертежом.")
            addAll(b.warnings)
        }
        AnimatedVisibility(visible = warnings.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF4E0)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                warnings.forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(w, fontSize = 14.sp)
                    }
                }
                if (spec.layoutMismatch != null) TextButton(onClick = { vm.updateSpec { it.fitted() } }) { Text("Подогнать сетку под размеры") }
            }
        }

        if (spec.info.isNotEmpty() || spec.notes.isNotBlank()) Appear(1) {
            SectionCard("С чертежа") {
                spec.info.forEach { Line2(it.label, it.value) }
                if (spec.notes.isNotBlank()) {
                    var open by remember { mutableStateOf(false) }
                    TextButton(onClick = { open = !open }) { Text(if (open) "Скрыть текст" else "Весь текст") }
                    AnimatedVisibility(open) { Text(spec.notes, fontSize = 14.sp) }
                }
            }
        }

        Appear(2) {
            SectionCard("Изделие") {
                Choice("Изделие", spec.product.title, Product.entries, { it.title }) { p -> vm.updateSpec { it.copy(product = p) } }
                Choice("Система", system?.name ?: "—", c.systems, { it.name + " — " + it.description }) { s ->
                    vm.updateSelection { it.copy(systemId = s.id, fillMm = if (it.fillMm in s.fillMin..s.fillMax) it.fillMm else s.fillDefault) }
                }
                system?.let { Text(it.description, fontSize = 12.sp, color = Color.Gray) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    NumberField("Ширина, мм", spec.widthMm, Modifier.weight(1f)) { v -> vm.setSize(width = v) }
                    NumberField("Высота, мм", spec.heightMm, Modifier.weight(1f)) { v -> vm.setSize(height = v) }
                }
                AnimatedVisibility(visible = spec.altWidths.isNotEmpty() || spec.altHeights.isNotEmpty()) {
                    Column(Modifier.padding(top = 10.dp)) {
                        Text("ИИ сомневается. Проверьте по чертежу:", color = Color(0xFFD97706), fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            spec.altWidths.forEach { w ->
                                AssistChip(onClick = { vm.setSize(width = w); vm.updateSpec { it.copy(altWidths = emptyList()) } },
                                    label = { Text("Ширина $w") }, colors = AssistChipDefaults.assistChipColors(labelColor = Accent))
                            }
                            spec.altHeights.forEach { h ->
                                AssistChip(onClick = { vm.setSize(height = h); vm.updateSpec { it.copy(altHeights = emptyList()) } },
                                    label = { Text("Высота $h") }, colors = AssistChipDefaults.assistChipColors(labelColor = Accent))
                            }
                        }
                    }
                }
                Text("Площадь: %.2f м²".format(spec.areaM2), color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Количество", Modifier.weight(1f))
                    TextButton(onClick = { vm.updateSpec { it.copy(quantity = maxOf(1, it.quantity - 1)) } }) { Text("−", fontSize = 22.sp) }
                    Text("${spec.quantity} шт", fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { vm.updateSpec { it.copy(quantity = minOf(999, it.quantity + 1)) } }) { Text("+", fontSize = 22.sp) }
                }
            }
        }

        Appear(3) {
            SectionCard("Заполнение, цвет, монтаж") {
                val fills = (c.glass.map { it.fillMm }.filter { f -> system == null || f in system.fillMin..system.fillMax } +
                    listOfNotNull(system?.fillDefault, sel.fillMm)).distinct().sorted()
                Choice("Заполнение", "${sel.fillMm} мм", fills, { "$it мм" }) { f -> vm.updateSelection { it.copy(fillMm = f) } }
                c.glassItem(sel.fillMm)?.let { Text(it.name, fontSize = 12.sp, color = Color.Gray) }
                var formula by remember(spec.glass) { mutableStateOf(spec.glass) }
                OutlinedTextField(formula, { formula = it; vm.updateSpec { s -> s.copy(glass = it) } }, label = { Text("Формула стеклопакета") },
                    placeholder = { Text("например 4-10-4-10-4") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                ColorRow("Цвет снаружи", outside, "—") { picking = "out" }
                ColorRow("Цвет внутри", if (sel.colorInsideId.isEmpty()) null else c.color(sel.colorInsideId), "Как снаружи") { picking = "in" }
                SwitchRow("Монтаж", sel.installation) { on -> vm.updateSelection { it.copy(installation = on) } }
            }
        }

        Appear(4) {
            SectionCard("Сетка и створки") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Split.entries.forEach { s ->
                        FilterChip(selected = spec.layout.split == s, onClick = { vm.setSplit(s) }, label = { Text(s.short) })
                    }
                }
                spec.layout.sections.forEachIndexed { si, s ->
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${spec.layout.split.sectionName} ${si + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (spec.layout.sections.size > 1) {
                            NumberField("мм", s.sizeMm, Modifier.width(110.dp)) { v -> vm.setSectionSize(si, v) }
                            IconButton(onClick = { vm.removeSection(si) }) { Icon(Icons.Filled.Delete, "Удалить", tint = Color.Gray) }
                        } else Text("${s.sizeMm} мм", color = Color.Gray)
                    }
                    s.cells.forEachIndexed { ci, cell ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { editing = si to ci }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Ячейка ${ci + 1} · ${cell.sizeMm} мм")
                                Text(cellSummary(cell), fontSize = 12.sp, color = Color.Gray)
                            }
                            Text("›", fontSize = 20.sp, color = Color.Gray)
                        }
                    }
                    TextButton(onClick = { vm.splitCell(si, s.cells.lastIndex) }) {
                        Text(if (spec.layout.split == Split.COLUMNS) "Разделить по высоте" else "Разделить по ширине", fontSize = 13.sp)
                    }
                }
                TextButton(onClick = vm::addSection) {
                    Icon(Icons.Filled.AddCircle, null); Spacer(Modifier.size(6.dp))
                    Text("Добавить ${if (spec.layout.split == Split.COLUMNS) "колонку" else "ряд"}")
                }
                Text("Колонки — деление сверху донизу, ряды — слева направо. Ячейка делит только свою секцию.", fontSize = 12.sp, color = Color.Gray)
            }
        }

        Appear(5) {
            SectionCard("Стоимость (прайс ${c.name})") {
                LineGroup.entries.forEach { g ->
                    val lines = b.lines.filter { it.group == g }
                    if (lines.isNotEmpty()) {
                        val open = g in expanded
                        Row(Modifier.fillMaxWidth().clickable { expanded = if (open) expanded - g else expanded + g }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(g.title, Modifier.weight(1f))
                            Text(Money.format(b.sum(g), b.currency), color = Color.Gray)
                            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Color.Gray)
                        }
                        AnimatedVisibility(open) {
                            Column(Modifier.padding(start = 8.dp, bottom = 6.dp)) {
                                lines.forEach { l ->
                                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(l.title, fontSize = 14.sp)
                                            Text((if (l.code.isEmpty()) "" else l.code + " · ") + l.detail, fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Text(Money.format(l.amount, b.currency), fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Line2("Наценка ${c.markupPercent.toInt()}%", Money.format(b.markup, b.currency))
                Line2("НДС ${c.vatPercent.toInt()}%", Money.format(b.vat, b.currency))
                val shownUnit by animateFloatAsState(b.unitTotal.toFloat(), tween(600), label = "unit")
                val shownTotal by animateFloatAsState(b.total.toFloat(), tween(600), label = "total")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(if (b.quantity > 1) "За 1 шт" else "Итого", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(Money.format(shownUnit.toDouble(), b.currency), fontSize = if (b.quantity > 1) 18.sp else 26.sp, fontWeight = FontWeight.Bold, color = Accent)
                }
                if (b.quantity > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Итого за ${b.quantity} шт", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(Money.format(shownTotal.toDouble(), b.currency), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent)
                }
                Text("Профили, уплотнители и комплектующие — по артикулам прайса. Стеклопакеты, фурнитура и работа — по ставкам из «Настроек». Точную цену подтверждает замер.",
                    fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            }
        }

        Appear(6) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val f = Pdf.offer(ctx, spec, sel, b, c, vm.company, vm.drawing, vm.render)
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(i, "Отправить КП"))
                }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.size(8.dp)); Text("Создать и отправить КП (PDF)")
                }
                FilledTonalButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vm.summary())
                    ctx.startActivity(Intent.createChooser(i, "Поделиться расчётом"))
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.Share, null); Spacer(Modifier.size(8.dp)); Text("Поделиться текстом")
                }
                OutlinedButton(
                    onClick = { vm.saveToHistory(); saved = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    enabled = !saved, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                ) {
                    Icon(if (saved) Icons.Filled.CheckCircle else Icons.Filled.Bookmark, null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (saved) "Сохранено в историю" else "Сохранить в историю")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { (si, ci) -> CellEditorSheet(vm, si, ci) { editing = null } }
    picking?.let { which ->
        ColorPickerDialog(
            catalog = c,
            selectedId = if (which == "out") sel.colorId else sel.colorInsideId,
            allowSame = which == "in",
            onPick = { id -> vm.updateSelection { if (which == "out") it.copy(colorId = id) else it.copy(colorInsideId = id) }; picking = null },
            onDismiss = { picking = null },
        )
    }
}

fun cellSummary(c: Cell): String {
    val parts = mutableListOf(if (c.door) "Дверь, " + c.opening.title.lowercase() else c.opening.title)
    if (c.hinge != Hinge.NONE) parts += c.hinge.title.lowercase()
    if (c.opening != Opening.FIXED) parts += if (c.inward) "внутрь" else "наружу"
    return parts.joinToString(", ")
}

@Composable
fun Line2(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(title, Modifier.weight(1f))
        Spacer(Modifier.size(12.dp))
        Text(value, color = Color.Gray, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ColorRow(title: String, color: ColorItem?, empty: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        if (color != null) {
            Swatch(color.hex, Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(color.code, color = Accent, fontWeight = FontWeight.Medium)
        } else Text(empty, color = Accent)
        Text(" ›", color = Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CellEditorSheet(vm: AppViewModel, section: Int, index: Int, onDone: () -> Unit) {
    val s = vm.spec.layout.sections.getOrNull(section)
    val cell = s?.cells?.getOrNull(index)
    if (s == null || cell == null) { onDone(); return }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cols = vm.spec.layout.split == Split.COLUMNS
    ModalBottomSheet(onDismissRequest = onDone, sheetState = state) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${vm.spec.layout.split.sectionName} ${section + 1}, ячейка ${index + 1}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Открывание", color = Color.Gray, fontSize = 13.sp)
            Opening.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { o ->
                        FilterChip(selected = cell.opening == o, onClick = { vm.updateCell(section, index) { it.copy(opening = o) } },
                            label = { Text(o.title, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (cell.opening.hinges.size > 1) {
                Text("Петли", color = Color.Gray, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cell.opening.hinges.forEach { h ->
                        FilterChip(selected = cell.hinge == h, onClick = { vm.updateCell(section, index) { it.copy(hinge = h) } },
                            label = { Text(h.title, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (cell.opening != Opening.FIXED) {
                SwitchRow("Дверная створка", cell.door) { on -> vm.updateCell(section, index) { it.copy(door = on) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = cell.inward, onClick = { vm.updateCell(section, index) { it.copy(inward = true) } }, label = { Text("Внутрь") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = !cell.inward, onClick = { vm.updateCell(section, index) { it.copy(inward = false) } }, label = { Text("Наружу") }, modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider()
            if (s.cells.size > 1) {
                NumberField(if (cols) "Высота ячейки, мм" else "Ширина ячейки, мм", cell.sizeMm, Modifier.fillMaxWidth()) { v -> vm.setCellSize(section, index, v) }
            } else {
                Line2(if (cols) "Высота" else "Ширина", "${cell.sizeMm} мм")
            }
            Line2(if (cols) "Ширина колонки" else "Высота ряда", "${s.sizeMm} мм")
            TextButton(onClick = { vm.splitCell(section, index) }) { Text("Разделить ячейку пополам") }
            if (s.cells.size > 1) TextButton(onClick = { vm.removeCell(section, index); onDone() }) { Text("Убрать деление (объединить)", color = Color(0xFFDC2626)) }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Готово") }
        }
    }
}

@Composable
fun ColorPickerDialog(catalog: Catalog, selectedId: String, allowSame: Boolean, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF6F7F9)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Text("Цвет", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
                OutlinedTextField(query, { query = it }, placeholder = { Text("RAL 7024, графит, дуб…") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    if (allowSame) item {
                        Row(Modifier.fillMaxWidth().clickable { onPick("") }.padding(vertical = 12.dp)) {
                            Text("Как снаружи", Modifier.weight(1f), fontWeight = if (selectedId.isEmpty()) FontWeight.Bold else FontWeight.Normal)
                            if (selectedId.isEmpty()) Text("✓", color = Accent)
                        }
                    }
                    catalog.colorGroups.forEach { g ->
                        val list = catalog.colors.filter { it.group == g.id && (query.isBlank() || it.title.lowercase().contains(query.trim().lowercase())) }
                        if (list.isNotEmpty()) {
                            item {
                                Text(if (g.surchargePercent > 0) "${g.name} · +${g.surchargePercent.toInt()}%" else g.name,
                                    fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                            }
                            items(list, key = { it.id }) { col ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(col.id) }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Swatch(col.hex, Modifier.size(36.dp))
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(col.code, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Default)
                                        Text(col.name, fontSize = 12.sp, color = Color.Gray)
                                    }
                                    if (col.id == selectedId) Text("✓", color = Accent, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


package kz.okno.proyom

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.Window
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider

/** Экран расчёта — тот же порядок блоков, что на iPhone (Form с группами). */
@Composable
fun ResultView(vm: AppViewModel) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val p = pal()
    val c = vm.catalog
    val spec = vm.spec
    val sel = vm.selection
    val o = vm.offer
    val b = o.items.getOrNull(vm.current)
    val system = c.system(sel.systemId)
    val outside = c.color(sel.colorId)
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }   // "out" | "in"
    var expanded by remember { mutableStateOf(setOf<LineGroup>()) }
    var notesOpen by remember { mutableStateOf(false) }
    var dim by remember { mutableStateOf<SchemeDimension?>(null) }
    var materials by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    LaunchedEffect(vm.current) { editing = null }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Окна КП
        Group { PositionsStrip(vm, o) }

        // Схема / чертёж / ИИ-фото
        Group {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(listOf("Схема", "Чертёж", "ИИ-фото"), tab) { tab = it }
                Crossfade(targetState = tab, label = "img") { t ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (t) {
                            0 -> {
                                SchemeView(spec, outside?.hex ?: "#45494E", editing?.let { "${it.first}-${it.second}" },
                                    onDimension = { d -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); dim = d }) { r ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    editing = r.section to r.index
                                }
                                Text("Голубые размеры можно менять нажатием. Нажмите на ячейку, чтобы выбрать открывание.",
                                    fontSize = 12.sp, color = p.secondary, textAlign = TextAlign.Center)
                            }
                            1 -> {
                                val d = vm.drawing
                                if (d != null) Image(d.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
                                else Placeholder("Чертёж не загружен (ручной ввод)")
                            }
                            else -> {
                                Segmented(RenderStyle.entries.map { it.title }, vm.renderStyle.ordinal) { vm.renderStyle = RenderStyle.entries[it] }
                                val r = vm.render
                                when {
                                    vm.isRendering -> Shimmer("Рисую окно по схеме…", Modifier.fillMaxWidth().height(240.dp))
                                    r != null -> {
                                        AnimatedVisibility(visible = true, enter = fadeIn(tween(500)) + scaleIn(initialScale = 0.96f)) {
                                            Image(r.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
                                        }
                                        Text("Сгенерировано ИИ по схеме окна", fontSize = 12.sp, color = p.secondary)
                                    }
                                    else -> Placeholder(vm.renderError ?: "Фото ещё не создано")
                                }
                                if (!vm.isRendering) BorderedButton(if (r == null) "Сгенерировать по схеме" else "Сгенерировать заново", Icons.Filled.AutoAwesome, onClick = vm::renderPhoto)
                            }
                        }
                    }
                }
            }
        }

        // Размеры — сразу под схемой
        Group("Размеры") {
            NumberRow("Ширина, мм", spec.widthMm, min = 100) { v -> vm.setSize(width = v) }
            Sep()
            NumberRow("Высота, мм", spec.heightMm, min = 100) { v -> vm.setSize(height = v) }
            if (spec.layout.sections.size > 1) spec.layout.sections.forEachIndexed { si, s ->
                Sep()
                NumberRow("${spec.layout.split.sectionName} ${si + 1}, мм", s.sizeMm, min = 100) { v -> vm.setSectionSize(si, v) }
            }
            if (spec.altWidths.isNotEmpty() || spec.altHeights.isNotEmpty()) {
                Sep()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.HelpOutline, null, tint = Warn, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ИИ сомневается. Проверьте по чертежу:", color = Warn, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        spec.altWidths.forEach { w -> AltChip("Ширина $w") { vm.setSize(width = w); vm.updateSpec { it.copy(altWidths = emptyList()) } } }
                        spec.altHeights.forEach { h -> AltChip("Высота $h") { vm.setSize(height = h); vm.updateSpec { it.copy(altHeights = emptyList()) } } }
                    }
                }
            }
            Sep()
            ValueRow("Площадь", "%.2f м²".format(spec.areaM2))
            Sep()
            StepperRow("Количество", "${spec.quantity} шт",
                onMinus = { vm.updateSpec { it.copy(quantity = maxOf(1, it.quantity - 1)) } },
                onPlus = { vm.updateSpec { it.copy(quantity = minOf(999, it.quantity + 1)) } })
        }

        if (spec.confidence < 0.6 && vm.drawing != null) Group {
            WarnRow(Icons.Filled.Warning, "ИИ не уверен в распознавании — сверьте схему с чертежом.")
        }
        if (b != null && b.warnings.isNotEmpty()) Group {
            b.warnings.forEachIndexed { i, w -> if (i > 0) Sep(); WarnRow(Icons.Filled.ErrorOutline, w) }
            if (spec.layoutMismatch != null) { Sep(); ButtonRow("Подогнать сетку под размеры") { vm.updateSpec { it.fitted() } } }
        }
        // Нестандартные размеры: предупреждение и рекомендация
        if (b != null && b.limits.isNotEmpty()) Group("Нестандарт", footer = "По умолчанию считается стандартными профилями. Нестандартные размеры рассматриваются индивидуально.") {
            b.limits.forEachIndexed { i, w ->
                if (i > 0) Sep()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Straighten, null, tint = Warn, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(w.text, color = Warn, fontSize = 13.sp, lineHeight = 17.sp)
                    }
                    if (w.code.isNotEmpty()) Text(
                        if (w.applied) "Применено: ${w.code}. Вернуть стандартный профиль" else "Рекомендация: ${w.rec} (${w.code}) — применить",
                        color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.applyRecommendation(w) }.padding(vertical = 4.dp),
                    )
                }
            }
        }

        // С чертежа
        if (spec.info.isNotEmpty() || spec.notes.isNotBlank()) Group("С чертежа") {
            spec.info.forEachIndexed { i, item -> if (i > 0) Sep(); ValueRow(item.label, item.value) }
            if (spec.notes.isNotBlank()) {
                if (spec.info.isNotEmpty()) Sep()
                RowBase(onClick = { notesOpen = !notesOpen }) {
                    Text("Весь текст", color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(if (notesOpen) "⌄" else "›", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                AnimatedVisibility(notesOpen) {
                    Text(spec.notes, color = p.text, fontSize = 16.sp, lineHeight = 21.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
                }
            }
        }

        // Изделие
        Group("Изделие") {
            TextRow("ОК-1", vm.mark, label = "Марка") { v -> vm.mark = v }
            Sep()
            PickerRow("Изделие", spec.product.title, Product.entries, { it.title }) { pr -> vm.updateSpec { it.copy(product = pr) } }
            Sep()
            PickerRow("Система", system?.name ?: "—", c.systems, { it.name }) { s ->
                vm.updateSelection { it.copy(systemId = s.id, fillMm = if (it.fillMm in s.fillMin..s.fillMax) it.fillMm else s.fillDefault, overrides = emptyList()) }
            }
            system?.let { Sep(); NoteRow(it.description) }
        }

        // Заполнение, цвет, монтаж
        Group(if (vm.options.scope == OfferScope.TURNKEY) "Заполнение, цвет, монтаж" else "Заполнение и цвет") {
            val fills = (c.glass.map { it.fillMm }.filter { f -> system == null || f in system.fillMin..system.fillMax } +
                listOfNotNull(system?.fillDefault, sel.fillMm)).distinct().sorted()
            PickerRow("Заполнение", "${sel.fillMm} мм", fills, { "$it мм" }) { f -> vm.updateSelection { it.copy(fillMm = f) } }
            c.glassItem(sel.fillMm)?.let { Sep(); ValueRow("Стеклопакет", it.name) }
            Sep()
            TextRow("4-10-4-10-4", spec.glass, label = "Формула") { v -> vm.updateSpec { it.copy(glass = v) } }
            Sep()
            ColorRow("Цвет снаружи", outside, "—") { picking = "out" }
            Sep()
            ColorRow("Цвет внутри", if (sel.colorInsideId.isEmpty()) null else c.color(sel.colorInsideId), "Как снаружи") { picking = "in" }
            if (vm.options.scope == OfferScope.TURNKEY) {
                Sep()
                ToggleRow("Монтаж", sel.installation) { on -> vm.updateSelection { it.copy(installation = on) } }
            }
        }

        // Сетка и створки
        Group("Сетка и створки", footer = "Колонки — деление сверху донизу, ряды — слева направо. Ячейка делит только свою секцию.") {
            val cols = spec.layout.split == Split.COLUMNS
            PickerRow("Деление", spec.layout.split.title, Split.entries, { it.title }) { s -> vm.setSplit(s) }
            spec.layout.sections.forEachIndexed { si, s ->
                Sep()
                Column(Modifier.animateContentSize().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${spec.layout.split.sectionName} ${si + 1}", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${s.sizeMm} мм", color = p.secondary, fontSize = 17.sp)
                    }
                    s.cells.forEachIndexed { ci, cell ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editing = si to ci }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(cellIcon(cell), null, tint = Accent, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Ячейка ${ci + 1} · ${cell.sizeMm} мм", color = p.text, fontSize = 17.sp)
                                Text(cellSummary(cell), fontSize = 12.sp, color = p.secondary)
                            }
                            Text("›", color = p.tertiary, fontSize = 22.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.splitCell(si, s.cells.lastIndex) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.VerticalSplit, null, tint = Accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (cols) "Разделить по высоте" else "Разделить по ширине", color = Accent, fontSize = 15.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        if (spec.layout.sections.size > 1) Icon(Icons.Outlined.Delete, "Удалить", tint = Danger,
                            modifier = Modifier.clip(CircleShape).clickable { vm.removeSection(si) }.padding(6.dp).size(20.dp))
                    }
                }
            }
            Sep()
            ButtonRow("Добавить ${if (cols) "колонку" else "ряд"}", icon = { Icon(Icons.Outlined.AddCircleOutline, null, tint = Accent) }, onClick = vm::addSection)
        }

        // Стоимость окна
        if (b != null) Group("Стоимость окна ${vm.mark}",
            footer = if (vm.options.scope == OfferScope.MATERIALS) "Профили, комплектующие и уплотнители по прайсу ${c.name} (цены с НДС)."
                     else "Материалы по прайсу ${c.name}, стеклопакеты, фурнитура и работа — по ставкам из «Настроек».") {
            var first = true
            LineGroup.entries.forEach { g ->
                val lines = b.lines.filter { it.group == g }
                if (lines.isNotEmpty()) {
                    if (!first) Sep()
                    first = false
                    val open = g in expanded
                    RowBase(onClick = { expanded = if (open) expanded - g else expanded + g }) {
                        Text(g.title, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text(Money.format(b.sum(g), b.currency), color = p.secondary, fontSize = 17.sp)
                        Text(if (open) "  ⌄" else "  ›", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    AnimatedVisibility(open) {
                        Column {
                            lines.forEach { l ->
                                Sep(32.dp)
                                Row(Modifier.padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(l.title, fontSize = 15.sp, color = p.text)
                                        Text((if (l.code.isEmpty()) "" else l.code + " · ") + l.detail, fontSize = 11.sp, color = p.secondary)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(Money.format(l.amount, b.currency), fontSize = 15.sp, color = p.text)
                                }
                            }
                        }
                    }
                }
            }
            if (b.markup > 0) { Sep(); ValueRow("Наценка ${c.markupPercent.toInt()}%", Money.format(b.markup, b.currency)) }
            val shownUnit by animateFloatAsState(b.unitTotal.toFloat(), tween(450), label = "unit")
            Sep()
            TotalRow("За 1 шт", Money.format(shownUnit.toDouble(), b.currency), big = false)
            if (b.quantity > 1) { Sep(); ValueRow("За ${b.quantity} шт", Money.format(b.total, b.currency)) }
        }

        // Коммерческое предложение
        Group("Коммерческое предложение · окон: ${vm.positions.size}",
            footer = if (vm.options.scope == OfferScope.MATERIALS) "Как в КП BKM Aluminium: профиль, комплектующие, уплотнители. Не входят сборка, монтаж, стеклопакеты и фурнитура."
                     else "Под ключ: материалы, стеклопакеты, фурнитура, изготовление и монтаж (если отмечен) с наценкой.") {
            TextRow("Название объекта", vm.options.objectName, label = "Объект") { v -> vm.updateOptions { it.copy(objectName = v) } }
            Sep()
            Segmented(OfferScope.entries.map { it.title }, vm.options.scope.ordinal, Modifier.padding(12.dp)) { i ->
                vm.updateOptions { it.copy(scope = OfferScope.entries[i]) }
            }
            Sep()
            ToggleRow("Раскрой профилей (целые хлысты)", vm.options.cutting) { on -> vm.updateOptions { it.copy(cutting = on) } }
            Sep()
            ToggleRow("Уплотнители кратно ${c.gasketStep.toInt()} м", vm.options.gasketRound) { on -> vm.updateOptions { it.copy(gasketRound = on) } }
            Sep()
            RateRow("Скидка", vm.options.discountPercent, "%") { v -> vm.updateOptions { it.copy(discountPercent = v.coerceIn(0.0, 100.0)) } }
            vm.positions.forEachIndexed { i, pos ->
                Sep()
                RowBase(onClick = { vm.select(i) }) {
                    Icon(if (i == vm.current) Icons.Filled.Edit else Icons.Outlined.Circle, null, tint = Accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${pos.mark} · ${pos.spec.product.title} ${pos.spec.widthMm}×${pos.spec.heightMm}", color = p.text, fontSize = 16.sp)
                        Text("${pos.spec.quantity} шт × ${Money.format(o.items[i].unitTotal, o.currency)}", color = p.secondary, fontSize = 12.sp)
                    }
                    Text(Money.format(o.items[i].total, o.currency), color = p.text, fontSize = 16.sp)
                }
            }
            Sep(); ValueRow("Площадь", "%.2f м²".format(o.areaM2))
            if (o.discount > 0) { Sep(); ValueRow("Скидка", "−" + Money.format(o.discount, o.currency)) }
            val shownTotal by animateFloatAsState(o.total.toFloat(), tween(450), label = "total")
            Sep(); TotalRow("Итого по КП", Money.format(shownTotal.toDouble(), o.currency), big = true)
            Sep(); ValueRow("В т. ч. НДС ${c.vatPercent.toInt()}%", Money.format(o.vatIncluded, o.currency))
            Sep(); NavRow("Спецификация материалов", leading = { Icon(Icons.Outlined.ListAlt, null, tint = Accent) }) { materials = true }
        }

        // Действия
        Group {
            ButtonRow("Создать и отправить КП (PDF)", icon = { Icon(Icons.Outlined.Description, null, tint = Accent) }) {
                val f = Pdf.offer(ctx, vm.positions, vm.options, o, c, vm.company, vm.drawings, vm.renders)
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(i, "Отправить КП"))
            }
            Sep(52.dp)
            ButtonRow("Поделиться текстом КП", icon = { Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Accent) }) {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vm.summary())
                ctx.startActivity(Intent.createChooser(i, "Поделиться КП"))
            }
            Sep(52.dp)
            ButtonRow(if (vm.isSaved) "Сохранено" else "Сохранить в историю", enabled = !vm.isSaved,
                icon = { Icon(if (vm.isSaved) Icons.Filled.CheckCircle else Icons.Filled.MoveToInbox, null, tint = if (vm.isSaved) p.secondary else Accent) }) {
                vm.saveToHistory(); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            if (vm.positions.size > 1) {
                Sep(52.dp)
                ButtonRow("Убрать окно ${vm.mark} из КП", color = Danger, icon = { Icon(Icons.Outlined.Delete, null, tint = Danger) }) { confirmRemove = true }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    editing?.let { (si, ci) -> CellEditorSheet(vm, si, ci) { editing = null } }
    picking?.let { which ->
        ColorPickerScreen(
            title = if (which == "out") "Цвет снаружи" else "Цвет внутри",
            catalog = c,
            selectedId = if (which == "out") sel.colorId else sel.colorInsideId,
            allowSame = which == "in",
            onPick = { id -> vm.updateSelection { if (which == "out") it.copy(colorId = id) else it.copy(colorInsideId = id) }; picking = null },
            onDismiss = { picking = null },
        )
    }
    dim?.let { d -> NumberAlert(d.title, d.value, onDismiss = { dim = null }) { v -> vm.applyDimension(d, v) } }
    if (materials) MaterialsScreen(o, vm.options, c) { materials = false }
    if (confirmRemove) IosAlert("Убрать окно ${vm.mark} из КП?", null, { confirmRemove = false },
        listOf(AlertButton("Отмена", cancel = true) {}, AlertButton("Убрать", destructive = true) { vm.removePosition(vm.current) }))
}

/** Лента окон КП: нажатие — редактировать окно, удержание — копия / убрать. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PositionsStrip(vm: AppViewModel, o: OfferBreakdown) {
    val p = pal()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var menuFor by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(vm.current, vm.positions.size) { if (vm.current in vm.positions.indices) listState.animateScrollToItem(vm.current) }
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.lazy.LazyRow(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.positions.size) { i ->
                val pos = vm.positions[i]
                val on = i == vm.current
                Box {
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) Accent else p.fill)
                            .combinedClickable(onClick = { vm.select(i) }, onLongClick = { menuFor = i })
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        val fg = if (on) Color.White else p.text
                        Text(pos.mark, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${pos.spec.widthMm}×${pos.spec.heightMm}" + if (pos.spec.quantity > 1) " · ${pos.spec.quantity} шт" else "", color = fg, fontSize = 11.sp)
                        o.items.getOrNull(i)?.let { Text(Money.format(it.total, o.currency), color = fg, fontSize = 11.sp) }
                    }
                    DropdownMenu(expanded = menuFor == i, onDismissRequest = { menuFor = null }, modifier = Modifier.background(p.card)) {
                        DropdownMenuItem(text = { Text("Копия окна", color = p.text) }, onClick = { menuFor = null; vm.select(i); vm.duplicateCurrent() })
                        if (vm.positions.size > 1) DropdownMenuItem(text = { Text("Убрать из КП", color = Danger) }, onClick = { menuFor = null; vm.removePosition(i) })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Окон в КП: ${vm.positions.sumOf { it.spec.quantity }}", color = p.secondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("Итого: ${Money.format(o.total, o.currency)}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Спецификация материалов по всему КП: профили хлыстами, комплектующие, уплотнители. */
@Composable
private fun MaterialsScreen(o: OfferBreakdown, opt: OfferOptions, c: Catalog, onDismiss: () -> Unit) {
    val p = pal()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
            TopBar("Спецификация", leading = {
                Text("‹ Расчёт", color = Accent, fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(horizontal = 8.dp, vertical = 6.dp))
            })
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                for ((g, title) in listOf(LineGroup.PROFILE to "Профили", LineGroup.ACCESSORY to "Комплектующие", LineGroup.GASKET to "Уплотнители")) {
                    val list = o.materials.filter { it.group == g }
                    if (list.isEmpty()) continue
                    Group(title, footer = "Итого: ${Money.format(list.sumOf { it.amount }, o.currency)}") {
                        list.forEachIndexed { i, m ->
                            if (i > 0) Sep()
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row {
                                    Text(m.code, color = Accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                    Text(Money.format(m.amount, o.currency), color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                }
                                val col = if (g == LineGroup.PROFILE) c.color(m.colorKey.substringBefore("/"))?.let { " · ${it.code}" } ?: "" else ""
                                Text(m.name + col, color = p.text, fontSize = 15.sp)
                                Text(materialDetail(m, o.currency), color = p.secondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (o.weightKg > 0) Group { ValueRow("Вес профилей", "≈ %.0f кг".format(o.weightKg)) }
            }
        }
    }
}

private fun materialDetail(m: MaterialLine, cur: String): String = when (m.group) {
    LineGroup.PROFILE -> if (m.bars != null && m.barMm != null)
        "Нужно %.2f м → %d × %s м = %.1f м, отход %.0f%%".format(m.need, m.bars, "%.1f".format(m.barMm / 1000), m.bought, m.wastePercent)
        else "Нужно %.2f м, с отходом %.2f м".format(m.need, m.bought)
    LineGroup.GASKET -> "Нужно %.2f м → к отпуску %.0f м".format(m.need, m.bought) + (m.packM?.let { " · в рулоне %.0f м".format(it) } ?: "")
    else -> "%.0f шт × %s".format(m.need, Money.format(m.price, cur))
}

fun cellSummary(c: Cell): String {
    val parts = mutableListOf(if (c.door) "Дверь, " + c.opening.title.lowercase() else c.opening.title)
    if (c.hinge != Hinge.NONE) parts += c.hinge.title.lowercase()
    if (c.opening != Opening.FIXED) parts += if (c.inward) "внутрь" else "наружу"
    return parts.joinToString(", ")
}

private fun cellIcon(c: Cell): ImageVector = when {
    c.door -> Icons.Outlined.DoorFront
    c.opening == Opening.FIXED -> Icons.Outlined.CropSquare
    else -> Icons.Outlined.Window
}

@Composable
private fun Placeholder(text: String) =
    Text(text, fontSize = 13.sp, color = pal().secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(top = 48.dp))

@Composable
private fun WarnRow(icon: ImageVector, text: String) = RowBase {
    Icon(icon, null, tint = Warn, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(10.dp))
    Text(text, color = Warn, fontSize = 13.sp, lineHeight = 17.sp)
}

@Composable
private fun TotalRow(title: String, value: String, big: Boolean) = RowBase {
    Text(title, color = pal().text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    Text(value, color = Accent, fontSize = if (big) 22.sp else 17.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun AltChip(text: String, onClick: () -> Unit) =
    Text(text, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Accent.copy(alpha = 0.15f)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp))

/** Кнопка .bordered из iOS: акцентный текст на светлой подложке. */
@Composable
fun BorderedButton(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Accent.copy(alpha = if (pal().dark) 0.25f else 0.13f)).clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = Accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ColorRow(title: String, color: ColorItem?, empty: String, onClick: () -> Unit) = RowBase(onClick = onClick) {
    Text(title, color = pal().text, fontSize = 17.sp, modifier = Modifier.weight(1f))
    if (color != null) {
        Swatch(color.hex, Modifier.size(18.dp).clip(CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(color.code, color = pal().secondary, fontSize = 17.sp)
    } else Text(empty, color = pal().secondary, fontSize = 17.sp)
    Text("  ›", color = pal().tertiary, fontSize = 22.sp)
}

/** Редактор ячейки — лист как на iPhone: заголовок, «Готово», группы. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CellEditorSheet(vm: AppViewModel, section: Int, index: Int, onDone: () -> Unit) {
    val s = vm.spec.layout.sections.getOrNull(section)
    val cell = s?.cells?.getOrNull(index)
    if (s == null || cell == null) { LaunchedEffect(Unit) { onDone() }; return }
    val p = pal()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cols = vm.spec.layout.split == Split.COLUMNS
    ModalBottomSheet(onDismissRequest = onDone, sheetState = state, containerColor = p.bg, dragHandle = null,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)) {
        Column(Modifier.fillMaxWidth().dismissKeyboard().imePadding()) {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.width(36.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.tertiary))
            }
            TopBar("${vm.spec.layout.split.sectionName} ${section + 1}, ячейка ${index + 1}", trailing = {
                Text("Готово", color = Accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDone).padding(horizontal = 8.dp, vertical = 6.dp))
            })
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                Group("Открывание") {
                    Opening.entries.forEachIndexed { i, o ->
                        if (i > 0) Sep()
                        RowBase(onClick = { vm.updateCell(section, index) { it.copy(opening = o) } }) {
                            Text(o.title, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            if (cell.opening == o) Text("✓", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (cell.opening.hinges.size > 1) Group("Петли") {
                    Segmented(cell.opening.hinges.map { it.title }, cell.opening.hinges.indexOf(cell.hinge).coerceAtLeast(0),
                        Modifier.padding(12.dp)) { i -> vm.updateCell(section, index) { it.copy(hinge = cell.opening.hinges[i]) } }
                }
                if (cell.opening != Opening.FIXED) Group {
                    ToggleRow("Дверная створка", cell.door) { on -> vm.updateCell(section, index) { it.copy(door = on) } }
                    Sep()
                    Segmented(listOf("Внутрь", "Наружу"), if (cell.inward) 0 else 1, Modifier.padding(12.dp)) { i ->
                        vm.updateCell(section, index) { it.copy(inward = i == 0) }
                    }
                }
                Group("Размер") {
                    if (s.cells.size > 1) NumberRow(if (cols) "Высота, мм" else "Ширина, мм", cell.sizeMm, min = 100) { v -> vm.setCellSize(section, index, v) }
                    else ValueRow(if (cols) "Высота" else "Ширина", "${cell.sizeMm} мм")
                    Sep()
                    ValueRow(if (cols) "Ширина колонки" else "Высота ряда", "${s.sizeMm} мм")
                }
                Group {
                    ButtonRow("Разделить ячейку пополам", icon = { Icon(Icons.Outlined.VerticalSplit, null, tint = Accent) }) { vm.splitCell(section, index) }
                    if (s.cells.size > 1) {
                        Sep(52.dp)
                        ButtonRow("Убрать деление (объединить)", color = Danger, icon = { Icon(Icons.Outlined.CropSquare, null, tint = Danger) }) {
                            vm.removeCell(section, index); onDone()
                        }
                    }
                }
            }
        }
    }
}

/** Выбор цвета — отдельный экран как на iPhone: поиск, группы RAL / анодирование / декор. */
@Composable
fun ColorPickerScreen(title: String, catalog: Catalog, selectedId: String, allowSame: Boolean, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val p = pal()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().imePadding().dismissKeyboard()) {
            TopBar(title, leading = {
                Text("‹ Назад", color = Accent, fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(horizontal = 8.dp, vertical = 6.dp))
            })
            SearchField(query, "RAL 7024, графит, дуб…") { query = it }
            val q = query.trim().lowercase()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                if (allowSame && q.isEmpty()) Group {
                    RowBase(onClick = { onPick("") }) {
                        Text("Как снаружи", color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        if (selectedId.isEmpty()) Text("✓", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                catalog.colorGroups.forEach { g ->
                    val list = catalog.colors.filter { it.group == g.id && (q.isEmpty() || it.code.lowercase().contains(q) || it.name.lowercase().contains(q)) }
                    if (list.isNotEmpty()) Group(if (g.surchargePercent > 0) "${g.name} · +${g.surchargePercent.toInt()}%" else g.name) {
                        list.forEachIndexed { i, col ->
                            if (i > 0) Sep(62.dp)
                            RowBase(onClick = { onPick(col.id) }) {
                                Swatch(col.hex, Modifier.size(34.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(col.code, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    Text(col.name, color = p.secondary, fontSize = 12.sp)
                                }
                                if (col.id == selectedId) Text("✓", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

package kz.okno.proyom

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun EstimateScreen(vm: AppViewModel) {
    AnimatedContent(
        targetState = vm.phase,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(tween(350)) + slideInVertically(tween(450)) { it / 10 }) togetherWith fadeOut(tween(200))
        },
        label = "phase",
    ) { phase ->
        when (phase) {
            Phase.Idle -> IdleView(vm)
            Phase.Analyzing -> AnalyzingView(vm)
            Phase.Ready -> ResultView(vm)
            is Phase.Failed -> FailedView(vm, phase.message)
        }
    }
}

@Composable
private fun IdleView(vm: AppViewModel) {
    val ctx = LocalContext.current
    val photoFile = remember { File(ctx.cacheDir, "drawing.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", photoFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) decodeImage(ctx, photoUri)?.let(vm::start)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { decodeImage(ctx, it) }?.let(vm::start)
    }
    val float = rememberInfiniteTransition(label = "float")
    val dy by float.animateFloat(-6f, 6f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy")

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Appear(0) {
            Image(
                painterResource(R.drawable.logo), contentDescription = null,
                modifier = Modifier.size(124.dp).graphicsLayer { translationY = dy }
                    .shadow(18.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)),
            )
        }
        Spacer(Modifier.height(22.dp))
        Appear(1) { Text("Проём", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Deep) }
        Appear(2) {
            Text(
                "Сфотографируйте чертёж окна — ИИ прочитает размеры, покажет готовый вид и посчитает цену.",
                textAlign = TextAlign.Center, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Appear(3) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { camera.launch(photoUri) },
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.size(8.dp)); Text("Снять чертёж", fontSize = 17.sp) }
                FilledTonalButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.size(8.dp)); Text("Выбрать из галереи") }
                TextButton(onClick = vm::manual, modifier = Modifier.fillMaxWidth()) { Text("Ввести параметры вручную") }
            }
        }
        vm.history.firstOrNull()?.let { last ->
            Appear(4) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color.White).clickable { vm.open(last) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.History, null, tint = Accent)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text("Последний расчёт", fontSize = 12.sp, color = Color.Gray)
                        Text("${last.spec.type.title} ${last.spec.widthMm}×${last.spec.heightMm}")
                    }
                    Text(Money.format(last.total, last.currency), fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private val steps = listOf("Читаю размеры…", "Сверяю цифры двумя моделями…", "Определяю створки…", "Подбираю позиции каталога…")

@Composable
private fun AnalyzingView(vm: AppViewModel) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (step < steps.lastIndex) { delay(1600); step++ } }
    val t = rememberInfiniteTransition(label = "scan")
    val scan by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Appear(0) {
            Box(
                Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(RoundedCornerShape(20.dp))
                    .border(2.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
            ) {
                vm.drawing?.let {
                    Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                }
                Canvas(Modifier.matchParentSize()) {
                    drawRect(Color.Black.copy(alpha = 0.12f))
                    val y = scan * size.height
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, Accent.copy(alpha = 0.5f), Color.Transparent), startY = y - 60f, endY = y + 60f),
                        topLeft = Offset(0f, y - 60f), size = androidx.compose.ui.geometry.Size(size.width, 120f),
                    )
                    drawLine(Accent, Offset(0f, y), Offset(size.width, y), strokeWidth = 4f)
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
            Spacer(Modifier.size(10.dp))
            AnimatedContent(
                targetState = steps[step],
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "step",
            ) { Text(it, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
        }
        Spacer(Modifier.height(14.dp))
        Dots(steps.size, step)
    }
}

@Composable
private fun FailedView(vm: AppViewModel, message: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Warning, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = vm::retry) { Text("Повторить") }
        TextButton(onClick = vm::manual) { Text("Ввести вручную") }
        TextButton(onClick = vm::reset) { Text("Назад") }
    }
}

@Composable
private fun ResultView(vm: AppViewModel) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val c = vm.catalog
    val spec = vm.spec
    val sel = vm.selection
    val b = vm.breakdown
    var tab by remember { mutableIntStateOf(if (vm.drawing == null) 1 else 0) }
    var saved by remember { mutableStateOf(false) }
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
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Чертёж") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Готовый вид") })
                }
                Spacer(Modifier.height(10.dp))
                Crossfade(targetState = tab, label = "img") { t ->
                    if (t == 0) {
                        val d = vm.drawing
                        if (d != null) Image(d.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                        else Text("Чертёж не загружен (ручной ввод)", color = Color.Gray, modifier = Modifier.padding(24.dp))
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val r = vm.render
                            when {
                                r != null -> {
                                    AnimatedVisibility(visible = true, enter = fadeIn(tween(500)) + scaleIn(initialScale = 0.95f)) {
                                        Image(r.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                                    }
                                    Text("Примерный вид, сгенерирован ИИ", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
                                }
                                vm.isRendering -> Shimmer("Рисую готовое окно…", Modifier.fillMaxWidth().height(240.dp))
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

        AnimatedVisibility(visible = spec.confidence < 0.6 && vm.drawing != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF4E0)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Warning, null, tint = Color(0xFFF59E0B))
                Spacer(Modifier.size(10.dp))
                Text("ИИ не уверен в распознавании — проверьте размеры и параметры.", fontSize = 14.sp)
            }
        }

        Appear(1) {
            SectionCard("Параметры изделия") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("Ширина, мм", spec.widthMm, Modifier.weight(1f)) { v -> vm.updateSpec { it.copy(widthMm = v) } }
                    NumberField("Высота, мм", spec.heightMm, Modifier.weight(1f)) { v -> vm.updateSpec { it.copy(heightMm = v) } }
                }
                AnimatedVisibility(visible = spec.altWidths.isNotEmpty() || spec.altHeights.isNotEmpty()) {
                    Column(Modifier.padding(top = 10.dp)) {
                        Text("ИИ сомневается. Проверьте по чертежу:", color = Color(0xFFD97706), fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            spec.altWidths.forEach { w ->
                                AssistChip(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    vm.updateSpec { it.copy(widthMm = w, altWidths = emptyList()) }
                                }, label = { Text("Ширина $w") }, colors = AssistChipDefaults.assistChipColors(labelColor = Accent))
                            }
                            spec.altHeights.forEach { h ->
                                AssistChip(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    vm.updateSpec { it.copy(heightMm = h, altHeights = emptyList()) }
                                }, label = { Text("Высота $h") }, colors = AssistChipDefaults.assistChipColors(labelColor = Accent))
                            }
                        }
                    }
                }
                Text("Площадь: %.2f м²".format(spec.areaM2), color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Choice("Вид", spec.type.title, WindowType.entries, { it.title }) { t -> vm.updateSpec { it.copy(type = t) } }
                Choice("Профиль", c.profiles.firstOrNull { it.id == sel.profileId }?.name ?: "—", c.profiles, { it.name }) { p -> vm.updateSelection { it.copy(profileId = p.id) } }
                Choice("Стеклопакет", c.glass.firstOrNull { it.id == sel.glassId }?.name ?: "—", c.glass, { it.name }) { g -> vm.updateSelection { it.copy(glassId = g.id) } }
                if (c.colors.isNotEmpty()) {
                    Choice("Цвет", c.colors.firstOrNull { it.id == sel.colorId }?.name ?: "—", c.colors, { it.name }) { col -> vm.updateSelection { it.copy(colorId = col.id) } }
                }
            }
        }

        Appear(2) {
            SectionCard("Створки (слева направо)") {
                spec.sashes.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Choice("Секция ${i + 1}" + (s.widthMm?.let { " · $it мм" } ?: ""), s.opening.title, Opening.entries, { it.title }) { o ->
                                vm.updateSpec { sp -> sp.copy(sashes = sp.sashes.mapIndexed { j, x -> if (j == i) x.copy(opening = o) else x }) }
                            }
                        }
                        if (spec.sashes.size > 1) IconButton(onClick = {
                            vm.updateSpec { sp -> sp.copy(sashes = sp.sashes.filterIndexed { j, _ -> j != i }) }
                        }) { Icon(Icons.Filled.Close, "Удалить", tint = Color.Gray) }
                    }
                }
                TextButton(onClick = { vm.updateSpec { it.copy(sashes = it.sashes + Sash(null, Opening.FIXED)) } }) {
                    Icon(Icons.Filled.AddCircle, null); Spacer(Modifier.size(6.dp)); Text("Добавить секцию")
                }
            }
        }

        if (c.extras.isNotEmpty()) Appear(3) {
            SectionCard("Дополнительно") {
                c.extras.forEach { e ->
                    SwitchRow(e.name, e.id in sel.extraIds) { on ->
                        vm.updateSelection { it.copy(extraIds = if (on) it.extraIds + e.id else it.extraIds - e.id) }
                    }
                }
                SwitchRow("Монтаж", sel.installation) { on -> vm.updateSelection { it.copy(installation = on) } }
            }
        }

        if (spec.notes.isNotBlank()) Appear(4) {
            SectionCard("Текст с чертежа") { Text(spec.notes, fontSize = 14.sp) }
        }

        Appear(5) {
            SectionCard("Ориентировочная стоимость") {
                b.lines.forEach { l ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.title)
                            Text(l.detail, fontSize = 12.sp, color = Color.Gray)
                        }
                        Text(Money.format(l.amount, b.currency))
                    }
                }
                if (b.markup > 0) PriceRow("Наценка", Money.format(b.markup, b.currency))
                if (b.vat > 0) PriceRow("НДС", Money.format(b.vat, b.currency))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                val shown by animateFloatAsState(b.total.toFloat(), tween(600), label = "total")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Итого", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(Money.format(shown.toDouble(), b.currency), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent)
                }
                Text("Каталог «${c.name}». Точную цену подтверждает замер.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            }
        }

        Appear(6) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val f = Pdf.offer(ctx, spec, b, c.name, vm.company, vm.drawing, vm.render)
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
                    AnimatedContent(targetState = saved, label = "saved") { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (s) Icons.Filled.CheckCircle else Icons.Filled.Bookmark, null)
                            Spacer(Modifier.size(8.dp))
                            Text(if (s) "Сохранено в историю" else "Сохранить в историю")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PriceRow(title: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(title, Modifier.weight(1f), color = Color.Gray)
        Text(value, color = Color.Gray)
    }
}

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
import androidx.compose.ui.graphics.vector.ImageVector
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
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = if (vm.phase == Phase.Idle) "Проём" else "Расчёт",
            leading = if (vm.phase == Phase.Idle) null else ({
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = vm::reset).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null, tint = Accent, modifier = Modifier.size(22.dp))
                    Text("Новый", color = Accent, fontSize = 17.sp)
                }
            }),
        )
        AnimatedContent(
            targetState = vm.phase,
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(tween(350)) + slideInVertically(tween(450)) { it / 10 }) togetherWith fadeOut(tween(200))
            },
            modifier = Modifier.weight(1f),
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
}

/** Кнопки как на iPhone: залитая (borderedProminent) и с лёгкой подложкой (bordered). */
@Composable
fun IosButton(text: String, icon: ImageVector? = null, prominent: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (prominent) Accent else Accent.copy(alpha = if (pal().dark) 0.25f else 0.13f)
    val fg = if (prominent) Color.White else Accent
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) bg else pal().fill)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) fg else pal().secondary, modifier = Modifier.size(20.dp)); Spacer(Modifier.size(8.dp)) }
        Text(text, color = if (enabled) fg else pal().secondary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IdleView(vm: AppViewModel) {
    val ctx = LocalContext.current
    val p = pal()
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

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Appear(0) {
            Image(
                painterResource(R.drawable.logo), contentDescription = null,
                modifier = Modifier.size(120.dp).graphicsLayer { translationY = dy }
                    .shadow(16.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)),
            )
        }
        Spacer(Modifier.height(20.dp))
        Appear(1) { Text("Сфотографируйте чертёж окна", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.text, textAlign = TextAlign.Center) }
        Appear(2) {
            Text(
                "ИИ прочитает размеры и параметры, покажет, как изделие будет выглядеть, и рассчитает ориентировочную цену.",
                textAlign = TextAlign.Center, color = p.secondary, fontSize = 17.sp, lineHeight = 22.sp,
                modifier = Modifier.padding(top = 10.dp, start = 8.dp, end = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Appear(3) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IosButton("Снять чертёж", Icons.Filled.CameraAlt) { camera.launch(photoUri) }
                IosButton("Выбрать из галереи", Icons.Filled.PhotoLibrary, prominent = false) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Text("Ввести параметры вручную", color = Accent, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = vm::manual).padding(8.dp))
            }
        }
        vm.history.firstOrNull()?.let { last ->
            Appear(4) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp))
                        .background(p.card).clickable { vm.open(last) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.History, null, tint = Accent)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text("Последний расчёт", fontSize = 12.sp, color = p.secondary)
                        Text("${last.spec.product.title} ${last.spec.widthMm}×${last.spec.heightMm}", color = p.text, fontSize = 16.sp)
                    }
                    Text(Money.format(last.total, last.currency), fontWeight = FontWeight.Bold, color = p.text, fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
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
            CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.5.dp)
            Spacer(Modifier.size(10.dp))
            AnimatedContent(
                targetState = steps[step],
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "step",
            ) { Text(it, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = pal().text) }
        }
        Spacer(Modifier.height(14.dp))
        Dots(steps.size, step)
    }
}

@Composable
private fun FailedView(vm: AppViewModel, message: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Warning, null, tint = Warn, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, textAlign = TextAlign.Center, color = pal().text, fontSize = 17.sp)
        Spacer(Modifier.height(20.dp))
        IosButton("Повторить", enabled = vm.drawing != null, onClick = vm::retry)
        Text("Ввести вручную", color = Accent, fontSize = 17.sp,
            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = vm::manual).padding(10.dp))
    }
}

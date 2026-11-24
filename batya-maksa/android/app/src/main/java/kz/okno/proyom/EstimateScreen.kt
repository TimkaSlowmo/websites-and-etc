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
                        Text("${last.spec.product.title} ${last.spec.widthMm}×${last.spec.heightMm}")
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


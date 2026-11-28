package kz.okno.proyom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Фото из камеры/галереи с учётом поворота EXIF и без гигантского размера. */
fun decodeImage(ctx: Context, uri: Uri): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        // Не больше 2400 px по большей стороне — как на iPhone: хватает ИИ и не забивает память
        val side = maxOf(info.size.width, info.size.height)
        if (side > 2400) {
            val k = 2400f / side
            decoder.setTargetSize(maxOf(1, (info.size.width * k).toInt()), maxOf(1, (info.size.height * k).toInt()))
        }
    }
}.getOrNull()

/** Плавное появление блока со сдвигом снизу (с задержкой по индексу). */
@Composable
fun Appear(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 70L); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(420), label = "appear")
    Box(Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 60f }) { content() }
}

/** Перелив, пока ИИ рисует фото. */
@Composable
fun Shimmer(text: String, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    val pulse by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(pal().fill)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, Accent.copy(alpha = 0.22f), Color.Transparent),
                    start = androidx.compose.ui.geometry.Offset(x * 800f, 0f),
                    end = androidx.compose.ui.geometry.Offset(x * 800f + 400f, 400f),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Accent, modifier = Modifier.size(32.dp).graphicsLayer { alpha = pulse; scaleX = pulse; scaleY = pulse })
            Text(text, color = pal().secondary, fontSize = 14.sp)
        }
    }
}

@Composable
fun Dots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val w by animateFloatAsState(if (i == current) 22f else 8f, label = "dot")
            Box(
                Modifier.width(w.dp).size(height = 8.dp, width = w.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (i <= current) Accent else pal().tertiary),
            )
        }
    }
}

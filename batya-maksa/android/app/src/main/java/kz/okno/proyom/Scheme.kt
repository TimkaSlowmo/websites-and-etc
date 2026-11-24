package kz.okno.proyom

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.max
import kotlin.math.min

/** Прямоугольник в пикселях. */
data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t
    val cx get() = (l + r) / 2
    val cy get() = (t + b) / 2
    fun inset(d: Float) = Box(l + d, t + d, r - d, b - d)
    fun contains(x: Float, y: Float) = x in l..r && y in t..b
}

/** Общая геометрия схемы: где рама, где каждая ячейка, какие линии открывания. */
class SchemeLayout(val spec: WindowSpec, width: Float, height: Float, val pad: Float = 40f) {
    val k: Float = min((width - pad * 2) / spec.widthMm, (height - pad * 2) / spec.heightMm)
    val frame: Box = run {
        val w = spec.widthMm * k
        val h = spec.heightMm * k
        Box((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
    }
    val profile: Float = max(4f, min(14f, frame.w / 28f))

    fun cellBox(r: CellRect) = Box(frame.l + (r.x * k).toFloat(), frame.t + (r.y * k).toFloat(),
        frame.l + ((r.x + r.w) * k).toFloat(), frame.t + ((r.y + r.h) * k).toFloat())

    fun glassBox(r: CellRect) = cellBox(r).inset(profile * if (r.cell.opening == Opening.FIXED) 0.55f else 1f)

    fun hit(x: Float, y: Float): CellRect? = spec.cellRects.firstOrNull { cellBox(it).contains(x, y) }

    /** Линии открывания: вершина — сторона ручки, петли напротив. Каждая линия — список точек. */
    fun openingLines(c: Cell, g: Box): List<List<Offset>> {
        val turn = if (c.hinge == Hinge.RIGHT) listOf(Offset(g.r, g.t), Offset(g.l, g.cy), Offset(g.r, g.b))
        else listOf(Offset(g.l, g.t), Offset(g.r, g.cy), Offset(g.l, g.b))
        val tilt = listOf(Offset(g.l, g.b), Offset(g.cx, g.t), Offset(g.r, g.b))
        val top = listOf(Offset(g.l, g.t), Offset(g.cx, g.b), Offset(g.r, g.t))
        return when (c.opening) {
            Opening.FIXED -> emptyList()
            Opening.TURN -> listOf(turn)
            Opening.TILT -> listOf(tilt)
            Opening.TILT_TURN -> listOf(turn, tilt)
            Opening.TOP_HUNG -> listOf(top)
            Opening.SLIDE -> {
                val x0 = g.l + g.w * 0.2f
                val x1 = g.r - g.w * 0.2f
                listOf(listOf(Offset(x0, g.cy), Offset(x1, g.cy)), listOf(Offset(x1 - 10, g.cy - 7), Offset(x1, g.cy), Offset(x1 - 10, g.cy + 7)))
            }
        }
    }

    /** Размерные линии: (начало, конец, текст, акцент). */
    fun dims(): List<Triple<Pair<Offset, Offset>, String, Boolean>> {
        val out = mutableListOf<Triple<Pair<Offset, Offset>, String, Boolean>>()
        out += Triple(Offset(frame.l, frame.t - 16) to Offset(frame.r, frame.t - 16), "${spec.widthMm}", false)
        out += Triple(Offset(frame.r + 16, frame.t) to Offset(frame.r + 16, frame.b), "${spec.heightMm}", false)
        if (spec.layout.sections.size > 1) {
            var a = 0.0
            for (s in spec.layout.sections) {
                if (spec.layout.split == Split.COLUMNS) {
                    val x0 = frame.l + (a * k).toFloat()
                    val x1 = frame.l + ((a + s.sizeMm) * k).toFloat()
                    out += Triple(Offset(x0, frame.b + 16) to Offset(x1, frame.b + 16), "${s.sizeMm}", true)
                } else {
                    val y0 = frame.t + (a * k).toFloat()
                    val y1 = frame.t + ((a + s.sizeMm) * k).toFloat()
                    out += Triple(Offset(frame.l - 16, y0) to Offset(frame.l - 16, y1), "${s.sizeMm}", true)
                }
                a += s.sizeMm
            }
        }
        return out
    }
}

fun parseHex(hex: String): Int = try { android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") } catch (e: Exception) { 0xFF45494E.toInt() }

private val GlassColor = Color(0xFFCDE6F7)

/** Отрисовка схемы на обычном Android Canvas (общая для экрана и PDF). */
fun drawScheme(canvas: android.graphics.Canvas, spec: WindowSpec, frameHex: String, width: Float, height: Float,
               selectedId: String? = null, density: Float = 1f) {
    val L = SchemeLayout(spec, width, height, pad = 40f * density)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = parseHex(frameHex)
    canvas.drawRect(L.frame.l, L.frame.t, L.frame.r, L.frame.b, p)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    for (r in spec.cellRects) {
        val cb = L.cellBox(r)
        val g = L.glassBox(r)
        p.style = Paint.Style.FILL
        p.color = GlassColor.toArgb()
        canvas.drawRect(g.l, g.t, g.r, g.b, p)
        p.color = 0x2EFFFFFF
        canvas.drawRect(g.l, g.t, g.l + g.w * 0.35f, g.b, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f * density
        p.color = Deep.copy(alpha = 0.85f).toArgb()
        p.pathEffect = if (r.cell.inward) null else android.graphics.DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
        for (line in L.openingLines(r.cell, g)) {
            for (i in 0 until line.size - 1) canvas.drawLine(line[i].x, line[i].y, line[i + 1].x, line[i + 1].y, p)
        }
        p.pathEffect = null
        if (r.cell.door) {
            p.style = Paint.Style.FILL
            p.color = parseHex(frameHex)
            val hx = if (r.cell.hinge == Hinge.RIGHT) g.l + 6 * density else g.r - 10 * density
            canvas.drawRoundRect(hx, g.cy - 14 * density, hx + 4 * density, g.cy + 14 * density, 2f, 2f, p)
        }
        if (r.id == selectedId) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f * density
            p.color = Accent.toArgb()
            canvas.drawRect(cb.l + 1.5f, cb.t + 1.5f, cb.r - 1.5f, cb.b - 1.5f, p)
        }
        val label = "${r.w.toInt()}×${r.h.toInt()}"
        text.textSize = max(8f, min(12f, cb.w / 7f / density)) * density
        val tw = text.measureText(label)
        if (tw < g.w - 4) {
            p.style = Paint.Style.FILL
            p.color = 0xD9FFFFFF.toInt()
            val bh = text.textSize + 4 * density
            canvas.drawRoundRect(g.cx - tw / 2 - 4 * density, g.b - bh - 6 * density, g.cx + tw / 2 + 4 * density, g.b - 6 * density, 4f, 4f, p)
            text.color = Deep.toArgb()
            canvas.drawText(label, g.cx, g.b - 6 * density - bh / 2 + text.textSize / 3, text)
        }
    }
    // Размеры
    val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f * density }
    val dimText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f * density; textAlign = Paint.Align.CENTER }
    for ((seg, label, accent) in L.dims()) {
        val (a, b) = seg
        val col = if (accent) Accent.toArgb() else 0xFF6B7280.toInt()
        dimPaint.color = col
        dimText.color = col
        canvas.drawLine(a.x, a.y, b.x, b.y, dimPaint)
        val vertical = kotlin.math.abs(a.x - b.x) < 1
        for (e in listOf(a, b)) {
            if (vertical) canvas.drawLine(e.x - 4 * density, e.y, e.x + 4 * density, e.y, dimPaint)
            else canvas.drawLine(e.x, e.y - 4 * density, e.x, e.y + 4 * density, dimPaint)
        }
        val mx = (a.x + b.x) / 2
        val my = (a.y + b.y) / 2
        val tw = dimText.measureText(label)
        val bg = Paint().apply { color = android.graphics.Color.WHITE }
        canvas.save()
        canvas.translate(mx, my)
        if (vertical) canvas.rotate(-90f)
        canvas.drawRect(-tw / 2 - 3 * density, -dimText.textSize / 2 - 1, tw / 2 + 3 * density, dimText.textSize / 2 + 1, bg)
        canvas.drawText(label, 0f, dimText.textSize / 3, dimText)
        canvas.restore()
    }
}

/** Схема на экране: нажатие по ячейке выбирает её. */
@Composable
fun SchemeView(spec: WindowSpec, frameHex: String, selectedId: String?, modifier: Modifier = Modifier, onTap: (CellRect) -> Unit) {
    val ratio = ((spec.widthMm + 300f) / (spec.heightMm + 300f)).coerceIn(0.35f, 2.5f)
    Canvas(
        modifier.fillMaxWidth().aspectRatio(ratio).pointerInput(spec) {
            detectTapGestures { pos ->
                SchemeLayout(spec, size.width.toFloat(), size.height.toFloat(), pad = 40f * density).hit(pos.x, pos.y)?.let(onTap)
            }
        },
    ) {
        drawIntoCanvas { drawScheme(it.nativeCanvas, spec, frameHex, size.width, size.height, selectedId, density) }
    }
}

/** Образец цвета. */
@Composable
fun Swatch(hex: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(Color(parseHex(hex)), cornerRadius = CornerRadius(8f, 8f))
        drawRoundRect(Color.Gray.copy(alpha = 0.4f), cornerRadius = CornerRadius(8f, 8f), style = Stroke(1.5f))
    }
}

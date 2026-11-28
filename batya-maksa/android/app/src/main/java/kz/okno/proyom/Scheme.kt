package kz.okno.proyom

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/** Размерная линия схемы: что меняет нажатие, где линия, подпись. */
class Dim(val dim: SchemeDimension, val a: Offset, val b: Offset, val text: String) {
    val vertical: Boolean get() = kotlin.math.abs(a.x - b.x) < 1
}

/** Подпись ячейки «ширина×высота»; dim != null — размер ячейки можно менять нажатием. */
class CellLabel(val rect: CellRect, val box: Box, val text: String, val textSize: Float, val dim: SchemeDimension?)

/** Общая геометрия схемы: где рама, где каждая ячейка, какие линии открывания. clean — без размеров (для ИИ-фото). */
class SchemeLayout(val spec: WindowSpec, width: Float, height: Float, val d: Float = 1f, clean: Boolean = false) {
    /** Поле под размерные линии с каждой стороны. */
    val pad: Float = (if (clean) 6f else 36f) * d
    val k: Float = min((width - pad * 2) / spec.widthMm, (height - pad * 2) / spec.heightMm)
    val frame: Box = run {
        val w = spec.widthMm * k
        val h = spec.heightMm * k
        Box((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
    }
    val profile: Float = max(3f * d, min(6f * d, frame.w / 28f))

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

    /** Размерные линии: общие (сверху и справа) и по секциям (снизу или слева). */
    fun dims(): List<Dim> {
        val o = 16f * d
        val out = mutableListOf(
            Dim(SchemeDimension(SchemeDimension.Kind.WIDTH, value = spec.widthMm, title = "Ширина изделия"),
                Offset(frame.l, frame.t - o), Offset(frame.r, frame.t - o), "${spec.widthMm}"),
            Dim(SchemeDimension(SchemeDimension.Kind.HEIGHT, value = spec.heightMm, title = "Высота изделия"),
                Offset(frame.r + o, frame.t), Offset(frame.r + o, frame.b), "${spec.heightMm}"),
        )
        if (spec.layout.sections.size > 1) {
            var a = 0.0
            val cols = spec.layout.split == Split.COLUMNS
            spec.layout.sections.forEachIndexed { i, s ->
                val dim = SchemeDimension(SchemeDimension.Kind.SECTION, i, 0, s.sizeMm,
                    "${spec.layout.split.sectionName} ${i + 1} — ${if (cols) "ширина" else "высота"}")
                if (cols) {
                    val x0 = frame.l + (a * k).toFloat()
                    val x1 = frame.l + ((a + s.sizeMm) * k).toFloat()
                    out += Dim(dim, Offset(x0, frame.b + o), Offset(x1, frame.b + o), "${s.sizeMm}")
                } else {
                    val y0 = frame.t + (a * k).toFloat()
                    val y1 = frame.t + ((a + s.sizeMm) * k).toFloat()
                    out += Dim(dim, Offset(frame.l - o, y0), Offset(frame.l - o, y1), "${s.sizeMm}")
                }
                a += s.sizeMm
            }
        }
        return out
    }

    /** Зона нажатия на подпись размера — не меньше 44×32 dp. */
    fun dimHit(dm: Dim): Box {
        val mx = (dm.a.x + dm.b.x) / 2
        val my = (dm.a.y + dm.b.y) / 2
        val along = max(44f * d, dm.text.length * 7f * d + 12f * d) / 2
        val across = 16f * d
        return if (dm.vertical) Box(mx - across, my - along, mx + across, my + along) else Box(mx - along, my - across, mx + along, my + across)
    }

    fun cellLabels(): List<CellLabel> = spec.cellRects.map { r ->
        val cb = cellBox(r)
        val g = glassBox(r)
        val text = "${r.w.toInt()}×${r.h.toInt()}"
        val ts = max(8f, min(12f, cb.w / 7f / d)) * d
        val w = text.length * ts * 0.62f + 8 * d
        val h = ts + 6 * d
        val box = Box(g.cx - w / 2, g.b - h - 4 * d, g.cx + w / 2, g.b - 4 * d)
        val many = spec.layout.sections[r.section].cells.size > 1
        val cols = spec.layout.split == Split.COLUMNS
        val dim = if (many) SchemeDimension(SchemeDimension.Kind.CELL, r.section, r.index, r.cell.sizeMm,
            "${spec.layout.split.sectionName} ${r.section + 1}, ячейка ${r.index + 1} — ${if (cols) "высота" else "ширина"}") else null
        CellLabel(r, box, text, ts, dim)
    }

    /** Что под пальцем: размер (если можно менять). */
    fun hitDimension(x: Float, y: Float): SchemeDimension? =
        dims().firstOrNull { dimHit(it).contains(x, y) }?.dim
            ?: cellLabels().firstOrNull { it.dim != null && it.box.inset(-8 * d).contains(x, y) }?.dim
}

fun parseHex(hex: String): Int = try { android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") } catch (e: Exception) { 0xFF45494E.toInt() }

private val GlassColor = Color(0xFFCDE6F7)

/**
 * Отрисовка схемы на обычном Android Canvas (общая для экрана, PDF и образца ИИ-фото).
 * editable — голубые размеры, которые можно нажать; clean — без размеров и значков открывания, с ручками.
 */
fun drawScheme(canvas: android.graphics.Canvas, spec: WindowSpec, frameHex: String, width: Float, height: Float,
               selectedId: String? = null, density: Float = 1f,
               labelBg: Int = android.graphics.Color.WHITE, dimColor: Int = 0xFF6B7280.toInt(),
               editable: Boolean = false, clean: Boolean = false) {
    val L = SchemeLayout(spec, width, height, density, clean)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val frameColor = parseHex(frameHex)
    p.color = frameColor
    canvas.drawRect(L.frame.l, L.frame.t, L.frame.r, L.frame.b, p)
    val accent = Accent.toArgb()
    for (r in spec.cellRects) {
        val cb = L.cellBox(r)
        val g = L.glassBox(r)
        p.style = Paint.Style.FILL
        p.color = GlassColor.toArgb()
        canvas.drawRect(g.l, g.t, g.r, g.b, p)
        p.color = 0x2EFFFFFF
        canvas.drawRect(g.l, g.t, g.l + g.w * 0.35f, g.b, p)
        if (r.cell.opening != Opening.FIXED) {
            // Контур створки
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f * density
            p.color = if (clean) 0x38FFFFFF else 0x59FFFFFF
            val half = L.profile * 0.5f
            canvas.drawRect(cb.l + half, cb.t + half, cb.r - half, cb.b - half, p)
        }
        if (clean) {
            // Ручка — со стороны, противоположной петлям
            if (r.cell.opening != Opening.FIXED) {
                p.style = Paint.Style.FILL
                p.color = frameColor
                val l = max(10f, min(g.h, g.w) * 0.12f)
                val t = max(2.5f, l * 0.22f)
                when (r.cell.hinge) {
                    Hinge.RIGHT -> canvas.drawRoundRect(g.l + t * 1.2f, g.cy - l / 2, g.l + t * 2.2f, g.cy + l / 2, t / 2, t / 2, p)
                    Hinge.BOTTOM -> canvas.drawRoundRect(g.cx - l / 2, g.t + t * 1.2f, g.cx + l / 2, g.t + t * 2.2f, t / 2, t / 2, p)
                    Hinge.TOP -> canvas.drawRoundRect(g.cx - l / 2, g.b - t * 2.2f, g.cx + l / 2, g.b - t * 1.2f, t / 2, t / 2, p)
                    else -> canvas.drawRoundRect(g.r - t * 2.2f, g.cy - l / 2, g.r - t * 1.2f, g.cy + l / 2, t / 2, t / 2, p)
                }
            }
            continue
        }
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
            p.color = frameColor
            val hx = if (r.cell.hinge == Hinge.RIGHT) g.l + 6 * density else g.r - 10 * density
            canvas.drawRoundRect(hx, g.cy - 14 * density, hx + 4 * density, g.cy + 14 * density, 2f, 2f, p)
        }
        if (r.id == selectedId) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f * density
            p.color = accent
            canvas.drawRect(cb.l + 1.5f, cb.t + 1.5f, cb.r - 1.5f, cb.b - 1.5f, p)
        }
    }
    if (clean) return
    // Подписи ячеек (голубые, если размер можно поменять)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    for (lb in L.cellLabels()) {
        val g = L.glassBox(lb.rect)
        if (lb.box.w >= g.w - 4) continue
        val tappable = editable && lb.dim != null
        p.style = Paint.Style.FILL
        p.color = 0xE0FFFFFF.toInt()
        canvas.drawRoundRect(lb.box.l, lb.box.t, lb.box.r, lb.box.b, 4 * density, 4 * density, p)
        if (tappable) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f * density
            p.color = accent
            canvas.drawRoundRect(lb.box.l, lb.box.t, lb.box.r, lb.box.b, 4 * density, 4 * density, p)
        }
        text.textSize = lb.textSize
        text.color = if (tappable) accent else Deep.toArgb()
        canvas.drawText(lb.text, lb.box.cx, lb.box.cy + lb.textSize / 3, text)
    }
    // Размеры
    val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f * density }
    val dimText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f * density; textAlign = Paint.Align.CENTER }
    for (dm in L.dims()) {
        val a = dm.a
        val b = dm.b
        val col = if (editable || dm.dim.kind == SchemeDimension.Kind.SECTION) accent else dimColor
        dimPaint.color = col
        dimText.color = col
        dimText.typeface = if (editable) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        canvas.drawLine(a.x, a.y, b.x, b.y, dimPaint)
        for (e in listOf(a, b)) {
            if (dm.vertical) canvas.drawLine(e.x - 4 * density, e.y, e.x + 4 * density, e.y, dimPaint)
            else canvas.drawLine(e.x, e.y - 4 * density, e.x, e.y + 4 * density, dimPaint)
        }
        val mx = (a.x + b.x) / 2
        val my = (a.y + b.y) / 2
        val tw = dimText.measureText(dm.text)
        canvas.save()
        canvas.translate(mx, my)
        if (dm.vertical) canvas.rotate(-90f)
        val l = -tw / 2 - 5 * density
        val t = -dimText.textSize / 2 - 2 * density
        val rr = tw / 2 + 5 * density
        val bb = dimText.textSize / 2 + 2 * density
        canvas.drawRoundRect(l, t, rr, bb, 5 * density, 5 * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = labelBg })
        if (editable) {
            canvas.drawRoundRect(l, t, rr, bb, 5 * density, 5 * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Accent.copy(alpha = 0.14f).toArgb() })
            canvas.drawRoundRect(l, t, rr, bb, 5 * density, 5 * density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1f * density; color = Accent.copy(alpha = 0.6f).toArgb()
            })
        }
        canvas.drawText(dm.text, 0f, dimText.textSize / 3, dimText)
        canvas.restore()
    }
}

/** Чистый фасад окна (без размеров и значков открывания) — образец для ИИ-фото, PNG до 512 px. */
fun cleanSchemePng(spec: WindowSpec, frameHex: String): ByteArray {
    val ratio = spec.widthMm.toFloat() / spec.heightMm.coerceAtLeast(1)
    val w = if (ratio >= 1) 512 else max(128, (512 * ratio).toInt())
    val h = if (ratio >= 1) max(128, (512 / ratio).toInt()) else 512
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    cv.drawColor(android.graphics.Color.WHITE)
    drawScheme(cv, spec, frameHex, w.toFloat(), h.toFloat(), clean = true)
    val out = java.io.ByteArrayOutputStream()
    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

/**
 * Схема на экране: вписывается и по ширине, и по высоте (не больше maxHeight), как на iPhone.
 * Нажатие по голубому размеру — изменить размер, по ячейке — выбрать её.
 */
@Composable
fun SchemeView(
    spec: WindowSpec, frameHex: String, selectedId: String?, maxHeight: Dp = 380.dp,
    onDimension: ((SchemeDimension) -> Unit)? = null, onTap: (CellRect) -> Unit,
) {
    val p = pal()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Пропорции изделия + поля под размеры; высота ограничена — узкое высокое окно сужается, а не вылезает
        val pad = 72.dp
        val ratio = (spec.widthMm.toFloat() / spec.heightMm.coerceAtLeast(1)).coerceIn(0.1f, 10f)
        val h = minOf((maxWidth - pad) / ratio + pad, maxHeight)
        val labelBg = p.card.toArgb()
        val dim = p.secondary.toArgb()
        val editable = onDimension != null
        Canvas(
            Modifier.fillMaxWidth().height(h).clipToBounds().pointerInput(spec, editable) {
                detectTapGestures { pos ->
                    val L = SchemeLayout(spec, size.width.toFloat(), size.height.toFloat(), density)
                    val d = if (onDimension != null) L.hitDimension(pos.x, pos.y) else null
                    if (d != null) onDimension?.invoke(d) else L.hit(pos.x, pos.y)?.let(onTap)
                }
            },
        ) {
            drawIntoCanvas { drawScheme(it.nativeCanvas, spec, frameHex, size.width, size.height, selectedId, density, labelBg, dim, editable) }
        }
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

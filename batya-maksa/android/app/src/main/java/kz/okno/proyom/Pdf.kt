package kz.okno.proyom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Коммерческое предложение в PDF (A4): схема, фото, параметры, данные с чертежа, стоимость по группам. */
object Pdf {
    fun offer(ctx: Context, spec: WindowSpec, sel: Selection, b: Breakdown, catalog: Catalog, company: String,
              drawing: Bitmap?, render: Bitmap?): File {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var c = page.canvas
        val left = 40f
        val right = 555f
        var y = 50f
        fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint().apply {
            textSize = size; this.color = color; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        fun ensure(h: Float) {
            if (y + h > 800) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); c = page.canvas; y = 50f
            }
        }
        fun line(text: String, p: Paint, gap: Float = 6f) { ensure(p.textSize); c.drawText(text, left, y, p); y += p.textSize + gap }
        fun row(title: String, value: String, p: Paint) {
            ensure(p.textSize)
            c.drawText(title, left, y, p)
            c.drawText(value, right - p.measureText(value), y, p)
            y += p.textSize + 5
        }
        val color = catalog.color(sel.colorId)

        if (company.isNotBlank()) line(company, paint(20f, true))
        line("Коммерческое предложение", paint(15f, color = Color.DKGRAY))
        line(SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date()), paint(10f, color = Color.GRAY))
        y += 6

        // Схема рисуется той же функцией, что и на экране.
        val scheme = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888).also {
            val cv = android.graphics.Canvas(it)
            cv.drawColor(Color.WHITE)
            drawScheme(cv, spec, color?.hex ?: "#45494E", 720f, 720f, null, 2f)
        }
        val imgs = listOfNotNull(scheme, render ?: drawing)
        val slot = (right - left - 12f * (imgs.size - 1)) / imgs.size
        val maxH = 230f
        imgs.forEachIndexed { i, img ->
            val r = minOf(slot / img.width, maxH / img.height)
            val x = left + i * (slot + 12f) + (slot - img.width * r) / 2
            c.drawBitmap(img, null, RectF(x, y, x + img.width * r, y + img.height * r), null)
        }
        y += maxH + 22

        val p10 = paint(10.5f)
        val p9 = paint(9.5f)
        line("Изделие", paint(13f, true))
        row("Тип", spec.product.title, p10)
        row("Система", catalog.system(sel.systemId)?.name ?: "—", p10)
        row("Размер", "${spec.widthMm} × ${spec.heightMm} мм (%.2f м²)".format(spec.areaM2), p10)
        row("Количество", "${spec.quantity} шт", p10)
        row("Заполнение", "${sel.fillMm} мм" + if (spec.glass.isBlank()) "" else " (${spec.glass})", p10)
        row("Цвет снаружи", color?.title ?: "—", p10)
        if (sel.colorInsideId.isNotEmpty()) catalog.color(sel.colorInsideId)?.let { row("Цвет внутри", it.title, p10) }
        spec.cellRects.forEach { r ->
            val cl = r.cell
            val what = (if (cl.door) "дверь, " else "") + cl.opening.title.lowercase() + if (cl.hinge != Hinge.NONE) ", " + cl.hinge.title.lowercase() else ""
            row("${spec.layout.split.sectionName} ${r.section + 1}, ячейка ${r.index + 1}", "${r.w.toInt()}×${r.h.toInt()} — $what", p9)
        }
        if (spec.info.isNotEmpty()) {
            y += 6
            line("С чертежа", paint(13f, true))
            spec.info.forEach { row(it.label, it.value, p9) }
        }
        y += 8
        line("Стоимость", paint(13f, true))
        LineGroup.entries.forEach { g -> val s = b.sum(g); if (s > 0) row(g.title, Money.format(s, b.currency), p10) }
        row("Наценка", Money.format(b.markup, b.currency), p10)
        row("НДС ${catalog.vatPercent.toInt()}%", Money.format(b.vat, b.currency), p10)
        y += 4
        if (b.quantity > 1) {
            row("Цена за 1 шт", Money.format(b.unitTotal, b.currency), paint(12f, true))
            row("Итого за ${b.quantity} шт", Money.format(b.total, b.currency), paint(15f, true))
        } else row("Итого", Money.format(b.total, b.currency), paint(15f, true))
        y += 12
        val note = paint(8.5f, color = Color.GRAY)
        line("Расчёт ориентировочный, по прайсу «${catalog.name}». Окончательная стоимость определяется после замера.", note, 2f)
        line("Изображение готового изделия создано ИИ и носит иллюстративный характер.", note)

        doc.finishPage(page)
        val file = File(ctx.cacheDir, "КП-${System.currentTimeMillis() / 1000}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}

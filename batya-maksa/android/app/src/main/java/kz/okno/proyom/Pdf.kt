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

/** Коммерческое предложение в PDF (A4). */
object Pdf {
    fun offer(ctx: Context, spec: WindowSpec, b: Breakdown, catalogName: String, company: String,
              drawing: Bitmap?, render: Bitmap?): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas
        val left = 40f
        val right = 555f
        var y = 50f
        fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint().apply {
            textSize = size; this.color = color; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        fun line(text: String, p: Paint, gap: Float = 6f) { c.drawText(text, left, y, p); y += p.textSize + gap }
        fun row(title: String, amount: String, p: Paint) {
            c.drawText(title, left, y, p)
            c.drawText(amount, right - p.measureText(amount), y, p)
            y += p.textSize + 6
        }

        if (company.isNotBlank()) line(company, paint(20f, true))
        line("Коммерческое предложение", paint(15f, color = Color.DKGRAY))
        line(SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date()), paint(11f, color = Color.GRAY))
        y += 8

        val imgs = listOfNotNull(drawing, render)
        if (imgs.isNotEmpty()) {
            val slot = (right - left - 12f * (imgs.size - 1)) / imgs.size
            val maxH = 200f
            imgs.forEachIndexed { i, img ->
                val r = minOf(slot / img.width, maxH / img.height)
                val x = left + i * (slot + 12f)
                c.drawBitmap(img, null, RectF(x, y, x + img.width * r, y + img.height * r), null)
            }
            y += maxH + 20
        }

        line("Параметры изделия", paint(13f, true))
        val p11 = paint(11f)
        line("Вид: ${spec.type.title}", p11)
        line("Размер: ${spec.widthMm} × ${spec.heightMm} мм (%.2f м²)".format(spec.areaM2), p11)
        line("Створки: " + spec.sashes.joinToString(", ") { it.opening.title }, p11)
        y += 8
        line("Стоимость", paint(13f, true))
        b.lines.forEach { row(it.title, Money.format(it.amount, b.currency), p11) }
        if (b.markup > 0) row("Наценка", Money.format(b.markup, b.currency), p11)
        if (b.vat > 0) row("НДС", Money.format(b.vat, b.currency), p11)
        y += 6
        row("Итого", Money.format(b.total, b.currency), paint(15f, true))
        y += 12
        val note = paint(9f, color = Color.GRAY)
        line("Расчёт ориентировочный (каталог «$catalogName»). Окончательная стоимость определяется после замера.", note, 2f)
        line("Изображение готового изделия создано ИИ и носит иллюстративный характер.", note)

        doc.finishPage(page)
        val file = File(ctx.cacheDir, "КП-${System.currentTimeMillis() / 1000}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}

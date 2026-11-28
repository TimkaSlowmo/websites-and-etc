package kz.okno.proyom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Коммерческое предложение в PDF (A4) по образцу КП BKM Aluminium — как на iPhone:
 * 1) таблица окон (схема, наименование, кол-во, м², цена за м², стоимость), итог, НДС, что входит/не входит, срок;
 * 2) спецификация материалов (профили хлыстами, комплектующие, уплотнители);
 * 3) окна по отдельности: схема, ИИ-фото, параметры, данные с чертежа.
 */
object Pdf {
    private const val LEFT = 40f
    private const val WIDTH = 515f
    private const val BOTTOM = 800f

    fun offer(ctx: Context, positions: List<Position>, opt: OfferOptions, o: OfferBreakdown, catalog: Catalog, company: String,
              drawings: Map<String, Bitmap>, renders: Map<String, Bitmap>): File {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var c = page.canvas
        var y = 40f
        val cur = o.currency
        fun money(v: Double) = Money.format(v, cur)
        val accent = 0xFF127A8C.toInt()

        fun newPage() {
            doc.finishPage(page); pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); c = page.canvas; y = 40f
        }
        fun ensure(h: Float) { if (y + h > BOTTOM) newPage() }
        fun tp(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = TextPaint().apply {
            textSize = size; this.color = color; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        fun layout(text: String, p: TextPaint, w: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) =
            StaticLayout.Builder.obtain(text, 0, text.length, p, w.toInt().coerceAtLeast(10)).setAlignment(align).build()
        fun drawAt(l: StaticLayout, x: Float, yy: Float) { c.save(); c.translate(x, yy); l.draw(c); c.restore() }
        fun text(s: String, p: TextPaint, gap: Float = 5f, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
            val l = layout(s, p, WIDTH, align)
            ensure(l.height.toFloat())
            drawAt(l, LEFT, y)
            y += l.height + gap
        }
        fun hline(yy: Float) { c.drawLine(LEFT, yy, LEFT + WIDTH, yy, Paint().apply { color = 0xFFD1D1D6.toInt(); strokeWidth = 0.6f }) }
        class Col(val x: Float, val w: Float, val align: Layout.Alignment)
        val R = Layout.Alignment.ALIGN_OPPOSITE
        val L0 = Layout.Alignment.ALIGN_NORMAL
        val C = Layout.Alignment.ALIGN_CENTER
        fun tableRow(cells: List<String>, cols: List<Col>, p: TextPaint, minH: Float = 0f, fill: Int? = null, image: Bitmap? = null, imageCol: Int = -1) {
            val layouts = cells.mapIndexed { i, s -> layout(s, p, cols[i].w, cols[i].align) }
            val h = maxOf(minH, layouts.maxOf { it.height.toFloat() }) + 8f
            ensure(h)
            if (fill != null) c.drawRect(LEFT, y, LEFT + WIDTH, y + h, Paint().apply { color = fill })
            layouts.forEachIndexed { i, l ->
                if (i == imageCol && image != null) {
                    val side = minOf(cols[i].w, h - 6f)
                    val k = minOf(side / image.width, side / image.height)
                    val w = image.width * k
                    val hh = image.height * k
                    c.drawBitmap(image, null, RectF(cols[i].x + (cols[i].w - w) / 2, y + (h - hh) / 2, cols[i].x + (cols[i].w + w) / 2, y + (h + hh) / 2), null)
                } else drawAt(l, cols[i].x, y + 4f)
            }
            y += h
            hline(y)
        }
        fun scheme(p: Position, side: Int, thumb: Boolean = false): Bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also {
            val cv = android.graphics.Canvas(it)
            cv.drawColor(Color.WHITE)
            drawScheme(cv, p.spec, catalog.color(p.selection.colorId)?.hex ?: "#45494E", side.toFloat(), side.toFloat(), null, side / 360f, thumb = thumb)
        }
        fun total(t: String, v: String, bold: Boolean = false, size: Float = 10.5f, color: Int = Color.BLACK) {
            val p = tp(size, bold, color)
            ensure(size + 6)
            drawAt(layout(t, p, 200f, R), LEFT + 200f, y)
            drawAt(layout(v, p, 110f, R), LEFT + 405f, y)
            y += size + 6f
        }

        // ---------- Страница 1: КП ----------
        if (company.isNotBlank()) text(company, tp(20f, true), 2f)
        text(if (opt.scope == OfferScope.MATERIALS) "Коммерческое предложение на поставку архитектурных систем алюминиевых профилей"
             else "Коммерческое предложение на изготовление оконно-дверных конструкций", tp(14f, color = Color.DKGRAY), 2f)
        if (opt.objectName.isNotBlank()) text("по объекту: ${opt.objectName}", tp(12f, true), 2f)
        text(SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date()), tp(10f, color = Color.GRAY))
        y += 6f
        val cols = listOf(Col(LEFT + 2, 18f, L0), Col(LEFT + 22, 62f, C), Col(LEFT + 88, 197f, L0), Col(LEFT + 288, 38f, R),
            Col(LEFT + 330, 50f, R), Col(LEFT + 384, 60f, R), Col(LEFT + 448, 65f, R))
        tableRow(listOf("№", "Схема", "Наименование", "Кол-во", "м²", "Цена 1 м²", "Стоимость"), cols, tp(8.5f, true, Color.DKGRAY), fill = 0xFFF2F2F2.toInt())
        positions.forEachIndexed { i, p ->
            val b = o.items[i]
            val sys = catalog.system(p.selection.systemId)?.name ?: ""
            val col = catalog.color(p.selection.colorId)
            var name = "${p.mark} · ${p.spec.product.title} ${p.spec.widthMm}×${p.spec.heightMm} мм\n$sys, ${col?.code ?: ""}"
            if (p.selection.colorInsideId.isNotEmpty()) catalog.color(p.selection.colorInsideId)?.let { name += " / внутри ${it.code}" }
            name += ", заполнение ${p.selection.fillMm} мм"
            val open = p.spec.layout.sections.flatMap { it.cells }.filter { it.opening != Opening.FIXED }
            name += "\n" + if (open.isEmpty()) "глухое" else open.joinToString(", ") { (if (it.door) "дверь " else "") + it.opening.title.lowercase() }
            tableRow(listOf("${i + 1}", "", name, "${b.quantity} шт", "%.2f".format(b.areaM2 * b.quantity), money(b.pricePerM2), money(b.total)),
                cols, tp(8.5f), minH = 56f, image = scheme(p, 360, thumb = true), imageCol = 1)
        }
        y += 6f
        total("Итоговая площадь, м²:", "%.2f".format(o.areaM2))
        if (o.discount > 0) {
            total("Сумма без скидки:", money(o.subtotal))
            total("Скидка ${"%.0f".format(opt.discountPercent)}%:", "−" + money(o.discount))
        }
        total("Итоговая стоимость:", money(o.total), bold = true, size = 14f, color = accent)
        total("В т. ч. НДС (${catalog.vatPercent.toInt()}%):", money(o.vatIncluded), size = 9.5f, color = Color.DKGRAY)
        if (o.areaM2 > 0) total("Стоимость за м²:", money(o.total / o.areaM2), size = 9.5f, color = Color.DKGRAY)
        y += 10f
        val small = tp(9.5f)
        text("Расчёт выполнен на основании предоставленных заказчиком чертежей.", small)
        val install = positions.any { it.selection.installation }
        val incl = if (opt.scope == OfferScope.MATERIALS)
            listOf("Алюминиевый профиль систем ${catalog.name} в виде материала", "Комплектующие и метизы для сборки изделий", "Резиновые уплотнители")
        else listOf("Алюминиевый профиль, комплектующие и уплотнители", "Стеклопакеты", "Фурнитура", "Изготовление изделий") + (if (install) listOf("Монтаж") else emptyList())
        val excl = if (opt.scope == OfferScope.MATERIALS)
            listOf("Сборка и монтаж изделий", "Заполнения (стеклопакеты, стекло и т. д.)", "Фурнитура (электроприводы, доводчики и т. д.)")
        else (if (install) emptyList() else listOf("Монтаж")) + listOf("Откосы, подоконники, отливы", "Доставка")
        text("В стоимость коммерческого предложения входит:", tp(9.5f, true), 2f)
        incl.forEach { text("•  $it", small, 1f) }
        y += 4f
        text("В стоимость коммерческого предложения не входит:", tp(9.5f, true), 2f)
        excl.forEach { text("•  $it", small, 1f) }
        y += 6f
        text("Коммерческое предложение действует в течение ${catalog.validDays} дней.", small, 2f)
        text("Стоимость рассчитана на базе курса от ${SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date())}. В случае колебаний валютного курса возможна корректировка итоговой стоимости на момент оплаты.",
            tp(8.5f, color = Color.GRAY))

        // ---------- Спецификация материалов ----------
        newPage()
        text("Спецификация материалов", tp(15f, true), 2f)
        text((if (opt.cutting) "Профили — целыми хлыстами по раскрою всего КП (пропил ${catalog.kerf.toInt()} мм, обрезка ${catalog.trim.toInt()} мм). "
              else "Профили — по длине с отходом ${catalog.wastePercent.toInt()}%. ") +
             (if (opt.gasketRound) "Уплотнители — кратно ${catalog.gasketStep.toInt()} м." else "Уплотнители — по расчёту.") +
             (if (o.weightKg > 0) " Вес профилей ≈ ${"%.0f".format(o.weightKg)} кг." else ""), tp(9f, color = Color.GRAY))
        y += 4f
        for ((group, title) in listOf(LineGroup.PROFILE to "Профили", LineGroup.ACCESSORY to "Комплектующие", LineGroup.GASKET to "Уплотнители")) {
            val list = o.materials.filter { it.group == group }
            if (list.isEmpty()) continue
            ensure(60f)
            text(title, tp(11f, true), 3f)
            val mc = listOf(Col(LEFT + 2, 98f, L0), Col(LEFT + 104, 177f, L0), Col(LEFT + 283, 72f, R), Col(LEFT + 357, 70f, R), Col(LEFT + 430, 85f, R))
            val head = when (group) {
                LineGroup.PROFILE -> listOf("Артикул", "Наименование, цвет", "Нужно / хлысты", "Цена за м", "Сумма")
                LineGroup.GASKET -> listOf("Артикул", "Наименование", "Нужно / к отпуску", "Цена за м", "Сумма")
                else -> listOf("Артикул", "Наименование", "Кол-во", "Цена", "Сумма")
            }
            tableRow(head, mc, tp(8f, true, Color.DKGRAY), fill = 0xFFF2F2F2.toInt())
            for (m in list) {
                var name = m.name
                if (group == LineGroup.PROFILE) catalog.color(m.colorKey.substringBefore("/"))?.let { name += " · ${it.code}" }
                val qty = when (group) {
                    LineGroup.PROFILE -> "%.2f м".format(m.need) + (m.bars?.let { "\n$it × ${"%.1f".format((m.barMm ?: 0.0) / 1000)} м" } ?: "\n%.2f м".format(m.bought))
                    LineGroup.GASKET -> "%.2f м\n%.0f м".format(m.need, m.bought)
                    else -> "%.0f шт".format(m.need)
                }
                tableRow(listOf(m.code, name, qty, money(m.price), money(m.amount)), mc, tp(8f))
            }
            total("$title, итого:", money(list.sumOf { it.amount }), bold = true, size = 9.5f)
            y += 6f
        }
        if (opt.scope == OfferScope.TURNKEY) text("Стеклопакеты, фурнитура, изготовление и монтаж — в стоимости окон (страница 1).", tp(9f, color = Color.GRAY))

        // ---------- Окна по отдельности ----------
        positions.forEachIndexed { i, p ->
            val b = o.items[i]
            newPage()
            text("${i + 1}. ${p.mark} — ${p.spec.product.title} ${p.spec.widthMm}×${p.spec.heightMm} мм, ${b.quantity} шт", tp(14f, true), 8f)
            val imgs = listOfNotNull(scheme(p, 720), (renders[p.id] ?: drawings[p.id])?.let { forPdf(it) })
            val slot = (WIDTH - 12f * (imgs.size - 1)) / imgs.size
            val maxH = 250f
            imgs.forEachIndexed { k, img ->
                val r = minOf(slot / img.width, maxH / img.height)
                val w = img.width * r
                val h = img.height * r
                val x = LEFT + k * (slot + 12f) + (slot - w) / 2
                c.drawBitmap(img, null, RectF(x, y, x + w, y + h), null)
            }
            y += maxH + 12f
            val pc = listOf(Col(LEFT + 2, 200f, L0), Col(LEFT + 205, 310f, R))
            val col = catalog.color(p.selection.colorId)
            val rows = mutableListOf(
                "Система" to (catalog.system(p.selection.systemId)?.name ?: "—"),
                "Площадь" to "%.2f м²".format(b.areaM2),
                "Заполнение" to ("${p.selection.fillMm} мм" + if (p.spec.glass.isNotEmpty()) " (${p.spec.glass})" else ""),
                "Цвет снаружи" to (col?.title ?: "—"),
            )
            if (p.selection.colorInsideId.isNotEmpty()) catalog.color(p.selection.colorInsideId)?.let { rows += "Цвет внутри" to it.title }
            for (r in p.spec.cellRects) {
                val cl = r.cell
                val what = (if (cl.door) "дверь, " else "") + cl.opening.title.lowercase() + if (cl.hinge != Hinge.NONE) ", " + cl.hinge.title.lowercase() else ""
                rows += "${p.spec.layout.split.sectionName} ${r.section + 1}, ячейка ${r.index + 1}" to "${r.w.toInt()}×${r.h.toInt()} — $what"
            }
            b.limits.forEach { w -> rows += "Нестандарт" to (w.text + if (w.applied) " · применено: ${w.code}" else "") }
            rows += "Цена за 1 шт" to money(b.unitTotal)
            rows += "Стоимость" to money(b.total)
            rows.forEach { tableRow(listOf(it.first, it.second), pc, tp(9.5f)) }
            if (p.spec.info.isNotEmpty()) {
                y += 8f
                text("С чертежа", tp(11f, true), 3f)
                p.spec.info.forEach { tableRow(listOf(it.label, it.value), pc, tp(9f)) }
            }
            if (renders[p.id] != null) {
                y += 6f
                text("Изображение готового изделия создано ИИ по схеме и носит иллюстративный характер.", tp(8f, color = Color.GRAY))
            }
        }
        doc.finishPage(page)
        val file = fileFor(ctx, opt.objectName)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** «КП Объект 28.11.2026.pdf» — так файл и увидит клиент в WhatsApp/почте. Своя папка на каждый экспорт. */
    fun fileFor(ctx: Context, objectName: String, date: Date = Date()): File {
        val obj = objectName.replace(Regex("[\\\\/:*?\"<>|\\s]+"), " ").trim().take(60)
        val name = (if (obj.isEmpty()) "КП" else "КП $obj") + " ${SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(date)}.pdf"
        val dir = File(ctx.cacheDir, "kp").apply { deleteRecursively(); mkdirs() }
        return File(dir, name)
    }

    /** Фото в PDF не больше 1400 px — файл КП остаётся лёгким. */
    private fun forPdf(b: Bitmap, max: Int = 1400): Bitmap {
        val side = maxOf(b.width, b.height)
        if (side <= max) return b
        val k = max.toFloat() / side
        return Bitmap.createScaledBitmap(b, maxOf(1, (b.width * k).toInt()), maxOf(1, (b.height * k).toInt()), true)
    }

}

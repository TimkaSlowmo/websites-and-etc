import SwiftUI
import UIKit

/// Коммерческое предложение в PDF (A4): схема, фото, параметры, данные с чертежа, стоимость по группам.
enum PDFExporter {
    @MainActor
    static func makeOffer(spec: WindowSpec, selection: Selection, breakdown b: PriceBreakdown, catalog: Catalog,
                          company: String, drawing: UIImage?, render: UIImage?) -> URL? {
        let page = CGRect(x: 0, y: 0, width: 595, height: 842)
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("КП-\(Int(Date().timeIntervalSince1970)).pdf")
        let color = catalog.color(selection.colorId)
        let scheme: UIImage? = {
            let r = ImageRenderer(content: SchemeView(spec: spec, frameHex: color?.hex ?? "#45494E")
                .frame(width: 360, height: 360).background(Color.white))
            r.scale = 3
            return r.uiImage
        }()

        do {
            try UIGraphicsPDFRenderer(bounds: page).writePDF(to: url) { ctx in
                ctx.beginPage()
                var y: CGFloat = 40
                let left: CGFloat = 40, width: CGFloat = 515

                func text(_ s: String, _ font: UIFont, color: UIColor = .black, align: NSTextAlignment = .left,
                          x: CGFloat = left, w: CGFloat = width, advance: Bool = true) {
                    let para = NSMutableParagraphStyle(); para.alignment = align
                    let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color, .paragraphStyle: para]
                    let h = ceil((s as NSString).boundingRect(with: CGSize(width: w, height: .greatestFiniteMagnitude),
                                                              options: .usesLineFragmentOrigin, attributes: attrs, context: nil).height)
                    if y + h > 800 { ctx.beginPage(); y = 40 }
                    (s as NSString).draw(in: CGRect(x: x, y: y, width: w, height: h), withAttributes: attrs)
                    if advance { y += h + 5 }
                }
                func row(_ title: String, _ value: String, bold: Bool = false, size: CGFloat = 10.5) {
                    let f: UIFont = bold ? .boldSystemFont(ofSize: size) : .systemFont(ofSize: size)
                    text(title, f, w: 370, advance: false)
                    text(value, f, align: .right, x: 410, w: 145)
                }

                if !company.isEmpty { text(company, .boldSystemFont(ofSize: 20)) }
                text("Коммерческое предложение", .systemFont(ofSize: 15), color: .darkGray)
                text(Date().formatted(date: .long, time: .omitted), .systemFont(ofSize: 10), color: .gray)
                y += 8

                let imgs = [scheme, render ?? drawing].compactMap { $0 }
                if !imgs.isEmpty {
                    let slot = (width - 12 * CGFloat(imgs.count - 1)) / CGFloat(imgs.count)
                    let maxH: CGFloat = 230
                    for (i, img) in imgs.enumerated() {
                        let ratio = min(slot / img.size.width, maxH / img.size.height)
                        let size = CGSize(width: img.size.width * ratio, height: img.size.height * ratio)
                        img.draw(in: CGRect(x: left + CGFloat(i) * (slot + 12) + (slot - size.width) / 2, y: y, width: size.width, height: size.height))
                    }
                    y += maxH + 14
                }

                text("Изделие", .boldSystemFont(ofSize: 13))
                let sys = catalog.system(selection.systemId)
                row("Тип", spec.product.title)
                row("Система", sys?.name ?? "—")
                row("Размер", "\(spec.widthMm) × \(spec.heightMm) мм (\(String(format: "%.2f", spec.areaM2)) м²)")
                row("Количество", "\(spec.quantity) шт")
                row("Заполнение", "\(selection.fillMm) мм" + (spec.glass.isEmpty ? "" : " (\(spec.glass))"))
                row("Цвет снаружи", color?.title ?? "—")
                if !selection.colorInsideId.isEmpty, let ci = catalog.color(selection.colorInsideId) { row("Цвет внутри", ci.title) }
                for r in spec.cellRects {
                    let c = r.cell
                    let what = (c.door ? "дверь, " : "") + c.opening.title.lowercased() + (c.hinge != .none ? ", " + c.hinge.title.lowercased() : "")
                    row("\(spec.layout.split.sectionName) \(r.section + 1), ячейка \(r.index + 1)", "\(Int(r.w))×\(Int(r.h)) — \(what)", size: 9.5)
                }
                if !spec.info.isEmpty {
                    y += 6
                    text("С чертежа", .boldSystemFont(ofSize: 13))
                    for i in spec.info { row(i.label, i.value, size: 9.5) }
                }
                y += 8
                text("Стоимость", .boldSystemFont(ofSize: 13))
                for g in LineGroup.allCases where b.sum(g) > 0 { row(g.title, Money.format(b.sum(g), currency: b.currency)) }
                row("Наценка", Money.format(b.markup, currency: b.currency))
                row("НДС \(Int(catalog.vatPercent))%", Money.format(b.vat, currency: b.currency))
                y += 4
                if b.quantity > 1 {
                    row("Цена за 1 шт", Money.format(b.unitTotal, currency: b.currency), bold: true, size: 12)
                    row("Итого за \(b.quantity) шт", Money.format(b.total, currency: b.currency), bold: true, size: 15)
                } else {
                    row("Итого", Money.format(b.total, currency: b.currency), bold: true, size: 15)
                }
                y += 12
                text("Расчёт ориентировочный, по прайсу «\(catalog.name)». Окончательная стоимость определяется после замера. Изображение готового изделия создано ИИ и носит иллюстративный характер.",
                     .systemFont(ofSize: 8.5), color: .gray)
            }
            return url
        } catch {
            return nil
        }
    }
}

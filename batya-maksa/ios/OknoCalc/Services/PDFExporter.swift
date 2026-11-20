import UIKit

/// Коммерческое предложение в PDF (A4).
enum PDFExporter {
    static func makeOffer(spec: WindowSpec, breakdown b: PriceBreakdown, catalogName: String,
                          company: String, drawing: UIImage?, render: UIImage?) -> URL? {
        let page = CGRect(x: 0, y: 0, width: 595, height: 842)
        let renderer = UIGraphicsPDFRenderer(bounds: page)
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("КП-\(Int(Date().timeIntervalSince1970)).pdf")

        do {
            try renderer.writePDF(to: url) { ctx in
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
                    if advance { y += h + 6 }
                }

                if !company.isEmpty { text(company, .boldSystemFont(ofSize: 20)) }
                text("Коммерческое предложение", .systemFont(ofSize: 15), color: .darkGray)
                text(Date().formatted(date: .long, time: .omitted), .systemFont(ofSize: 11), color: .gray)
                y += 10

                // Изображения
                let imgs = [drawing, render].compactMap { $0 }
                if !imgs.isEmpty {
                    let slot = (width - 12 * CGFloat(imgs.count - 1)) / CGFloat(imgs.count)
                    let maxH: CGFloat = 200
                    for (i, img) in imgs.enumerated() {
                        let ratio = min(slot / img.size.width, maxH / img.size.height)
                        let size = CGSize(width: img.size.width * ratio, height: img.size.height * ratio)
                        img.draw(in: CGRect(x: left + CGFloat(i) * (slot + 12), y: y, width: size.width, height: size.height))
                    }
                    y += maxH + 14
                }

                // Параметры
                text("Параметры изделия", .boldSystemFont(ofSize: 13))
                let params = [
                    "Вид: \(spec.type.title)",
                    "Размер: \(spec.widthMm) × \(spec.heightMm) мм (\(String(format: "%.2f", spec.areaM2)) м²)",
                    "Створки: " + spec.sashes.map { $0.opening.title }.joined(separator: ", "),
                ]
                for p in params { text(p, .systemFont(ofSize: 11)) }
                y += 8

                // Таблица цен
                text("Стоимость", .boldSystemFont(ofSize: 13))
                for line in b.lines {
                    text(line.title, .systemFont(ofSize: 11), w: 350, advance: false)
                    text(Money.format(line.amount, currency: b.currency), .systemFont(ofSize: 11), align: .right, x: 400, w: 155)
                }
                if b.markup > 0 {
                    text("Наценка", .systemFont(ofSize: 11), w: 350, advance: false)
                    text(Money.format(b.markup, currency: b.currency), .systemFont(ofSize: 11), align: .right, x: 400, w: 155)
                }
                if b.vat > 0 {
                    text("НДС", .systemFont(ofSize: 11), w: 350, advance: false)
                    text(Money.format(b.vat, currency: b.currency), .systemFont(ofSize: 11), align: .right, x: 400, w: 155)
                }
                y += 6
                text("Итого", .boldSystemFont(ofSize: 15), w: 350, advance: false)
                text(Money.format(b.total, currency: b.currency), .boldSystemFont(ofSize: 15), align: .right, x: 400, w: 155)
                y += 12
                text("Расчёт ориентировочный (каталог «\(catalogName)»). Окончательная стоимость определяется после замера. Изображение готового изделия создано ИИ и носит иллюстративный характер.",
                     .systemFont(ofSize: 9), color: .gray)
            }
            return url
        } catch {
            return nil
        }
    }
}

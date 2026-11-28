import SwiftUI
import UIKit

/// Коммерческое предложение в PDF (A4) по образцу КП BKM Aluminium:
/// 1) таблица окон (схема, наименование, кол-во, м², цена за м², стоимость), итог, НДС, что входит/не входит, срок;
/// 2) спецификация материалов (профили хлыстами, комплектующие, уплотнители);
/// 3) окна по отдельности: схема, ИИ-фото, параметры, данные с чертежа.
enum PDFExporter {
    struct Images { var drawing: UIImage?; var render: UIImage? }

    @MainActor
    static func makeOffer(positions: [Position], options: OfferOptions, breakdown o: OfferBreakdown, catalog: Catalog,
                          company: String, images: [UUID: Images]) -> URL? {
        let page = CGRect(x: 0, y: 0, width: 595, height: 842)
        let df = DateFormatter()
        df.dateFormat = "yyyyMMdd"
        df.locale = Locale(identifier: "en_US_POSIX")
        let stamp = df.string(from: Date())
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("КП-\(stamp)-\(Int(Date().timeIntervalSince1970) % 100000).pdf")
        let cur = o.currency
        func money(_ v: Double) -> String { Money.format(v, currency: cur) }
        func scheme(_ p: Position, side: CGFloat) -> UIImage? {
            let hex = catalog.color(p.selection.colorId)?.hex ?? "#45494E"
            let r = ImageRenderer(content: SchemeView(spec: p.spec, frameHex: hex, maxHeight: side)
                .frame(width: side, height: side).background(Color.white).environment(\.colorScheme, .light))
            r.scale = 3
            return r.uiImage
        }
        let accent = UIColor(red: 0.07, green: 0.47, blue: 0.55, alpha: 1)

        do {
            try UIGraphicsPDFRenderer(bounds: page).writePDF(to: url) { ctx in
                ctx.beginPage()
                var y: CGFloat = 40
                let left: CGFloat = 40, width: CGFloat = 515, bottom: CGFloat = 800

                func height(_ s: String, _ font: UIFont, _ w: CGFloat) -> CGFloat {
                    ceil((s as NSString).boundingRect(with: CGSize(width: w, height: .greatestFiniteMagnitude), options: .usesLineFragmentOrigin,
                                                      attributes: [.font: font], context: nil).height)
                }
                func draw(_ s: String, _ font: UIFont, x: CGFloat, w: CGFloat, color: UIColor = .black, align: NSTextAlignment = .left, at yy: CGFloat) {
                    let para = NSMutableParagraphStyle(); para.alignment = align
                    (s as NSString).draw(in: CGRect(x: x, y: yy, width: w, height: height(s, font, w) + 2),
                                         withAttributes: [.font: font, .foregroundColor: color, .paragraphStyle: para])
                }
                func ensure(_ h: CGFloat) { if y + h > bottom { ctx.beginPage(); y = 40 } }
                func text(_ s: String, _ font: UIFont, color: UIColor = .black, align: NSTextAlignment = .left, gap: CGFloat = 5) {
                    let h = height(s, font, width)
                    ensure(h)
                    draw(s, font, x: left, w: width, color: color, align: align, at: y)
                    y += h + gap
                }
                func line(_ yy: CGFloat, _ color: UIColor = UIColor(white: 0.82, alpha: 1)) {
                    let p = UIBezierPath(); p.move(to: CGPoint(x: left, y: yy)); p.addLine(to: CGPoint(x: left + width, y: yy))
                    color.setStroke(); p.lineWidth = 0.6; p.stroke()
                }
                /// Строка таблицы: колонки (x, w, выравнивание); высота — по самой высокой ячейке.
                func tableRow(_ cells: [String], _ cols: [(CGFloat, CGFloat, NSTextAlignment)], font: UIFont, minH: CGFloat = 0,
                              color: UIColor = .black, fill: UIColor? = nil, image: UIImage? = nil, imageCol: Int = -1) {
                    let h = max(minH, cells.enumerated().map { height($0.element, font, cols[$0.offset].1) }.max() ?? 0) + 8
                    ensure(h)
                    if let fill { fill.setFill(); UIBezierPath(rect: CGRect(x: left, y: y, width: width, height: h)).fill() }
                    for (i, c) in cells.enumerated() {
                        if i == imageCol, let image {
                            let side = min(cols[i].1, h - 6)
                            let k = min(side / image.size.width, side / image.size.height)
                            let sz = CGSize(width: image.size.width * k, height: image.size.height * k)
                            image.draw(in: CGRect(x: cols[i].0 + (cols[i].1 - sz.width) / 2, y: y + (h - sz.height) / 2, width: sz.width, height: sz.height))
                        } else {
                            draw(c, font, x: cols[i].0, w: cols[i].1, color: color, align: cols[i].2, at: y + 4)
                        }
                    }
                    y += h
                    line(y)
                }

                // ---------- Страница 1: КП ----------
                if !company.isEmpty { text(company, .boldSystemFont(ofSize: 20), gap: 2) }
                text(options.scope == .materials
                     ? "Коммерческое предложение на поставку архитектурных систем алюминиевых профилей"
                     : "Коммерческое предложение на изготовление оконно-дверных конструкций",
                     .systemFont(ofSize: 14), color: .darkGray, gap: 2)
                if !options.objectName.isEmpty { text("по объекту: \(options.objectName)", .boldSystemFont(ofSize: 12), gap: 2) }
                text(Date().formatted(date: .long, time: .omitted), .systemFont(ofSize: 10), color: .gray)
                y += 6

                let cols: [(CGFloat, CGFloat, NSTextAlignment)] = [
                    (left + 2, 18, .left), (left + 22, 62, .center), (left + 88, 197, .left), (left + 288, 38, .right),
                    (left + 330, 50, .right), (left + 384, 60, .right), (left + 448, 65, .right),
                ]
                tableRow(["№", "Схема", "Наименование", "Кол-во", "м²", "Цена 1 м²", "Стоимость"], cols,
                         font: .boldSystemFont(ofSize: 8.5), color: .darkGray, fill: UIColor(white: 0.95, alpha: 1))
                for (i, p) in positions.enumerated() {
                    let b = o.items[i]
                    let sys = catalog.system(p.selection.systemId)?.name ?? ""
                    let col = catalog.color(p.selection.colorId)
                    var name = "\(p.mark) · \(p.spec.product.title) \(p.spec.widthMm)×\(p.spec.heightMm) мм\n\(sys), \(col?.code ?? "")"
                    if !p.selection.colorInsideId.isEmpty, let ci = catalog.color(p.selection.colorInsideId) { name += " / внутри \(ci.code)" }
                    name += ", заполнение \(p.selection.fillMm) мм"
                    let open = p.spec.layout.sections.flatMap(\.cells).filter { $0.opening != .fixed }
                    name += "\n" + (open.isEmpty ? "глухое" : open.map { ($0.door ? "дверь " : "") + $0.opening.title.lowercased() }.joined(separator: ", "))
                    tableRow(["\(i + 1)", "", name, "\(b.quantity) шт", String(format: "%.2f", b.areaM2 * Double(b.quantity)),
                              money(b.pricePerM2), money(b.total)], cols, font: .systemFont(ofSize: 8.5), minH: 56,
                             image: scheme(p, side: 120), imageCol: 1)
                }
                y += 6
                func total(_ t: String, _ v: String, bold: Bool = false, size: CGFloat = 10.5, color: UIColor = .black) {
                    let f: UIFont = bold ? .boldSystemFont(ofSize: size) : .systemFont(ofSize: size)
                    ensure(18)
                    draw(t, f, x: left + 200, w: 200, color: color, align: .right, at: y)
                    draw(v, f, x: left + 405, w: 110, color: color, align: .right, at: y)
                    y += height(t, f, 200) + 4
                }
                total("Итоговая площадь, м²:", String(format: "%.2f", o.areaM2))
                if o.discount > 0 {
                    total("Сумма без скидки:", money(o.subtotal))
                    total("Скидка \(String(format: "%g", options.discountPercent))%:", "−" + money(o.discount))
                }
                total("Итоговая стоимость:", money(o.total), bold: true, size: 14, color: accent)
                total("В т. ч. НДС (\(Int(catalog.vatPercent))%):", money(o.vatIncluded), size: 9.5, color: .darkGray)
                if o.areaM2 > 0 { total("Стоимость за м²:", money(o.total / o.areaM2), size: 9.5, color: .darkGray) }
                y += 10

                let small = UIFont.systemFont(ofSize: 9.5)
                text("Расчёт выполнен на основании предоставленных заказчиком чертежей.", small)
                let install = positions.contains { $0.selection.installation }
                let incl = options.scope == .materials
                    ? ["Алюминиевый профиль систем \(catalog.name) в виде материала", "Комплектующие и метизы для сборки изделий", "Резиновые уплотнители"]
                    : ["Алюминиевый профиль, комплектующие и уплотнители", "Стеклопакеты", "Фурнитура", "Изготовление изделий"] + (install ? ["Монтаж"] : [])
                let excl = options.scope == .materials
                    ? ["Сборка и монтаж изделий", "Заполнения (стеклопакеты, стекло и т. д.)", "Фурнитура (электроприводы, доводчики и т. д.)"]
                    : (install ? [] : ["Монтаж"]) + ["Откосы, подоконники, отливы", "Доставка"]
                text("В стоимость коммерческого предложения входит:", .boldSystemFont(ofSize: 9.5), gap: 2)
                for s in incl { text("•  " + s, small, gap: 1) }
                y += 4
                text("В стоимость коммерческого предложения не входит:", .boldSystemFont(ofSize: 9.5), gap: 2)
                for s in excl { text("•  " + s, small, gap: 1) }
                y += 6
                text("Коммерческое предложение действует в течение \(catalog.validDays) дней.", small, gap: 2)
                text("Стоимость рассчитана на базе курса от \(Date().formatted(date: .numeric, time: .omitted)). В случае колебаний валютного курса возможна корректировка итоговой стоимости на момент оплаты.",
                     .systemFont(ofSize: 8.5), color: .gray)

                // ---------- Спецификация материалов ----------
                ctx.beginPage(); y = 40
                text("Спецификация материалов", .boldSystemFont(ofSize: 15), gap: 2)
                text((options.cutting ? "Профили — целыми хлыстами по раскрою всего КП (пропил \(Int(catalog.kerf)) мм, обрезка \(Int(catalog.trim)) мм). "
                                      : "Профили — по длине с отходом \(Int(catalog.wastePercent))%. ")
                     + (options.gasketRound ? "Уплотнители — кратно \(Int(catalog.gasketStep)) м." : "Уплотнители — по расчёту."),
                     .systemFont(ofSize: 9), color: .gray)
                y += 4
                for (group, title) in [(LineGroup.profile, "Профили"), (.accessory, "Комплектующие"), (.gasket, "Уплотнители")] {
                    let list = o.materials.filter { $0.group == group }
                    guard !list.isEmpty else { continue }
                    ensure(60)
                    text(title, .boldSystemFont(ofSize: 11), gap: 3)
                    let mc: [(CGFloat, CGFloat, NSTextAlignment)] = [
                        (left + 2, 92, .left), (left + 96, 185, .left), (left + 283, 72, .right), (left + 357, 70, .right), (left + 430, 85, .right),
                    ]
                    let head: [String] = group == .profile ? ["Артикул", "Наименование, цвет", "Нужно / хлысты", "Цена за м", "Сумма"]
                        : group == .gasket ? ["Артикул", "Наименование", "Нужно / к отпуску", "Цена за м", "Сумма"]
                        : ["Артикул", "Наименование", "Кол-во", "Цена", "Сумма"]
                    tableRow(head, mc, font: .boldSystemFont(ofSize: 8), color: .darkGray, fill: UIColor(white: 0.95, alpha: 1))
                    for m in list {
                        var name = m.name
                        if group == .profile, let c = catalog.color(String(m.colorKey.split(separator: "/").first ?? "")) { name += " · \(c.code)" }
                        let qty: String
                        switch group {
                        case .profile:
                            qty = String(format: "%.2f м", m.need) + (m.bars.map { "\n\($0) × \(String(format: "%g", ($0 > 0 ? (m.barMm ?? 0) : 0) / 1000)) м" } ?? String(format: "\n%.2f м", m.bought))
                        case .gasket:
                            qty = String(format: "%.2f м\n%.0f м", m.need, m.bought)
                        default:
                            qty = String(format: "%.0f шт", m.need)
                        }
                        tableRow([m.code, name, qty, money(m.price), money(m.amount)], mc, font: .systemFont(ofSize: 8))
                    }
                    total("\(title), итого:", money(list.reduce(0) { $0 + $1.amount }), bold: true, size: 9.5)
                    y += 6
                }
                if o.weightKg > 0 { text(String(format: "Вес профилей ≈ %.0f кг", o.weightKg), .systemFont(ofSize: 9), color: .gray) }
                if options.scope == .turnkey {
                    text("Стеклопакеты, фурнитура, изготовление и монтаж — в стоимости окон (страница 1).", .systemFont(ofSize: 9), color: .gray)
                }

                // ---------- Окна по отдельности ----------
                for (i, p) in positions.enumerated() {
                    let b = o.items[i]
                    ctx.beginPage(); y = 40
                    text("\(i + 1). \(p.mark) — \(p.spec.product.title) \(p.spec.widthMm)×\(p.spec.heightMm) мм, \(b.quantity) шт",
                         .boldSystemFont(ofSize: 14), gap: 8)
                    let imgs = [scheme(p, side: 300), images[p.id]?.render ?? images[p.id]?.drawing].compactMap { $0 }
                    if !imgs.isEmpty {
                        let slot = (width - 12 * CGFloat(imgs.count - 1)) / CGFloat(imgs.count)
                        let maxH: CGFloat = 250
                        for (k, img) in imgs.enumerated() {
                            let r = min(slot / img.size.width, maxH / img.size.height)
                            let sz = CGSize(width: img.size.width * r, height: img.size.height * r)
                            img.draw(in: CGRect(x: left + CGFloat(k) * (slot + 12) + (slot - sz.width) / 2, y: y, width: sz.width, height: sz.height))
                        }
                        y += maxH + 12
                    }
                    let pc: [(CGFloat, CGFloat, NSTextAlignment)] = [(left + 2, 200, .left), (left + 205, 310, .right)]
                    let col = catalog.color(p.selection.colorId)
                    var rows: [(String, String)] = [
                        ("Система", catalog.system(p.selection.systemId)?.name ?? "—"),
                        ("Площадь", String(format: "%.2f м²", b.areaM2)),
                        ("Заполнение", "\(p.selection.fillMm) мм" + (p.spec.glass.isEmpty ? "" : " (\(p.spec.glass))")),
                        ("Цвет снаружи", col?.title ?? "—"),
                    ]
                    if !p.selection.colorInsideId.isEmpty, let ci = catalog.color(p.selection.colorInsideId) { rows.append(("Цвет внутри", ci.title)) }
                    for r in p.spec.cellRects {
                        let c = r.cell
                        let what = (c.door ? "дверь, " : "") + c.opening.title.lowercased() + (c.hinge != .none ? ", " + c.hinge.title.lowercased() : "")
                        rows.append(("\(p.spec.layout.split.sectionName) \(r.section + 1), ячейка \(r.index + 1)", "\(Int(r.w))×\(Int(r.h)) — \(what)"))
                    }
                    for w in b.limits { rows.append(("Нестандарт", w.text + (w.applied ? " · применено: \(w.code)" : ""))) }
                    rows.append(("Цена за 1 шт", money(b.unitTotal)))
                    rows.append(("Стоимость", money(b.total)))
                    for r in rows { tableRow([r.0, r.1], pc, font: .systemFont(ofSize: 9.5)) }
                    if !p.spec.info.isEmpty {
                        y += 8
                        text("С чертежа", .boldSystemFont(ofSize: 11), gap: 3)
                        for inf in p.spec.info { tableRow([inf.label, inf.value], pc, font: .systemFont(ofSize: 9)) }
                    }
                    if images[p.id]?.render != nil {
                        y += 6
                        text("Изображение готового изделия создано ИИ по схеме и носит иллюстративный характер.", .systemFont(ofSize: 8), color: .gray)
                    }
                }
            }
            return url
        } catch {
            return nil
        }
    }
}

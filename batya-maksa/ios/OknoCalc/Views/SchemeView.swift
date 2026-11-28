import SwiftUI

/// Размер на схеме, который можно изменить нажатием (выделен голубым).
struct SchemeDimension: Identifiable, Equatable {
    enum Kind { case width, height, section, cell }
    var kind: Kind
    var section = 0
    var index = 0
    var value: Int
    var title: String
    var id: String { "\(kind)-\(section)-\(index)" }
}

/// Схема изделия по сетке: рама цвета профиля, импосты, символы открывания (вершина — сторона ручки),
/// размеры секций и ячеек. Нажатие на ячейку выбирает её, нажатие на голубой размер — меняет его.
/// clean = true — чистый фасад без размеров и значков (образец для ИИ-фото).
struct SchemeView: View {
    var spec: WindowSpec
    var frameHex: String = "#45494E"
    var selected: CellRect.ID? = nil
    var onTap: ((CellRect) -> Void)? = nil
    /// Схема занимает всю ширину, а высота — по пропорциям изделия, но не больше maxHeight (как на Android).
    var maxHeight: CGFloat = 380
    var onDimension: ((SchemeDimension) -> Void)? = nil
    var clean = false
    /// Миниатюра для таблицы КП: значки открывания есть, размеров и подписей нет.
    var thumb = false

    private var dimSpace: CGFloat { clean || thumb ? 6 : 34 }

    var body: some View {
        GeometryReader { geo in
            let box = fitBox(in: geo.size)
            let k = box.width / CGFloat(spec.widthMm)
            Canvas { ctx, _ in draw(ctx: &ctx, box: box, k: k) }
                .contentShape(Rectangle())
                .onTapGesture { p in
                    if let onDimension, let d = dims(box, k).first(where: { hitRect($0).contains(p) }) {
                        onDimension(d.dim); return
                    }
                    if let onDimension, let c = cellLabels(box, k).first(where: { $0.rect.insetBy(dx: -8, dy: -8).contains(p) }), let d = c.dim {
                        onDimension(d); return
                    }
                    guard let onTap else { return }
                    for r in spec.cellRects where rect(r, box, k).contains(p) { onTap(r); return }
                }
        }
        .modifier(SchemeSizing(ratio: aspect, pad: dimSpace * 2, maxHeight: maxHeight))
        .accessibilityLabel("Схема изделия \(spec.widthMm) на \(spec.heightMm) миллиметров")
    }

    private var aspect: CGFloat {
        (CGFloat(spec.widthMm) + 0.0001) / CGFloat(max(1, spec.heightMm)) * 1.0
    }

    private func fitBox(in size: CGSize) -> CGRect {
        let availW = size.width - dimSpace * 2
        let availH = size.height - dimSpace * 2
        let k = min(availW / CGFloat(spec.widthMm), availH / CGFloat(spec.heightMm))
        let w = CGFloat(spec.widthMm) * k, h = CGFloat(spec.heightMm) * k
        return CGRect(x: (size.width - w) / 2, y: (size.height - h) / 2, width: w, height: h)
    }

    private func rect(_ r: CellRect, _ box: CGRect, _ k: CGFloat) -> CGRect {
        CGRect(x: box.minX + CGFloat(r.x) * k, y: box.minY + CGFloat(r.y) * k, width: CGFloat(r.w) * k, height: CGFloat(r.h) * k)
    }

    private struct Dim { var dim: SchemeDimension; var a: CGPoint; var b: CGPoint; var text: String }

    /// Размерные линии: общие (сверху и справа) и по секциям (снизу или слева).
    private func dims(_ box: CGRect, _ k: CGFloat) -> [Dim] {
        var out = [
            Dim(dim: SchemeDimension(kind: .width, value: spec.widthMm, title: "Ширина изделия"),
                a: CGPoint(x: box.minX, y: box.minY - 14), b: CGPoint(x: box.maxX, y: box.minY - 14), text: "\(spec.widthMm)"),
            Dim(dim: SchemeDimension(kind: .height, value: spec.heightMm, title: "Высота изделия"),
                a: CGPoint(x: box.maxX + 14, y: box.minY), b: CGPoint(x: box.maxX + 14, y: box.maxY), text: "\(spec.heightMm)"),
        ]
        guard spec.layout.sections.count > 1 else { return out }
        var a = 0.0
        let name = spec.layout.split.sectionName
        for (i, s) in spec.layout.sections.enumerated() {
            let d = SchemeDimension(kind: .section, section: i, value: s.sizeMm,
                                    title: "\(name) \(i + 1) — \(spec.layout.split == .columns ? "ширина" : "высота")")
            if spec.layout.split == .columns {
                let x0 = box.minX + CGFloat(a) * k, x1 = box.minX + CGFloat(a + Double(s.sizeMm)) * k
                out.append(Dim(dim: d, a: CGPoint(x: x0, y: box.maxY + 14), b: CGPoint(x: x1, y: box.maxY + 14), text: "\(s.sizeMm)"))
            } else {
                let y0 = box.minY + CGFloat(a) * k, y1 = box.minY + CGFloat(a + Double(s.sizeMm)) * k
                out.append(Dim(dim: d, a: CGPoint(x: box.minX - 14, y: y0), b: CGPoint(x: box.minX - 14, y: y1), text: "\(s.sizeMm)"))
            }
            a += Double(s.sizeMm)
        }
        return out
    }

    private func labelRect(_ d: Dim) -> CGRect {
        let mid = CGPoint(x: (d.a.x + d.b.x) / 2, y: (d.a.y + d.b.y) / 2)
        let w = CGFloat(d.text.count) * 7 + 12, h: CGFloat = 17
        return abs(d.a.x - d.b.x) < 1 ? CGRect(x: mid.x - h / 2, y: mid.y - w / 2, width: h, height: w)
                                       : CGRect(x: mid.x - w / 2, y: mid.y - h / 2, width: w, height: h)
    }

    /// Зона нажатия на размер — не меньше 44×32 pt.
    private func hitRect(_ d: Dim) -> CGRect {
        let r = labelRect(d)
        return r.insetBy(dx: min(0, (r.width - 44) / 2), dy: min(0, (r.height - 32) / 2))
    }

    /// Подписи ячеек «ширина×высота»; в секции из нескольких ячеек размер можно менять.
    private func cellLabels(_ box: CGRect, _ k: CGFloat) -> [(r: CellRect, rect: CGRect, text: String, dim: SchemeDimension?)] {
        spec.cellRects.map { r in
            let cr = rect(r, box, k)
            let text = "\(Int(r.w))×\(Int(r.h))"
            let fs = max(8, min(12, cr.width / 7))
            let w = CGFloat(text.count) * fs * 0.62 + 8, h = fs + 6
            let profile = max(4, min(14, box.width / 28))
            let rr = CGRect(x: cr.midX - w / 2, y: cr.maxY - profile - h - 4, width: w, height: h)
            let many = spec.layout.sections[r.section].cells.count > 1
            let cols = spec.layout.split == .columns
            let d = many ? SchemeDimension(kind: .cell, section: r.section, index: r.index, value: r.cell.sizeMm,
                                           title: "\(spec.layout.split.sectionName) \(r.section + 1), ячейка \(r.index + 1) — \(cols ? "высота" : "ширина")") : nil
            return (r, rr, text, d)
        }
    }

    private func draw(ctx: inout GraphicsContext, box: CGRect, k: CGFloat) {
        let frameColor = Color(hex: frameHex)
        let profile = max(4, min(14, box.width / 28))
        let glass = Color(red: 0.80, green: 0.90, blue: 0.97)
        let editable = onDimension != nil

        // Рама
        ctx.fill(Path(box), with: .color(frameColor))
        let labels = clean || thumb ? [] : cellLabels(box, k)
        for r in spec.cellRects {
            let cr = rect(r, box, k)
            let opening = r.cell.opening != .fixed
            let inner = cr.insetBy(dx: profile * (opening ? 1.0 : 0.55), dy: profile * (opening ? 1.0 : 0.55))
            if opening {
                // Створка: контур профиля створки внутри рамы
                ctx.stroke(Path(cr.insetBy(dx: profile * 0.5, dy: profile * 0.5)), with: .color(.white.opacity(clean ? 0.22 : 0.35)), lineWidth: 1)
            }
            ctx.fill(Path(inner), with: .color(glass))
            ctx.fill(Path(CGRect(x: inner.minX, y: inner.minY, width: inner.width * 0.35, height: inner.height)),
                     with: .color(.white.opacity(0.18)))
            if clean {
                drawHandle(ctx: &ctx, cell: r.cell, in: inner, color: frameColor.opacity(0.9))
            } else {
                drawOpening(ctx: &ctx, cell: r.cell, in: inner)
            }
            if r.cell.door && !clean {
                let hx = r.cell.hinge == .right ? inner.minX + 6 : inner.maxX - 10
                ctx.fill(Path(roundedRect: CGRect(x: hx, y: inner.midY - 14, width: 4, height: 28), cornerRadius: 2), with: .color(frameColor))
            }
            if r.id == selected && !clean {
                ctx.stroke(Path(cr.insetBy(dx: 1.5, dy: 1.5)), with: .color(Theme.accent), lineWidth: 3)
            }
        }
        if clean || thumb { return }
        // Размер ячейки (голубой, если его можно изменить)
        for l in labels {
            let cr = rect(l.r, box, k)
            let fs = max(8, min(12, cr.width / 7))
            guard l.rect.width < cr.width - profile * 2 else { continue }
            let tappable = editable && l.dim != nil
            ctx.fill(Path(roundedRect: l.rect, cornerRadius: 4), with: .color(.white.opacity(0.88)))
            if tappable { ctx.stroke(Path(roundedRect: l.rect, cornerRadius: 4), with: .color(Theme.accent), lineWidth: 1) }
            let t = ctx.resolve(Text(l.text).font(.system(size: fs, weight: .semibold)).foregroundColor(tappable ? Theme.accent : Theme.deep))
            ctx.draw(t, at: CGPoint(x: l.rect.midX, y: l.rect.midY), anchor: .center)
        }
        for d in dims(box, k) {
            drawDim(ctx: &ctx, d, editable: editable)
        }
    }

    /// Ручка на створке — со стороны, противоположной петлям (для ИИ-фото).
    private func drawHandle(ctx: inout GraphicsContext, cell: Cell, in r: CGRect, color: Color) {
        guard cell.opening != .fixed else { return }
        let l = max(10, min(r.height, r.width) * 0.12), t = max(2.5, l * 0.22)
        let rect: CGRect
        switch cell.hinge {
        case .left: rect = CGRect(x: r.maxX - t * 2.2, y: r.midY - l / 2, width: t, height: l)
        case .right: rect = CGRect(x: r.minX + t * 1.2, y: r.midY - l / 2, width: t, height: l)
        case .bottom: rect = CGRect(x: r.midX - l / 2, y: r.minY + t * 1.2, width: l, height: t)
        case .top: rect = CGRect(x: r.midX - l / 2, y: r.maxY - t * 2.2, width: l, height: t)
        case .none: rect = CGRect(x: r.maxX - t * 2.2, y: r.midY - l / 2, width: t, height: l)
        }
        ctx.fill(Path(roundedRect: rect, cornerRadius: t / 2), with: .color(color))
    }

    /// Вершина треугольника — сторона ручки; петли с противоположной стороны. Наружу — пунктир.
    private func drawOpening(ctx: inout GraphicsContext, cell: Cell, in r: CGRect) {
        let style = StrokeStyle(lineWidth: 1.4, dash: cell.inward ? [] : [5, 4])
        let color = Theme.deep.opacity(0.85)
        func lines(_ a: CGPoint, _ b: CGPoint, _ apex: CGPoint) {
            var p = Path()
            p.move(to: a); p.addLine(to: apex); p.addLine(to: b)
            ctx.stroke(p, with: .color(color), style: style)
        }
        let turnApex: (CGPoint, CGPoint, CGPoint) = cell.hinge == .right
            ? (CGPoint(x: r.maxX, y: r.minY), CGPoint(x: r.maxX, y: r.maxY), CGPoint(x: r.minX, y: r.midY))
            : (CGPoint(x: r.minX, y: r.minY), CGPoint(x: r.minX, y: r.maxY), CGPoint(x: r.maxX, y: r.midY))
        switch cell.opening {
        case .fixed:
            break
        case .turn:
            lines(turnApex.0, turnApex.1, turnApex.2)
        case .tilt:
            lines(CGPoint(x: r.minX, y: r.maxY), CGPoint(x: r.maxX, y: r.maxY), CGPoint(x: r.midX, y: r.minY))
        case .tilt_turn:
            lines(turnApex.0, turnApex.1, turnApex.2)
            lines(CGPoint(x: r.minX, y: r.maxY), CGPoint(x: r.maxX, y: r.maxY), CGPoint(x: r.midX, y: r.minY))
        case .top_hung:
            lines(CGPoint(x: r.minX, y: r.minY), CGPoint(x: r.maxX, y: r.minY), CGPoint(x: r.midX, y: r.maxY))
        case .slide:
            var p = Path()
            let y = r.midY, x0 = r.minX + r.width * 0.2, x1 = r.maxX - r.width * 0.2
            p.move(to: CGPoint(x: x0, y: y)); p.addLine(to: CGPoint(x: x1, y: y))
            p.move(to: CGPoint(x: x1 - 8, y: y - 6)); p.addLine(to: CGPoint(x: x1, y: y)); p.addLine(to: CGPoint(x: x1 - 8, y: y + 6))
            ctx.stroke(p, with: .color(color), lineWidth: 2)
        }
    }

    private func drawDim(ctx: inout GraphicsContext, _ d: Dim, editable: Bool) {
        let a = d.a, b = d.b
        let color: Color = editable || d.dim.kind == .section ? Theme.accent : .secondary
        var p = Path()
        p.move(to: a); p.addLine(to: b)
        let vertical = abs(a.x - b.x) < 1
        for e in [a, b] {
            if vertical { p.move(to: CGPoint(x: e.x - 4, y: e.y)); p.addLine(to: CGPoint(x: e.x + 4, y: e.y)) }
            else { p.move(to: CGPoint(x: e.x, y: e.y - 4)); p.addLine(to: CGPoint(x: e.x, y: e.y + 4)) }
        }
        ctx.stroke(p, with: .color(color.opacity(0.8)), lineWidth: 1)
        let t = ctx.resolve(Text(d.text).font(.system(size: 11, weight: editable ? .semibold : .medium)).foregroundColor(color))
        let mid = CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
        let sz = t.measure(in: CGSize(width: 200, height: 40))
        let bg = CGRect(x: -sz.width / 2 - 5, y: -sz.height / 2 - 1, width: sz.width + 10, height: sz.height + 2)
        var c2 = ctx
        c2.translateBy(x: mid.x, y: mid.y)
        if vertical { c2.rotate(by: .degrees(-90)) }
        c2.fill(Path(roundedRect: bg, cornerRadius: 5), with: .color(Color(.secondarySystemGroupedBackground)))
        if editable {
            c2.fill(Path(roundedRect: bg, cornerRadius: 5), with: .color(Theme.accent.opacity(0.14)))
            c2.stroke(Path(roundedRect: bg, cornerRadius: 5), with: .color(Theme.accent.opacity(0.6)), lineWidth: 1)
        }
        c2.draw(t, at: .zero, anchor: .center)
    }
}

/// Чистый фасад окна (без размеров и значков открывания) — образец для ИИ-фото, до 512 px.
enum SchemeRender {
    @MainActor
    static func cleanPNG(spec: WindowSpec, frameHex: String) -> Data? {
        let ratio = CGFloat(spec.widthMm) / CGFloat(max(1, spec.heightMm))
        let w: CGFloat = ratio >= 1 ? 512 : max(128, 512 * ratio)
        let h: CGFloat = ratio >= 1 ? max(128, 512 / ratio) : 512
        let r = ImageRenderer(content: SchemeView(spec: spec, frameHex: frameHex, maxHeight: h, clean: true)
            .frame(width: w, height: h).background(Color.white).environment(\.colorScheme, .light))
        r.scale = 1
        return r.uiImage?.pngData()
    }
}

extension Color {
    init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("#") { s.removeFirst() }
        let v = UInt64(s, radix: 16) ?? 0x45494E
        self.init(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }
}

/// Размер схемы: ширина — сколько дали, высота = ширина по пропорции + поля под размеры, не больше maxHeight.
private struct SchemeSizing: ViewModifier {
    let ratio: CGFloat, pad: CGFloat, maxHeight: CGFloat
    func body(content: Content) -> some View {
        SchemeSizer(ratio: ratio, pad: pad, maxHeight: maxHeight) { content }
    }
}

private struct SchemeSizer: SwiftUI.Layout {
    let ratio: CGFloat, pad: CGFloat, maxHeight: CGFloat
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let w = proposal.width ?? 320
        let h = min((w - pad) / max(ratio, 0.05) + pad, maxHeight, proposal.height ?? .infinity)
        return CGSize(width: w, height: max(120, h))
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for s in subviews { s.place(at: bounds.origin, proposal: ProposedViewSize(bounds.size)) }
    }
}

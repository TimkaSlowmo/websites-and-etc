import SwiftUI

/// Схема изделия по сетке: рама цвета профиля, импосты, символы открывания (вершина — сторона ручки),
/// размеры секций и ячеек. Нажатие на ячейку выбирает её.
struct SchemeView: View {
    var spec: WindowSpec
    var frameHex: String = "#45494E"
    var selected: CellRect.ID? = nil
    var onTap: ((CellRect) -> Void)? = nil

    private let dimSpace: CGFloat = 34

    var body: some View {
        GeometryReader { geo in
            let box = fitBox(in: geo.size)
            let k = box.width / CGFloat(spec.widthMm)
            Canvas { ctx, _ in draw(ctx: &ctx, box: box, k: k) }
                .contentShape(Rectangle())
                .onTapGesture { p in
                    guard let onTap else { return }
                    for r in spec.cellRects where rect(r, box, k).contains(p) { onTap(r); return }
                }
        }
        .aspectRatio(aspect, contentMode: .fit)
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

    private func draw(ctx: inout GraphicsContext, box: CGRect, k: CGFloat) {
        let frameColor = Color(hex: frameHex)
        let profile = max(4, min(14, box.width / 28))
        let glass = Color(red: 0.80, green: 0.90, blue: 0.97)

        // Рама
        ctx.fill(Path(box), with: .color(frameColor))
        for r in spec.cellRects {
            let cr = rect(r, box, k)
            let inner = cr.insetBy(dx: profile * (r.cell.opening == .fixed ? 0.55 : 1.0), dy: profile * (r.cell.opening == .fixed ? 0.55 : 1.0))
            if r.cell.opening != .fixed {
                // Створка: тонкая линия контура внутри рамы
                ctx.stroke(Path(cr.insetBy(dx: profile * 0.5, dy: profile * 0.5)), with: .color(.white.opacity(0.35)), lineWidth: 1)
            }
            ctx.fill(Path(inner), with: .color(glass))
            ctx.fill(Path(CGRect(x: inner.minX, y: inner.minY, width: inner.width * 0.35, height: inner.height)),
                     with: .color(.white.opacity(0.18)))
            drawOpening(ctx: &ctx, cell: r.cell, in: inner)
            if r.cell.door {
                let hx = r.cell.hinge == .right ? inner.minX + 6 : inner.maxX - 10
                ctx.fill(Path(roundedRect: CGRect(x: hx, y: inner.midY - 14, width: 4, height: 28), cornerRadius: 2), with: .color(frameColor))
            }
            if r.id == selected {
                ctx.stroke(Path(cr.insetBy(dx: 1.5, dy: 1.5)), with: .color(Theme.accent), lineWidth: 3)
            }
            // Размер ячейки
            let label = "\(Int(r.w))×\(Int(r.h))"
            let text = Text(label).font(.system(size: max(8, min(12, cr.width / 7)), weight: .semibold)).foregroundColor(Theme.deep)
            let resolved = ctx.resolve(text)
            let sz = resolved.measure(in: cr.size)
            if sz.width < inner.width - 4 {
                let bg = CGRect(x: inner.midX - sz.width / 2 - 4, y: inner.maxY - sz.height - 6, width: sz.width + 8, height: sz.height + 2)
                ctx.fill(Path(roundedRect: bg, cornerRadius: 4), with: .color(.white.opacity(0.85)))
                ctx.draw(resolved, at: CGPoint(x: inner.midX, y: bg.midY), anchor: .center)
            }
        }

        // Размерные линии: общие и по секциям
        let dim = Color.secondary
        drawDim(ctx: &ctx, from: CGPoint(x: box.minX, y: box.minY - 14), to: CGPoint(x: box.maxX, y: box.minY - 14), text: "\(spec.widthMm)", color: dim)
        drawDim(ctx: &ctx, from: CGPoint(x: box.maxX + 14, y: box.minY), to: CGPoint(x: box.maxX + 14, y: box.maxY), text: "\(spec.heightMm)", color: dim)
        if spec.layout.sections.count > 1 {
            var a = 0.0
            for s in spec.layout.sections {
                if spec.layout.split == .columns {
                    let x0 = box.minX + CGFloat(a) * k, x1 = box.minX + CGFloat(a + Double(s.sizeMm)) * k
                    drawDim(ctx: &ctx, from: CGPoint(x: x0, y: box.maxY + 14), to: CGPoint(x: x1, y: box.maxY + 14), text: "\(s.sizeMm)", color: Theme.accent)
                } else {
                    let y0 = box.minY + CGFloat(a) * k, y1 = box.minY + CGFloat(a + Double(s.sizeMm)) * k
                    drawDim(ctx: &ctx, from: CGPoint(x: box.minX - 14, y: y0), to: CGPoint(x: box.minX - 14, y: y1), text: "\(s.sizeMm)", color: Theme.accent)
                }
                a += Double(s.sizeMm)
            }
        }
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

    private func drawDim(ctx: inout GraphicsContext, from a: CGPoint, to b: CGPoint, text: String, color: Color) {
        var p = Path()
        p.move(to: a); p.addLine(to: b)
        let vertical = abs(a.x - b.x) < 1
        for e in [a, b] {
            if vertical { p.move(to: CGPoint(x: e.x - 4, y: e.y)); p.addLine(to: CGPoint(x: e.x + 4, y: e.y)) }
            else { p.move(to: CGPoint(x: e.x, y: e.y - 4)); p.addLine(to: CGPoint(x: e.x, y: e.y + 4)) }
        }
        ctx.stroke(p, with: .color(color.opacity(0.8)), lineWidth: 1)
        let t = ctx.resolve(Text(text).font(.system(size: 11, weight: .medium)).foregroundColor(color))
        let mid = CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
        if vertical {
            var c2 = ctx
            c2.translateBy(x: mid.x, y: mid.y)
            c2.rotate(by: .degrees(-90))
            let sz = t.measure(in: CGSize(width: 200, height: 40))
            c2.fill(Path(CGRect(x: -sz.width / 2 - 3, y: -sz.height / 2, width: sz.width + 6, height: sz.height)), with: .color(Color(.systemBackground)))
            c2.draw(t, at: .zero, anchor: .center)
        } else {
            let sz = t.measure(in: CGSize(width: 200, height: 40))
            ctx.fill(Path(CGRect(x: mid.x - sz.width / 2 - 3, y: mid.y - sz.height / 2, width: sz.width + 6, height: sz.height)), with: .color(Color(.systemBackground)))
            ctx.draw(t, at: mid, anchor: .center)
        }
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

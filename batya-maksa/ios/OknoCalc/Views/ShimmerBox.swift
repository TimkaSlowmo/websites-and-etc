import SwiftUI

/// Плейсхолдер с переливом, пока ИИ рисует фото.
struct ShimmerBox: View {
    var text: String
    @State private var phase: CGFloat = -1

    var body: some View {
        RoundedRectangle(cornerRadius: 12, style: .continuous)
            .fill(Color(.secondarySystemBackground))
            .overlay {
                GeometryReader { geo in
                    LinearGradient(colors: [.clear, Theme.accent.opacity(0.25), .clear], startPoint: .leading, endPoint: .trailing)
                        .frame(width: geo.size.width * 0.6)
                        .offset(x: phase * geo.size.width)
                }
                .clipped()
            }
            .overlay {
                VStack(spacing: 8) {
                    Image(systemName: "sparkles").font(.title).foregroundStyle(Theme.accent)
                        .symbolEffect(.pulse)
                    Text(text).font(.footnote).foregroundStyle(.secondary)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .onAppear {
                withAnimation(.linear(duration: 1.3).repeatForever(autoreverses: false)) { phase = 1.4 }
            }
    }
}

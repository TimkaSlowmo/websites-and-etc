import SwiftUI

/// Экран анализа: линия сканирования по чертежу и сменяющиеся шаги.
struct AnalyzingView: View {
    let image: UIImage?
    /// Текст кнопки отмены («Отмена» или «Вернуться к КП»)
    var cancelTitle = "Отмена"
    var onCancel: (() -> Void)? = nil
    @State private var scan = false
    @State private var step = 0
    @State private var appeared = false
    private let steps = ["Читаю размеры…", "Сверяю цифры двумя моделями…", "Определяю створки…", "Подбираю позиции каталога…"]
    private let timer = Timer.publish(every: 1.6, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(spacing: 22) {
            Spacer()
            ZStack {
                if let image {
                    Image(uiImage: image).resizable().scaledToFit()
                        .overlay(Color.black.opacity(0.15))
                } else {
                    RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemBackground)).frame(height: 260)
                }
            }
            .frame(maxHeight: 300)
            .overlay(alignment: .top) {
                GeometryReader { geo in
                    LinearGradient(colors: [Theme.accent.opacity(0), Theme.accent.opacity(0.55), Theme.accent.opacity(0)],
                                   startPoint: .top, endPoint: .bottom)
                        .frame(height: 70)
                        .overlay(Rectangle().fill(Theme.accent).frame(height: 2))
                        .offset(y: scan ? geo.size.height - 35 : -35)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Theme.accent.opacity(0.6), lineWidth: 2))
            .shadow(color: Theme.accent.opacity(0.25), radius: 20)
            .padding(.horizontal, 28)
            .scaleEffect(appeared ? 1 : 0.92)
            .opacity(appeared ? 1 : 0)

            HStack(spacing: 10) {
                ProgressView().tint(Theme.accent)
                Text(steps[step])
                    .font(.headline)
                    .contentTransition(.opacity)
                    .id(step)
                    .transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity),
                                            removal: .move(edge: .top).combined(with: .opacity)))
            }
            .frame(height: 30)
            .clipped()

            HStack(spacing: 6) {
                ForEach(steps.indices, id: \.self) { i in
                    Capsule().fill(i <= step ? Theme.accent : Color.secondary.opacity(0.25))
                        .frame(width: i == step ? 22 : 8, height: 8)
                }
            }
            .animation(.spring(response: 0.4, dampingFraction: 0.7), value: step)
            Spacer()
            if let onCancel {
                Button(cancelTitle, action: onCancel)
                    .font(.body.weight(.medium))
                    .padding(.bottom, 20)
            }
        }
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.8)) { appeared = true }
            withAnimation(.easeInOut(duration: 1.6).repeatForever(autoreverses: true)) { scan = true }
        }
        .onReceive(timer) { _ in
            withAnimation(.spring(response: 0.45, dampingFraction: 0.85)) { step = min(step + 1, steps.count - 1) }
        }
    }
}

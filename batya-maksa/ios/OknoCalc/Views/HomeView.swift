import SwiftUI
import PhotosUI

struct HomeView: View {
    @EnvironmentObject private var vm: EstimateViewModel
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var history: HistoryStore
    @State private var showCamera = false
    @State private var photoItem: PhotosPickerItem?

    var body: some View {
        VStack(spacing: 0) {
            switch vm.phase {
            case .idle: idle.transition(.opacity.combined(with: .scale(scale: 0.97)))
            case .analyzing: AnalyzingView(image: vm.drawing).transition(.opacity)
            case .failed(let msg): failed(msg).transition(.opacity)
            case .ready: ResultView().transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.5, dampingFraction: 0.88), value: vm.phase)
        .navigationTitle(vm.phase == .idle ? "Проём" : "Расчёт")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                if vm.phase != .idle {
                    Button { vm.reset() } label: { Label("Новый", systemImage: "plus") }
                }
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { vm.start(with: $0) }.ignoresSafeArea()
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) {
                    vm.start(with: img.normalizedOrientation())
                }
                photoItem = nil
            }
        }
    }

    private var idle: some View {
        VStack(spacing: 20) {
            Spacer()
            Image("Logo").resizable().scaledToFit().frame(width: 120, height: 120)
                .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                .shadow(color: Theme.deep.opacity(0.3), radius: 16, y: 8)
            Text("Сфотографируйте чертёж окна").font(.title2.bold())
            Text("ИИ прочитает размеры и параметры, покажет, как изделие будет выглядеть, и рассчитает ориентировочную цену.")
                .multilineTextAlignment(.center).foregroundStyle(.secondary).padding(.horizontal, 32)

            Spacer()

            VStack(spacing: 12) {
                Button { showCamera = true } label: {
                    Label("Снять чертёж", systemImage: "camera.fill").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent).controlSize(.large)
                .disabled(!CameraPicker.isAvailable)
                .symbolEffect(.bounce, value: showCamera)

                PhotosPicker(selection: $photoItem, matching: .images) {
                    Label("Выбрать из галереи", systemImage: "photo.on.rectangle").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered).controlSize(.large)

                Button("Ввести параметры вручную") { vm.startManual() }
                    .font(.footnote)
            }
            .padding(.horizontal, 24).padding(.bottom, 12)

            if let last = history.items.first {
                Button { vm.load(last) } label: {
                    HStack {
                        Image(systemName: "clock.arrow.circlepath").foregroundStyle(Theme.accent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Последний расчёт").font(.caption).foregroundStyle(.secondary)
                            Text(verbatim: "\(last.spec.product.title) \(last.spec.widthMm)×\(last.spec.heightMm)").font(.callout)
                        }
                        Spacer()
                        Text(Money.format(last.total, currency: last.currency)).font(.callout.bold())
                    }
                    .padding(12)
                    .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 24).padding(.bottom, 16)
            }
        }
    }

    private func failed(_ msg: String) -> some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 48)).foregroundStyle(.orange)
            Text(msg).multilineTextAlignment(.center).padding(.horizontal, 32)
            Button("Повторить") { vm.retry() }.buttonStyle(.borderedProminent)
            Button("Ввести вручную") { vm.startManual() }
            Spacer()
        }
    }
}

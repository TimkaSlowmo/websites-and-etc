import SwiftUI
import PhotosUI

struct HomeView: View {
    @EnvironmentObject private var vm: EstimateViewModel
    @EnvironmentObject private var settings: AppSettings
    @State private var showCamera = false
    @State private var photoItem: PhotosPickerItem?
    @State private var showSettings = false

    var body: some View {
        VStack(spacing: 0) {
            switch vm.phase {
            case .idle: idle
            case .analyzing: analyzing
            case .failed(let msg): failed(msg)
            case .ready: ResultView()
            }
        }
        .navigationTitle("Батя Макса")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                if vm.phase != .idle {
                    Button { vm.reset() } label: { Label("Новый", systemImage: "plus") }
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button { showSettings = true } label: { Image(systemName: "gearshape") }
            }
        }
        .sheet(isPresented: $showSettings) { NavigationStack { SettingsView() } }
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
            Image(systemName: "window.vertical.closed")
                .font(.system(size: 72)).foregroundStyle(.tint)
            Text("Сфотографируйте чертёж окна").font(.title2.bold())
            Text("ИИ прочитает размеры и параметры, покажет, как изделие будет выглядеть, и рассчитает ориентировочную цену.")
                .multilineTextAlignment(.center).foregroundStyle(.secondary).padding(.horizontal, 32)

            if !settings.isConfigured {
                Label("Сначала укажите адрес сервера в настройках", systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(.orange)
            }
            Spacer()

            VStack(spacing: 12) {
                Button { showCamera = true } label: {
                    Label("Снять чертёж", systemImage: "camera.fill").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent).controlSize(.large)
                .disabled(!CameraPicker.isAvailable)

                PhotosPicker(selection: $photoItem, matching: .images) {
                    Label("Выбрать из галереи", systemImage: "photo.on.rectangle").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered).controlSize(.large)

                Button("Ввести параметры вручную") { vm.startManual() }
                    .font(.footnote)
            }
            .padding(.horizontal, 24).padding(.bottom, 24)
        }
    }

    private var analyzing: some View {
        VStack(spacing: 16) {
            Spacer()
            if let img = vm.drawing {
                Image(uiImage: img).resizable().scaledToFit().frame(maxHeight: 280)
                    .clipShape(RoundedRectangle(cornerRadius: 12)).padding(.horizontal, 24)
            }
            ProgressView()
            Text("Читаю чертёж…").foregroundStyle(.secondary)
            Spacer()
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

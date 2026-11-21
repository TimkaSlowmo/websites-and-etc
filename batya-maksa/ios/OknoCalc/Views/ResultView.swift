import SwiftUI

struct ResultView: View {
    @EnvironmentObject private var vm: EstimateViewModel
    @EnvironmentObject private var history: HistoryStore
    @State private var tab = 0
    @State private var pdfURL: URL?
    @State private var saved = false

    var body: some View {
        let catalog = vm.catalog
        let b = vm.breakdown

        Form {
            // Изображение: чертёж / готовый вид
            Section {
                VStack(spacing: 10) {
                    Picker("", selection: $tab) {
                        Text("Чертёж").tag(0)
                        Text("Готовый вид").tag(1)
                    }
                    .pickerStyle(.segmented)

                    if tab == 0 {
                        if let img = vm.drawing {
                            Image(uiImage: img).resizable().scaledToFit().frame(maxHeight: 320)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        } else {
                            placeholder("Чертёж не загружен (ручной ввод)")
                        }
                    } else {
                        renderArea
                    }
                }
                .listRowInsets(EdgeInsets(top: 12, leading: 12, bottom: 12, trailing: 12))
            }

            if vm.spec.confidence < 0.6 && vm.drawing != nil {
                Section {
                    Label("ИИ не уверен в распознавании — проверьте размеры и параметры ниже.",
                          systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.orange).font(.footnote)
                }
            }

            // Параметры (редактируемые)
            Section("Параметры изделия") {
                LabeledContent("Ширина, мм") {
                    TextField("мм", value: $vm.spec.widthMm, format: .number.grouping(.never))
                        .keyboardType(.numberPad).multilineTextAlignment(.trailing)
                }
                LabeledContent("Высота, мм") {
                    TextField("мм", value: $vm.spec.heightMm, format: .number.grouping(.never))
                        .keyboardType(.numberPad).multilineTextAlignment(.trailing)
                }
                if let alt = vm.spec.alternatives,
                   !(alt.widthMm ?? []).isEmpty || !(alt.heightMm ?? []).isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Label("ИИ сомневается. Проверьте по чертежу:", systemImage: "questionmark.circle")
                            .font(.footnote).foregroundStyle(.orange)
                        HStack {
                            ForEach(alt.widthMm ?? [], id: \.self) { w in
                                altChip("Ширина \(w)") { vm.spec.widthMm = w; vm.spec.alternatives?.widthMm = [] }
                            }
                            ForEach(alt.heightMm ?? [], id: \.self) { h in
                                altChip("Высота \(h)") { vm.spec.heightMm = h; vm.spec.alternatives?.heightMm = [] }
                            }
                        }
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
                LabeledContent("Площадь", value: String(format: "%.2f м²", vm.spec.areaM2))
                Picker("Вид", selection: $vm.spec.type) {
                    ForEach(WindowType.allCases) { Text($0.title).tag($0) }
                }
                Picker("Профиль", selection: $vm.selection.profileId) {
                    ForEach(catalog.profiles) { Text($0.name).tag($0.id) }
                }
                Picker("Стеклопакет", selection: $vm.selection.glassId) {
                    ForEach(catalog.glass) { Text($0.name).tag($0.id) }
                }
                if !catalog.colors.isEmpty {
                    Picker("Цвет", selection: $vm.selection.colorId) {
                        ForEach(catalog.colors) { Text($0.name).tag($0.id) }
                    }
                }
            }

            Section {
                ForEach($vm.spec.sashes) { $s in
                    Picker("Секция", selection: $s.opening) {
                        ForEach(OpeningKind.allCases) { Text($0.title).tag($0) }
                    }
                }
                .onDelete { vm.spec.sashes.remove(atOffsets: $0) }
                Button { vm.spec.sashes.append(Sash(widthMm: nil, opening: .fixed)) } label: {
                    Label("Добавить секцию", systemImage: "plus.circle")
                }
            } header: { Text("Створки (слева направо)") }

            if !catalog.extras.isEmpty {
                Section("Дополнительно") {
                    ForEach(catalog.extras) { e in
                        Toggle(e.name, isOn: Binding(
                            get: { vm.selection.extraIds.contains(e.id) },
                            set: { on in
                                if on { vm.selection.extraIds.insert(e.id) } else { vm.selection.extraIds.remove(e.id) }
                            }))
                    }
                    Toggle("Монтаж", isOn: $vm.selection.includeInstallation)
                }
            }

            if !vm.spec.notes.isEmpty {
                Section("Заметки с чертежа") {
                    Text(vm.spec.notes).font(.callout)
                }
            }

            // Цена
            Section {
                ForEach(b.lines) { line in
                    HStack(alignment: .firstTextBaseline) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(line.title)
                            Text(line.detail).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(Money.format(line.amount, currency: b.currency)).monospacedDigit()
                    }
                }
                if b.markup > 0 { LabeledContent("Наценка", value: Money.format(b.markup, currency: b.currency)) }
                if b.vat > 0 { LabeledContent("НДС", value: Money.format(b.vat, currency: b.currency)) }
                HStack {
                    Text("Итого").font(.headline)
                    Spacer()
                    Text(Money.format(b.total, currency: b.currency)).font(.title2.bold()).monospacedDigit()
                        .foregroundStyle(Theme.accent)
                        .contentTransition(.numericText(value: b.total))
                        .animation(.snappy(duration: 0.35), value: b.total)
                }
            } header: { Text("Ориентировочная стоимость") }
            footer: { Text("Расчёт по каталогу «\(catalog.name)». Точную цену подтверждает замер.") }

            Section {
                Button {
                    pdfURL = PDFExporter.makeOffer(spec: vm.spec, breakdown: b, catalogName: catalog.name,
                                                   company: vm.companyName, drawing: vm.drawing, render: vm.renderImage)
                } label: { Label("Создать КП (PDF)", systemImage: "doc.richtext") }
                if let pdfURL {
                    ShareLink(item: pdfURL) { Label("Отправить КП", systemImage: "square.and.arrow.up") }
                }
                ShareLink(item: vm.summaryText) { Label("Поделиться текстом расчёта", systemImage: "text.bubble") }
                Button { vm.saveToHistory(); saved = true } label: {
                    Label(saved ? "Сохранено" : "Сохранить в историю", systemImage: saved ? "checkmark.circle.fill" : "tray.and.arrow.down")
                }
                .disabled(saved)
            }
        }
    }

    @ViewBuilder private var renderArea: some View {
        if let img = vm.renderImage {
            VStack(spacing: 8) {
                Image(uiImage: img).resizable().scaledToFit().frame(maxHeight: 320)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
                Text("Примерный вид, сгенерирован ИИ").font(.caption).foregroundStyle(.secondary)
                regenerateButton
            }
        } else if vm.isRendering {
            ShimmerBox(text: "Рисую готовое окно…").frame(height: 240)
        } else {
            VStack(spacing: 10) {
                placeholder(vm.renderError ?? "Фото ещё не создано")
                regenerateButton
            }
        }
    }

    private var regenerateButton: some View {
        Button { Task { await vm.renderPhoto() } } label: {
            Label("Сгенерировать заново по параметрам", systemImage: "arrow.clockwise")
        }
        .buttonStyle(.bordered).disabled(vm.isRendering)
    }

    private func altChip(_ title: String, action: @escaping () -> Void) -> some View {
        Button {
            withAnimation(.spring) { action() }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } label: {
            Text(title).font(.footnote.bold()).padding(.horizontal, 12).padding(.vertical, 6)
                .background(Theme.accent.opacity(0.15), in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private func placeholder(_ text: String) -> some View {
        Text(text).font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, minHeight: 120)
    }
}

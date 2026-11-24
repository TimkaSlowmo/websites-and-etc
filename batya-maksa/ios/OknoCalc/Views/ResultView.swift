import SwiftUI

struct ResultView: View {
    @EnvironmentObject private var vm: EstimateViewModel
    @EnvironmentObject private var catalogStore: CatalogStore
    @State private var tab = 0
    @State private var pdfURL: URL?
    @State private var saved = false
    @State private var editing: CellRect?
    @State private var expanded: Set<LineGroup> = []

    var body: some View {
        let catalog = catalogStore.catalog
        let b = vm.breakdown
        let system = catalog.system(vm.selection.systemId)
        let outside = catalog.color(vm.selection.colorId)

        Form {
            // Схема / чертёж / готовый вид
            Section {
                VStack(spacing: 10) {
                    Picker("", selection: $tab) {
                        Text("Схема").tag(0)
                        Text("Чертёж").tag(1)
                        Text("Готовый вид").tag(2)
                    }
                    .pickerStyle(.segmented)

                    switch tab {
                    case 0:
                        SchemeView(spec: vm.spec, frameHex: outside?.hex ?? "#45494E", selected: editing?.id) { r in
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            editing = r
                        }
                        .frame(maxHeight: 380)
                        Text("Нажмите на ячейку, чтобы изменить открывание и размер")
                            .font(.caption).foregroundStyle(.secondary)
                    case 1:
                        if let img = vm.drawing {
                            Image(uiImage: img).resizable().scaledToFit().frame(maxHeight: 360)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        } else {
                            placeholder("Чертёж не загружен (ручной ввод)")
                        }
                    default:
                        renderArea
                    }
                }
                .animation(.snappy, value: tab)
                .listRowInsets(EdgeInsets(top: 12, leading: 12, bottom: 12, trailing: 12))
            }

            if vm.spec.confidence < 0.6 && vm.drawing != nil {
                Section {
                    Label("ИИ не уверен в распознавании — сверьте схему с чертежом.", systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.orange).font(.footnote)
                }
            }
            if !b.warnings.isEmpty {
                Section {
                    ForEach(b.warnings, id: \.self) { w in
                        Label(w, systemImage: "exclamationmark.circle").font(.footnote).foregroundStyle(.orange)
                    }
                    if vm.spec.layoutMismatch != nil {
                        Button("Подогнать сетку под размеры") { withAnimation { vm.spec.fitLayout() } }
                    }
                }
            }

            // Данные с чертежа
            if !vm.spec.info.isEmpty || !vm.spec.notes.isEmpty {
                Section("С чертежа") {
                    ForEach(vm.spec.info) { item in
                        LabeledContent(item.label, value: item.value)
                    }
                    if !vm.spec.notes.isEmpty {
                        DisclosureGroup("Весь текст") {
                            Text(vm.spec.notes).font(.callout).textSelection(.enabled)
                        }
                    }
                }
            }

            // Изделие
            Section("Изделие") {
                Picker("Изделие", selection: $vm.spec.product) {
                    ForEach(Product.allCases) { Text($0.title).tag($0) }
                }
                Picker("Система", selection: $vm.selection.systemId) {
                    ForEach(catalog.systems) { s in Text(s.name).tag(s.id) }
                }
                if let system { Text(system.description).font(.caption).foregroundStyle(.secondary) }
                LabeledContent("Ширина, мм") {
                    TextField("мм", value: Binding(get: { vm.spec.widthMm }, set: { vm.setSize(width: $0) }), format: .number.grouping(.never))
                        .keyboardType(.numberPad).multilineTextAlignment(.trailing)
                }
                LabeledContent("Высота, мм") {
                    TextField("мм", value: Binding(get: { vm.spec.heightMm }, set: { vm.setSize(height: $0) }), format: .number.grouping(.never))
                        .keyboardType(.numberPad).multilineTextAlignment(.trailing)
                }
                if let alt = vm.spec.alternatives, !(alt.widthMm ?? []).isEmpty || !(alt.heightMm ?? []).isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Label("ИИ сомневается. Проверьте по чертежу:", systemImage: "questionmark.circle")
                            .font(.footnote).foregroundStyle(.orange)
                        HStack {
                            ForEach(alt.widthMm ?? [], id: \.self) { w in
                                altChip("Ширина \(w)") { vm.setSize(width: w); vm.spec.alternatives?.widthMm = [] }
                            }
                            ForEach(alt.heightMm ?? [], id: \.self) { h in
                                altChip("Высота \(h)") { vm.setSize(height: h); vm.spec.alternatives?.heightMm = [] }
                            }
                        }
                    }
                }
                LabeledContent("Площадь", value: String(format: "%.2f м²", vm.spec.areaM2))
                Stepper(value: $vm.spec.quantity, in: 1...999) {
                    LabeledContent("Количество", value: "\(vm.spec.quantity) шт")
                }
            }

            Section("Заполнение, цвет, монтаж") {
                Picker("Заполнение", selection: $vm.selection.fillMm) {
                    ForEach(fillOptions(system: system, catalog: catalog), id: \.self) { f in Text("\(f) мм").tag(f) }
                }
                if let gi = catalog.glassItem(for: vm.selection.fillMm) {
                    LabeledContent("Стеклопакет", value: gi.name).font(.callout)
                }
                LabeledContent("Формула") {
                    TextField("например 4-10-4-10-4", text: $vm.spec.glass).multilineTextAlignment(.trailing)
                }
                NavigationLink {
                    ColorPickerView(title: "Цвет снаружи", selectedId: $vm.selection.colorId, allowSame: false)
                } label: {
                    colorRow("Цвет снаружи", catalog.color(vm.selection.colorId))
                }
                NavigationLink {
                    ColorPickerView(title: "Цвет внутри", selectedId: $vm.selection.colorInsideId, allowSame: true)
                } label: {
                    colorRow("Цвет внутри", vm.selection.colorInsideId.isEmpty ? nil : catalog.color(vm.selection.colorInsideId), emptyText: "Как снаружи")
                }
                Toggle("Монтаж", isOn: $vm.selection.installation)
            }

            // Сетка
            Section {
                Picker("Деление", selection: Binding(get: { vm.spec.layout.split }, set: { s in withAnimation { vm.setSplit(s) } })) {
                    ForEach(Split.allCases) { Text($0.title).tag($0) }
                }
                ForEach(Array(vm.spec.layout.sections.enumerated()), id: \.element.id) { si, s in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("\(vm.spec.layout.split.sectionName) \(si + 1)").font(.headline)
                            Spacer()
                            if vm.spec.layout.sections.count > 1 {
                                TextField("мм", value: Binding(get: { s.sizeMm }, set: { vm.setSectionSize(si, $0) }), format: .number.grouping(.never))
                                    .keyboardType(.numberPad).multilineTextAlignment(.trailing).frame(width: 70)
                                Text("мм").foregroundStyle(.secondary)
                            } else {
                                Text("\(s.sizeMm) мм").foregroundStyle(.secondary)
                            }
                        }
                        ForEach(Array(s.cells.enumerated()), id: \.element.id) { ci, c in
                            Button {
                                if let r = vm.spec.cellRects.first(where: { $0.section == si && $0.index == ci }) { editing = r }
                            } label: {
                                HStack {
                                    Image(systemName: c.door ? "door.left.hand.open" : (c.opening == .fixed ? "square" : "macwindow"))
                                        .foregroundStyle(Theme.accent).frame(width: 22)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Ячейка \(ci + 1) · \(c.sizeMm) мм").foregroundStyle(.primary)
                                        Text(cellSummary(c)).font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                        HStack {
                            Button { withAnimation { vm.splitCell(section: si, index: s.cells.count - 1) } } label: {
                                Label(vm.spec.layout.split == .columns ? "Разделить по высоте" : "Разделить по ширине", systemImage: "rectangle.split.2x1")
                            }
                            .font(.footnote)
                            Spacer()
                            if vm.spec.layout.sections.count > 1 {
                                Button(role: .destructive) { withAnimation { vm.removeSection(si) } } label: { Image(systemName: "trash") }
                                    .font(.footnote)
                            }
                        }
                        .buttonStyle(.borderless)
                    }
                    .padding(.vertical, 4)
                }
                Button { withAnimation { vm.addSection() } } label: {
                    Label("Добавить \(vm.spec.layout.split == .columns ? "колонку" : "ряд")", systemImage: "plus.circle")
                }
            } header: { Text("Сетка и створки") }
            footer: { Text("Колонки — деление сверху донизу, ряды — слева направо. Ячейка делит только свою секцию.") }

            // Стоимость
            Section {
                ForEach(LineGroup.allCases, id: \.self) { g in
                    let lines = b.lines.filter { $0.group == g }
                    if !lines.isEmpty {
                        DisclosureGroup(isExpanded: Binding(get: { expanded.contains(g) }, set: { if $0 { expanded.insert(g) } else { expanded.remove(g) } })) {
                            ForEach(lines) { line in
                                HStack(alignment: .firstTextBaseline) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(line.title).font(.callout)
                                        Text((line.code.isEmpty ? "" : line.code + " · ") + line.detail).font(.caption2).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text(Money.format(line.amount, currency: b.currency)).font(.callout).monospacedDigit()
                                }
                            }
                        } label: {
                            HStack {
                                Text(g.title)
                                Spacer()
                                Text(Money.format(b.sum(g), currency: b.currency)).monospacedDigit().foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                LabeledContent("Наценка \(Int(catalog.markupPercent))%", value: Money.format(b.markup, currency: b.currency))
                LabeledContent("НДС \(Int(catalog.vatPercent))%", value: Money.format(b.vat, currency: b.currency))
                HStack {
                    Text(b.quantity > 1 ? "За 1 шт" : "Итого").font(.headline)
                    Spacer()
                    Text(Money.format(b.unitTotal, currency: b.currency)).font(b.quantity > 1 ? .headline : .title2.bold()).monospacedDigit()
                        .foregroundStyle(Theme.accent)
                        .contentTransition(.numericText(value: b.unitTotal))
                        .animation(.snappy(duration: 0.35), value: b.unitTotal)
                }
                if b.quantity > 1 {
                    HStack {
                        Text("Итого за \(b.quantity) шт").font(.headline)
                        Spacer()
                        Text(Money.format(b.total, currency: b.currency)).font(.title2.bold()).monospacedDigit()
                            .foregroundStyle(Theme.accent)
                            .contentTransition(.numericText(value: b.total))
                            .animation(.snappy(duration: 0.35), value: b.total)
                    }
                }
            } header: { Text("Стоимость (по прайсу \(catalog.name))") }
            footer: { Text("Профили, уплотнители и комплектующие — по артикулам прайса. Стеклопакеты, фурнитура и работа — по ставкам из «Настроек». Точную цену подтверждает замер.") }

            Section {
                Button {
                    pdfURL = PDFExporter.makeOffer(spec: vm.spec, selection: vm.selection, breakdown: b, catalog: catalog,
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
        .sheet(item: $editing) { r in
            CellEditor(section: r.section, index: r.index).presentationDetents([.medium, .large])
        }
        .onChange(of: vm.selection.systemId) { _, id in
            if let s = catalog.system(id), !(s.fillMin...s.fillMax).contains(vm.selection.fillMm) { vm.selection.fillMm = s.fillDefault }
        }
    }

    private func fillOptions(system: SystemDef?, catalog: Catalog) -> [Int] {
        var opts = catalog.glass.map(\.fillMm)
        if let s = system {
            opts = opts.filter { $0 >= s.fillMin && $0 <= s.fillMax }
            if !opts.contains(s.fillDefault) { opts.append(s.fillDefault) }
        }
        if !opts.contains(vm.selection.fillMm) { opts.append(vm.selection.fillMm) }
        return Array(Set(opts)).sorted()
    }

    private func cellSummary(_ c: Cell) -> String {
        var parts = [c.door ? "Дверь, " + c.opening.title.lowercased() : c.opening.title]
        if c.hinge != .none { parts.append(c.hinge.title.lowercased()) }
        if c.opening != .fixed { parts.append(c.inward ? "внутрь" : "наружу") }
        return parts.joined(separator: ", ")
    }

    private func colorRow(_ title: String, _ c: ColorItem?, emptyText: String = "—") -> some View {
        HStack {
            Text(title)
            Spacer()
            if let c {
                Circle().fill(Color(hex: c.hex)).frame(width: 18, height: 18).overlay(Circle().stroke(.secondary.opacity(0.4)))
                Text(c.code).foregroundStyle(.secondary)
            } else {
                Text(emptyText).foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder private var renderArea: some View {
        if let img = vm.renderImage {
            VStack(spacing: 8) {
                Image(uiImage: img).resizable().scaledToFit().frame(maxHeight: 340)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
                Text("Примерный вид, сгенерирован ИИ").font(.caption).foregroundStyle(.secondary)
                regenerateButton
            }
        } else if vm.isRendering {
            ShimmerBox(text: "Рисую готовое изделие…").frame(height: 240)
        } else {
            VStack(spacing: 10) {
                placeholder(vm.renderError ?? "Фото ещё не создано")
                regenerateButton
            }
        }
    }

    private var regenerateButton: some View {
        Button { Task { await vm.renderPhoto() } } label: {
            Label("Сгенерировать по параметрам", systemImage: "arrow.clockwise")
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

/// Редактор одной ячейки: открывание, петли, дверь, направление, размер, деление.
struct CellEditor: View {
    @EnvironmentObject private var vm: EstimateViewModel
    @Environment(\.dismiss) private var dismiss
    let section: Int
    let index: Int

    var body: some View {
        NavigationStack {
            if let cell = current {
                Form {
                    Section {
                        Picker("Открывание", selection: bind(\.opening, cell)) {
                            ForEach(Opening.allCases) { Text($0.title).tag($0) }
                        }
                        .pickerStyle(.inline)
                        .labelsHidden()
                    } header: { Text("Открывание") }
                    if cell.opening.hinges.count > 1 {
                        Section("Петли") {
                            Picker("Петли", selection: bind(\.hinge, cell)) {
                                ForEach(cell.opening.hinges) { Text($0.title).tag($0) }
                            }
                            .pickerStyle(.segmented)
                        }
                    }
                    if cell.opening != .fixed {
                        Section {
                            Toggle("Дверная створка", isOn: bind(\.door, cell))
                            Picker("Направление", selection: bind(\.inward, cell)) {
                                Text("Внутрь").tag(true)
                                Text("Наружу").tag(false)
                            }
                            .pickerStyle(.segmented)
                        }
                    }
                    Section("Размер") {
                        let sec = vm.spec.layout.sections[section]
                        if sec.cells.count > 1 {
                            LabeledContent(vm.spec.layout.split == .columns ? "Высота, мм" : "Ширина, мм") {
                                TextField("мм", value: Binding(get: { cell.sizeMm }, set: { vm.setCellSize(section: section, index: index, $0) }),
                                          format: .number.grouping(.never))
                                    .keyboardType(.numberPad).multilineTextAlignment(.trailing)
                            }
                        } else {
                            LabeledContent(vm.spec.layout.split == .columns ? "Высота" : "Ширина", value: "\(cell.sizeMm) мм")
                        }
                        LabeledContent(vm.spec.layout.split == .columns ? "Ширина колонки" : "Высота ряда", value: "\(sec.sizeMm) мм")
                    }
                    Section {
                        Button { withAnimation { vm.splitCell(section: section, index: index) } } label: {
                            Label("Разделить ячейку пополам", systemImage: "rectangle.split.2x1")
                        }
                        if vm.spec.layout.sections[section].cells.count > 1 {
                            Button(role: .destructive) { vm.removeCell(section: section, index: index); dismiss() } label: {
                                Label("Убрать деление (объединить)", systemImage: "rectangle")
                            }
                        }
                    }
                }
                .navigationTitle("\(vm.spec.layout.split.sectionName) \(section + 1), ячейка \(index + 1)")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Готово") { dismiss() } } }
            } else {
                Text("Ячейка удалена").onAppear { dismiss() }
            }
        }
    }

    private var current: Cell? {
        guard vm.spec.layout.sections.indices.contains(section), vm.spec.layout.sections[section].cells.indices.contains(index) else { return nil }
        return vm.spec.layout.sections[section].cells[index]
    }

    private func bind<T>(_ kp: WritableKeyPath<Cell, T>, _ cell: Cell) -> Binding<T> {
        Binding(get: { current?[keyPath: kp] ?? cell[keyPath: kp] },
                set: { v in withAnimation(.snappy) { vm.updateCell(section: section, index: index) { $0[keyPath: kp] = v } } })
    }
}

/// Выбор цвета с образцами, поиском и группами (RAL, анодирование, декор).
struct ColorPickerView: View {
    @EnvironmentObject private var catalogStore: CatalogStore
    @Environment(\.dismiss) private var dismiss
    let title: String
    @Binding var selectedId: String
    var allowSame: Bool
    @State private var query = ""

    var body: some View {
        let c = catalogStore.catalog
        List {
            if allowSame {
                Button { selectedId = ""; dismiss() } label: {
                    HStack { Text("Как снаружи"); Spacer(); if selectedId.isEmpty { Image(systemName: "checkmark").foregroundStyle(Theme.accent) } }
                }
            }
            ForEach(c.colorGroups) { g in
                let items = c.colors.filter { $0.group == g.id && matches($0) }
                if !items.isEmpty {
                    Section {
                        ForEach(items) { col in
                            Button { selectedId = col.id; dismiss() } label: {
                                HStack(spacing: 12) {
                                    RoundedRectangle(cornerRadius: 6).fill(Color(hex: col.hex)).frame(width: 34, height: 34)
                                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(.secondary.opacity(0.3)))
                                    VStack(alignment: .leading) {
                                        Text(col.code).font(.headline)
                                        Text(col.name).font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    if col.id == selectedId { Image(systemName: "checkmark").foregroundStyle(Theme.accent) }
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    } header: {
                        Text(g.surchargePercent > 0 ? "\(g.name) · +\(Int(g.surchargePercent))%" : g.name)
                    }
                }
            }
        }
        .searchable(text: $query, prompt: "RAL 7024, графит, дуб…")
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func matches(_ c: ColorItem) -> Bool {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        return q.isEmpty || c.code.lowercased().contains(q) || c.name.lowercased().contains(q)
    }
}

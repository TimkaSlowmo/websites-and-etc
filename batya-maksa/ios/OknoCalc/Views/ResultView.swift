import SwiftUI

struct ResultView: View {
    @EnvironmentObject private var vm: EstimateViewModel
    @EnvironmentObject private var catalogStore: CatalogStore
    @State private var tab = 0
    @State private var pdfURL: ShareFile?
    @State private var editing: CellRect?
    @State private var expanded: Set<LineGroup> = []
    @State private var dim: SchemeDimension?
    @State private var dimText = ""
    @State private var confirmRemove = false

    var body: some View {
        let catalog = catalogStore.catalog
        let o = vm.offer
        let b = o.items.indices.contains(vm.current) ? o.items[vm.current] : nil
        let system = catalog.system(vm.selection.systemId)
        let outside = catalog.color(vm.selection.colorId)

        Form {
            stripAndScheme(o, outside)
            sizesSection()
            warningsSections(b)
            productSections(catalog, system)
            gridSection()
            costSection(b, catalog)
            offerSection(o, catalog)
            actionsSection(o, catalog)
        }
        .keyboardDone()
        .sheet(item: $pdfURL) { f in ShareSheet(items: [f.url]).presentationDetents([.medium, .large]) }
        .sheet(item: $editing) { r in
            CellEditor(section: r.section, index: r.index).presentationDetents([.medium, .large])
        }
        .alert(dim?.title ?? "", isPresented: Binding(get: { dim != nil }, set: { if !$0 { dim = nil } })) {
            TextField("мм", text: $dimText).keyboardType(.numberPad)
            Button("Отмена", role: .cancel) { dim = nil }
            Button("Готово") {
                if let d = dim, let v = Int(dimText.filter(\.isNumber)), v > 0 { withAnimation { vm.applyDimension(d, value: v) } }
                dim = nil
            }
        } message: { Text(verbatim: "Сейчас \(dim?.value ?? 0) мм") }
        .confirmationDialog("Убрать окно \(vm.mark) из КП?", isPresented: $confirmRemove, titleVisibility: .visible) {
            Button("Убрать", role: .destructive) { vm.removePosition(vm.current) }
        }
        .onChange(of: vm.selection.systemId) { _, id in
            if let s = catalog.system(id), !(s.fillMin...s.fillMax).contains(vm.selection.fillMm) { vm.selection.fillMm = s.fillDefault }
        }
        .onChange(of: vm.current) { _, _ in editing = nil }
    }

    @ViewBuilder private func stripAndScheme(_ o: OfferBreakdown, _ outside: ColorItem?) -> some View {
        // Окна КП
        Section {
            PositionsStrip(offer: o)
                .listRowInsets(EdgeInsets(top: 10, leading: 0, bottom: 10, trailing: 0))
        }

        // Схема / чертёж / ИИ-фото
        Section {
            VStack(spacing: 10) {
                Picker("", selection: $tab) {
                    Text("Схема").tag(0)
                    Text("Чертёж").tag(1)
                    Text("ИИ-фото").tag(2)
                }
                .pickerStyle(.segmented)

                switch tab {
                case 0:
                    SchemeView(spec: vm.spec, frameHex: outside?.hex ?? "#45494E", selected: editing?.id, onTap: { r in
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        editing = r
                    }, onDimension: { d in
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        dimText = "\(d.value)"
                        dim = d
                    })
                    .frame(maxHeight: 380)
                    Text("Голубые размеры можно менять нажатием. Нажмите на ячейку, чтобы выбрать открывание.")
                        .font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
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

    }

    @ViewBuilder private func sizesSection() -> some View {
        // Размеры — сразу под схемой
        Section("Размеры") {
            LabeledContent("Ширина, мм") {
                TextField("мм", value: Binding(get: { vm.spec.widthMm }, set: { vm.setSize(width: $0) }), format: .number.grouping(.never))
                    .keyboardType(.numberPad).multilineTextAlignment(.trailing)
            }
            LabeledContent("Высота, мм") {
                TextField("мм", value: Binding(get: { vm.spec.heightMm }, set: { vm.setSize(height: $0) }), format: .number.grouping(.never))
                    .keyboardType(.numberPad).multilineTextAlignment(.trailing)
            }
            if vm.spec.layout.sections.count > 1 {
                ForEach(Array(vm.spec.layout.sections.enumerated()), id: \.element.id) { si, s in
                    LabeledContent("\(vm.spec.layout.split.sectionName) \(si + 1), мм") {
                        TextField("мм", value: Binding(get: { s.sizeMm }, set: { vm.setSectionSize(si, $0) }), format: .number.grouping(.never))
                            .keyboardType(.numberPad).multilineTextAlignment(.trailing)
                    }
                }
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

    }

    @ViewBuilder private func warningsSections(_ b: PriceBreakdown?) -> some View {
        if vm.spec.confidence < 0.6 && vm.drawing != nil {
            Section {
                Label("ИИ не уверен в распознавании — сверьте схему с чертежом.", systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.orange).font(.footnote)
            }
        }
        if let b, !b.warnings.isEmpty {
            Section {
                ForEach(b.warnings, id: \.self) { w in
                    Label(w, systemImage: "exclamationmark.circle").font(.footnote).foregroundStyle(.orange)
                }
                if vm.spec.layoutMismatch != nil {
                    Button("Подогнать сетку под размеры") { withAnimation { vm.spec.fitLayout() } }
                }
            }
        }
        // Нестандартные размеры: предупреждение и рекомендация
        if let b, !b.limits.isEmpty {
            Section {
                ForEach(b.limits) { w in
                    VStack(alignment: .leading, spacing: 6) {
                        Label(w.text, systemImage: "ruler").font(.footnote).foregroundStyle(.orange)
                        if !w.code.isEmpty {
                            Button { withAnimation { vm.applyRecommendation(w) } } label: {
                                Text(w.applied ? "Применено: \(w.code). Вернуть стандартный профиль" : "Рекомендация: \(w.rec) (\(w.code)) — применить")
                                    .font(.footnote.weight(.semibold))
                            }
                            .buttonStyle(.borderless)
                        }
                    }
                    .padding(.vertical, 2)
                }
            } header: { Text("Нестандарт") }
            footer: { Text("По умолчанию считается стандартными профилями. Нестандартные размеры рассматриваются индивидуально.") }
        }

    }

    @ViewBuilder private func productSections(_ catalog: Catalog, _ system: SystemDef?) -> some View {
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
            LabeledContent("Марка") {
                TextField("ОК-1", text: $vm.mark).multilineTextAlignment(.trailing)
            }
            Picker("Изделие", selection: $vm.spec.product) {
                ForEach(Product.allCases) { Text($0.title).tag($0) }
            }
            Picker("Система", selection: $vm.selection.systemId) {
                ForEach(catalog.systems) { s in Text(s.name).tag(s.id) }
            }
            if let system { Text(system.description).font(.caption).foregroundStyle(.secondary) }
        }

        Section(vm.options.scope == .turnkey ? "Заполнение, цвет, монтаж" : "Заполнение и цвет") {
            Picker("Заполнение", selection: $vm.selection.fillMm) {
                ForEach(fillOptions(system: system, catalog: catalog), id: \.self) { f in Text(verbatim: "\(f) мм").tag(f) }
            }
            if let gi = catalog.glassItem(for: vm.selection.fillMm) {
                LabeledContent("Стеклопакет", value: gi.name).font(.callout)
            }
            LabeledContent("Формула") {
                TextField("4-10-4-10-4", text: $vm.spec.glass).multilineTextAlignment(.trailing)
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
            if vm.options.scope == .turnkey { Toggle("Монтаж", isOn: $vm.selection.installation) }
        }

    }

    @ViewBuilder private func gridSection() -> some View {
        // Сетка
        Section {
            Picker("Деление", selection: Binding(get: { vm.spec.layout.split }, set: { s in withAnimation { vm.setSplit(s) } })) {
                ForEach(Split.allCases) { Text($0.title).tag($0) }
            }
            ForEach(Array(vm.spec.layout.sections.enumerated()), id: \.element.id) { si, s in
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(verbatim: "\(vm.spec.layout.split.sectionName) \(si + 1)").font(.headline)
                        Spacer()
                        Text(verbatim: "\(s.sizeMm) мм").foregroundStyle(.secondary)
                    }
                    ForEach(Array(s.cells.enumerated()), id: \.element.id) { ci, c in
                        Button {
                            if let r = vm.spec.cellRects.first(where: { $0.section == si && $0.index == ci }) { editing = r }
                        } label: {
                            HStack {
                                Image(systemName: c.door ? "door.left.hand.open" : (c.opening == .fixed ? "square" : "macwindow"))
                                    .foregroundStyle(Theme.accent).frame(width: 22)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(verbatim: "Ячейка \(ci + 1) · \(c.sizeMm) мм").foregroundStyle(.primary)
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

    }

    @ViewBuilder private func costSection(_ b: PriceBreakdown?, _ catalog: Catalog) -> some View {
        // Стоимость окна
        if let b {
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
                if b.markup > 0 {
                    LabeledContent("Наценка \(Int(catalog.markupPercent))%", value: Money.format(b.markup, currency: b.currency))
                }
                HStack {
                    Text("За 1 шт").font(.headline)
                    Spacer()
                    Text(Money.format(b.unitTotal, currency: b.currency)).font(.headline).monospacedDigit()
                        .foregroundStyle(Theme.accent)
                        .contentTransition(.numericText(value: b.unitTotal))
                        .animation(.snappy(duration: 0.35), value: b.unitTotal)
                }
                if b.quantity > 1 {
                    LabeledContent("За \(b.quantity) шт", value: Money.format(b.total, currency: b.currency))
                }
            } header: { Text(verbatim: "Стоимость окна \(vm.mark)") }
            footer: { Text(vm.options.scope == .materials
                           ? "Профили, комплектующие и уплотнители по прайсу \(catalog.name) (цены с НДС)."
                           : "Материалы по прайсу \(catalog.name), стеклопакеты, фурнитура и работа — по ставкам из «Настроек».") }
        }

    }

    @ViewBuilder private func offerSection(_ o: OfferBreakdown, _ catalog: Catalog) -> some View {
        // Коммерческое предложение
        Section {
            LabeledContent("Объект") {
                TextField("Название объекта", text: $vm.options.objectName).multilineTextAlignment(.trailing)
            }
            Picker("Состав КП", selection: $vm.options.scope) {
                ForEach(OfferScope.allCases) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented)
            Toggle("Раскрой профилей (целые хлысты)", isOn: $vm.options.cutting)
            Toggle("Уплотнители кратно \(Int(catalog.gasketStep)) м", isOn: $vm.options.gasketRound)
            LabeledContent("Скидка, %") {
                TextField("0", value: $vm.options.discountPercent, format: .number.precision(.fractionLength(0)))
                    .keyboardType(.numberPad).multilineTextAlignment(.trailing)
            }
            ForEach(Array(vm.positions.enumerated()), id: \.element.id) { i, p in
                Button { vm.select(i) } label: {
                    HStack {
                        Image(systemName: i == vm.current ? "pencil.circle.fill" : "circle").foregroundStyle(Theme.accent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(verbatim: "\(p.mark) · \(p.spec.product.title) \(p.spec.widthMm)×\(p.spec.heightMm)")
                            Text(verbatim: "\(p.spec.quantity) шт × \(Money.format(o.items[i].unitTotal, currency: o.currency))")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(Money.format(o.items[i].total, currency: o.currency)).monospacedDigit()
                    }
                }
                .buttonStyle(.plain)
            }
            LabeledContent("Площадь", value: String(format: "%.2f м²", o.areaM2))
            if o.discount > 0 { LabeledContent("Скидка", value: "−" + Money.format(o.discount, currency: o.currency)) }
            HStack {
                Text("Итого по КП").font(.headline)
                Spacer()
                Text(Money.format(o.total, currency: o.currency)).font(.title2.bold()).monospacedDigit()
                    .foregroundStyle(Theme.accent)
                    .contentTransition(.numericText(value: o.total))
                    .animation(.snappy(duration: 0.35), value: o.total)
            }
            LabeledContent("В т. ч. НДС \(Int(catalog.vatPercent))%", value: Money.format(o.vatIncluded, currency: o.currency))
            NavigationLink {
                MaterialsView(offer: o, options: vm.options)
            } label: {
                Label("Спецификация материалов", systemImage: "list.bullet.rectangle")
            }
        } header: { Text(verbatim: "Коммерческое предложение · окон: \(vm.positions.count)") }
        footer: { Text(vm.options.scope == .materials
                       ? "Как в КП BKM Aluminium: профиль, комплектующие, уплотнители. Не входят сборка, монтаж, стеклопакеты и фурнитура."
                       : "Под ключ: материалы, стеклопакеты, фурнитура, изготовление и монтаж (если отмечен) с наценкой.") }

    }

    @ViewBuilder private func actionsSection(_ o: OfferBreakdown, _ catalog: Catalog) -> some View {
        Section {
            Button {
                hideKeyboard()
                let imgs = Dictionary(uniqueKeysWithValues: vm.positions.map { ($0.id, PDFExporter.Images(drawing: vm.drawings[$0.id], render: vm.renders[$0.id])) })
                if let url = PDFExporter.makeOffer(positions: vm.positions, options: vm.options, breakdown: o, catalog: catalog,
                                                   company: vm.companyName, images: imgs) {
                    pdfURL = ShareFile(url: url)
                }
            } label: { Label("Создать и отправить КП (PDF)", systemImage: "doc.richtext") }
            ShareLink(item: vm.summaryText) { Label("Поделиться текстом КП", systemImage: "text.bubble") }
            Button { vm.saveToHistory() } label: {
                Label(vm.isSaved ? "Сохранено" : "Сохранить в историю", systemImage: vm.isSaved ? "checkmark.circle.fill" : "tray.and.arrow.down")
            }
            .disabled(vm.isSaved)
            if vm.positions.count > 1 {
                Button(role: .destructive) { confirmRemove = true } label: {
                    Label("Убрать окно \(vm.mark) из КП", systemImage: "trash")
                }
            }
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
        VStack(spacing: 10) {
            Picker("Стиль", selection: $vm.renderStyle) {
                ForEach(EstimateViewModel.RenderStyle.allCases) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented)
            if vm.isRendering {
                ShimmerBox(text: "Рисую окно по схеме…").frame(height: 240)
            } else if let img = vm.renderImage {
                Image(uiImage: img).resizable().scaledToFit().frame(maxHeight: 340)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
                Text("Сгенерировано ИИ по схеме окна").font(.caption).foregroundStyle(.secondary)
            } else {
                placeholder(vm.renderError ?? "Фото ещё не создано")
            }
            Button { Task { await vm.renderPhoto() } } label: {
                Label(vm.renderImage == nil ? "Сгенерировать по схеме" : "Сгенерировать заново", systemImage: "wand.and.stars")
            }
            .buttonStyle(.bordered).disabled(vm.isRendering)
        }
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

/// Лента окон КП: нажатие — редактировать окно; удержание — копия / убрать.
struct PositionsStrip: View {
    @EnvironmentObject private var vm: EstimateViewModel
    let offer: OfferBreakdown

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(vm.positions.enumerated()), id: \.element.id) { i, p in
                            let on = i == vm.current
                            Button { vm.select(i) } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(p.mark).font(.subheadline.bold())
                                    Text(verbatim: "\(p.spec.widthMm)×\(p.spec.heightMm)" + (p.spec.quantity > 1 ? " · \(p.spec.quantity) шт" : ""))
                                        .font(.caption2)
                                    if offer.items.indices.contains(i) {
                                        Text(Money.format(offer.items[i].total, currency: offer.currency)).font(.caption2.monospacedDigit())
                                    }
                                }
                                .foregroundStyle(on ? Color.white : Color.primary)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(on ? Theme.accent : Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 12))
                            }
                            .buttonStyle(.plain)
                            .id(p.id)
                            .contextMenu {
                                Button { vm.select(i); vm.duplicateCurrent() } label: { Label("Копия окна", systemImage: "plus.square.on.square") }
                                if vm.positions.count > 1 {
                                    Button(role: .destructive) { vm.removePosition(i) } label: { Label("Убрать из КП", systemImage: "trash") }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .onChange(of: vm.current) { _, c in
                    if vm.positions.indices.contains(c) { withAnimation { proxy.scrollTo(vm.positions[c].id, anchor: .center) } }
                }
            }
            HStack {
                Text(verbatim: "Окон в КП: \(vm.positions.reduce(0) { $0 + $1.spec.quantity })").font(.caption).foregroundStyle(.secondary)
                Spacer()
                Text("Итого: \(Money.format(offer.total, currency: offer.currency))").font(.caption.bold()).monospacedDigit()
                    .foregroundStyle(Theme.accent)
            }
            .padding(.horizontal, 16)
        }
    }
}

/// Спецификация материалов по всему КП: профили хлыстами, комплектующие, уплотнители.
struct MaterialsView: View {
    @EnvironmentObject private var catalogStore: CatalogStore
    let offer: OfferBreakdown
    let options: OfferOptions

    var body: some View {
        let c = catalogStore.catalog
        List {
            ForEach([(LineGroup.profile, "Профили"), (.accessory, "Комплектующие"), (.gasket, "Уплотнители")], id: \.0) { item in
                let g = item.0, title = item.1
                let list = offer.materials.filter { $0.group == g }
                if !list.isEmpty {
                    Section {
                        ForEach(list) { m in
                            VStack(alignment: .leading, spacing: 3) {
                                HStack(alignment: .firstTextBaseline) {
                                    Text(m.code).font(.caption.monospaced()).foregroundStyle(Theme.accent)
                                    Spacer()
                                    Text(Money.format(m.amount, currency: offer.currency)).font(.callout.weight(.semibold)).monospacedDigit()
                                }
                                Text(m.name + colorSuffix(m, c)).font(.callout)
                                Text(detail(m)).font(.caption).foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 2)
                        }
                    } header: { Text(title) }
                    footer: { Text(verbatim: "Итого: \(Money.format(list.reduce(0) { $0 + $1.amount }, currency: offer.currency))") }
                }
            }
            if offer.weightKg > 0 {
                Section { LabeledContent("Вес профилей", value: String(format: "≈ %.0f кг", offer.weightKg)) }
            }
        }
        .navigationTitle("Спецификация")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func colorSuffix(_ m: MaterialLine, _ c: Catalog) -> String {
        guard m.group == .profile, let id = m.colorKey.split(separator: "/").first, let col = c.color(String(id)) else { return "" }
        return " · \(col.code)"
    }

    private func detail(_ m: MaterialLine) -> String {
        switch m.group {
        case .profile:
            if let bars = m.bars, let mm = m.barMm {
                return String(format: "Нужно %.2f м → %d × %g м = %.1f м, отход %.0f%%", m.need, bars, mm / 1000, m.bought, m.wastePercent)
            }
            return String(format: "Нужно %.2f м, с отходом %.2f м", m.need, m.bought)
        case .gasket:
            let pack = m.packM.map { String(format: " · в рулоне %g м", $0) } ?? ""
            return String(format: "Нужно %.2f м → к отпуску %.0f м", m.need, m.bought) + pack
        default:
            return String(format: "%.0f шт × %@", m.need, Money.format(m.price, currency: offer.currency))
        }
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
                .keyboardDone()
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

/// Файл для листа «Поделиться».
struct ShareFile: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

/// Системный лист «Поделиться» (UIActivityViewController).
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

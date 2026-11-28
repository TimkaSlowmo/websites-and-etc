import SwiftUI

struct HistoryView: View {
    @EnvironmentObject private var history: HistoryStore
    @EnvironmentObject private var vm: EstimateViewModel

    var body: some View {
        Group {
            if history.items.isEmpty {
                ContentUnavailableView("Пока пусто", systemImage: "clock",
                                       description: Text("Сохраняйте КП кнопкой «Сохранить в историю»."))
            } else {
                List {
                    ForEach(history.items) { item in
                        Button { vm.load(item) } label: { row(item) }
                            .buttonStyle(.plain)
                    }
                    .onDelete { idx in idx.map { history.items[$0] }.forEach(history.delete) }
                }
            }
        }
        .navigationTitle("История")
    }

    private func row(_ item: HistoryItem) -> some View {
        HStack(spacing: 12) {
            Group {
                if let img = history.thumbnail(for: item) {
                    Image(uiImage: img).resizable().scaledToFill()
                } else {
                    Image(systemName: "window.vertical.closed").foregroundStyle(.secondary)
                }
            }
            .frame(width: 56, height: 56)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 3) {
                Text(verbatim: item.options.objectName.isEmpty ? item.title : item.options.objectName).font(.body).lineLimit(1)
                Text(verbatim: "\(item.date.formatted(date: .abbreviated, time: .shortened)) · окон: \(item.windows)").font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Text(Money.format(item.total, currency: item.currency)).font(.callout.bold()).monospacedDigit()
        }
        .contentShape(Rectangle())
    }
}

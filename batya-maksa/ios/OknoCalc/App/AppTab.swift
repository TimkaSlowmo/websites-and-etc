import SwiftUI

enum AppTab: Hashable { case estimate, history, catalog, settings }

enum Theme {
    static let accent = Color(red: 0.07, green: 0.47, blue: 0.55)
    static let deep = Color(red: 0.05, green: 0.13, blue: 0.29)
}

/// Скрыть клавиатуру из любого места.
func hideKeyboard() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
}

extension View {
    /// Кнопка «Готово» над цифровой клавиатурой и скрытие клавиатуры при прокрутке.
    func keyboardDone() -> some View {
        toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Готово") { hideKeyboard() }.fontWeight(.semibold)
            }
        }
        .scrollDismissesKeyboard(.immediately)
    }
}

import SwiftUI

enum AppTab: Hashable { case estimate, history, catalog, settings }

enum Theme {
    /// Фирменный бирюзовый; в тёмной теме светлее, чтобы читался на чёрном (как на Android).
    static let accent = Color(UIColor { t in
        t.userInterfaceStyle == .dark ? UIColor(red: 0.20, green: 0.66, blue: 0.75, alpha: 1) : UIColor(red: 0.07, green: 0.47, blue: 0.55, alpha: 1)
    })
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

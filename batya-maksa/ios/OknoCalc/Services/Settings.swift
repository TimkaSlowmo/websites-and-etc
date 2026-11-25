import Foundation
import Security

import SwiftUI

/// Настройки приложения. Адрес ИИ-сервера зашит в сборку и пользователю не показывается.
final class AppSettings: ObservableObject {
    let workerURL: String
    /// Тема: "system" | "light" | "dark".
    @Published var theme: String {
        didSet { UserDefaults.standard.set(theme, forKey: "theme") }
    }
    @Published var companyName: String {
        didSet { UserDefaults.standard.set(companyName, forKey: "companyName") }
    }
    @Published var token: String {
        didSet { Keychain.set(token, for: "appToken") }
    }

    init() {
        workerURL = Self.bundled("DefaultWorkerURL")
        theme = UserDefaults.standard.string(forKey: "theme") ?? "system"
        companyName = UserDefaults.standard.string(forKey: "companyName") ?? ""
        token = Keychain.get("appToken") ?? ""
    }

    /// Адрес сервера зашит в сборку (Info.plist), чтобы не вводить его вручную.
    private static func bundled(_ key: String) -> String {
        let v = (Bundle.main.object(forInfoDictionaryKey: key) as? String) ?? ""
        return v.hasPrefix("$(") ? "" : v
    }

    var colorScheme: ColorScheme? { theme == "light" ? .light : theme == "dark" ? .dark : nil }

    var isConfigured: Bool { URL(string: workerURL)?.host != nil }
}

enum Keychain {
    private static let service = "kz.okno.calc"

    static func set(_ value: String, for key: String) {
        let base: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                   kSecAttrService as String: service,
                                   kSecAttrAccount as String: key]
        SecItemDelete(base as CFDictionary)
        guard !value.isEmpty else { return }
        var add = base
        add[kSecValueData as String] = Data(value.utf8)
        SecItemAdd(add as CFDictionary, nil)
    }

    static func get(_ key: String) -> String? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: service,
                                kSecAttrAccount as String: key,
                                kSecReturnData as String: true,
                                kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess,
              let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

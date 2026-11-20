import Foundation
import Security

/// Адрес Worker и токен. Токен хранится в Keychain.
final class AppSettings: ObservableObject {
    @Published var workerURL: String {
        didSet { UserDefaults.standard.set(workerURL, forKey: "workerURL") }
    }
    @Published var companyName: String {
        didSet { UserDefaults.standard.set(companyName, forKey: "companyName") }
    }
    @Published var token: String {
        didSet { Keychain.set(token, for: "appToken") }
    }

    init() {
        workerURL = UserDefaults.standard.string(forKey: "workerURL") ?? Self.bundled("DefaultWorkerURL")
        companyName = UserDefaults.standard.string(forKey: "companyName") ?? ""
        token = Keychain.get("appToken") ?? ""
    }

    /// Адрес сервера зашит в сборку (Info.plist), чтобы не вводить его вручную.
    private static func bundled(_ key: String) -> String {
        let v = (Bundle.main.object(forInfoDictionaryKey: key) as? String) ?? ""
        return v.hasPrefix("$(") ? "" : v
    }

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

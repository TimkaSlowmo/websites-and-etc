import UIKit

enum APIError: LocalizedError {
    case notConfigured
    case server(String)
    case badResponse

    var errorDescription: String? {
        switch self {
        case .notConfigured: return "ИИ-сервер не настроен в этой сборке. Обновите приложение."
        case .server(let m): return "Ошибка ИИ-сервера: \(m)"
        case .badResponse: return "Сервер вернул неожиданный ответ."
        }
    }
}

private struct ServerError: Decodable { let error: String?; let message: String? }

/// Клиент Cloudflare Worker (Workers AI).
struct APIClient {
    let baseURL: URL
    let token: String

    init(settings: AppSettings) throws {
        guard settings.isConfigured, let url = URL(string: settings.workerURL) else { throw APIError.notConfigured }
        baseURL = url
        token = settings.token
    }

    func analyze(drawing: UIImage) async throws -> WindowSpec {
        struct Req: Encodable { let image: String }
        struct Res: Decodable { let spec: WindowSpec }
        let b64 = try Self.jpegBase64(drawing, maxSide: 1280, quality: 0.8)
        let res: Res = try await post("analyze", body: Req(image: b64))
        return res.spec
    }

    /// ИИ-фото: схема (PNG до 512 px) — образец для точной сетки и створок; style: facade | interior | studio.
    func render(spec: WindowSpec, scheme: Data?, style: String) async throws -> UIImage {
        struct Req: Encodable { let spec: WindowSpec; let scheme: String?; let style: String }
        struct Res: Decodable { let image: String }
        let res: Res = try await post("render", body: Req(spec: spec, scheme: scheme?.base64EncodedString(), style: style))
        guard let data = Data(base64Encoded: res.image), let img = UIImage(data: data) else { throw APIError.badResponse }
        return img
    }

    private func post<B: Encodable, R: Decodable>(_ path: String, body: B) async throws -> R {
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.httpMethod = "POST"
        req.timeoutInterval = 150
        if !token.isEmpty { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let enc = JSONEncoder()
        enc.keyEncodingStrategy = .convertToSnakeCase
        req.httpBody = try enc.encode(body)

        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse else { throw APIError.badResponse }
        guard http.statusCode == 200 else {
            let e = try? JSONDecoder().decode(ServerError.self, from: data)
            throw APIError.server(e?.message ?? e?.error ?? "HTTP \(http.statusCode)")
        }
        do { return try JSONDecoder.snake.decode(R.self, from: data) } catch { throw APIError.badResponse }
    }

    static func jpegBase64(_ image: UIImage, maxSide: CGFloat, quality: CGFloat) throws -> String {
        // В пикселях (масштаб 1), иначе на iPhone уходило в 3 раза больше — медленно и тяжело
        guard let data = image.prepared(maxSide: maxSide).jpegData(compressionQuality: quality) else { throw APIError.badResponse }
        return data.base64EncodedString()
    }
}

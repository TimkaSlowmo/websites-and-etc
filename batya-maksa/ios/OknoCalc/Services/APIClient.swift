import UIKit

enum APIError: LocalizedError {
    case notConfigured
    case server(String)
    case badResponse

    var errorDescription: String? {
        switch self {
        case .notConfigured: return "Укажите адрес сервера и токен в настройках."
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
        let b64 = try Self.jpegBase64(drawing, maxSide: 1600, quality: 0.85)
        let res: Res = try await post("analyze", body: Req(image: b64))
        return res.spec
    }

    func render(spec: WindowSpec) async throws -> UIImage {
        struct Req: Encodable { let spec: WindowSpec }
        struct Res: Decodable { let image: String }
        let res: Res = try await post("render", body: Req(spec: spec))
        guard let data = Data(base64Encoded: res.image), let img = UIImage(data: data) else { throw APIError.badResponse }
        return img
    }

    private func post<B: Encodable, R: Decodable>(_ path: String, body: B) async throws -> R {
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.httpMethod = "POST"
        req.timeoutInterval = 120
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
        let size = image.size
        let scale = min(1, maxSide / max(size.width, size.height))
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: target)
        let resized = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: target)) }
        guard let data = resized.jpegData(compressionQuality: quality) else { throw APIError.badResponse }
        return data.base64EncodedString()
    }
}

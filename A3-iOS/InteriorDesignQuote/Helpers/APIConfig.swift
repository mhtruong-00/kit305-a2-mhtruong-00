import Foundation

enum APIConfig {
    static let baseURL = "https://utasbot.dev/kit305_2026"

    static func productURL(category: String) -> URL? {
        return URL(string: "\(baseURL)/product?category=\(category)")
    }
}

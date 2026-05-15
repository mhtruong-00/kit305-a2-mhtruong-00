import Foundation

struct Product: Codable {
    let id: String
    let name: String
    let description: String
    let category: String
    let imageUrl: String
    let pricePerSqm: Double
    let minHeight: Double
    let maxHeight: Double
    let minWidth: Double
    let maxWidth: Double
    let maxPanelCount: Int
    let variants: [String]
}

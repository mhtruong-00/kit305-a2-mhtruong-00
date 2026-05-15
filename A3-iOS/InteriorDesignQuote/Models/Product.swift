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

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case description
        case category
        case imageUrl
        case image_url
        case pricePerSqm
        case price_per_sqm
        case minHeight
        case min_height
        case maxHeight
        case max_height
        case minWidth
        case min_width
        case maxWidth
        case max_width
        case maxPanelCount
        case max_panels
        case variants
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        description = try container.decodeIfPresent(String.self, forKey: .description) ?? ""
        category = try container.decodeIfPresent(String.self, forKey: .category) ?? ""
        imageUrl = try container.decodeIfPresent(String.self, forKey: .imageUrl)
            ?? container.decodeIfPresent(String.self, forKey: .image_url)
            ?? ""
        pricePerSqm = try container.decodeFlexibleDouble(forKeys: [.pricePerSqm, .price_per_sqm]) ?? 0
        minHeight = try container.decodeFlexibleDouble(forKeys: [.minHeight, .min_height]) ?? 0
        maxHeight = try container.decodeFlexibleDouble(forKeys: [.maxHeight, .max_height]) ?? 0
        minWidth = try container.decodeFlexibleDouble(forKeys: [.minWidth, .min_width]) ?? 0
        maxWidth = try container.decodeFlexibleDouble(forKeys: [.maxWidth, .max_width]) ?? 0
        maxPanelCount = try container.decodeFlexibleInt(forKeys: [.maxPanelCount, .max_panels]) ?? 1
        variants = try container.decodeIfPresent([String].self, forKey: .variants) ?? []
    }
}

extension Product {
    static func decodeList(from data: Data) throws -> [Product] {
        let decoder = JSONDecoder()
        if let direct = try? decoder.decode([Product].self, from: data) {
            return direct
        }
        let wrapped = try decoder.decode(ProductResponse.self, from: data)
        return wrapped.data
    }
}

private struct ProductResponse: Decodable {
    let data: [Product]
}

private extension KeyedDecodingContainer where K == Product.CodingKeys {
    func decodeFlexibleDouble(forKeys keys: [K]) throws -> Double? {
        for key in keys {
            if let value = try decodeIfPresent(Double.self, forKey: key) { return value }
            if let value = try decodeIfPresent(Int.self, forKey: key) { return Double(value) }
            if let value = try decodeIfPresent(String.self, forKey: key), let parsed = Double(value) { return parsed }
        }
        return nil
    }

    func decodeFlexibleInt(forKeys keys: [K]) throws -> Int? {
        for key in keys {
            if let value = try decodeIfPresent(Int.self, forKey: key) { return value }
            if let value = try decodeIfPresent(Double.self, forKey: key) { return Int(value) }
            if let value = try decodeIfPresent(String.self, forKey: key), let parsed = Int(value) { return parsed }
        }
        return nil
    }
}

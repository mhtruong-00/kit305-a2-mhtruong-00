import Foundation

enum FirestoreValue {
    static func requiredString(_ data: [String: Any], key: String) -> String? {
        guard let value = data[key] as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    static func optionalString(_ data: [String: Any], key: String) -> String? {
        guard let value = data[key] as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    static func double(_ data: [String: Any], key: String) -> Double? {
        if let value = data[key] as? Double { return value }
        if let value = data[key] as? Int { return Double(value) }
        if let value = data[key] as? Int64 { return Double(value) }
        if let value = data[key] as? NSNumber { return value.doubleValue }
        if let value = data[key] as? String { return Double(value) }
        return nil
    }

    static func int(_ data: [String: Any], key: String) -> Int? {
        if let value = data[key] as? Int { return value }
        if let value = data[key] as? Int64 { return Int(value) }
        if let value = data[key] as? Double { return Int(value) }
        if let value = data[key] as? NSNumber {
            let truncated = value.doubleValue.rounded(.towardZero)
            guard truncated >= Double(Int.min), truncated <= Double(Int.max) else { return nil }
            return Int(truncated)
        }
        if let value = data[key] as? String { return Int(value) }
        return nil
    }
}

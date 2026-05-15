import Foundation

struct CompatibilityResult {
    let compatible: Bool
    let panelCount: Int
    let message: String
}

struct CompatibilityChecker {
    static func check(product: Product, windowWidth: Double?, windowHeight: Double?) -> CompatibilityResult {
        if product.category == "floor" {
            return CompatibilityResult(compatible: true, panelCount: 1, message: "Compatible")
        }
        guard let width = windowWidth, let height = windowHeight else {
            return CompatibilityResult(compatible: true, panelCount: 1, message: "No dimensions set")
        }
        if height < product.minHeight {
            return CompatibilityResult(compatible: false, panelCount: 0,
                                       message: "Height too small (min \(Int(product.minHeight))mm)")
        }
        if height > product.maxHeight {
            return CompatibilityResult(compatible: false, panelCount: 0,
                                       message: "Height too large (max \(Int(product.maxHeight))mm)")
        }
        for panels in 1...max(1, product.maxPanelCount) {
            let panelWidth = width / Double(panels)
            if panelWidth >= product.minWidth && panelWidth <= product.maxWidth {
                return CompatibilityResult(compatible: true, panelCount: panels,
                                           message: "Compatible (\(panels) panel\(panels > 1 ? "s" : ""))")
            }
        }
        return CompatibilityResult(compatible: false, panelCount: 0,
                                   message: "No compatible panel configuration")
    }
}

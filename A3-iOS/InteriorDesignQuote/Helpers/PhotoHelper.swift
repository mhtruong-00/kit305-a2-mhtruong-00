import UIKit

struct PhotoHelper {
    static func imageToBase64(_ image: UIImage) -> String? {
        return image.jpegData(compressionQuality: 0.7)?.base64EncodedString()
    }

    static func base64ToImage(_ base64: String) -> UIImage? {
        guard let data = Data(base64Encoded: base64) else { return nil }
        return UIImage(data: data)
    }
}

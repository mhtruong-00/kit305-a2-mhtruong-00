import UIKit

struct PhotoHelper {
    // 0.7 balances acceptable image quality against Firestore document size limits
    // (Firestore documents are capped at 1 MiB; 0.7 keeps typical room photos well under that)
    private static let compressionQuality: CGFloat = 0.7

    static func imageToBase64(_ image: UIImage) -> String? {
        return image.jpegData(compressionQuality: compressionQuality)?.base64EncodedString()
    }

    static func base64ToImage(_ base64: String) -> UIImage? {
        guard let data = Data(base64Encoded: base64) else { return nil }
        return UIImage(data: data)
    }
}

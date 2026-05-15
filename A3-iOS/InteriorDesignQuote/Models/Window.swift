import Foundation

struct WindowItem {
    var id: String
    var name: String
    var roomId: String
    var widthMm: Double?
    var heightMm: Double?
    var selectedProductId: String?
    var selectedProductName: String?
    var selectedProductVariant: String?
    var panelCount: Int
    var photoBase64: String?

    init(id: String = "",
         name: String,
         roomId: String,
         widthMm: Double? = nil,
         heightMm: Double? = nil,
         selectedProductId: String? = nil,
         selectedProductName: String? = nil,
         selectedProductVariant: String? = nil,
         panelCount: Int = 1,
         photoBase64: String? = nil) {
        self.id = id
        self.name = name
        self.roomId = roomId
        self.widthMm = widthMm
        self.heightMm = heightMm
        self.selectedProductId = selectedProductId
        self.selectedProductName = selectedProductName
        self.selectedProductVariant = selectedProductVariant
        self.panelCount = panelCount
        self.photoBase64 = photoBase64
    }

    init?(id: String, data: [String: Any]) {
        guard let name = FirestoreValue.requiredString(data, key: "name"),
              let roomId = FirestoreValue.requiredString(data, key: "roomId") else { return nil }
        self.id = id
        self.name = name
        self.roomId = roomId
        self.widthMm = FirestoreValue.double(data, key: "widthMm")
        self.heightMm = FirestoreValue.double(data, key: "heightMm")
        self.selectedProductId = FirestoreValue.optionalString(data, key: "selectedProductId")
        self.selectedProductName = FirestoreValue.optionalString(data, key: "selectedProductName")
        self.selectedProductVariant = FirestoreValue.optionalString(data, key: "selectedProductVariant")
        self.panelCount = FirestoreValue.int(data, key: "panelCount") ?? 1
        self.photoBase64 = FirestoreValue.optionalString(data, key: "photoBase64")
    }

    func toFirestore() -> [String: Any] {
        var dict: [String: Any] = [
            "name": name,
            "roomId": roomId,
            "panelCount": panelCount
        ]
        if let w = widthMm { dict["widthMm"] = w }
        if let h = heightMm { dict["heightMm"] = h }
        if let pid = selectedProductId { dict["selectedProductId"] = pid }
        if let pname = selectedProductName { dict["selectedProductName"] = pname }
        if let pvariant = selectedProductVariant { dict["selectedProductVariant"] = pvariant }
        if let photo = photoBase64 { dict["photoBase64"] = photo }
        return dict
    }
}

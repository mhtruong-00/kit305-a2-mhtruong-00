import Foundation

struct Room {
    var id: String
    var name: String
    var houseId: String
    var photoBase64: String?

    init(id: String = "", name: String, houseId: String, photoBase64: String? = nil) {
        self.id = id
        self.name = name
        self.houseId = houseId
        self.photoBase64 = photoBase64
    }

    init?(id: String, data: [String: Any]) {
        guard let name = data["name"] as? String,
              let houseId = data["houseId"] as? String else { return nil }
        self.id = id
        self.name = name
        self.houseId = houseId
        self.photoBase64 = data["photoBase64"] as? String
    }

    func toFirestore() -> [String: Any] {
        var dict: [String: Any] = [
            "name": name,
            "houseId": houseId
        ]
        if let photo = photoBase64 {
            dict["photoBase64"] = photo
        }
        return dict
    }
}

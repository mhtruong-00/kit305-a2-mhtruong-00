import Foundation
import FirebaseFirestore

struct House {
    var id: String
    var customerName: String
    var address: String

    init(id: String = "", customerName: String, address: String) {
        self.id = id
        self.customerName = customerName
        self.address = address
    }

    init?(id: String, data: [String: Any]) {
        guard let customerName = FirestoreValue.requiredString(data, key: "customerName"),
              let address = FirestoreValue.requiredString(data, key: "address") else { return nil }
        self.id = id
        self.customerName = customerName
        self.address = address
    }

    func toFirestore() -> [String: Any] {
        return [
            "customerName": customerName,
            "address": address
        ]
    }
}

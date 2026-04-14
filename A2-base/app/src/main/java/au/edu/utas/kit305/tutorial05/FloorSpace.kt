package au.edu.utas.kit305.tutorial05

import com.google.firebase.firestore.Exclude

class FloorSpace(
    @get:Exclude var id: String? = null,
    var roomId: String? = null,
    var name: String? = null,
    var widthMm: Int = 0,
    var depthMm: Int = 0,
    var selectedProductId: String? = null,
    var selectedProductName: String? = null,
    var photoBase64: String? = null
)

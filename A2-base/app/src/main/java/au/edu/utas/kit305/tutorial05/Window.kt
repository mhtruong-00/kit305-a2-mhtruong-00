// With support from copilot
package au.edu.utas.kit305.tutorial05

import com.google.firebase.firestore.Exclude

class Window(
    @get:Exclude var id: String? = null,
    var roomId: String? = null,
    var name: String? = null,
    var widthMm: Int = 0,
    var heightMm: Int = 0,
    var selectedProductId: String? = null,
    var selectedProductName: String? = null,
    var selectedProductVariant: String? = null,
    var panelCount: Int = 1,
    var photoBase64: String? = null
)

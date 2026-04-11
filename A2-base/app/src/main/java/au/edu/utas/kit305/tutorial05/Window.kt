package au.edu.utas.kit305.tutorial05

import com.google.firebase.firestore.Exclude

class Window(
    @get:Exclude var id: String? = null,
    var roomId: String? = null,
    var name: String? = null,
    var widthMm: Int = 0,
    var heightMm: Int = 0
)


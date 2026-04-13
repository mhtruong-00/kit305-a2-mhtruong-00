package au.edu.utas.kit305.tutorial05

import com.google.firebase.firestore.Exclude

class Room(
    @get:Exclude var id: String? = null,
    var houseId: String? = null,
    var name: String? = null,
    var photoUrl: String? = null
)


package au.edu.utas.kit305.tutorial05

import com.google.firebase.firestore.Exclude

class Movie (
    @get:Exclude var id : String? = null,

    var title : String? = null,
    var year : Int? = null,
    var duration : Float? = null
)
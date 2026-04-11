package au.edu.utas.kit305.tutorial05

class Product(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var category: String = "", // window or floor
    var imageUrl: String? = null,
    var pricePerSqm: Double = 0.0,
    var minHeight: Int = 0,
    var maxHeight: Int = 9999,
    var minWidth: Int = 0,
    var maxWidth: Int = 9999,
    var maxPanelCount: Int = 1,
    var variants: List<String>? = null
)

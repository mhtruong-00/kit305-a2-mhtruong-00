package au.edu.utas.kit305.tutorial05

import com.google.gson.annotations.SerializedName

data class Product(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "", // "window" or "floor"

    @SerializedName(value = "imageUrl", alternate = ["image"])
    val imageUrl: String? = null,

    @SerializedName(value = "price_per_sqm", alternate = ["price"])
    val pricePerSqm: Double = 0.0,

    @SerializedName(value = "minHeight", alternate = ["min_height"])
    val minHeight: Int = 0,

    @SerializedName(value = "maxHeight", alternate = ["max_height"])
    val maxHeight: Int = 9999,

    @SerializedName(value = "minWidth", alternate = ["min_width"])
    val minWidth: Int = 0,

    @SerializedName(value = "maxWidth", alternate = ["max_width"])
    val maxWidth: Int = 9999,

    @SerializedName(value = "maxPanelCount", alternate = ["max_panels", "maxPanels"])
    val maxPanelCount: Int = 1,

    val variants: List<String>? = null
)

data class ProductListResponse(
    val data: List<Product> = emptyList()
)

data class ProductDetailResponse(
    val data: Product? = null
)

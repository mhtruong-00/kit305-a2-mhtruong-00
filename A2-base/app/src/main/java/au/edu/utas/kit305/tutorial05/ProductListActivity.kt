package au.edu.utas.kit305.tutorial05

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val EXTRA_PRODUCT_TYPE = "product_type"
const val EXTRA_SPACE_WIDTH = "space_width"
const val EXTRA_SPACE_HEIGHT = "space_height"
const val RESULT_PRODUCT_ID = "result_product_id"
const val RESULT_PRODUCT_NAME = "result_product_name"
const val RESULT_PANEL_COUNT = "result_panel_count"

data class CompatibilityResult(
    val compatible: Boolean,
    val panelCount: Int = 1,
    val message: String = ""
)

fun checkCompatibility(product: Product, windowWidth: Int, windowHeight: Int): CompatibilityResult {
    // Floor products have no size constraints
    if (product.category != "window") return CompatibilityResult(true, 1, "")
    // No dimensions recorded yet — allow selection
    if (windowWidth <= 0 && windowHeight <= 0) return CompatibilityResult(true, 1, "No dimensions set")

    // Height check
    if (windowHeight > 0) {
        if (windowHeight < product.minHeight) {
            return CompatibilityResult(false, 0, "Too short: ${windowHeight}mm (min ${product.minHeight}mm required)")
        }
        if (windowHeight > product.maxHeight) {
            return CompatibilityResult(false, 0, "Too tall: ${windowHeight}mm (max ${product.maxHeight}mm allowed)")
        }
    }

    return CompatibilityResult(true, 1, "")
}

class ProductListActivity : AppCompatActivity() {

    private lateinit var lstProducts: RecyclerView
    private lateinit var progressProducts: ProgressBar
    private lateinit var lblProductError: TextView
    private lateinit var lblTitle: TextView
    private lateinit var lblSpaceInfo: TextView

    private val products = mutableListOf<Product>()
    private var productType = "window"
    private var spaceWidthMm = 0
    private var spaceHeightMm = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_list)

        lstProducts = findViewById(R.id.lstProducts)
        progressProducts = findViewById(R.id.progressProducts)
        lblProductError = findViewById(R.id.lblProductError)
        lblTitle = findViewById(R.id.lblProductListTitle)
        lblSpaceInfo = findViewById(R.id.lblSpaceInfo)

        productType = intent.getStringExtra(EXTRA_PRODUCT_TYPE) ?: "window"
        spaceWidthMm = intent.getIntExtra(EXTRA_SPACE_WIDTH, 0)
        spaceHeightMm = intent.getIntExtra(EXTRA_SPACE_HEIGHT, 0)

        val typeLabel = if (productType == "window") "Window" else "Floor"
        lblTitle.text = "Select a $typeLabel Product"

        val dimLabel = if (productType == "window") {
            "Window: ${spaceWidthMm}mm wide x ${spaceHeightMm}mm tall"
        } else {
            "Floor space: ${spaceWidthMm}mm x ${spaceHeightMm}mm"
        }
        lblSpaceInfo.text = dimLabel

        lstProducts.layoutManager = LinearLayoutManager(this)
        lstProducts.adapter = ProductAdapter(products) { product ->
            returnSelectedProduct(product)
        }

        loadProductsFromApi()
    }

    private fun loadProductsFromApi() {
        progressProducts.visibility = View.VISIBLE
        lblProductError.visibility = View.GONE

        Thread {
            try {
                val endpoint = "https://utasbot.dev/kit305_2026/product?category=$productType"
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    runOnUiThread {
                        progressProducts.visibility = View.GONE
                        lblProductError.text = "Failed to load products ($responseCode)"
                        lblProductError.visibility = View.VISIBLE
                    }
                    conn.disconnect()
                    return@Thread
                }

                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val parsedProducts = parseProducts(responseText)

                runOnUiThread {
                    progressProducts.visibility = View.GONE
                    products.clear()
                    products.addAll(parsedProducts)
                    lstProducts.adapter?.notifyDataSetChanged()
                    if (products.isEmpty()) {
                        lblProductError.text = "No $productType products returned by API."
                        lblProductError.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressProducts.visibility = View.GONE
                    lblProductError.text = "Network error: ${e.message}"
                    lblProductError.visibility = View.VISIBLE
                }
                Log.e(FIREBASE_TAG, "Error loading products from API", e)
            }
        }.start()
    }

    private fun parseProducts(responseText: String): List<Product> {
        val out = mutableListOf<Product>()

        // Support both possible formats:
        // 1) { "data": [ ... ] }
        // 2) [ ... ]
        val array: JSONArray = if (responseText.trim().startsWith("{")) {
            val root = JSONObject(responseText)
            root.optJSONArray("data") ?: JSONArray()
        } else {
            JSONArray(responseText)
        }

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(
                Product(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    description = obj.optString("description"),
                    category = obj.optString("category"),
                    imageUrl = obj.optString("imageUrl").ifBlank { null },
                    pricePerSqm = obj.optDouble("price_per_sqm", 0.0),
                    minWidth = obj.optInt("min_width", 0),
                    maxWidth = obj.optInt("max_width", 9999),
                    minHeight = obj.optInt("min_height", 0),
                    maxHeight = obj.optInt("max_height", 9999),
                    maxPanelCount = obj.optInt("max_panels", 1),
                    variants = parseVariants(obj.optJSONArray("variants"))
                )
            )
        }

        return out
    }

    private fun parseVariants(variantsArray: JSONArray?): List<String> {
        if (variantsArray == null) return emptyList()
        val variants = mutableListOf<String>()
        for (i in 0 until variantsArray.length()) {
            variants.add(variantsArray.optString(i))
        }
        return variants
    }

    private fun returnSelectedProduct(product: Product) {
        val panelCount = if (productType == "window" && product.maxWidth > 0) {
            val rawPanels = Math.ceil(spaceWidthMm.toDouble() / product.maxWidth).toInt()
            maxOf(1, minOf(rawPanels, product.maxPanelCount))
        } else {
            1
        }

        val resultIntent = Intent()
        resultIntent.putExtra(RESULT_PRODUCT_ID, product.id)
        resultIntent.putExtra(RESULT_PRODUCT_NAME, product.name)
        resultIntent.putExtra(RESULT_PANEL_COUNT, panelCount)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)
        val txtName: TextView = itemView.findViewById(R.id.txtProductName)
        val txtDescription: TextView = itemView.findViewById(R.id.txtProductDescription)
        val txtPrice: TextView = itemView.findViewById(R.id.txtProductPrice)
        val txtConstraints: TextView = itemView.findViewById(R.id.txtProductConstraints)
    }

    class ProductAdapter(
        private val products: List<Product>,
        private val onSelect: (Product) -> Unit
    ) : RecyclerView.Adapter<ProductViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.product_list_item, parent, false)
            return ProductViewHolder(v)
        }

        override fun getItemCount() = products.size

        override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
            val product = products[position]

            holder.txtName.text = product.name
            holder.txtDescription.text = product.description
            holder.txtPrice.text = "$${String.format("%.2f", product.pricePerSqm)} / m2"

            holder.txtConstraints.text = if (product.category == "window") {
                "W: ${product.minWidth}-${product.maxWidth}mm  H: ${product.minHeight}-${product.maxHeight}mm  Max panels: ${product.maxPanelCount}"
            } else {
                "Floor covering"
            }

            // Keep a built-in placeholder icon (no third-party image loader).
            holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery)

            holder.itemView.setOnClickListener { onSelect(product) }
        }
    }
}

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
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val EXTRA_PRODUCT_TYPE = "product_type"
const val EXTRA_SPACE_WIDTH  = "space_width"
const val EXTRA_SPACE_HEIGHT = "space_height"
const val RESULT_PRODUCT_ID   = "result_product_id"
const val RESULT_PRODUCT_NAME = "result_product_name"
const val RESULT_PANEL_COUNT  = "result_panel_count"

class ProductListActivity : AppCompatActivity() {

    private lateinit var lstProducts: RecyclerView
    private lateinit var progressProducts: ProgressBar
    private lateinit var lblProductError: TextView
    private lateinit var lblTitle: TextView
    private lateinit var lblSpaceInfo: TextView

    private val products = mutableListOf<Product>()
    private var productType = "window"
    private var spaceWidthMm  = 0
    private var spaceHeightMm = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_list)

        lstProducts      = findViewById(R.id.lstProducts)
        progressProducts = findViewById(R.id.progressProducts)
        lblProductError  = findViewById(R.id.lblProductError)
        lblTitle         = findViewById(R.id.lblProductListTitle)
        lblSpaceInfo     = findViewById(R.id.lblSpaceInfo)

        productType   = intent.getStringExtra(EXTRA_PRODUCT_TYPE) ?: "window"
        spaceWidthMm  = intent.getIntExtra(EXTRA_SPACE_WIDTH, 0)
        spaceHeightMm = intent.getIntExtra(EXTRA_SPACE_HEIGHT, 0)

        val typeLabel = if (productType == "window") "Window" else "Floor"
        lblTitle.text = "Select a $typeLabel Product"

        val dimLabel = if (productType == "window")
            "Window: ${spaceWidthMm}mm wide × ${spaceHeightMm}mm tall"
        else
            "Floor space: ${spaceWidthMm}mm × ${spaceHeightMm}mm"
        lblSpaceInfo.text = dimLabel

        lstProducts.layoutManager = LinearLayoutManager(this)
        lstProducts.adapter = ProductAdapter(products) { product ->
            returnSelectedProduct(product)
        }

        loadProducts()
    }

    private fun loadProducts() {
        progressProducts.visibility = View.VISIBLE
        lblProductError.visibility  = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.productApi.getProductsByCategory(productType)
                withContext(Dispatchers.Main) {
                    progressProducts.visibility = View.GONE
                    if (response.isSuccessful) {
                        val result = response.body() ?: emptyList()
                        products.clear()
                        products.addAll(result)
                        lstProducts.adapter?.notifyDataSetChanged()
                        if (products.isEmpty()) {
                            lblProductError.text = "No $productType products available"
                            lblProductError.visibility = View.VISIBLE
                        }
                    } else {
                        lblProductError.text = "Failed to load products (${response.code()})"
                        lblProductError.visibility = View.VISIBLE
                        Log.e(FIREBASE_TAG, "Product API error: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressProducts.visibility = View.GONE
                    lblProductError.text = "Network error: ${e.message}\nCheck your internet connection."
                    lblProductError.visibility = View.VISIBLE
                    Log.e(FIREBASE_TAG, "Product API exception", e)
                }
            }
        }
    }

    private fun returnSelectedProduct(product: Product) {
        // Calculate how many panels are needed for window spaces
        val panelCount = if (productType == "window" && product.maxWidth > 0) {
            val rawPanels = Math.ceil(spaceWidthMm.toDouble() / product.maxWidth).toInt()
            maxOf(1, minOf(rawPanels, product.maxPanelCount))
        } else {
            1
        }

        val resultIntent = Intent()
        resultIntent.putExtra(RESULT_PRODUCT_ID,   product.id)
        resultIntent.putExtra(RESULT_PRODUCT_NAME, product.name)
        resultIntent.putExtra(RESULT_PANEL_COUNT,  panelCount)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    // ─── ViewHolder ─────────────────────────────────────────────────────────────

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduct:       ImageView = itemView.findViewById(R.id.imgProduct)
        val txtName:          TextView  = itemView.findViewById(R.id.txtProductName)
        val txtDescription:   TextView  = itemView.findViewById(R.id.txtProductDescription)
        val txtPrice:         TextView  = itemView.findViewById(R.id.txtProductPrice)
        val txtConstraints:   TextView  = itemView.findViewById(R.id.txtProductConstraints)
    }

    // ─── Adapter ────────────────────────────────────────────────────────────────

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
            val p = holder.itemView.context
            val product = products[position]

            holder.txtName.text        = product.name
            holder.txtDescription.text = product.description
            holder.txtPrice.text       = "$${String.format("%.2f", product.price)}"

            val constraintText = if (product.category == "window") {
                "W: ${product.minWidth}–${product.maxWidth}mm  " +
                "H: ${product.minHeight}–${product.maxHeight}mm  " +
                "Max panels: ${product.maxPanelCount}"
            } else {
                "Floor covering  •  $${String.format("%.2f", product.price)}/m²"
            }
            holder.txtConstraints.text = constraintText

            if (!product.image.isNullOrBlank()) {
                Glide.with(p)
                    .load(product.image)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(holder.imgProduct)
            } else {
                holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            holder.itemView.setOnClickListener { onSelect(product) }
        }
    }
}


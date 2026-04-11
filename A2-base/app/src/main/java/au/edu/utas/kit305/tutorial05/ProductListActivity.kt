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
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

const val EXTRA_PRODUCT_TYPE = "product_type"
const val EXTRA_SPACE_WIDTH = "space_width"
const val EXTRA_SPACE_HEIGHT = "space_height"
const val RESULT_PRODUCT_ID = "result_product_id"
const val RESULT_PRODUCT_NAME = "result_product_name"
const val RESULT_PANEL_COUNT = "result_panel_count"

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

        loadProductsFromFirestore()
    }

    private fun loadProductsFromFirestore() {
        progressProducts.visibility = View.VISIBLE
        lblProductError.visibility = View.GONE

        Firebase.firestore.collection("products")
            .whereEqualTo("category", productType)
            .get()
            .addOnSuccessListener { result ->
                progressProducts.visibility = View.GONE
                products.clear()

                for (doc in result.documents) {
                    val p = Product(
                        id = doc.getString("id") ?: doc.id,
                        name = doc.getString("name") ?: "",
                        description = doc.getString("description") ?: "",
                        category = doc.getString("category") ?: "",
                        imageUrl = doc.getString("imageUrl"),
                        pricePerSqm = doc.getDouble("price_per_sqm") ?: (doc.getDouble("pricePerSqm") ?: 0.0),
                        minWidth = doc.getLong("min_width")?.toInt() ?: (doc.getLong("minWidth")?.toInt() ?: 0),
                        maxWidth = doc.getLong("max_width")?.toInt() ?: (doc.getLong("maxWidth")?.toInt() ?: 9999),
                        minHeight = doc.getLong("min_height")?.toInt() ?: (doc.getLong("minHeight")?.toInt() ?: 0),
                        maxHeight = doc.getLong("max_height")?.toInt() ?: (doc.getLong("maxHeight")?.toInt() ?: 9999),
                        maxPanelCount = doc.getLong("max_panels")?.toInt() ?: (doc.getLong("maxPanelCount")?.toInt() ?: 1),
                        variants = (doc.get("variants") as? List<*>)?.filterIsInstance<String>()
                    )
                    products.add(p)
                }

                lstProducts.adapter?.notifyDataSetChanged()
                if (products.isEmpty()) {
                    lblProductError.text = "No $productType products found in Firestore."
                    lblProductError.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                progressProducts.visibility = View.GONE
                lblProductError.text = "Could not load products: ${e.message}"
                lblProductError.visibility = View.VISIBLE
                Log.e(FIREBASE_TAG, "Error reading products from Firestore", e)
            }
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

            // No third-party image loader: use a built-in placeholder icon.
            holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery)

            holder.itemView.setOnClickListener { onSelect(product) }
        }
    }
}

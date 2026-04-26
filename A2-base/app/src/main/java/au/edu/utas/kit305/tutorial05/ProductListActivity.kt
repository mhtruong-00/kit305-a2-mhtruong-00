// With support from copilot
package au.edu.utas.kit305.tutorial05

import android.app.AlertDialog
import android.content.Intent
import androidx.core.graphics.toColorInt
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

const val EXTRA_PRODUCT_TYPE = "product_type"
const val EXTRA_SPACE_WIDTH = "space_width"
const val EXTRA_SPACE_HEIGHT = "space_height"
const val RESULT_PRODUCT_ID = "result_product_id"
const val RESULT_PRODUCT_NAME = "result_product_name"
const val RESULT_PANEL_COUNT = "result_panel_count"
const val RESULT_PRODUCT_VARIANT = "result_product_variant"

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

    // Width check — iterate panel counts until one fits
    if (windowWidth > 0) {
        for (panels in 1..maxOf(1, product.maxPanelCount)) {
            val panelWidth = windowWidth.toDouble() / panels
            if (panelWidth >= product.minWidth && panelWidth <= product.maxWidth) {
                val msg = if (panels == 1) "Single panel — ${windowWidth}mm wide"
                          else "$panels panels — each ~${panelWidth.roundToInt()}mm wide"
                return CompatibilityResult(true, panels, msg)
            }
        }
        return when {
            windowWidth < product.minWidth ->
                CompatibilityResult(false, 0, "Too narrow: ${windowWidth}mm (min ${product.minWidth}mm per panel)")
            product.maxPanelCount <= 1 ->
                CompatibilityResult(false, 0, "Too wide: ${windowWidth}mm exceeds ${product.maxWidth}mm (single panel only)")
            else ->
                CompatibilityResult(false, 0, "Cannot fit: ${windowWidth}mm cannot be split into " +
                    "1–${product.maxPanelCount} panels each ${product.minWidth}–${product.maxWidth}mm wide")
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

        val typeLabelText = if (productType == "window") {
            getString(R.string.product_type_window)
        } else {
            getString(R.string.product_type_floor)
        }
        lblTitle.text = getString(R.string.product_list_title_format, typeLabelText)

        val dimLabel = if (productType == "window") {
            getString(R.string.product_space_window_format, spaceWidthMm, spaceHeightMm)
        } else {
            getString(R.string.product_space_floor_format, spaceWidthMm, spaceHeightMm)
        }
        lblSpaceInfo.text = dimLabel

        lstProducts.layoutManager = LinearLayoutManager(this)
        lstProducts.adapter = ProductAdapter(
            products = products,
            spaceWidthMm = spaceWidthMm,
            spaceHeightMm = spaceHeightMm
        ) { product, compat, variant ->
            returnSelectedProduct(product, compat, variant)
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
                        lblProductError.text = getString(R.string.product_load_failed_code, responseCode)
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
                        lblProductError.text = getString(R.string.product_none_returned, productType)
                        lblProductError.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressProducts.visibility = View.GONE
                    lblProductError.text = getString(R.string.product_network_error_format, e.message ?: "")
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
                    imageUrl = obj.optString("imageUrl")
                        .ifBlank { obj.optString("image_url") }
                        .ifBlank { null },
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

    private fun returnSelectedProduct(product: Product, compat: CompatibilityResult, variant: String?) {
        val resultIntent = Intent()
        resultIntent.putExtra(RESULT_PRODUCT_ID, product.id)
        resultIntent.putExtra(RESULT_PRODUCT_NAME, product.name)
        resultIntent.putExtra(RESULT_PANEL_COUNT, compat.panelCount)
        resultIntent.putExtra(RESULT_PRODUCT_VARIANT, variant ?: "")
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)
        val txtName: TextView = itemView.findViewById(R.id.txtProductName)
        val txtDescription: TextView = itemView.findViewById(R.id.txtProductDescription)
        val txtPrice: TextView = itemView.findViewById(R.id.txtProductPrice)
        val txtConstraints: TextView = itemView.findViewById(R.id.txtProductConstraints)
        val txtCompatibility: TextView = itemView.findViewById(R.id.txtCompatibility)
    }

    class ProductAdapter(
        private val products: List<Product>,
        private val spaceWidthMm: Int,
        private val spaceHeightMm: Int,
        private val onSelect: (Product, CompatibilityResult, String?) -> Unit
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
            holder.txtPrice.text = holder.itemView.context.getString(R.string.product_price_format, product.pricePerSqm)

            holder.txtConstraints.text = if (product.category == "window") {
                holder.itemView.context.getString(
                    R.string.product_constraints_window_format,
                    product.minWidth,
                    product.maxWidth,
                    product.minHeight,
                    product.maxHeight,
                    product.maxPanelCount
                )
            } else {
                holder.itemView.context.getString(R.string.product_constraints_floor)
            }

            Glide.with(holder.itemView)
                .load(product.imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(holder.imgProduct)

            // Check if this product fits the window dimensions
            val compat = checkCompatibility(product, spaceWidthMm, spaceHeightMm)
            if (compat.compatible) {
                holder.txtCompatibility.text =
                    if (compat.message.isNotBlank()) {
                        holder.itemView.context.getString(R.string.product_compat_ok_with_reason, compat.message)
                    } else {
                        holder.itemView.context.getString(R.string.product_compat_ok)
                    }
                holder.txtCompatibility.setTextColor("#2E7D32".toColorInt())
                holder.itemView.alpha = 1.0f
                holder.itemView.setOnClickListener {
                    showVariantPickerIfNeeded(holder.itemView, product, compat)
                }
            } else {
                // Incompatible — show why in red and block selection
                holder.txtCompatibility.text = holder.itemView.context.getString(R.string.product_compat_not_ok_with_reason, compat.message)
                holder.txtCompatibility.setTextColor("#C62828".toColorInt())
                holder.itemView.alpha = 0.45f
                holder.itemView.setOnClickListener {
                    Toast.makeText(
                        holder.itemView.context,
                        holder.itemView.context.getString(R.string.product_cannot_select_reason, compat.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        private fun showVariantPickerIfNeeded(view: View, product: Product, compat: CompatibilityResult) {
            val variants = product.variants?.filter { it.isNotBlank() } ?: emptyList()
            if (variants.isEmpty()) {
                onSelect(product, compat, null)
                return
            }

            AlertDialog.Builder(view.context)
                .setTitle(R.string.select_variant)
                .setItems(variants.toTypedArray()) { _, which ->
                    onSelect(product, compat, variants[which])
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}

class QuoteActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_WINDOW_RATE = 50.0
        private const val DEFAULT_FLOOR_RATE = 100.0
        private const val ROOM_LABOUR = 200.0
        private const val QUOTE_SECTION_PREVIEW_COUNT = 2
    }

    private data class RoomQuoteData(
        val room: Room,
        val windows: List<Window>,
        val floorSpaces: List<FloorSpace>
    )

    private lateinit var lblQuoteTitle: TextView
    private lateinit var lblQuoteAddress: TextView
    private lateinit var lblQuoteStatus: TextView
    private lateinit var lblQuoteTotal: TextView
    private lateinit var lblDiscountSummary: TextView
    private lateinit var edtDiscountPercent: EditText
    private lateinit var btnApplyDiscount: Button
    private lateinit var btnClearDiscount: Button
    private lateinit var btnShareQuote: Button
    private lateinit var layoutQuoteContent: LinearLayout
    private lateinit var houseId: String
    private var currentRoomQuotes: List<RoomQuoteData> = emptyList()
    private var currentProductRates: Map<String, Double> = emptyMap()
    private var currentUsingDefaults: Boolean = false
    private val includedRooms = mutableMapOf<String, Boolean>()
    private val includedItems = mutableMapOf<String, Boolean>()
    private val expandedWindowsByRoom = mutableMapOf<String, Boolean>()
    private val expandedFloorsByRoom = mutableMapOf<String, Boolean>()
    private var discountPercent: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quote)

        lblQuoteTitle = findViewById(R.id.lblQuoteTitle)
        lblQuoteAddress = findViewById(R.id.lblQuoteAddress)
        lblQuoteStatus = findViewById(R.id.lblQuoteStatus)
        lblQuoteTotal = findViewById(R.id.lblQuoteTotal)
        lblDiscountSummary = findViewById(R.id.lblDiscountSummary)
        edtDiscountPercent = findViewById(R.id.edtDiscountPercent)
        btnApplyDiscount = findViewById(R.id.btnApplyDiscount)
        btnClearDiscount = findViewById(R.id.btnClearDiscount)
        btnShareQuote = findViewById(R.id.btnShareQuote)
        layoutQuoteContent = findViewById(R.id.layoutQuoteContent)
        lblDiscountSummary.text = getString(R.string.discount_none)
        btnApplyDiscount.setOnClickListener { applyDiscountFromInput() }
        btnClearDiscount.setOnClickListener { clearDiscount() }
        btnShareQuote.setOnClickListener { shareQuoteText() }
        edtDiscountPercent.doAfterTextChanged {
            updateDiscountButtons()
        }
        updateDiscountButtons()

        houseId = intent.getStringExtra(HOUSE_ID_EXTRA) ?: run {
            finish()
            return
        }

        val houseName = intent.getStringExtra(HOUSE_NAME_EXTRA)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.quote_title_default)

        title = getString(R.string.quote_title_default)
        lblQuoteTitle.text = houseName
        lblQuoteStatus.text = getString(R.string.quote_loading)
        lblQuoteTotal.text = getString(R.string.quote_total_format, 0.0)

        loadHouseAndRooms()
    }

    private fun loadHouseAndRooms() {
        Firebase.firestore.collection("houses").document(houseId).get()
            .addOnSuccessListener { document ->
                val house = document.toObject<House>()
                lblQuoteTitle.text = house?.customerName?.takeIf { it.isNotBlank() }
                    ?: lblQuoteTitle.text
                lblQuoteAddress.text = house?.address?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.quote_address_missing)
                loadRooms()
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error loading house for quote", it)
                lblQuoteAddress.text = getString(R.string.quote_address_missing)
                loadRooms()
            }
    }

    private fun loadRooms() {
        Firebase.firestore.collection("rooms")
            .whereEqualTo("houseId", houseId)
            .get()
            .addOnSuccessListener { result ->
                val rooms = result.documents.mapNotNull { doc ->
                    doc.toObject<Room>()?.apply { id = doc.id }
                }

                if (rooms.isEmpty()) {
                    layoutQuoteContent.removeAllViews()
                    lblQuoteStatus.text = getString(R.string.quote_no_rooms)
                    return@addOnSuccessListener
                }

                val pending = intArrayOf(rooms.size)
                val loadedRooms = mutableListOf<RoomQuoteData>()

                rooms.forEach { room ->
                    loadRoomMeasurements(room,
                        onLoaded = { roomData ->
                            loadedRooms.add(roomData)
                            pending[0] -= 1
                            if (pending[0] == 0) {
                                loadProductsAndRender(loadedRooms.sortedBy { it.room.name ?: "" })
                            }
                        },
                        onFailed = {
                            pending[0] -= 1
                            if (pending[0] == 0) {
                                loadProductsAndRender(loadedRooms.sortedBy { it.room.name ?: "" })
                            }
                        }
                    )
                }
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error loading rooms for quote", it)
                layoutQuoteContent.removeAllViews()
                lblQuoteStatus.text = getString(R.string.quote_no_rooms)
                lblQuoteTotal.text = getString(R.string.quote_total_format, 0.0)
            }
    }

    private fun loadProductsAndRender(roomQuotes: List<RoomQuoteData>) {
        lblQuoteStatus.text = getString(R.string.quote_loading)

        Thread {
            try {
                val endpoint = "https://utasbot.dev/kit305_2026/product"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    runOnUiThread { showQuote(roomQuotes, emptyMap(), true) }
                    return@Thread
                }

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val productRates = parseProductRates(responseText)
                runOnUiThread { showQuote(roomQuotes, productRates, false) }
            } catch (e: Exception) {
                Log.e(FIREBASE_TAG, "Error loading quote product prices", e)
                runOnUiThread { showQuote(roomQuotes, emptyMap(), true) }
            }
        }.start()
    }

    private fun showQuote(
        roomQuotes: List<RoomQuoteData>,
        productRates: Map<String, Double>,
        usingDefaults: Boolean
    ) {
        currentRoomQuotes = roomQuotes
        currentProductRates = productRates
        currentUsingDefaults = usingDefaults

        // Rebuild include maps per fresh quote load to avoid stale hidden states.
        includedRooms.clear()
        includedItems.clear()
        expandedWindowsByRoom.clear()
        expandedFloorsByRoom.clear()

        for (roomQuote in roomQuotes) {
            val roomId = roomQuote.room.id ?: continue
            includedRooms[roomId] = true
            roomQuote.windows.forEachIndexed { index, window ->
                val key = buildItemKey("window", roomId, window.id, window.name ?: "", index)
                includedItems[key] = true
            }
            roomQuote.floorSpaces.forEachIndexed { index, floorSpace ->
                val key = buildItemKey("floor", roomId, floorSpace.id, floorSpace.name ?: "", index)
                includedItems[key] = true
            }
        }
        renderRooms()
    }

    private fun loadRoomMeasurements(
        room: Room,
        onLoaded: (RoomQuoteData) -> Unit,
        onFailed: () -> Unit
    ) {
        val roomId = room.id ?: run {
            onFailed()
            return
        }

        Firebase.firestore.collection("windows")
            .whereEqualTo("roomId", roomId)
            .get()
            .addOnSuccessListener { windowResult ->
                val windows = windowResult.documents.mapNotNull { doc ->
                    doc.toObject<Window>()?.apply { id = doc.id }
                }

                Firebase.firestore.collection("floorspaces")
                    .whereEqualTo("roomId", roomId)
                    .get()
                    .addOnSuccessListener { floorResult ->
                        val floorSpaces = floorResult.documents.mapNotNull { doc ->
                            doc.toObject<FloorSpace>()?.apply { id = doc.id }
                        }
                        onLoaded(RoomQuoteData(room, windows, floorSpaces))
                    }
                    .addOnFailureListener {
                        Log.e(FIREBASE_TAG, "Error loading floor spaces for quote", it)
                        onLoaded(RoomQuoteData(room, windows, emptyList()))
                    }
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error loading windows for quote", it)
                onLoaded(RoomQuoteData(room, emptyList(), emptyList()))
            }
    }

    private fun renderRooms() {
        layoutQuoteContent.removeAllViews()
        lblQuoteStatus.text = if (currentUsingDefaults) getString(R.string.quote_using_defaults) else ""

        var houseTotal = 0.0

        for (roomQuote in currentRoomQuotes) {
            val roomId = roomQuote.room.id ?: continue
            val unnamed = getString(R.string.unnamed_room)
            val unnamedItem = getString(R.string.unnamed)
            val roomName = roomQuote.room.name?.ifBlank { unnamed } ?: unnamed
            val includeRoom = includedRooms[roomId] != false

            val roomToggle = CheckBox(this).apply {
                text = getString(R.string.quote_room_include_format, roomName)
                isChecked = includeRoom
                textSize = 16f
                setOnCheckedChangeListener { _, checked ->
                    includedRooms[roomId] = checked
                    renderRooms()
                }
            }
            roomToggle.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * resources.displayMetrics.density).toInt()
            }
            layoutQuoteContent.addView(roomToggle)

            var roomSubtotal = 0.0
            var hasMeasuredIncludedItem = false
            val roomTitle = makeText(
                getString(
                    R.string.quote_room_heading,
                    roomName
                ),
                18f,
                leftPaddingDp = 12,
                topMarginDp = 4
            )
            layoutQuoteContent.addView(roomTitle)

            if (!includeRoom) {
                layoutQuoteContent.addView(
                    makeText(
                        getString(R.string.quote_room_excluded),
                        14f,
                        leftPaddingDp = 12,
                        topMarginDp = 4
                    )
                )
                continue
            }

            val hasItems = roomQuote.windows.isNotEmpty() || roomQuote.floorSpaces.isNotEmpty()
            if (!hasItems) {
                layoutQuoteContent.addView(makeText(getString(R.string.quote_no_items), 14f, leftPaddingDp = 12))
            } else {
                val windowsExpanded = expandedWindowsByRoom[roomId] == true
                val floorsExpanded = expandedFloorsByRoom[roomId] == true

                roomQuote.windows.forEachIndexed { index, window ->
                    val itemKey = buildItemKey("window", roomId, window.id, window.name ?: "", index)
                    val includeItem = includedItems[itemKey] != false
                    val showItem = windowsExpanded || index < QUOTE_SECTION_PREVIEW_COUNT

                    if (showItem) {
                        layoutQuoteContent.addView(
                            makeItemToggle(
                                getString(R.string.quote_window_label),
                                window.name?.ifBlank { unnamedItem } ?: unnamedItem,
                                includeItem
                            ) { checked ->
                                includedItems[itemKey] = checked
                                renderRooms()
                            }
                        )
                    }

                    if (!includeItem) {
                        if (showItem) {
                            layoutQuoteContent.addView(
                                makeText(
                                    getString(R.string.quote_item_excluded, getString(R.string.quote_window_label)),
                                    13f,
                                    leftPaddingDp = 24,
                                    topMarginDp = 4
                                )
                            )
                        }
                        return@forEachIndexed
                    }

                    val productName = displayProductNameWithVariant(
                        window.selectedProductName,
                        window.selectedProductVariant,
                        getString(R.string.quote_product_basic_window)
                    )
                    val rate = resolveRate(window.selectedProductId, currentProductRates, DEFAULT_WINDOW_RATE)
                    val area = calculateArea(window.widthMm, window.heightMm)
                    val itemCost = area * rate
                    if (area > 0.0) {
                        hasMeasuredIncludedItem = true
                    }
                    roomSubtotal += itemCost

                    if (!showItem) {
                        return@forEachIndexed
                    }

                    layoutQuoteContent.addView(
                        makeText(
                            getString(
                                R.string.quote_window_line,
                                window.name?.ifBlank { unnamedItem } ?: unnamedItem,
                                window.widthMm,
                                window.heightMm
                            ),
                            14f,
                            leftPaddingDp = 12,
                            topMarginDp = 6
                        )
                    )
                    layoutQuoteContent.addView(makeText(getString(R.string.quote_product_line, productName), 13f, leftPaddingDp = 24))
                    layoutQuoteContent.addView(makeText(getString(R.string.quote_item_area_format, area), 13f, leftPaddingDp = 24))
                    layoutQuoteContent.addView(makeText(getString(R.string.quote_item_rate_format, rate), 13f, leftPaddingDp = 24))
                    layoutQuoteContent.addView(makeText(getString(R.string.quote_item_cost_format, itemCost), 13f, leftPaddingDp = 24))
                    if (window.panelCount > 1) {
                        layoutQuoteContent.addView(makeText(getString(R.string.quote_item_panels_format, window.panelCount), 13f, leftPaddingDp = 24))
                    }
                }

                if (roomQuote.windows.size > QUOTE_SECTION_PREVIEW_COUNT) {
                    layoutQuoteContent.addView(
                        makeSectionToggleButton(
                            if (windowsExpanded) getString(R.string.quote_show_less_windows)
                            else getString(R.string.quote_show_more_windows)
                        ) {
                            expandedWindowsByRoom[roomId] = !windowsExpanded
                            renderRooms()
                        }
                    )
                }

                roomQuote.floorSpaces.forEachIndexed { index, floorSpace ->
                    val itemKey = buildItemKey("floor", roomId, floorSpace.id, floorSpace.name ?: "", index)
                    val includeItem = includedItems[itemKey] != false
                    val showItem = floorsExpanded || index < QUOTE_SECTION_PREVIEW_COUNT

                    if (showItem) {
                        layoutQuoteContent.addView(
                            makeItemToggle(
                                getString(R.string.quote_floor_label),
                                floorSpace.name?.ifBlank { unnamedItem } ?: unnamedItem,
                                includeItem
                            ) { checked ->
                                includedItems[itemKey] = checked
                                renderRooms()
                            }
                        )
                    }

                    if (!includeItem) {
                        if (showItem) {
                            layoutQuoteContent.addView(
                                makeText(
                                    getString(R.string.quote_item_excluded, getString(R.string.quote_floor_label)),
                                    13f,
                                    leftPaddingDp = 24,
                                    topMarginDp = 4
                                )
                            )
                        }
                        return@forEachIndexed
                    }

                    val productName = displayProductNameWithVariant(
                        floorSpace.selectedProductName,
                        floorSpace.selectedProductVariant,
                        getString(R.string.quote_product_basic_floor)
                    )
                    val rate = resolveRate(floorSpace.selectedProductId, currentProductRates, DEFAULT_FLOOR_RATE)
                    val area = calculateArea(floorSpace.widthMm, floorSpace.depthMm)
                    val itemCost = area * rate
                    if (area > 0.0) {
                        hasMeasuredIncludedItem = true
                    }
                    roomSubtotal += itemCost

                    if (!showItem) {
                        return@forEachIndexed
                    }

                    layoutQuoteContent.addView(
                        makeText(
                            getString(
                                R.string.quote_floor_line,
                                floorSpace.name?.ifBlank { unnamedItem } ?: unnamedItem,
                                floorSpace.widthMm,
                                floorSpace.depthMm
                            ),
                            14f,
                            leftPaddingDp = 12,
                            topMarginDp = 6
                        )
                    )
                    layoutQuoteContent.addView(makeText(getString(R.string.quote_product_line, productName), 13f, leftPaddingDp = 24))
                    layoutQuoteContent.addView(makeText(getString(R.string.quote_item_area_format, area), 13f, leftPaddingDp = 24))
                    layoutQuoteContent.addView(makeText(getString(R.string.quote_item_rate_format, rate), 13f, leftPaddingDp = 24))
                    layoutQuoteContent.addView(makeText(getString(R.string.quote_item_cost_format, itemCost), 13f, leftPaddingDp = 24))
                }

                if (roomQuote.floorSpaces.size > QUOTE_SECTION_PREVIEW_COUNT) {
                    layoutQuoteContent.addView(
                        makeSectionToggleButton(
                            if (floorsExpanded) getString(R.string.quote_show_less_floors)
                            else getString(R.string.quote_show_more_floors)
                        ) {
                            expandedFloorsByRoom[roomId] = !floorsExpanded
                            renderRooms()
                        }
                    )
                }
            }

            val roomLabour = if (hasMeasuredIncludedItem) ROOM_LABOUR else 0.0
            val roomTotal = roomSubtotal + roomLabour
            houseTotal += roomTotal
            layoutQuoteContent.addView(makeText(getString(R.string.quote_room_subtotal_format, roomSubtotal), 14f, leftPaddingDp = 12, topMarginDp = 8))
            layoutQuoteContent.addView(makeText(getString(R.string.quote_room_labour_format, roomLabour), 14f, leftPaddingDp = 12))
            layoutQuoteContent.addView(makeText(getString(R.string.quote_room_total_format, roomTotal), 15f, leftPaddingDp = 12))
        }

        val discountAmount = houseTotal * (discountPercent / 100.0)
        val finalTotal = houseTotal - discountAmount

        lblDiscountSummary.text = if (discountPercent > 0.0) {
            getString(R.string.discount_applied_format, discountPercent, discountAmount)
        } else {
            getString(R.string.discount_none)
        }
        lblQuoteTotal.text = getString(R.string.quote_total_after_discount_format, finalTotal)
    }

    private fun shareQuoteText() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildShareCsv())
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_quote_chooser)))
    }

    private fun applyDiscountFromInput() {
        val parsed = edtDiscountPercent.text?.toString()?.trim()?.toDoubleOrNull()
        if (parsed == null || parsed < 0.0 || parsed > 100.0) {
            edtDiscountPercent.error = getString(R.string.discount_invalid)
            return
        }

        edtDiscountPercent.error = null
        discountPercent = parsed
        val normalized = String.Companion.format(Locale.US, "%.1f", discountPercent)
        if (edtDiscountPercent.text?.toString() != normalized) {
            edtDiscountPercent.setText(normalized)
            edtDiscountPercent.setSelection(normalized.length)
        }
        updateDiscountButtons()
        renderRooms()
    }

    private fun clearDiscount() {
        discountPercent = 0.0
        edtDiscountPercent.setText("")
        edtDiscountPercent.error = null
        updateDiscountButtons()
        renderRooms()
    }

    private fun updateDiscountButtons() {
        val typedText = edtDiscountPercent.text?.toString()?.trim().orEmpty()
        val hasTypedValue = typedText.isNotEmpty()
        val typedValue = typedText.toDoubleOrNull()
        val hasValidValue = typedValue != null && typedValue in 0.0..100.0
        val valueChanged = hasValidValue && abs((typedValue ?: 0.0) - discountPercent) > 0.0001

        btnApplyDiscount.isEnabled = hasValidValue && valueChanged
        btnClearDiscount.isEnabled = discountPercent > 0.0 || hasTypedValue
    }

    private fun buildShareCsv(): String {
        val rows = mutableListOf<String>()
        rows += csvRow(
            "type",
            "house",
            "address",
            "room",
            "item_type",
            "item_name",
            "width_mm",
            "height_or_depth_mm",
            "product",
            "variant",
            "rate_per_sqm",
            "area_sqm",
            "item_cost",
            "room_subtotal",
            "room_labour",
            "room_total",
            "included"
        )

        val houseName = lblQuoteTitle.text?.toString()?.ifBlank { getString(R.string.quote_title_default) }
            ?: getString(R.string.quote_title_default)
        val houseAddress = lblQuoteAddress.text?.toString()?.ifBlank { getString(R.string.quote_address_missing) }
            ?: getString(R.string.quote_address_missing)

        var houseSubtotal = 0.0

        for (roomQuote in currentRoomQuotes) {
            val roomId = roomQuote.room.id ?: continue
            val roomName = roomQuote.room.name?.ifBlank { getString(R.string.unnamed_room) } ?: getString(R.string.unnamed_room)
            val includeRoom = includedRooms[roomId] != false

            var roomSubtotal = 0.0
            var hasMeasuredIncludedItem = false

            roomQuote.windows.forEachIndexed { index, window ->
                val itemKey = buildItemKey("window", roomId, window.id, window.name ?: "", index)
                val includeItem = includeRoom && includedItems[itemKey] != false
                val productName = window.selectedProductName?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.quote_product_basic_window)
                val variant = window.selectedProductVariant.orEmpty()
                val rate = resolveRate(window.selectedProductId, currentProductRates, DEFAULT_WINDOW_RATE)
                val area = calculateArea(window.widthMm, window.heightMm)
                val itemCost = if (includeItem) area * rate else 0.0
                if (includeItem && area > 0.0) hasMeasuredIncludedItem = true
                roomSubtotal += itemCost

                rows += csvRow(
                    "item",
                    houseName,
                    houseAddress,
                    roomName,
                    "window",
                    window.name?.ifBlank { getString(R.string.unnamed) } ?: getString(R.string.unnamed),
                    window.widthMm.toString(),
                    window.heightMm.toString(),
                    productName,
                    variant,
                    formatMoney(rate),
                    formatArea(area),
                    formatMoney(itemCost),
                    "",
                    "",
                    "",
                    includeItem.toString()
                )
            }

            roomQuote.floorSpaces.forEachIndexed { index, floorSpace ->
                val itemKey = buildItemKey("floor", roomId, floorSpace.id, floorSpace.name ?: "", index)
                val includeItem = includeRoom && includedItems[itemKey] != false
                val productName = floorSpace.selectedProductName?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.quote_product_basic_floor)
                val variant = floorSpace.selectedProductVariant.orEmpty()
                val rate = resolveRate(floorSpace.selectedProductId, currentProductRates, DEFAULT_FLOOR_RATE)
                val area = calculateArea(floorSpace.widthMm, floorSpace.depthMm)
                val itemCost = if (includeItem) area * rate else 0.0
                if (includeItem && area > 0.0) hasMeasuredIncludedItem = true
                roomSubtotal += itemCost

                rows += csvRow(
                    "item",
                    houseName,
                    houseAddress,
                    roomName,
                    "floor",
                    floorSpace.name?.ifBlank { getString(R.string.unnamed) } ?: getString(R.string.unnamed),
                    floorSpace.widthMm.toString(),
                    floorSpace.depthMm.toString(),
                    productName,
                    variant,
                    formatMoney(rate),
                    formatArea(area),
                    formatMoney(itemCost),
                    "",
                    "",
                    "",
                    includeItem.toString()
                )
            }

            val roomLabour = if (includeRoom && hasMeasuredIncludedItem) ROOM_LABOUR else 0.0
            val roomTotal = if (includeRoom) roomSubtotal + roomLabour else 0.0
            houseSubtotal += roomTotal

            rows += csvRow(
                "room_total",
                houseName,
                houseAddress,
                roomName,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                formatMoney(roomSubtotal),
                formatMoney(roomLabour),
                formatMoney(roomTotal),
                includeRoom.toString()
            )
        }

        val discountAmount = houseSubtotal * (discountPercent / 100.0)
        val finalTotal = houseSubtotal - discountAmount

        rows += csvRow("summary", houseName, houseAddress, "", "", "", "", "", "", "", "", "", "", "", "", "", "")
        rows += csvRow("subtotal", houseName, houseAddress, "", "", "", "", "", "", "", "", "", formatMoney(houseSubtotal), "", "", "", "")
        rows += csvRow("discount", houseName, houseAddress, "", "", "", "", "", "", "", "", "", formatMoney(discountAmount), "", "", "", formatPercent(discountPercent))
        rows += csvRow("final_total", houseName, houseAddress, "", "", "", "", "", "", "", "", "", formatMoney(finalTotal), "", "", "", "")

        if (currentUsingDefaults) {
            rows += csvRow("note", houseName, houseAddress, "", "", "", "", "", getString(R.string.quote_using_defaults), "", "", "", "", "", "", "", "")
        }

        return rows.joinToString("\n")
    }

    private fun csvRow(vararg values: String): String =
        values.joinToString(",") { escapeCsv(it) }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun formatMoney(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun formatArea(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f", value)

    private fun buildItemKey(type: String, roomId: String, id: String?, fallbackName: String, index: Int): String {
        val stablePart = id ?: fallbackName
        return "$type:$roomId:$stablePart:$index"
    }

    private fun resolveRate(productId: String?, productRates: Map<String, Double>, defaultRate: Double): Double {
        if (productId.isNullOrBlank()) return defaultRate
        return productRates[productId] ?: defaultRate
    }

    private fun displayProductNameWithVariant(productName: String?, variant: String?, fallback: String): String {
        val base = productName?.takeIf { it.isNotBlank() } ?: fallback
        val selectedVariant = variant?.takeIf { it.isNotBlank() } ?: return base
        return getString(R.string.product_with_variant_format, base, selectedVariant)
    }

    private fun calculateArea(widthMm: Int, heightMm: Int): Double {
        return (widthMm / 1000.0) * (heightMm / 1000.0)
    }

    private fun parseProductRates(responseText: String): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val array: JSONArray = if (responseText.trim().startsWith("{")) {
            val root = JSONObject(responseText)
            root.optJSONArray("data") ?: JSONArray()
        } else {
            JSONArray(responseText)
        }

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isBlank()) continue
            result[id] = obj.optDouble("price_per_sqm", 0.0)
        }
        return result
    }

    private fun makeText(
        text: String,
        textSizeSp: Float,
        leftPaddingDp: Int = 0,
        topMarginDp: Int = 0
    ): TextView {
        val view = TextView(this)
        view.text = text
        view.textSize = textSizeSp

        val density = resources.displayMetrics.density
        val leftPaddingPx = (leftPaddingDp * density).toInt()
        view.setPadding(leftPaddingPx, view.paddingTop, view.paddingRight, view.paddingBottom)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = (topMarginDp * density).toInt()
        view.layoutParams = params
        return view
    }

    private fun makeItemToggle(
        itemType: String,
        itemName: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): CheckBox {
        return CheckBox(this).apply {
            text = getString(R.string.quote_item_include_format, itemType, itemName)
            isChecked = checked
            textSize = 14f
            setPadding((12 * resources.displayMetrics.density).toInt(), paddingTop, paddingRight, paddingBottom)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * resources.displayMetrics.density).toInt()
            }
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        }
    }

    private fun makeSectionToggleButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * resources.displayMetrics.density).toInt()
                marginStart = (12 * resources.displayMetrics.density).toInt()
                marginEnd = (12 * resources.displayMetrics.density).toInt()
            }
            setOnClickListener { onClick() }
        }
    }
}
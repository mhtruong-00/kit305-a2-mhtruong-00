package au.edu.utas.kit305.tutorial05

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class QuoteActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_WINDOW_RATE = 50.0
        private const val DEFAULT_FLOOR_RATE = 100.0
        private const val ROOM_LABOUR = 200.0
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
            edtDiscountPercent.error = null
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
            val roomName = roomQuote.room.name?.ifBlank { "Unnamed room" } ?: "Unnamed room"
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
                roomQuote.windows.forEachIndexed { index, window ->
                    val itemKey = buildItemKey("window", roomId, window.id, window.name ?: "", index)
                    val includeItem = includedItems[itemKey] != false
                    layoutQuoteContent.addView(
                        makeItemToggle(
                            getString(R.string.quote_window_label),
                            window.name?.ifBlank { "Unnamed" } ?: "Unnamed",
                            includeItem
                        ) { checked ->
                            includedItems[itemKey] = checked
                            renderRooms()
                        }
                    )

                    if (!includeItem) {
                        layoutQuoteContent.addView(
                            makeText(
                                getString(R.string.quote_item_excluded, getString(R.string.quote_window_label)),
                                13f,
                                leftPaddingDp = 24,
                                topMarginDp = 4
                            )
                        )
                        return@forEachIndexed
                    }

                    val productName = window.selectedProductName?.ifBlank { getString(R.string.quote_product_basic_window) }
                        ?: getString(R.string.quote_product_basic_window)
                    val rate = resolveRate(window.selectedProductId, currentProductRates, DEFAULT_WINDOW_RATE)
                    val area = calculateArea(window.widthMm, window.heightMm)
                    val itemCost = area * rate
                    if (area > 0.0) {
                        hasMeasuredIncludedItem = true
                    }
                    roomSubtotal += itemCost

                    layoutQuoteContent.addView(
                        makeText(
                            getString(
                                R.string.quote_window_line,
                                window.name?.ifBlank { "Unnamed" } ?: "Unnamed",
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

                roomQuote.floorSpaces.forEachIndexed { index, floorSpace ->
                    val itemKey = buildItemKey("floor", roomId, floorSpace.id, floorSpace.name ?: "", index)
                    val includeItem = includedItems[itemKey] != false
                    layoutQuoteContent.addView(
                        makeItemToggle(
                            getString(R.string.quote_floor_label),
                            floorSpace.name?.ifBlank { "Unnamed" } ?: "Unnamed",
                            includeItem
                        ) { checked ->
                            includedItems[itemKey] = checked
                            renderRooms()
                        }
                    )

                    if (!includeItem) {
                        layoutQuoteContent.addView(
                            makeText(
                                getString(R.string.quote_item_excluded, getString(R.string.quote_floor_label)),
                                13f,
                                leftPaddingDp = 24,
                                topMarginDp = 4
                            )
                        )
                        return@forEachIndexed
                    }

                    val productName = floorSpace.selectedProductName?.ifBlank { getString(R.string.quote_product_basic_floor) }
                        ?: getString(R.string.quote_product_basic_floor)
                    val rate = resolveRate(floorSpace.selectedProductId, currentProductRates, DEFAULT_FLOOR_RATE)
                    val area = calculateArea(floorSpace.widthMm, floorSpace.depthMm)
                    val itemCost = area * rate
                    if (area > 0.0) {
                        hasMeasuredIncludedItem = true
                    }
                    roomSubtotal += itemCost

                    layoutQuoteContent.addView(
                        makeText(
                            getString(
                                R.string.quote_floor_line,
                                floorSpace.name?.ifBlank { "Unnamed" } ?: "Unnamed",
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
            putExtra(Intent.EXTRA_TEXT, buildShareText())
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
        val hasTypedValue = !edtDiscountPercent.text.isNullOrBlank()
        btnClearDiscount.isEnabled = discountPercent > 0.0 || hasTypedValue
    }

    private fun buildShareText(): String {
        val titleText = lblQuoteTitle.text?.toString()?.ifBlank { getString(R.string.quote_title_default) }
            ?: getString(R.string.quote_title_default)
        val addressText = lblQuoteAddress.text?.toString()?.ifBlank { getString(R.string.quote_address_missing) }
            ?: getString(R.string.quote_address_missing)

        val summary = StringBuilder()
        summary.append(getString(R.string.quote_share_title_format, titleText)).append("\n")
        summary.append("Address: ").append(addressText).append("\n")

        if (currentUsingDefaults) {
            summary.append(getString(R.string.quote_using_defaults)).append("\n")
        }

        summary.append("\n")
        if (currentRoomQuotes.isEmpty()) {
            summary.append(getString(R.string.quote_no_rooms)).append("\n")
            summary.append(getString(R.string.quote_subtotal_format, 0.0)).append("\n")
            summary.append(getString(R.string.quote_discount_line_format, discountPercent, 0.0)).append("\n")
            summary.append(getString(R.string.quote_total_after_discount_format, 0.0))
            return summary.toString()
        }

        var houseTotal = 0.0
        var hasIncludedRoom = false

        for (roomQuote in currentRoomQuotes) {
            val roomId = roomQuote.room.id ?: continue
            if (includedRooms[roomId] == false) {
                continue
            }

            hasIncludedRoom = true
            val roomName = roomQuote.room.name?.ifBlank { "Unnamed room" } ?: "Unnamed room"
            summary.append(getString(R.string.quote_room_heading, roomName)).append("\n")

            var roomSubtotal = 0.0
            var hasMeasuredIncludedItem = false

            roomQuote.windows.forEachIndexed { index, window ->
                val itemKey = buildItemKey("window", roomId, window.id, window.name ?: "", index)
                if (includedItems[itemKey] == false) {
                    return@forEachIndexed
                }

                val windowName = window.name?.ifBlank { "Unnamed" } ?: "Unnamed"
                val rate = resolveRate(window.selectedProductId, currentProductRates, DEFAULT_WINDOW_RATE)
                val area = calculateArea(window.widthMm, window.heightMm)
                val itemCost = area * rate
                if (area > 0.0) hasMeasuredIncludedItem = true
                roomSubtotal += itemCost

                summary.append("  ")
                    .append(getString(R.string.quote_window_line, windowName, window.widthMm, window.heightMm))
                    .append("\n")
                summary.append("  ")
                    .append(getString(R.string.quote_item_cost_format, itemCost))
                    .append("\n")
            }

            roomQuote.floorSpaces.forEachIndexed { index, floorSpace ->
                val itemKey = buildItemKey("floor", roomId, floorSpace.id, floorSpace.name ?: "", index)
                if (includedItems[itemKey] == false) {
                    return@forEachIndexed
                }

                val floorName = floorSpace.name?.ifBlank { "Unnamed" } ?: "Unnamed"
                val rate = resolveRate(floorSpace.selectedProductId, currentProductRates, DEFAULT_FLOOR_RATE)
                val area = calculateArea(floorSpace.widthMm, floorSpace.depthMm)
                val itemCost = area * rate
                if (area > 0.0) hasMeasuredIncludedItem = true
                roomSubtotal += itemCost

                summary.append("  ")
                    .append(getString(R.string.quote_floor_line, floorName, floorSpace.widthMm, floorSpace.depthMm))
                    .append("\n")
                summary.append("  ")
                    .append(getString(R.string.quote_item_cost_format, itemCost))
                    .append("\n")
            }

            val roomLabour = if (hasMeasuredIncludedItem) ROOM_LABOUR else 0.0
            val roomTotal = roomSubtotal + roomLabour
            houseTotal += roomTotal

            summary.append("  ").append(getString(R.string.quote_room_subtotal_format, roomSubtotal)).append("\n")
            summary.append("  ").append(getString(R.string.quote_room_labour_format, roomLabour)).append("\n")
            summary.append("  ").append(getString(R.string.quote_room_total_format, roomTotal)).append("\n\n")
        }

        if (!hasIncludedRoom) {
            summary.append(getString(R.string.quote_no_rooms)).append("\n")
            summary.append(getString(R.string.quote_subtotal_format, 0.0)).append("\n")
            summary.append(getString(R.string.quote_discount_line_format, discountPercent, 0.0)).append("\n")
            summary.append(getString(R.string.quote_total_after_discount_format, 0.0))
            return summary.toString()
        }

        val discountAmount = houseTotal * (discountPercent / 100.0)
        val finalTotal = houseTotal - discountAmount
        summary.append(getString(R.string.quote_subtotal_format, houseTotal)).append("\n")
        summary.append(getString(R.string.quote_discount_line_format, discountPercent, discountAmount)).append("\n")
        summary.append(getString(R.string.quote_total_after_discount_format, finalTotal))

        return summary.toString()
    }

    private fun buildItemKey(type: String, roomId: String, id: String?, fallbackName: String, index: Int): String {
        val stablePart = id ?: fallbackName
        return "$type:$roomId:$stablePart:$index"
    }

    private fun resolveRate(productId: String?, productRates: Map<String, Double>, defaultRate: Double): Double {
        if (productId.isNullOrBlank()) return defaultRate
        return productRates[productId] ?: defaultRate
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
}












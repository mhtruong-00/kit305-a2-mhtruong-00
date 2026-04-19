package au.edu.utas.kit305.tutorial05

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
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
    private lateinit var layoutQuoteContent: LinearLayout
    private lateinit var houseId: String
    private var currentRoomQuotes: List<RoomQuoteData> = emptyList()
    private var currentProductRates: Map<String, Double> = emptyMap()
    private var currentUsingDefaults: Boolean = false
    private val includedRooms = mutableMapOf<String, Boolean>()
    private val includedItems = mutableMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quote)

        lblQuoteTitle = findViewById(R.id.lblQuoteTitle)
        lblQuoteAddress = findViewById(R.id.lblQuoteAddress)
        lblQuoteStatus = findViewById(R.id.lblQuoteStatus)
        lblQuoteTotal = findViewById(R.id.lblQuoteTotal)
        layoutQuoteContent = findViewById(R.id.layoutQuoteContent)

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
        for (roomQuote in roomQuotes) {
            val roomId = roomQuote.room.id ?: continue
            if (!includedRooms.containsKey(roomId)) {
                includedRooms[roomId] = true
            }
            roomQuote.windows.forEach { window ->
                val key = buildItemKey("window", window.id, window.name ?: "")
                if (!includedItems.containsKey(key)) includedItems[key] = true
            }
            roomQuote.floorSpaces.forEach { floorSpace ->
                val key = buildItemKey("floor", floorSpace.id, floorSpace.name ?: "")
                if (!includedItems.containsKey(key)) includedItems[key] = true
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
                roomQuote.windows.forEach { window ->
                    val itemKey = buildItemKey("window", window.id, window.name ?: "")
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
                        return@forEach
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

                roomQuote.floorSpaces.forEach { floorSpace ->
                    val itemKey = buildItemKey("floor", floorSpace.id, floorSpace.name ?: "")
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
                        return@forEach
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

        lblQuoteTotal.text = getString(R.string.quote_total_format, houseTotal)
    }

    private fun buildItemKey(type: String, id: String?, fallbackName: String): String {
        return "$type:${id ?: fallbackName}"
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










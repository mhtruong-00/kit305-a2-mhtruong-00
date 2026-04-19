package au.edu.utas.kit305.tutorial05

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase

class QuoteActivity : AppCompatActivity() {

    private data class RoomQuoteData(
        val room: Room,
        val windows: List<Window>,
        val floorSpaces: List<FloorSpace>
    )

    private lateinit var lblQuoteTitle: TextView
    private lateinit var lblQuoteAddress: TextView
    private lateinit var lblQuoteStatus: TextView
    private lateinit var layoutQuoteContent: LinearLayout
    private lateinit var houseId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quote)

        lblQuoteTitle = findViewById(R.id.lblQuoteTitle)
        lblQuoteAddress = findViewById(R.id.lblQuoteAddress)
        lblQuoteStatus = findViewById(R.id.lblQuoteStatus)
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
                                renderRooms(loadedRooms.sortedBy { it.room.name ?: "" })
                            }
                        },
                        onFailed = {
                            pending[0] -= 1
                            if (pending[0] == 0) {
                                renderRooms(loadedRooms.sortedBy { it.room.name ?: "" })
                            }
                        }
                    )
                }
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error loading rooms for quote", it)
                layoutQuoteContent.removeAllViews()
                lblQuoteStatus.text = getString(R.string.quote_no_rooms)
            }
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

    private fun renderRooms(roomQuotes: List<RoomQuoteData>) {
        layoutQuoteContent.removeAllViews()
        lblQuoteStatus.text = ""

        for (roomQuote in roomQuotes) {
            val roomTitle = makeText(
                getString(
                    R.string.quote_room_heading,
                    roomQuote.room.name?.ifBlank { "Unnamed room" } ?: "Unnamed room"
                ),
                18f,
                topMarginDp = 12
            )
            layoutQuoteContent.addView(roomTitle)

            val hasItems = roomQuote.windows.isNotEmpty() || roomQuote.floorSpaces.isNotEmpty()
            if (!hasItems) {
                layoutQuoteContent.addView(makeText(getString(R.string.quote_no_items), 14f, leftPaddingDp = 12))
                continue
            }

            roomQuote.windows.forEach { window ->
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
                layoutQuoteContent.addView(
                    makeText(
                        getString(
                            R.string.quote_product_line,
                            window.selectedProductName?.ifBlank { getString(R.string.quote_product_basic_window) }
                                ?: getString(R.string.quote_product_basic_window)
                        ),
                        13f,
                        leftPaddingDp = 24
                    )
                )
            }

            roomQuote.floorSpaces.forEach { floorSpace ->
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
                layoutQuoteContent.addView(
                    makeText(
                        getString(
                            R.string.quote_product_line,
                            floorSpace.selectedProductName?.ifBlank { getString(R.string.quote_product_basic_floor) }
                                ?: getString(R.string.quote_product_basic_floor)
                        ),
                        13f,
                        leftPaddingDp = 24
                    )
                )
            }
        }
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
}



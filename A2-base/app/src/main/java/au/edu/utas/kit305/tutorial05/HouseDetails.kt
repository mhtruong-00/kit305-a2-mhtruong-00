package au.edu.utas.kit305.tutorial05

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import au.edu.utas.kit305.tutorial05.databinding.ActivityHouseDetailsBinding
import au.edu.utas.kit305.tutorial05.databinding.MyListItemBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase

class HouseDetails : AppCompatActivity() {
    private lateinit var ui: ActivityHouseDetailsBinding
    private val roomList = mutableListOf<Room>()
    private val filteredRooms = mutableListOf<Room>()
    private var houseId: String? = null
    private var roomsExpanded = false
    private var roomSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityHouseDetailsBinding.inflate(layoutInflater)
        setContentView(ui.root)

        houseId = intent.getStringExtra(HOUSE_ID_EXTRA)
        if (houseId.isNullOrBlank()) {
            val houseIndex = intent.getIntExtra(HOUSE_INDEX, -1)
            if (houseIndex != -1 && houseIndex < houses.size) {
                houseId = houses[houseIndex].id
            }
        }

        if (houseId.isNullOrBlank()) {
            finish()
            return
        }

        title = intent.getStringExtra(HOUSE_NAME_EXTRA) ?: getString(R.string.label_house_rooms)

        ui.lstRooms.layoutManager = LinearLayoutManager(this)
        val roomAdapter = RoomAdapter(
            rooms = filteredRooms,
            onClick = { position -> openRoomDetails(position) },
            onEdit = { position -> openRoomDetails(position) },
            onDelete = { position -> promptDeleteRoom(position) },
            onLongPress = { position -> promptDeleteRoom(position) }
        )
        roomAdapter.setToggleCallback { roomsExpanded = !roomsExpanded; roomAdapter.setExpanded(roomsExpanded) }
        ui.lstRooms.adapter = roomAdapter

        ui.lblRoomCount.text = getString(R.string.room_count_format, roomList.size)
        ui.btnAddRoom.setOnClickListener { addRoom(houseId) }
        ui.btnViewQuote.setOnClickListener { openQuote() }
        ui.txtSearchRooms.doAfterTextChanged {
            roomSearchQuery = it?.toString()?.trim().orEmpty()
            applyRoomFilter()
        }

        loadRooms(houseId)
    }

    override fun onResume() {
        super.onResume()
        loadRooms(houseId)
    }

    private fun openRoomDetails(position: Int) {
        if (position < 0 || position >= filteredRooms.size) return
        val room = filteredRooms[position]
        openRoomDetails(room)
    }

    private fun openRoomDetails(room: Room) {
        val i = Intent(this, RoomDetails::class.java)
        i.putExtra("room_id", room.id)
        i.putExtra("room_name", room.name ?: "")
        startActivity(i)
    }

    private fun openQuote() {
        val currentHouseId = houseId ?: return
        val i = Intent(this, QuoteActivity::class.java)
        i.putExtra(HOUSE_ID_EXTRA, currentHouseId)
        i.putExtra(HOUSE_NAME_EXTRA, intent.getStringExtra(HOUSE_NAME_EXTRA) ?: getString(R.string.quote_title_default))
        startActivity(i)
    }

    private fun addRoom(houseId: String?) {
        if (houseId.isNullOrBlank()) return
        val newRoom = Room(houseId = houseId, name = "New Room")
        Firebase.firestore.collection("rooms")
            .add(newRoom)
            .addOnSuccessListener {
                newRoom.id = it.id
                roomList.add(0, newRoom)
                applyRoomFilter()
                ui.lstRooms.scrollToPosition(0)
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error creating room", it)
            }
    }

    private fun promptDeleteRoom(position: Int) {
        if (position < 0 || position >= filteredRooms.size) return
        val room = filteredRooms[position]
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_room_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> deleteRoom(room) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteRoom(room: Room) {
        val roomId = room.id ?: return
        Firebase.firestore.collection("rooms")
            .document(roomId)
            .delete()
            .addOnSuccessListener {
                roomList.removeAll { it.id == roomId }
                applyRoomFilter()
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error deleting room", it)
            }
    }

    private fun loadRooms(houseId: String?) {
        if (houseId.isNullOrBlank()) {
            roomList.clear()
            applyRoomFilter()
            return
        }
        Firebase.firestore.collection("rooms")
            .whereEqualTo("houseId", houseId)
            .get()
            .addOnSuccessListener { result ->
                roomList.clear()
                for (document in result) {
                    val room = document.toObject<Room>()
                    room.id = document.id
                    roomList.add(room)
                }
                applyRoomFilter()
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error reading rooms", it)
                applyRoomFilter()
            }
    }

    private fun applyRoomFilter() {
        val q = roomSearchQuery.lowercase()
        filteredRooms.clear()
        if (q.isBlank()) {
            filteredRooms.addAll(roomList)
        } else {
            filteredRooms.addAll(roomList.filter { it.name.orEmpty().lowercase().contains(q) })
        }
        ui.lstRooms.adapter?.notifyDataSetChanged()
        ui.lblRoomCount.text = getString(R.string.room_count_format, filteredRooms.size)
    }

    class RoomHolder(var ui: MyListItemBinding) : RecyclerView.ViewHolder(ui.root)

    class RoomAdapter(
        private val rooms: MutableList<Room>,
        private val onClick: (Int) -> Unit,
        private val onEdit: (Int) -> Unit,
        private val onDelete: (Int) -> Unit,
        private val onLongPress: (Int) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var isExpanded: Boolean = false
        private var onToggleExpand: (() -> Unit)? = null

        private val ITEM_TYPE_ROOM = 0
        private val ITEM_TYPE_MORE = 1
        private val ITEMS_PER_PAGE = 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == ITEM_TYPE_ROOM) {
                val ui = MyListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                RoomHolder(ui)
            } else {
                val button = android.widget.Button(parent.context)
                button.text = "Show More Rooms"
                button.setOnClickListener { onToggleExpand?.invoke() }
                val params = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                button.layoutParams = params
                MoreHolder(button)
            }
        }

        override fun getItemCount(): Int {
            val visibleCount = if (isExpanded) rooms.size else minOf(ITEMS_PER_PAGE, rooms.size)
            val hasToggle = rooms.size > ITEMS_PER_PAGE
            return visibleCount + (if (hasToggle) 1 else 0)
        }

        override fun getItemViewType(position: Int): Int {
            val visibleCount = if (isExpanded) rooms.size else minOf(ITEMS_PER_PAGE, rooms.size)
            return if (position < visibleCount) ITEM_TYPE_ROOM else ITEM_TYPE_MORE
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is RoomHolder && position < rooms.size) {
                val room = rooms[position]
                holder.ui.txtName.text = room.name ?: "Unnamed room"
                holder.ui.txtYear.text = holder.ui.root.context.getString(R.string.label_room)

                // Load and display room photo
                if (!room.photoBase64.isNullOrBlank()) {
                    try {
                        val bytes = Base64.decode(room.photoBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            holder.ui.imgRoomPhoto.setImageBitmap(bitmap)
                            holder.ui.imgRoomPhoto.visibility = View.VISIBLE
                        } else {
                            holder.ui.imgRoomPhoto.visibility = View.GONE
                        }
                    } catch (e: Exception) {
                        Log.e(FIREBASE_TAG, "Error decoding room photo", e)
                        holder.ui.imgRoomPhoto.visibility = View.GONE
                    }
                } else {
                    holder.ui.imgRoomPhoto.visibility = View.GONE
                }

                holder.ui.root.setOnClickListener {
                    val p = holder.bindingAdapterPosition
                    if (p != RecyclerView.NO_POSITION && p < rooms.size) onClick(p)
                }
                holder.ui.btnEditRoom.setOnClickListener {
                    val p = holder.bindingAdapterPosition
                    if (p != RecyclerView.NO_POSITION && p < rooms.size) onEdit(p)
                }
                holder.ui.btnDeleteRoom.setOnClickListener {
                    val p = holder.bindingAdapterPosition
                    if (p != RecyclerView.NO_POSITION && p < rooms.size) onDelete(p)
                }
                holder.ui.root.setOnLongClickListener {
                    val p = holder.bindingAdapterPosition
                    if (p != RecyclerView.NO_POSITION && p < rooms.size) onLongPress(p)
                    true
                }
            } else if (holder is MoreHolder) {
                val button = holder.itemView as android.widget.Button
                button.text = if (isExpanded) "Show Less Rooms" else "Show More Rooms"
            }
        }

        fun setExpanded(expanded: Boolean) {
            isExpanded = expanded
            notifyDataSetChanged()
        }

        fun setToggleCallback(callback: (() -> Unit)?) {
            onToggleExpand = callback
        }
    }

    class MoreHolder(itemView: android.widget.Button) : RecyclerView.ViewHolder(itemView)
}

package au.edu.utas.kit305.tutorial05

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
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
    private var houseId: String? = null

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
        ui.lstRooms.adapter = RoomAdapter(
            rooms = roomList,
            onClick = { position -> openRoomDetails(position) },
            onLongPress = { position -> promptDeleteRoom(position) }
        )

        ui.lblRoomCount.text = getString(R.string.room_count_format, roomList.size)
        ui.btnAddRoom.setOnClickListener { addRoom(houseId) }
        ui.btnViewQuote.setOnClickListener { openQuote() }

        loadRooms(houseId)
    }

    override fun onResume() {
        super.onResume()
        loadRooms(houseId)
    }

    private fun openRoomDetails(position: Int) {
        if (position < 0 || position >= roomList.size) return
        val room = roomList[position]
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
                ui.lstRooms.adapter?.notifyItemInserted(0)
                ui.lblRoomCount.text = getString(R.string.room_count_format, roomList.size)
                ui.lstRooms.scrollToPosition(0)
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error creating room", it)
            }
    }

    private fun promptDeleteRoom(position: Int) {
        if (position < 0 || position >= roomList.size) return
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_room_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> deleteRoom(position) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteRoom(position: Int) {
        val room = roomList[position]
        val roomId = room.id ?: return
        Firebase.firestore.collection("rooms")
            .document(roomId)
            .delete()
            .addOnSuccessListener {
                roomList.removeAt(position)
                ui.lstRooms.adapter?.notifyItemRemoved(position)
                ui.lblRoomCount.text = getString(R.string.room_count_format, roomList.size)
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error deleting room", it)
            }
    }

    private fun loadRooms(houseId: String?) {
        if (houseId.isNullOrBlank()) {
            roomList.clear()
            ui.lstRooms.adapter?.notifyDataSetChanged()
            ui.lblRoomCount.text = getString(R.string.room_count_format, 0)
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
                ui.lstRooms.adapter?.notifyDataSetChanged()
                ui.lblRoomCount.text = getString(R.string.room_count_format, roomList.size)
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error reading rooms", it)
                ui.lblRoomCount.text = getString(R.string.room_count_format, 0)
            }
    }

    class RoomHolder(var ui: MyListItemBinding) : RecyclerView.ViewHolder(ui.root)

    class RoomAdapter(
        private val rooms: MutableList<Room>,
        private val onClick: (Int) -> Unit,
        private val onLongPress: (Int) -> Unit
    ) : RecyclerView.Adapter<RoomHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomHolder {
            val ui = MyListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return RoomHolder(ui)
        }

        override fun getItemCount() = rooms.size

        override fun onBindViewHolder(holder: RoomHolder, position: Int) {
            val room = rooms[position]
            holder.ui.txtName.text = room.name ?: "Unnamed room"
            holder.ui.txtYear.text = holder.ui.root.context.getString(R.string.label_room)

            holder.ui.root.setOnClickListener {
                val p = holder.bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onClick(p)
            }
            holder.ui.root.setOnLongClickListener {
                val p = holder.bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onLongPress(p)
                true
            }
        }
    }
}

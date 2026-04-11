package au.edu.utas.kit305.tutorial05

import android.app.AlertDialog
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityHouseDetailsBinding.inflate(layoutInflater)
        setContentView(ui.root)

        val houseIndex = intent.getIntExtra(HOUSE_INDEX, -1)
        if (houseIndex == -1 || houseIndex >= houses.size) {
            finish()
            return
        }

        val house = houses[houseIndex]
        ui.txtCustomerName.setText(house.customerName ?: "")
        ui.txtAddress.setText(house.address ?: "")

        ui.lstRooms.layoutManager = LinearLayoutManager(this)
        ui.lstRooms.adapter = RoomAdapter(roomList) { position ->
            promptDeleteRoom(position)
        }

        ui.lblRoomCount.text = getString(R.string.room_count_format, roomList.size)
        ui.btnAddRoom.setOnClickListener { addRoom(house.id) }

        loadRooms(house.id)

        ui.btnSaveHouse.setOnClickListener {
            house.customerName = ui.txtCustomerName.text.toString().trim()
            house.address = ui.txtAddress.text.toString().trim()

            if (house.customerName.isNullOrBlank() || house.address.isNullOrBlank()) {
                ui.lblRoomCount.text = getString(R.string.error_house_required_fields)
                return@setOnClickListener
            }

            val houseId = house.id
            if (houseId.isNullOrBlank()) {
                ui.lblRoomCount.text = getString(R.string.error_house_save_failed)
                return@setOnClickListener
            }

            Firebase.firestore.collection("houses")
                .document(houseId)
                .set(house)
                .addOnSuccessListener {
                    Log.d(FIREBASE_TAG, "Successfully updated house $houseId")
                    finish()
                }
                .addOnFailureListener {
                    Log.e(FIREBASE_TAG, "Error updating house $houseId", it)
                    ui.lblRoomCount.text = getString(R.string.error_house_save_failed)
                }
        }
    }

    private fun openRoomDetails(position: Int) {
        if (position < 0 || position >= roomList.size) return
        val room = roomList[position]
        val i = Intent(this, RoomDetails::class.java)
        i.putExtra("room_id", room.id)
        i.putExtra("room_name", room.name ?: "")
        startActivity(i)
    }

    private fun addRoom(houseId: String?) {
        if (houseId.isNullOrBlank()) return

        val newRoom = Room(
            houseId = houseId,
            name = "New Room"
        )

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
        val adapter = ui.lstRooms.adapter as RoomAdapter
        if (houseId.isNullOrBlank()) {
            val previousCount = roomList.size
            roomList.clear()
            if (previousCount > 0) {
                adapter.notifyItemRangeRemoved(0, previousCount)
            }
            ui.lblRoomCount.text = getString(R.string.room_count_format, 0)
            return
        }

        Firebase.firestore.collection("rooms")
            .whereEqualTo("houseId", houseId)
            .get()
            .addOnSuccessListener { result ->
                val previousCount = roomList.size
                roomList.clear()
                for (document in result) {
                    val room = document.toObject<Room>()
                    room.id = document.id
                    roomList.add(room)
                }

                if (previousCount > 0) {
                    adapter.notifyItemRangeRemoved(0, previousCount)
                }
                if (roomList.isNotEmpty()) {
                    adapter.notifyItemRangeInserted(0, roomList.size)
                }
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
        private val onLongPress: (Int) -> Unit
    ) : RecyclerView.Adapter<RoomHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomHolder {
            val ui = MyListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return RoomHolder(ui)
        }

        override fun getItemCount(): Int {
            return rooms.size
        }

        override fun onBindViewHolder(holder: RoomHolder, position: Int) {
            val room = rooms[position]
            holder.ui.txtName.text = room.name ?: "Unnamed room"
            holder.ui.txtYear.text = holder.ui.root.context.getString(R.string.label_room)

            holder.ui.root.setOnLongClickListener {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnLongClickListener true
                onLongPress(currentPosition)
                true
            }
        }
    }
}


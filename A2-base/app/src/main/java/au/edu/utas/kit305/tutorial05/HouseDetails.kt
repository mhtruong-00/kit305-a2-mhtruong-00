package au.edu.utas.kit305.tutorial05

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
        ui.lstRooms.adapter = RoomAdapter(roomList)

        loadRooms(house.id)

        ui.btnSaveHouse.setOnClickListener {
            house.customerName = ui.txtCustomerName.text.toString().trim()
            house.address = ui.txtAddress.text.toString().trim()

            if (house.customerName.isNullOrBlank() || house.address.isNullOrBlank()) {
                ui.lblRoomCount.text = "Customer name and address are required"
                return@setOnClickListener
            }

            val houseId = house.id
            if (houseId.isNullOrBlank()) {
                ui.lblRoomCount.text = "Could not save house"
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
                    ui.lblRoomCount.text = "Could not save house"
                }
        }
    }

    private fun loadRooms(houseId: String?) {
        if (houseId.isNullOrBlank()) {
            roomList.clear()
            ui.lstRooms.adapter?.notifyDataSetChanged()
            ui.lblRoomCount.text = "0 Rooms"
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
                ui.lblRoomCount.text = "${roomList.size} Rooms"
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error reading rooms", it)
                ui.lblRoomCount.text = "0 Rooms"
            }
    }

    class RoomHolder(var ui: MyListItemBinding) : RecyclerView.ViewHolder(ui.root)

    class RoomAdapter(private val rooms: MutableList<Room>) : RecyclerView.Adapter<RoomHolder>() {
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
            holder.ui.txtYear.text = "Room"
        }
    }
}

package au.edu.utas.kit305.tutorial05

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

const val ROOM_INDEX = "Room_Index"
const val HOUSE_ID_EXTRA = "House_Id"

class RoomDetails : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_details)

        val txtRoomName = findViewById<EditText>(R.id.txtRoomName)
        val lblRoomTitle = findViewById<TextView>(R.id.lblRoomTitle)
        val btnSaveRoom = findViewById<Button>(R.id.btnSaveRoom)

        val roomId = intent.getStringExtra("room_id") ?: run { finish(); return }
        val roomName = intent.getStringExtra("room_name") ?: ""

        txtRoomName.setText(roomName)
        lblRoomTitle.text = roomName

        btnSaveRoom.setOnClickListener {
            val name = txtRoomName.text.toString().trim()

            if (name.isBlank()) {
                lblRoomTitle.text = "Room name cannot be empty"
                return@setOnClickListener
            }

            Firebase.firestore.collection("rooms")
                .document(roomId)
                .update("name", name)
                .addOnSuccessListener {
                    Log.d(FIREBASE_TAG, "Room $roomId updated to: $name")
                    finish()
                }
                .addOnFailureListener {
                    Log.e(FIREBASE_TAG, "Error updating room", it)
                    lblRoomTitle.text = "Could not save room"
                }
        }
    }
}

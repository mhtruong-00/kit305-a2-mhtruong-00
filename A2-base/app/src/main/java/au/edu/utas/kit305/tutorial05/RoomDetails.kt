package au.edu.utas.kit305.tutorial05

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import au.edu.utas.kit305.tutorial05.databinding.MyListItemBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase

const val ROOM_INDEX = "Room_Index"
const val HOUSE_ID_EXTRA = "House_Id"
const val WINDOW_ID_EXTRA = "window_id"
const val FLOOR_SPACE_ID_EXTRA = "floor_space_id"

class RoomDetails : AppCompatActivity() {

    private lateinit var txtRoomName: android.widget.EditText
    private lateinit var lblRoomTitle: android.widget.TextView
    private lateinit var btnSaveRoom: android.widget.Button
    private lateinit var lstWindows: RecyclerView
    private lateinit var lstFloorSpaces: RecyclerView
    private lateinit var btnAddWindow: android.widget.Button
    private lateinit var btnAddFloorSpace: android.widget.Button
    private lateinit var lblWindowCount: android.widget.TextView
    private lateinit var lblFloorSpaceCount: android.widget.TextView

    private val windowList = mutableListOf<Window>()
    private val floorSpaceList = mutableListOf<FloorSpace>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_details)

        txtRoomName      = findViewById(R.id.txtRoomName)
        lblRoomTitle     = findViewById(R.id.lblRoomTitle)
        btnSaveRoom      = findViewById(R.id.btnSaveRoom)
        lstWindows       = findViewById(R.id.lstWindows)
        lstFloorSpaces   = findViewById(R.id.lstFloorSpaces)
        btnAddWindow     = findViewById(R.id.btnAddWindow)
        btnAddFloorSpace = findViewById(R.id.btnAddFloorSpace)
        lblWindowCount   = findViewById(R.id.lblWindowCount)
        lblFloorSpaceCount = findViewById(R.id.lblFloorSpaceCount)

        val roomId   = intent.getStringExtra("room_id") ?: run { finish(); return }
        val roomName = intent.getStringExtra("room_name") ?: ""

        txtRoomName.setText(roomName)
        lblRoomTitle.text = roomName

        lstWindows.layoutManager = LinearLayoutManager(this)
        lstWindows.adapter = MeasurementAdapter(
            items = windowList,
            labelFn = { w -> "${w.name} (${w.widthMm}mm × ${w.heightMm}mm)" },
            onClick = { pos -> openWindowEdit(pos) },
            onDelete = { pos -> deleteWindow(pos) }
        )

        lstFloorSpaces.layoutManager = LinearLayoutManager(this)
        lstFloorSpaces.adapter = MeasurementAdapter(
            items = floorSpaceList,
            labelFn = { f -> "${f.name} (${f.widthMm}mm × ${f.depthMm}mm)" },
            onClick = { pos -> openFloorSpaceEdit(pos) },
            onDelete = { pos -> deleteFloorSpace(pos) }
        )

        loadWindows(roomId)
        loadFloorSpaces(roomId)

        btnAddWindow.setOnClickListener     { addWindow(roomId) }
        btnAddFloorSpace.setOnClickListener { addFloorSpace(roomId) }

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

    // ─── Windows ─────────────────────────────────────────────────────────────

    private fun loadWindows(roomId: String) {
        Firebase.firestore.collection("windows")
            .whereEqualTo("roomId", roomId)
            .get()
            .addOnSuccessListener { result ->
                windowList.clear()
                for (doc in result) {
                    val w = doc.toObject<Window>(); w.id = doc.id; windowList.add(w)
                }
                lstWindows.adapter?.notifyDataSetChanged()
                lblWindowCount.text = "${windowList.size} Windows"
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error loading windows", it) }
    }

    private fun addWindow(roomId: String) {
        val w = Window(roomId = roomId, name = "New Window", widthMm = 0, heightMm = 0)
        Firebase.firestore.collection("windows").add(w)
            .addOnSuccessListener {
                w.id = it.id
                windowList.add(0, w)
                lstWindows.adapter?.notifyItemInserted(0)
                lblWindowCount.text = "${windowList.size} Windows"
                openWindowEdit(0)
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error adding window", it) }
    }

    private fun deleteWindow(pos: Int) {
        val w = windowList[pos]; val wId = w.id ?: return
        AlertDialog.Builder(this)
            .setMessage("Delete this window?")
            .setPositiveButton("Delete") { _, _ ->
                Firebase.firestore.collection("windows").document(wId).delete()
                    .addOnSuccessListener {
                        windowList.removeAt(pos)
                        lstWindows.adapter?.notifyItemRemoved(pos)
                        lblWindowCount.text = "${windowList.size} Windows"
                    }
                    .addOnFailureListener { Log.e(FIREBASE_TAG, "Error deleting window", it) }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun openWindowEdit(pos: Int) {
        if (pos < 0 || pos >= windowList.size) return
        val w = windowList[pos]; val wId = w.id ?: return
        showMeasurementDialog(
            title      = "Edit Window",
            name       = w.name ?: "",
            dim1Label  = "Width (mm)",
            dim1Value  = w.widthMm,
            dim2Label  = "Height (mm)",
            dim2Value  = w.heightMm
        ) { name, dim1, dim2 ->
            val updated = hashMapOf("name" to name, "widthMm" to dim1, "heightMm" to dim2)
            Firebase.firestore.collection("windows").document(wId).update(updated as Map<String, Any>)
                .addOnSuccessListener {
                    w.name = name; w.widthMm = dim1; w.heightMm = dim2
                    lstWindows.adapter?.notifyItemChanged(pos)
                }
                .addOnFailureListener { Log.e(FIREBASE_TAG, "Error updating window", it) }
        }
    }

    // ─── Floor Spaces ────────────────────────────────────────────────────────

    private fun loadFloorSpaces(roomId: String) {
        Firebase.firestore.collection("floorspaces")
            .whereEqualTo("roomId", roomId)
            .get()
            .addOnSuccessListener { result ->
                floorSpaceList.clear()
                for (doc in result) {
                    val f = doc.toObject<FloorSpace>(); f.id = doc.id; floorSpaceList.add(f)
                }
                lstFloorSpaces.adapter?.notifyDataSetChanged()
                lblFloorSpaceCount.text = "${floorSpaceList.size} Floor Spaces"
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error loading floor spaces", it) }
    }

    private fun addFloorSpace(roomId: String) {
        val f = FloorSpace(roomId = roomId, name = "New Floor Space", widthMm = 0, depthMm = 0)
        Firebase.firestore.collection("floorspaces").add(f)
            .addOnSuccessListener {
                f.id = it.id
                floorSpaceList.add(0, f)
                lstFloorSpaces.adapter?.notifyItemInserted(0)
                lblFloorSpaceCount.text = "${floorSpaceList.size} Floor Spaces"
                openFloorSpaceEdit(0)
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error adding floor space", it) }
    }

    private fun deleteFloorSpace(pos: Int) {
        val f = floorSpaceList[pos]; val fId = f.id ?: return
        AlertDialog.Builder(this)
            .setMessage("Delete this floor space?")
            .setPositiveButton("Delete") { _, _ ->
                Firebase.firestore.collection("floorspaces").document(fId).delete()
                    .addOnSuccessListener {
                        floorSpaceList.removeAt(pos)
                        lstFloorSpaces.adapter?.notifyItemRemoved(pos)
                        lblFloorSpaceCount.text = "${floorSpaceList.size} Floor Spaces"
                    }
                    .addOnFailureListener { Log.e(FIREBASE_TAG, "Error deleting floor space", it) }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun openFloorSpaceEdit(pos: Int) {
        if (pos < 0 || pos >= floorSpaceList.size) return
        val f = floorSpaceList[pos]; val fId = f.id ?: return
        showMeasurementDialog(
            title      = "Edit Floor Space",
            name       = f.name ?: "",
            dim1Label  = "Width (mm)",
            dim1Value  = f.widthMm,
            dim2Label  = "Depth (mm)",
            dim2Value  = f.depthMm
        ) { name, dim1, dim2 ->
            val updated = hashMapOf("name" to name, "widthMm" to dim1, "depthMm" to dim2)
            Firebase.firestore.collection("floorspaces").document(fId).update(updated as Map<String, Any>)
                .addOnSuccessListener {
                    f.name = name; f.widthMm = dim1; f.depthMm = dim2
                    lstFloorSpaces.adapter?.notifyItemChanged(pos)
                }
                .addOnFailureListener { Log.e(FIREBASE_TAG, "Error updating floor space", it) }
        }
    }

    // ─── Shared dialog for name + two dimensions ──────────────────────────────

    private fun showMeasurementDialog(
        title: String,
        name: String, dim1Label: String, dim1Value: Int,
        dim2Label: String, dim2Value: Int,
        onSave: (name: String, dim1: Int, dim2: Int) -> Unit
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, 0)
        }

        fun editText(hint: String, value: String, numeric: Boolean) = EditText(this).apply {
            this.hint = hint; setText(value)
            if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val etName  = editText("Name", name, false)
        val etDim1  = editText(dim1Label, if (dim1Value > 0) dim1Value.toString() else "", true)
        val etDim2  = editText(dim2Label, if (dim2Value > 0) dim2Value.toString() else "", true)
        layout.addView(etName); layout.addView(etDim1); layout.addView(etDim2)

        AlertDialog.Builder(this).setTitle(title).setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val n  = etName.text.toString().trim().ifBlank { "Unnamed" }
                val d1 = etDim1.text.toString().toIntOrNull() ?: 0
                val d2 = etDim2.text.toString().toIntOrNull() ?: 0
                onSave(n, d1, d2)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Generic list adapter ─────────────────────────────────────────────────

    class MeasurementHolder(val ui: MyListItemBinding) : RecyclerView.ViewHolder(ui.root)

    class MeasurementAdapter<T>(
        private val items: MutableList<T>,
        private val labelFn: (T) -> String,
        private val onClick: (Int) -> Unit,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<MeasurementHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeasurementHolder {
            val ui = MyListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return MeasurementHolder(ui)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: MeasurementHolder, position: Int) {
            val label = labelFn(items[position]).split(" (")
            holder.ui.txtName.text = label.first()
            holder.ui.txtYear.text = if (label.size > 1) "(${label[1]}" else ""
            holder.ui.root.setOnClickListener {
                val p = holder.adapterPosition
                if (p != RecyclerView.NO_POSITION) onClick(p)
            }
            holder.ui.root.setOnLongClickListener {
                val p = holder.adapterPosition
                if (p != RecyclerView.NO_POSITION) onDelete(p)
                true
            }
        }
    }
}

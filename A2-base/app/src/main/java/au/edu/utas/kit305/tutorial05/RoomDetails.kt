package au.edu.utas.kit305.tutorial05

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase

const val ROOM_INDEX           = "Room_Index"
const val HOUSE_ID_EXTRA       = "House_Id"
const val WINDOW_ID_EXTRA      = "window_id"
const val FLOOR_SPACE_ID_EXTRA = "floor_space_id"

class RoomDetails : AppCompatActivity() {

    private lateinit var txtRoomName:         android.widget.EditText
    private lateinit var lblRoomTitle:        android.widget.TextView
    private lateinit var btnSaveRoom:         android.widget.Button
    private lateinit var lstWindows:          RecyclerView
    private lateinit var lstFloorSpaces:      RecyclerView
    private lateinit var btnAddWindow:        android.widget.Button
    private lateinit var btnAddFloorSpace:    android.widget.Button
    private lateinit var lblWindowCount:      android.widget.TextView
    private lateinit var lblFloorSpaceCount:  android.widget.TextView

    private val windowList     = mutableListOf<Window>()
    private val floorSpaceList = mutableListOf<FloorSpace>()

    private var pendingWindowPos     = -1
    private var pendingFloorSpacePos = -1
    private lateinit var roomId: String

    // ─── Activity Result Launchers ───────────────────────────────────────────

    private val windowProductLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && pendingWindowPos >= 0) {
                val productId   = result.data?.getIntExtra(RESULT_PRODUCT_ID, -1) ?: -1
                val productName = result.data?.getStringExtra(RESULT_PRODUCT_NAME) ?: ""
                val panelCount  = result.data?.getIntExtra(RESULT_PANEL_COUNT, 1) ?: 1
                if (productId != -1) saveWindowProduct(pendingWindowPos, productId, productName, panelCount)
            }
            pendingWindowPos = -1
        }

    private val floorProductLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && pendingFloorSpacePos >= 0) {
                val productId   = result.data?.getIntExtra(RESULT_PRODUCT_ID, -1) ?: -1
                val productName = result.data?.getStringExtra(RESULT_PRODUCT_NAME) ?: ""
                if (productId != -1) saveFloorSpaceProduct(pendingFloorSpacePos, productId, productName)
            }
            pendingFloorSpacePos = -1
        }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_details)

        txtRoomName        = findViewById(R.id.txtRoomName)
        lblRoomTitle       = findViewById(R.id.lblRoomTitle)
        btnSaveRoom        = findViewById(R.id.btnSaveRoom)
        lstWindows         = findViewById(R.id.lstWindows)
        lstFloorSpaces     = findViewById(R.id.lstFloorSpaces)
        btnAddWindow       = findViewById(R.id.btnAddWindow)
        btnAddFloorSpace   = findViewById(R.id.btnAddFloorSpace)
        lblWindowCount     = findViewById(R.id.lblWindowCount)
        lblFloorSpaceCount = findViewById(R.id.lblFloorSpaceCount)

        roomId = intent.getStringExtra("room_id") ?: run { finish(); return }
        val roomName = intent.getStringExtra("room_name") ?: ""

        txtRoomName.setText(roomName)
        lblRoomTitle.text = roomName

        lstWindows.layoutManager = LinearLayoutManager(this)
        lstWindows.adapter = MeasurementAdapter(
            items           = windowList,
            nameFn          = { w -> w.name ?: "Unnamed" },
            dimsFn          = { w -> "${w.widthMm}mm × ${w.heightMm}mm" },
            productFn       = { w -> w.selectedProductName },
            onEdit          = { pos -> openWindowEdit(pos) },
            onDelete        = { pos -> deleteWindow(pos) },
            onSelectProduct = { pos -> launchWindowProductPicker(pos) }
        )

        lstFloorSpaces.layoutManager = LinearLayoutManager(this)
        lstFloorSpaces.adapter = MeasurementAdapter(
            items           = floorSpaceList,
            nameFn          = { f -> f.name ?: "Unnamed" },
            dimsFn          = { f -> "${f.widthMm}mm × ${f.depthMm}mm" },
            productFn       = { f -> f.selectedProductName },
            onEdit          = { pos -> openFloorSpaceEdit(pos) },
            onDelete        = { pos -> deleteFloorSpace(pos) },
            onSelectProduct = { pos -> launchFloorProductPicker(pos) }
        )

        loadWindows(roomId)
        loadFloorSpaces(roomId)

        btnAddWindow.setOnClickListener     { addWindow(roomId) }
        btnAddFloorSpace.setOnClickListener { addFloorSpace(roomId) }

        btnSaveRoom.setOnClickListener {
            val name = txtRoomName.text.toString().trim()
            if (name.isBlank()) { lblRoomTitle.text = "Room name cannot be empty"; return@setOnClickListener }
            Firebase.firestore.collection("rooms").document(roomId)
                .update("name", name)
                .addOnSuccessListener { Log.d(FIREBASE_TAG, "Room updated"); finish() }
                .addOnFailureListener { Log.e(FIREBASE_TAG, "Error updating room", it); lblRoomTitle.text = "Could not save room" }
        }
    }

    // ─── Windows ─────────────────────────────────────────────────────────────

    private fun loadWindows(roomId: String) {
        Firebase.firestore.collection("windows").whereEqualTo("roomId", roomId).get()
            .addOnSuccessListener { result ->
                windowList.clear()
                for (doc in result) { val w = doc.toObject<Window>(); w.id = doc.id; windowList.add(w) }
                lstWindows.adapter?.notifyDataSetChanged()
                lblWindowCount.text = "${windowList.size} Windows"
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error loading windows", it) }
    }

    private fun addWindow(roomId: String) {
        val w = Window(roomId = roomId, name = "New Window")
        Firebase.firestore.collection("windows").add(w)
            .addOnSuccessListener { w.id = it.id; windowList.add(0, w); lstWindows.adapter?.notifyItemInserted(0); lblWindowCount.text = "${windowList.size} Windows"; openWindowEdit(0) }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error adding window", it) }
    }

    private fun deleteWindow(pos: Int) {
        val w = windowList[pos]; val wId = w.id ?: return
        AlertDialog.Builder(this).setMessage("Delete this window?")
            .setPositiveButton("Delete") { _, _ ->
                Firebase.firestore.collection("windows").document(wId).delete()
                    .addOnSuccessListener { windowList.removeAt(pos); lstWindows.adapter?.notifyItemRemoved(pos); lblWindowCount.text = "${windowList.size} Windows" }
                    .addOnFailureListener { Log.e(FIREBASE_TAG, "Error deleting window", it) }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun openWindowEdit(pos: Int) {
        if (pos < 0 || pos >= windowList.size) return
        val w = windowList[pos]; val wId = w.id ?: return
        showMeasurementDialog("Edit Window", w.name ?: "", "Width (mm)", w.widthMm, "Height (mm)", w.heightMm) { name, d1, d2 ->
            Firebase.firestore.collection("windows").document(wId).update(mapOf("name" to name, "widthMm" to d1, "heightMm" to d2))
                .addOnSuccessListener { w.name = name; w.widthMm = d1; w.heightMm = d2; lstWindows.adapter?.notifyItemChanged(pos) }
                .addOnFailureListener { Log.e(FIREBASE_TAG, "Error updating window", it) }
        }
    }

    private fun launchWindowProductPicker(pos: Int) {
        if (pos < 0 || pos >= windowList.size) return
        val w = windowList[pos]; pendingWindowPos = pos
        val i = Intent(this, ProductListActivity::class.java)
            .putExtra(EXTRA_PRODUCT_TYPE, "window")
            .putExtra(EXTRA_SPACE_WIDTH,  w.widthMm)
            .putExtra(EXTRA_SPACE_HEIGHT, w.heightMm)
        windowProductLauncher.launch(i)
    }

    private fun saveWindowProduct(pos: Int, productId: Int, productName: String, panelCount: Int) {
        if (pos < 0 || pos >= windowList.size) return
        val w = windowList[pos]; val wId = w.id ?: return
        Firebase.firestore.collection("windows").document(wId)
            .update(mapOf("selectedProductId" to productId, "selectedProductName" to productName, "panelCount" to panelCount))
            .addOnSuccessListener {
                w.selectedProductId = productId; w.selectedProductName = productName; w.panelCount = panelCount
                lstWindows.adapter?.notifyItemChanged(pos)
                Log.d(FIREBASE_TAG, "Window product saved: $productName x$panelCount panels")
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error saving window product", it) }
    }

    // ─── Floor Spaces ────────────────────────────────────────────────────────

    private fun loadFloorSpaces(roomId: String) {
        Firebase.firestore.collection("floorspaces").whereEqualTo("roomId", roomId).get()
            .addOnSuccessListener { result ->
                floorSpaceList.clear()
                for (doc in result) { val f = doc.toObject<FloorSpace>(); f.id = doc.id; floorSpaceList.add(f) }
                lstFloorSpaces.adapter?.notifyDataSetChanged()
                lblFloorSpaceCount.text = "${floorSpaceList.size} Floor Spaces"
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error loading floor spaces", it) }
    }

    private fun addFloorSpace(roomId: String) {
        val f = FloorSpace(roomId = roomId, name = "New Floor Space")
        Firebase.firestore.collection("floorspaces").add(f)
            .addOnSuccessListener { f.id = it.id; floorSpaceList.add(0, f); lstFloorSpaces.adapter?.notifyItemInserted(0); lblFloorSpaceCount.text = "${floorSpaceList.size} Floor Spaces"; openFloorSpaceEdit(0) }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error adding floor space", it) }
    }

    private fun deleteFloorSpace(pos: Int) {
        val f = floorSpaceList[pos]; val fId = f.id ?: return
        AlertDialog.Builder(this).setMessage("Delete this floor space?")
            .setPositiveButton("Delete") { _, _ ->
                Firebase.firestore.collection("floorspaces").document(fId).delete()
                    .addOnSuccessListener { floorSpaceList.removeAt(pos); lstFloorSpaces.adapter?.notifyItemRemoved(pos); lblFloorSpaceCount.text = "${floorSpaceList.size} Floor Spaces" }
                    .addOnFailureListener { Log.e(FIREBASE_TAG, "Error deleting floor space", it) }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun openFloorSpaceEdit(pos: Int) {
        if (pos < 0 || pos >= floorSpaceList.size) return
        val f = floorSpaceList[pos]; val fId = f.id ?: return
        showMeasurementDialog("Edit Floor Space", f.name ?: "", "Width (mm)", f.widthMm, "Depth (mm)", f.depthMm) { name, d1, d2 ->
            Firebase.firestore.collection("floorspaces").document(fId).update(mapOf("name" to name, "widthMm" to d1, "depthMm" to d2))
                .addOnSuccessListener { f.name = name; f.widthMm = d1; f.depthMm = d2; lstFloorSpaces.adapter?.notifyItemChanged(pos) }
                .addOnFailureListener { Log.e(FIREBASE_TAG, "Error updating floor space", it) }
        }
    }

    private fun launchFloorProductPicker(pos: Int) {
        if (pos < 0 || pos >= floorSpaceList.size) return
        val f = floorSpaceList[pos]; pendingFloorSpacePos = pos
        val i = Intent(this, ProductListActivity::class.java)
            .putExtra(EXTRA_PRODUCT_TYPE, "floor")
            .putExtra(EXTRA_SPACE_WIDTH,  f.widthMm)
            .putExtra(EXTRA_SPACE_HEIGHT, f.depthMm)
        floorProductLauncher.launch(i)
    }

    private fun saveFloorSpaceProduct(pos: Int, productId: Int, productName: String) {
        if (pos < 0 || pos >= floorSpaceList.size) return
        val f = floorSpaceList[pos]; val fId = f.id ?: return
        Firebase.firestore.collection("floorspaces").document(fId)
            .update(mapOf("selectedProductId" to productId, "selectedProductName" to productName))
            .addOnSuccessListener {
                f.selectedProductId = productId; f.selectedProductName = productName
                lstFloorSpaces.adapter?.notifyItemChanged(pos)
                Log.d(FIREBASE_TAG, "FloorSpace product saved: $productName")
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error saving floor space product", it) }
    }

    // ─── Shared dialog ────────────────────────────────────────────────────────

    private fun showMeasurementDialog(
        title: String, name: String,
        dim1Label: String, dim1Value: Int,
        dim2Label: String, dim2Value: Int,
        onSave: (String, Int, Int) -> Unit
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, 0)
        }
        fun et(hint: String, value: String, numeric: Boolean) = EditText(this).apply {
            this.hint = hint; setText(value)
            if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val etName = et("Name", name, false)
        val etD1   = et(dim1Label, if (dim1Value > 0) dim1Value.toString() else "", true)
        val etD2   = et(dim2Label, if (dim2Value > 0) dim2Value.toString() else "", true)
        layout.addView(etName); layout.addView(etD1); layout.addView(etD2)

        AlertDialog.Builder(this).setTitle(title).setView(layout)
            .setPositiveButton("Save") { _, _ ->
                onSave(
                    etName.text.toString().trim().ifBlank { "Unnamed" },
                    etD1.text.toString().toIntOrNull() ?: 0,
                    etD2.text.toString().toIntOrNull() ?: 0
                )
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ─── Adapter ─────────────────────────────────────────────────────────────

    class MeasurementHolder(val root: android.view.View) : RecyclerView.ViewHolder(root) {
        val txtName:    TextView = root.findViewById(R.id.txtMeasurementName)
        val txtDims:    TextView = root.findViewById(R.id.txtMeasurementDims)
        val txtProduct: TextView = root.findViewById(R.id.txtSelectedProduct)
        val btnEdit:    Button   = root.findViewById(R.id.btnMeasurementEdit)
        val btnDelete:  Button   = root.findViewById(R.id.btnMeasurementDelete)
        val btnProduct: Button   = root.findViewById(R.id.btnSelectProduct)
    }

    class MeasurementAdapter<T>(
        private val items:           MutableList<T>,
        private val nameFn:          (T) -> String,
        private val dimsFn:          (T) -> String,
        private val productFn:       (T) -> String?,
        private val onEdit:          (Int) -> Unit,
        private val onDelete:        (Int) -> Unit,
        private val onSelectProduct: (Int) -> Unit
    ) : RecyclerView.Adapter<MeasurementHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeasurementHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.measurement_list_item, parent, false)
            return MeasurementHolder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: MeasurementHolder, position: Int) {
            val item = items[position]
            holder.txtName.text = nameFn(item)
            holder.txtDims.text = dimsFn(item)
            val prod = productFn(item)
            holder.txtProduct.text = if (!prod.isNullOrBlank()) "Product: $prod" else "No product selected"

            holder.btnEdit.setOnClickListener   { val p = holder.bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) onEdit(p) }
            holder.btnDelete.setOnClickListener { val p = holder.bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) onDelete(p) }
            holder.btnProduct.setOnClickListener { val p = holder.bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) onSelectProduct(p) }        }
    }
}


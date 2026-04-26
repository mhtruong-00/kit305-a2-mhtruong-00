package au.edu.utas.kit305.tutorial05

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import kotlin.math.roundToInt

const val ROOM_INDEX           = "Room_Index"
const val HOUSE_ID_EXTRA       = "House_Id"
const val WINDOW_ID_EXTRA      = "window_id"
const val FLOOR_SPACE_ID_EXTRA = "floor_space_id"

class RoomDetails : AppCompatActivity() {

    private enum class PhotoTarget {
        ROOM,
        WINDOW,
        FLOOR_SPACE
    }

    private lateinit var txtRoomName:         android.widget.EditText
    private lateinit var lblRoomTitle:        android.widget.TextView
    private lateinit var btnSaveRoom:         android.widget.Button
    private lateinit var lstWindows:          RecyclerView
    private lateinit var lstFloorSpaces:      RecyclerView
    private lateinit var btnAddWindow:        android.widget.Button
    private lateinit var btnAddFloorSpace:    android.widget.Button
    private lateinit var btnToggleWindows:    android.widget.Button
    private lateinit var btnToggleFloor:      android.widget.Button
    private lateinit var txtSearchWindows:    EditText
    private lateinit var txtSearchFloor:      EditText
    private lateinit var lblWindowCount:      android.widget.TextView
    private lateinit var lblFloorSpaceCount:  android.widget.TextView
    private lateinit var imgRoom:             ImageView
    private lateinit var btnTakePhoto:        android.widget.Button
    private lateinit var btnPickGallery:      android.widget.Button
    private lateinit var btnRemoveRoomPhoto:  android.widget.Button

    private val windowList     = mutableListOf<Window>()
    private val floorSpaceList = mutableListOf<FloorSpace>()
    private val filteredWindowList = mutableListOf<Window>()
    private val filteredFloorSpaceList = mutableListOf<FloorSpace>()
    private var windowsExpanded = false
    private var floorSpacesExpanded = false
    private var windowsSearchQuery = ""
    private var floorSearchQuery = ""

    private var pendingWindowId: String? = null
    private var pendingFloorSpaceId: String? = null
    private lateinit var roomId: String
    private var pendingCameraUri: Uri? = null
    private var currentPhotoTarget: PhotoTarget = PhotoTarget.ROOM
    private var currentPhotoItemPos: Int = -1

    private lateinit var windowsAdapter: MeasurementAdapter<Window>
    private lateinit var floorAdapter: MeasurementAdapter<FloorSpace>

    // ─── Activity Result Launchers ───────────────────────────────────────────

    private val windowProductLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && !pendingWindowId.isNullOrBlank()) {
                val productId = result.data?.getStringExtra(RESULT_PRODUCT_ID) ?: ""
                val productName = result.data?.getStringExtra(RESULT_PRODUCT_NAME) ?: ""
                val panelCount = result.data?.getIntExtra(RESULT_PANEL_COUNT, 1) ?: 1
                if (productId.isNotBlank()) saveWindowProductById(pendingWindowId!!, productId, productName, panelCount)
            }
            pendingWindowId = null
        }

    private val floorProductLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && !pendingFloorSpaceId.isNullOrBlank()) {
                val productId = result.data?.getStringExtra(RESULT_PRODUCT_ID) ?: ""
                val productName = result.data?.getStringExtra(RESULT_PRODUCT_NAME) ?: ""
                if (productId.isNotBlank()) saveFloorSpaceProductById(pendingFloorSpaceId!!, productId, productName)
            }
            pendingFloorSpaceId = null
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCameraCapture()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            if (success && uri != null) {
                handleSelectedPhoto(uri)
            }
            pendingCameraUri = null
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                handleSelectedPhoto(uri)
            }
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
        btnToggleWindows   = findViewById(R.id.btnToggleWindows)
        btnToggleFloor     = findViewById(R.id.btnToggleFloorSpaces)
        txtSearchWindows   = findViewById(R.id.txtSearchWindows)
        txtSearchFloor     = findViewById(R.id.txtSearchFloorSpaces)
        lblWindowCount     = findViewById(R.id.lblWindowCount)
        lblFloorSpaceCount = findViewById(R.id.lblFloorSpaceCount)
        imgRoom            = findViewById(R.id.imgRoom)
        btnTakePhoto       = findViewById(R.id.btnTakePhoto)
        btnPickGallery     = findViewById(R.id.btnPickGallery)
        btnRemoveRoomPhoto = findViewById(R.id.btnRemoveRoomPhoto)
        btnRemoveRoomPhoto.visibility = View.GONE

        roomId = intent.getStringExtra("room_id") ?: run { finish(); return }
        val roomName = intent.getStringExtra("room_name") ?: ""

        txtRoomName.setText(roomName)
        lblRoomTitle.text = roomName
        loadRoom(roomId)

        lstWindows.layoutManager = LinearLayoutManager(this)
        windowsAdapter = MeasurementAdapter(
            items           = filteredWindowList,
            nameFn          = { w -> w.name ?: "Unnamed" },
            dimsFn          = { w -> "${w.widthMm}mm × ${w.heightMm}mm" },
            productFn       = { w -> w.selectedProductName },
            photoFn         = { w -> w.photoBase64 },
            onEdit          = { pos -> openWindowEdit(pos) },
            onDelete        = { pos -> deleteWindow(pos) },
            onSelectProduct = { pos -> launchWindowProductPicker(pos) },
            onPhoto         = { pos -> startMeasurementCamera(PhotoTarget.WINDOW, pos) },
            onGallery       = { pos -> startMeasurementGallery(PhotoTarget.WINDOW, pos) },
            onRemovePhoto   = { pos -> promptRemovePhoto(PhotoTarget.WINDOW, pos) }
        )
        lstWindows.adapter = windowsAdapter

        lstFloorSpaces.layoutManager = LinearLayoutManager(this)
        floorAdapter = MeasurementAdapter(
            items           = filteredFloorSpaceList,
            nameFn          = { f -> f.name ?: "Unnamed" },
            dimsFn          = { f -> "${f.widthMm}mm × ${f.depthMm}mm" },
            productFn       = { f -> f.selectedProductName },
            photoFn         = { f -> f.photoBase64 },
            onEdit          = { pos -> openFloorSpaceEdit(pos) },
            onDelete        = { pos -> deleteFloorSpace(pos) },
            onSelectProduct = { pos -> launchFloorProductPicker(pos) },
            onPhoto         = { pos -> startMeasurementCamera(PhotoTarget.FLOOR_SPACE, pos) },
            onGallery       = { pos -> startMeasurementGallery(PhotoTarget.FLOOR_SPACE, pos) },
            onRemovePhoto   = { pos -> promptRemovePhoto(PhotoTarget.FLOOR_SPACE, pos) }
        )
        lstFloorSpaces.adapter = floorAdapter

        btnToggleWindows.setOnClickListener {
            windowsExpanded = !windowsExpanded
            windowsAdapter.setExpanded(windowsExpanded)
            lstWindows.post { lstWindows.requestLayout() }
            updateToggleButtons()
        }

        btnToggleFloor.setOnClickListener {
            floorSpacesExpanded = !floorSpacesExpanded
            floorAdapter.setExpanded(floorSpacesExpanded)
            lstFloorSpaces.post { lstFloorSpaces.requestLayout() }
            updateToggleButtons()
        }

        txtSearchWindows.doAfterTextChanged {
            windowsSearchQuery = it?.toString()?.trim().orEmpty()
            applyWindowFilter()
        }

        txtSearchFloor.doAfterTextChanged {
            floorSearchQuery = it?.toString()?.trim().orEmpty()
            applyFloorFilter()
        }

        loadWindows(roomId)
        loadFloorSpaces(roomId)

        btnAddWindow.setOnClickListener     { addWindow(roomId) }
        btnAddFloorSpace.setOnClickListener { addFloorSpace(roomId) }
        btnTakePhoto.setOnClickListener {
            setPhotoTarget(PhotoTarget.ROOM)
            requestCameraPermissionAndCapture()
        }
        btnPickGallery.setOnClickListener {
            setPhotoTarget(PhotoTarget.ROOM)
            pickImageLauncher.launch("image/*")
        }
        btnRemoveRoomPhoto.setOnClickListener {
            promptRemovePhoto(PhotoTarget.ROOM, -1)
        }

        btnSaveRoom.setOnClickListener {
            val name = txtRoomName.text.toString().trim()
            if (name.isBlank()) { lblRoomTitle.text = "Room name cannot be empty"; return@setOnClickListener }
            Firebase.firestore.collection("rooms").document(roomId)
                .update("name", name)
                .addOnSuccessListener { Log.d(FIREBASE_TAG, "Room updated"); finish() }
                .addOnFailureListener { Log.e(FIREBASE_TAG, "Error updating room", it); lblRoomTitle.text = "Could not save room" }
        }
    }

    private fun loadRoom(roomId: String) {
        Firebase.firestore.collection("rooms").document(roomId).get()
            .addOnSuccessListener { doc ->
                val room = doc.toObject<Room>() ?: return@addOnSuccessListener
                txtRoomName.setText(room.name ?: "")
                lblRoomTitle.text = room.name ?: "Room"
                showRoomPhoto(room.photoBase64, room.photoUrl)
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error loading room", it) }
    }

    private fun requestCameraPermissionAndCapture() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraCapture() {
        val uri = createTempImageUri() ?: run {
            Toast.makeText(this, "Could not prepare camera file", Toast.LENGTH_SHORT).show()
            return
        }
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun setPhotoTarget(target: PhotoTarget, itemPos: Int = -1) {
        currentPhotoTarget = target
        currentPhotoItemPos = itemPos
    }

    private fun startMeasurementCamera(target: PhotoTarget, pos: Int) {
        setPhotoTarget(target, pos)
        requestCameraPermissionAndCapture()
    }

    private fun startMeasurementGallery(target: PhotoTarget, pos: Int) {
        setPhotoTarget(target, pos)
        pickImageLauncher.launch("image/*")
    }

    private fun handleSelectedPhoto(uri: Uri) {
        when (currentPhotoTarget) {
            PhotoTarget.ROOM -> {
                imgRoom.visibility = View.VISIBLE
                imgRoom.setImageURI(uri)
                uploadRoomPhoto(uri)
            }
            PhotoTarget.WINDOW -> saveWindowPhoto(currentPhotoItemPos, uri)
            PhotoTarget.FLOOR_SPACE -> saveFloorSpacePhoto(currentPhotoItemPos, uri)
        }
    }

    private fun createTempImageUri(): Uri? {
        return try {
            val imageDir = File(cacheDir, "room_photos").apply { mkdirs() }
            val imageFile = File.createTempFile("room_${roomId}_", ".jpg", imageDir)
            FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", imageFile)
        } catch (e: Exception) {
            Log.e(FIREBASE_TAG, "Error creating temp image uri", e)
            null
        }
    }

    private fun uploadRoomPhoto(uri: Uri) {
        val base64 = encodeImageToBase64(uri)
        if (base64 == null) {
            Toast.makeText(this, "Photo processing failed", Toast.LENGTH_SHORT).show()
            return
        }

        Firebase.firestore.collection("rooms").document(roomId)
            .update(mapOf("photoBase64" to base64, "photoUrl" to ""))
            .addOnSuccessListener {
                Toast.makeText(this, "Room photo saved", Toast.LENGTH_SHORT).show()
                showRoomPhoto(base64, null)
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error saving photo in Firestore", it)
                Toast.makeText(this, "Photo save failed: ${it.message ?: "unknown"}", Toast.LENGTH_LONG).show()
            }
    }

    private fun saveWindowPhoto(pos: Int, uri: Uri) {
        if (pos !in filteredWindowList.indices) return
        val base64 = encodeImageToBase64(uri) ?: run {
            Toast.makeText(this, "Photo processing failed", Toast.LENGTH_SHORT).show()
            return
        }
        val window = filteredWindowList[pos]
        val windowId = window.id ?: return

        Firebase.firestore.collection("windows").document(windowId)
            .update("photoBase64", base64)
            .addOnSuccessListener {
                window.photoBase64 = base64
                applyWindowFilter()
                Toast.makeText(this, "Window photo saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error saving window photo", it)
                Toast.makeText(this, "Window photo save failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveFloorSpacePhoto(pos: Int, uri: Uri) {
        if (pos !in filteredFloorSpaceList.indices) return
        val base64 = encodeImageToBase64(uri) ?: run {
            Toast.makeText(this, "Photo processing failed", Toast.LENGTH_SHORT).show()
            return
        }
        val floorSpace = filteredFloorSpaceList[pos]
        val floorSpaceId = floorSpace.id ?: return

        Firebase.firestore.collection("floorspaces").document(floorSpaceId)
            .update("photoBase64", base64)
            .addOnSuccessListener {
                floorSpace.photoBase64 = base64
                applyFloorFilter()
                Toast.makeText(this, "Floor photo saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error saving floor photo", it)
                Toast.makeText(this, "Floor photo save failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun removeMeasurementPhoto(target: PhotoTarget, pos: Int) {
        when (target) {
            PhotoTarget.ROOM -> {
                Firebase.firestore.collection("rooms").document(roomId)
                    .update(mapOf("photoBase64" to "", "photoUrl" to ""))
                    .addOnSuccessListener {
                        imgRoom.setImageDrawable(null)
                        imgRoom.visibility = View.GONE
                        btnRemoveRoomPhoto.visibility = View.GONE
                    }
                return
            }
            PhotoTarget.WINDOW -> {
                if (pos !in filteredWindowList.indices) return
                val item = filteredWindowList[pos]
                val itemId = item.id ?: return
                Firebase.firestore.collection("windows").document(itemId)
                    .update("photoBase64", "")
                    .addOnSuccessListener {
                        item.photoBase64 = null
                        applyWindowFilter()
                    }
                    .addOnFailureListener { Log.e(FIREBASE_TAG, "Error removing window photo", it) }
            }
            PhotoTarget.FLOOR_SPACE -> {
                if (pos !in filteredFloorSpaceList.indices) return
                val item = filteredFloorSpaceList[pos]
                val itemId = item.id ?: return
                Firebase.firestore.collection("floorspaces").document(itemId)
                    .update("photoBase64", "")
                    .addOnSuccessListener {
                        item.photoBase64 = null
                        applyFloorFilter()
                    }
                    .addOnFailureListener { Log.e(FIREBASE_TAG, "Error removing floor photo", it) }
            }
        }
    }

    private fun promptRemovePhoto(target: PhotoTarget, pos: Int) {
        val message = when (target) {
            PhotoTarget.ROOM -> "Remove room photo?"
            PhotoTarget.WINDOW -> "Remove this window photo?"
            PhotoTarget.FLOOR_SPACE -> "Remove this floor space photo?"
        }

        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("Remove") { _, _ -> removeMeasurementPhoto(target, pos) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun encodeImageToBase64(uri: Uri): String? {
        return try {
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
            val bytes = compressBitmap(bitmap)
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(FIREBASE_TAG, "Error encoding photo", e)
            null
        }
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val scaledBitmap = scaleBitmap(bitmap, 1280)
        val output = ByteArrayOutputStream()
        var quality = 85
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)

        // Keep comfortably under Firestore doc size constraints after Base64 expansion.
        while (output.size() > 250_000 && quality > 25) {
            output.reset()
            quality -= 10
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        }
        return output.toByteArray()
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / largestSide.toFloat()
        val width = (bitmap.width * scale).roundToInt()
        val height = (bitmap.height * scale).roundToInt()
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun showRoomPhoto(photoBase64: String?, photoUrl: String?) {
        if (!photoBase64.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    imgRoom.setImageBitmap(bitmap)
                    imgRoom.visibility = View.VISIBLE
                    btnRemoveRoomPhoto.visibility = View.VISIBLE
                    return
                }
            } catch (e: Exception) {
                Log.e(FIREBASE_TAG, "Error decoding base64 room photo", e)
            }
        }

        if (photoUrl.isNullOrBlank()) {
            imgRoom.setImageDrawable(null)
            imgRoom.visibility = View.GONE
            btnRemoveRoomPhoto.visibility = View.GONE
            return
        }

        imgRoom.visibility = View.VISIBLE
        Thread {
            try {
                val bitmap = URL(photoUrl).openStream().use { BitmapFactory.decodeStream(it) }
                runOnUiThread {
                    if (bitmap != null) {
                        imgRoom.setImageBitmap(bitmap)
                        btnRemoveRoomPhoto.visibility = View.VISIBLE
                    } else {
                        imgRoom.setImageDrawable(null)
                        imgRoom.visibility = View.GONE
                        btnRemoveRoomPhoto.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(FIREBASE_TAG, "Error loading room photo", e)
                runOnUiThread {
                    imgRoom.setImageDrawable(null)
                    imgRoom.visibility = View.GONE
                    btnRemoveRoomPhoto.visibility = View.GONE
                }
            }
        }.start()
    }

    // ─── Windows ─────────────────────────────────────────────────────────────

    private fun loadWindows(roomId: String) {
        Firebase.firestore.collection("windows").whereEqualTo("roomId", roomId).get()
            .addOnSuccessListener { result ->
                windowList.clear()
                for (doc in result) { val w = doc.toObject<Window>(); w.id = doc.id; windowList.add(w) }
                if (windowList.size <= 2) windowsExpanded = false
                windowsAdapter.setExpanded(windowsExpanded)
                lblWindowCount.text = "${windowList.size} Windows"
                updateToggleButtons()
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error loading windows", it) }
    }

    private fun addWindow(roomId: String) {
        showMeasurementDialog(
            "Add Window",
            "New Window",
            "Width (mm)",
            0,
            "Height (mm)",
            0
        ) { name, d1, d2 ->
            val w = Window(roomId = roomId, name = name, widthMm = d1, heightMm = d2)
            Firebase.firestore.collection("windows").add(w)
                .addOnSuccessListener {
                    w.id = it.id
                    windowList.add(0, w)
                    lblWindowCount.text = "${windowList.size} Windows"
                    lstWindows.scrollToPosition(0)
                    applyWindowFilter()
                }
                .addOnFailureListener { Log.e(FIREBASE_TAG, "Error adding window", it) }
        }
    }

    private fun deleteWindow(pos: Int) {
        if (pos !in filteredWindowList.indices) return
        val w = filteredWindowList[pos]
        val wId = w.id ?: return
        AlertDialog.Builder(this).setMessage("Delete this window?")
            .setPositiveButton("Delete") { _, _ ->
                Firebase.firestore.collection("windows").document(wId).delete()
                    .addOnSuccessListener {
                        windowList.removeAll { it.id == wId }
                        if (windowList.size <= 2) windowsExpanded = false
                        windowsAdapter.setExpanded(windowsExpanded)
                        lblWindowCount.text = "${windowList.size} Windows"
                        applyWindowFilter()
                    }
                    .addOnFailureListener { Log.e(FIREBASE_TAG, "Error deleting window", it) }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun openWindowEdit(pos: Int) {
        if (pos < 0 || pos >= filteredWindowList.size) return
        val w = filteredWindowList[pos]; val wId = w.id ?: return
        showMeasurementDialog("Edit Window", w.name ?: "", "Width (mm)", w.widthMm, "Height (mm)", w.heightMm) { name, d1, d2 ->
            Firebase.firestore.collection("windows").document(wId).update(mapOf("name" to name, "widthMm" to d1, "heightMm" to d2))
                .addOnSuccessListener { w.name = name; w.widthMm = d1; w.heightMm = d2; applyWindowFilter() }
                .addOnFailureListener { Log.e(FIREBASE_TAG, "Error updating window", it) }
        }
    }

    private fun launchWindowProductPicker(pos: Int) {
        if (pos < 0 || pos >= filteredWindowList.size) return
        val w = filteredWindowList[pos]
        pendingWindowId = w.id
        val i = Intent(this, ProductListActivity::class.java)
            .putExtra(EXTRA_PRODUCT_TYPE, "window")
            .putExtra(EXTRA_SPACE_WIDTH,  w.widthMm)
            .putExtra(EXTRA_SPACE_HEIGHT, w.heightMm)
        windowProductLauncher.launch(i)
    }

    private fun saveWindowProductById(windowId: String, productId: String, productName: String, panelCount: Int) {
        val w = windowList.firstOrNull { it.id == windowId } ?: return
        val wId = w.id ?: return
        Firebase.firestore.collection("windows").document(wId)
            .update(mapOf("selectedProductId" to productId, "selectedProductName" to productName, "panelCount" to panelCount))
            .addOnSuccessListener {
                w.selectedProductId = productId; w.selectedProductName = productName; w.panelCount = panelCount
                applyWindowFilter()
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
                lblFloorSpaceCount.text = "${floorSpaceList.size} Floor Spaces"
                applyFloorFilter()
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error loading floor spaces", it) }
    }

    private fun addFloorSpace(roomId: String) {
        showMeasurementDialog(
            "Add Floor Space",
            "New Floor Space",
            "Width (mm)",
            0,
            "Depth (mm)",
            0
        ) { name, d1, d2 ->
            val f = FloorSpace(roomId = roomId, name = name, widthMm = d1, depthMm = d2)
            Firebase.firestore.collection("floorspaces").add(f)
                .addOnSuccessListener {
                    f.id = it.id
                    floorSpaceList.add(0, f)
                    lblFloorSpaceCount.text = "${floorSpaceList.size} Floor Spaces"
                    lstFloorSpaces.scrollToPosition(0)
                    applyFloorFilter()
                }
                .addOnFailureListener { Log.e(FIREBASE_TAG, "Error adding floor space", it) }
        }
    }

    private fun deleteFloorSpace(pos: Int) {
        if (pos !in filteredFloorSpaceList.indices) return
        val f = filteredFloorSpaceList[pos]
        val fId = f.id ?: return
        AlertDialog.Builder(this).setMessage("Delete this floor space?")
            .setPositiveButton("Delete") { _, _ ->
                Firebase.firestore.collection("floorspaces").document(fId).delete()
                    .addOnSuccessListener {
                        floorSpaceList.removeAll { it.id == fId }
                        if (floorSpaceList.size <= 2) floorSpacesExpanded = false
                        floorAdapter.setExpanded(floorSpacesExpanded)
                        lblFloorSpaceCount.text = "${floorSpaceList.size} Floor Spaces"
                        applyFloorFilter()
                    }
                    .addOnFailureListener { Log.e(FIREBASE_TAG, "Error deleting floor space", it) }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun openFloorSpaceEdit(pos: Int) {
        if (pos < 0 || pos >= filteredFloorSpaceList.size) return
        val f = filteredFloorSpaceList[pos]; val fId = f.id ?: return
        showMeasurementDialog("Edit Floor Space", f.name ?: "", "Width (mm)", f.widthMm, "Depth (mm)", f.depthMm) { name, d1, d2 ->
            Firebase.firestore.collection("floorspaces").document(fId).update(mapOf("name" to name, "widthMm" to d1, "depthMm" to d2))
                .addOnSuccessListener { f.name = name; f.widthMm = d1; f.depthMm = d2; applyFloorFilter() }
                .addOnFailureListener { Log.e(FIREBASE_TAG, "Error updating floor space", it) }
        }
    }

    private fun launchFloorProductPicker(pos: Int) {
        if (pos < 0 || pos >= filteredFloorSpaceList.size) return
        val f = filteredFloorSpaceList[pos]
        pendingFloorSpaceId = f.id
        val i = Intent(this, ProductListActivity::class.java)
            .putExtra(EXTRA_PRODUCT_TYPE, "floor")
            .putExtra(EXTRA_SPACE_WIDTH,  f.widthMm)
            .putExtra(EXTRA_SPACE_HEIGHT, f.depthMm)
        floorProductLauncher.launch(i)
    }

    private fun saveFloorSpaceProductById(floorId: String, productId: String, productName: String) {
        val f = floorSpaceList.firstOrNull { it.id == floorId } ?: return
        val fId = f.id ?: return
        Firebase.firestore.collection("floorspaces").document(fId)
            .update(mapOf("selectedProductId" to productId, "selectedProductName" to productName))
            .addOnSuccessListener {
                f.selectedProductId = productId; f.selectedProductName = productName
                applyFloorFilter()
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

    private fun updateToggleButtons() {
        btnToggleWindows.visibility = if (filteredWindowList.size > 2) View.VISIBLE else View.GONE
        btnToggleWindows.text = if (windowsExpanded) "Show Less Windows" else "Show More Windows"

        btnToggleFloor.visibility = if (filteredFloorSpaceList.size > 2) View.VISIBLE else View.GONE
        btnToggleFloor.text = if (floorSpacesExpanded) "Show Less Floor Spaces" else "Show More Floor Spaces"
    }

    private fun applyWindowFilter() {
        val q = windowsSearchQuery.lowercase()
        filteredWindowList.clear()
        if (q.isBlank()) {
            filteredWindowList.addAll(windowList)
        } else {
            filteredWindowList.addAll(
                windowList.filter {
                    val name = it.name.orEmpty().lowercase()
                    val product = it.selectedProductName.orEmpty().lowercase()
                    name.contains(q) || product.contains(q)
                }
            )
        }

        if (filteredWindowList.size <= 2) windowsExpanded = false
        windowsAdapter.setExpanded(windowsExpanded)
        updateToggleButtons()
    }

    private fun applyFloorFilter() {
        val q = floorSearchQuery.lowercase()
        filteredFloorSpaceList.clear()
        if (q.isBlank()) {
            filteredFloorSpaceList.addAll(floorSpaceList)
        } else {
            filteredFloorSpaceList.addAll(
                floorSpaceList.filter {
                    val name = it.name.orEmpty().lowercase()
                    val product = it.selectedProductName.orEmpty().lowercase()
                    name.contains(q) || product.contains(q)
                }
            )
        }

        if (filteredFloorSpaceList.size <= 2) floorSpacesExpanded = false
        floorAdapter.setExpanded(floorSpacesExpanded)
        updateToggleButtons()
    }

    // ─── Adapter ─────────────────────────────────────────────────────────────

    class MeasurementHolder(val root: android.view.View) : RecyclerView.ViewHolder(root) {
        val txtName:    TextView = root.findViewById(R.id.txtMeasurementName)
        val txtDims:    TextView = root.findViewById(R.id.txtMeasurementDims)
        val txtProduct: TextView = root.findViewById(R.id.txtSelectedProduct)
        val imgPhoto:   ImageView = root.findViewById(R.id.imgMeasurementPhoto)
        val btnEdit:    Button   = root.findViewById(R.id.btnMeasurementEdit)
        val btnDelete:  Button   = root.findViewById(R.id.btnMeasurementDelete)
        val btnProduct: Button   = root.findViewById(R.id.btnSelectProduct)
        val btnPhoto:   Button   = root.findViewById(R.id.btnMeasurementPhoto)
        val btnGallery: Button = root.findViewById(R.id.btnMeasurementGallery)
        val btnRemovePhoto: Button = root.findViewById(R.id.btnMeasurementRemovePhoto)
    }

    class MeasurementAdapter<T>(
        private val items:           MutableList<T>,
        private val nameFn:          (T) -> String,
        private val dimsFn:          (T) -> String,
        private val productFn:       (T) -> String?,
        private val photoFn:         (T) -> String?,
        private val onEdit:          (Int) -> Unit,
        private val onDelete:        (Int) -> Unit,
        private val onSelectProduct: (Int) -> Unit,
        private val onPhoto:         (Int) -> Unit,
        private val onGallery:       (Int) -> Unit,
        private val onRemovePhoto:   (Int) -> Unit
    ) : RecyclerView.Adapter<MeasurementHolder>() {

        private var isExpanded = false
        private val itemsPerPage = 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeasurementHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.measurement_list_item, parent, false)
            return MeasurementHolder(v)
        }

        override fun getItemCount(): Int {
            return if (isExpanded) items.size else minOf(itemsPerPage, items.size)
        }

        override fun onBindViewHolder(holder: MeasurementHolder, position: Int) {
            val item = items[position]
            holder.txtName.text = nameFn(item)
            holder.txtDims.text = dimsFn(item)
            val prod = productFn(item)
            holder.txtProduct.text = if (!prod.isNullOrBlank()) "Product: $prod" else "No product selected"
            bindPhoto(holder.imgPhoto, holder.btnRemovePhoto, photoFn(item))

            holder.root.setOnClickListener {
                val p = holder.bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onEdit(p)
            }

            holder.btnEdit.setOnClickListener { val p = holder.bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) onEdit(p) }
            holder.btnDelete.setOnClickListener { val p = holder.bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) onDelete(p) }
            holder.btnProduct.setOnClickListener { val p = holder.bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) onSelectProduct(p) }
            holder.btnPhoto.setOnClickListener { val p = holder.bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) onPhoto(p) }
            holder.btnGallery.setOnClickListener { val p = holder.bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) onGallery(p) }
            holder.btnRemovePhoto.setOnClickListener { val p = holder.bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) onRemovePhoto(p) }
        }

        fun setExpanded(expanded: Boolean) {
            isExpanded = expanded
            notifyDataSetChanged()
        }

        private fun bindPhoto(imageView: ImageView, removeButton: Button, photoBase64: String?) {
            if (photoBase64.isNullOrBlank()) {
                imageView.setImageDrawable(null)
                imageView.visibility = View.GONE
                removeButton.visibility = View.GONE
                return
            }

            try {
                val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = View.VISIBLE
                    removeButton.visibility = View.VISIBLE
                } else {
                    imageView.setImageDrawable(null)
                    imageView.visibility = View.GONE
                    removeButton.visibility = View.GONE
                }
            } catch (_: Exception) {
                imageView.setImageDrawable(null)
                imageView.visibility = View.GONE
                removeButton.visibility = View.GONE
            }
        }
    }

}

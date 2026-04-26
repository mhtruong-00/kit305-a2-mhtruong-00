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
import android.text.InputFilter
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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
import androidx.core.graphics.scale
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URL
import kotlin.math.roundToInt

const val HOUSE_ID_EXTRA       = "House_Id"

class RoomDetails : AppCompatActivity() {

    companion object {
        private const val MIN_DIMENSION_MM = 1
        private const val MAX_DIMENSION_MM = 20_000
        private const val MAX_IMAGE_DIMENSION = 1280
    }

    private enum class PhotoTarget {
        ROOM,
        WINDOW,
        FLOOR_SPACE
    }

    private lateinit var txtRoomName:         EditText
    private lateinit var lblRoomTitle:        TextView
    private lateinit var btnSaveRoom:         Button
    private lateinit var roomDetailsScroll:   ScrollView
    private lateinit var lstWindows:          RecyclerView
    private lateinit var lstFloorSpaces:      RecyclerView
    private lateinit var btnAddWindow:        Button
    private lateinit var btnAddFloorSpace:    Button
    private lateinit var btnToggleWindows:    Button
    private lateinit var btnToggleFloor:      Button
    private lateinit var txtSearchWindows:    EditText
    private lateinit var txtSearchFloor:      EditText
    private lateinit var lblWindowCount:      TextView
    private lateinit var lblFloorSpaceCount:  TextView
    private lateinit var imgRoom:             ImageView
    private lateinit var btnTakePhoto:        Button
    private lateinit var btnPickGallery:      Button
    private lateinit var btnRemoveRoomPhoto:  Button

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
            val selectedWindowId = pendingWindowId
            if (result.resultCode == RESULT_OK && !selectedWindowId.isNullOrBlank()) {
                val productId = result.data?.getStringExtra(RESULT_PRODUCT_ID) ?: ""
                val productName = result.data?.getStringExtra(RESULT_PRODUCT_NAME) ?: ""
                val panelCount = result.data?.getIntExtra(RESULT_PANEL_COUNT, 1) ?: 1
                val variant = result.data?.getStringExtra(RESULT_PRODUCT_VARIANT)
                if (productId.isNotBlank()) {
                    saveWindowProductById(selectedWindowId, productId, productName, panelCount, variant)
                }
            }
            pendingWindowId = null
        }

    private val floorProductLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val selectedFloorId = pendingFloorSpaceId
            if (result.resultCode == RESULT_OK && !selectedFloorId.isNullOrBlank()) {
                val productId = result.data?.getStringExtra(RESULT_PRODUCT_ID) ?: ""
                val productName = result.data?.getStringExtra(RESULT_PRODUCT_NAME) ?: ""
                val variant = result.data?.getStringExtra(RESULT_PRODUCT_VARIANT)
                if (productId.isNotBlank()) {
                    saveFloorSpaceProductById(selectedFloorId, productId, productName, variant)
                }
            }
            pendingFloorSpaceId = null
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCameraCapture()
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
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
        roomDetailsScroll  = findViewById(R.id.roomDetailsScroll)
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
            nameFn          = { w -> w.name ?: getString(R.string.unnamed) },
            dimsFn          = { w -> getString(R.string.window_dims_format, w.widthMm, w.heightMm) },
            productFn       = { w -> formatProductDisplay(w.selectedProductName, w.selectedProductVariant) },
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
            nameFn          = { f -> f.name ?: getString(R.string.unnamed) },
            dimsFn          = { f -> getString(R.string.floor_dims_format, f.widthMm, f.depthMm) },
            productFn       = { f -> formatProductDisplay(f.selectedProductName, f.selectedProductVariant) },
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
            val previousY = roomDetailsScroll.scrollY
            windowsExpanded = !windowsExpanded
            windowsAdapter.setExpanded(windowsExpanded)
            updateToggleButtons()
            roomDetailsScroll.post { roomDetailsScroll.scrollTo(0, previousY) }
        }

        btnToggleFloor.setOnClickListener {
            val previousY = roomDetailsScroll.scrollY
            floorSpacesExpanded = !floorSpacesExpanded
            floorAdapter.setExpanded(floorSpacesExpanded)
            updateToggleButtons()
            roomDetailsScroll.post { roomDetailsScroll.scrollTo(0, previousY) }
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
            if (name.isBlank()) { lblRoomTitle.text = getString(R.string.room_name_required); return@setOnClickListener }
            Firebase.firestore.collection("rooms").document(roomId)
                .update("name", name)
                .addOnSuccessListener { Log.d(FIREBASE_TAG, "Room updated"); finish() }
                .addOnFailureListener { Log.e(FIREBASE_TAG, "Error updating room", it); lblRoomTitle.text = getString(R.string.room_save_failed) }
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
            Toast.makeText(this, getString(R.string.camera_prepare_failed), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.photo_processing_failed), Toast.LENGTH_SHORT).show()
            return
        }

        Firebase.firestore.collection("rooms").document(roomId)
            .update(mapOf("photoBase64" to base64, "photoUrl" to ""))
            .addOnSuccessListener {
                Toast.makeText(this, getString(R.string.room_photo_saved), Toast.LENGTH_SHORT).show()
                showRoomPhoto(base64, null)
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error saving photo in Firestore", it)
                val reason = it.message ?: getString(R.string.unknown_error)
                Toast.makeText(this, getString(R.string.photo_save_failed_with_reason, reason), Toast.LENGTH_LONG).show()
            }
    }

    private fun saveWindowPhoto(pos: Int, uri: Uri) {
        if (pos !in filteredWindowList.indices) return
        val base64 = encodeImageToBase64(uri) ?: run {
            Toast.makeText(this, getString(R.string.photo_processing_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val window = filteredWindowList[pos]
        val windowId = window.id ?: return

        Firebase.firestore.collection("windows").document(windowId)
            .update("photoBase64", base64)
            .addOnSuccessListener {
                window.photoBase64 = base64
                applyWindowFilter()
                Toast.makeText(this, getString(R.string.window_photo_saved), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error saving window photo", it)
                Toast.makeText(this, getString(R.string.window_photo_save_failed), Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveFloorSpacePhoto(pos: Int, uri: Uri) {
        if (pos !in filteredFloorSpaceList.indices) return
        val base64 = encodeImageToBase64(uri) ?: run {
            Toast.makeText(this, getString(R.string.photo_processing_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val floorSpace = filteredFloorSpaceList[pos]
        val floorSpaceId = floorSpace.id ?: return

        Firebase.firestore.collection("floorspaces").document(floorSpaceId)
            .update("photoBase64", base64)
            .addOnSuccessListener {
                floorSpace.photoBase64 = base64
                applyFloorFilter()
                Toast.makeText(this, getString(R.string.floor_photo_saved), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error saving floor photo", it)
                Toast.makeText(this, getString(R.string.floor_photo_save_failed), Toast.LENGTH_SHORT).show()
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
            PhotoTarget.ROOM -> getString(R.string.remove_room_photo_confirm)
            PhotoTarget.WINDOW -> getString(R.string.remove_window_photo_confirm)
            PhotoTarget.FLOOR_SPACE -> getString(R.string.remove_floor_photo_confirm)
        }

        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.remove) { _, _ -> removeMeasurementPhoto(target, pos) }
            .setNegativeButton(R.string.cancel, null)
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
        val scaledBitmap = scaleBitmap(bitmap)
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

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= MAX_IMAGE_DIMENSION) return bitmap

        val scaleFactor = MAX_IMAGE_DIMENSION.toFloat() / largestSide.toFloat()
        val width = (bitmap.width * scaleFactor).roundToInt()
        val height = (bitmap.height * scaleFactor).roundToInt()
        return bitmap.scale(width, height)
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
                lblWindowCount.text = getString(R.string.window_count_format, windowList.size)
                // Keep adapter/toggle state in sync with the currently filtered list.
                applyWindowFilter()
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error loading windows", it) }
    }

    private fun addWindow(roomId: String) {
        showMeasurementDialog(
            getString(R.string.add_window_title),
            getString(R.string.new_window_name),
            getString(R.string.width_mm_label),
            0,
            getString(R.string.height_mm_label),
            0
        ) { name, d1, d2 ->
            val w = Window(roomId = roomId, name = name, widthMm = d1, heightMm = d2)
            Firebase.firestore.collection("windows").add(w)
                .addOnSuccessListener {
                    w.id = it.id
                    windowList.add(0, w)
                    lblWindowCount.text = getString(R.string.window_count_format, windowList.size)
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
        AlertDialog.Builder(this).setMessage(R.string.delete_window_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                Firebase.firestore.collection("windows").document(wId).delete()
                    .addOnSuccessListener {
                        windowList.removeAll { it.id == wId }
                        if (windowList.size <= 2) windowsExpanded = false
                        windowsAdapter.setExpanded(windowsExpanded)
                        lblWindowCount.text = getString(R.string.window_count_format, windowList.size)
                        applyWindowFilter()
                    }
                    .addOnFailureListener { Log.e(FIREBASE_TAG, "Error deleting window", it) }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun openWindowEdit(pos: Int) {
        if (pos < 0 || pos >= filteredWindowList.size) return
        val w = filteredWindowList[pos]; val wId = w.id ?: return
        showMeasurementDialog(
            getString(R.string.edit_window_title),
            w.name ?: "",
            getString(R.string.width_mm_label),
            w.widthMm,
            getString(R.string.height_mm_label),
            w.heightMm
        ) { name, d1, d2 ->
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

    private fun saveWindowProductById(windowId: String, productId: String, productName: String, panelCount: Int, variant: String?) {
        val w = windowList.firstOrNull { it.id == windowId } ?: return
        val wId = w.id ?: return
        Firebase.firestore.collection("windows").document(wId)
            .update(
                mapOf(
                    "selectedProductId" to productId,
                    "selectedProductName" to productName,
                    "selectedProductVariant" to variant.orEmpty(),
                    "panelCount" to panelCount
                )
            )
            .addOnSuccessListener {
                w.selectedProductId = productId
                w.selectedProductName = productName
                w.selectedProductVariant = variant
                w.panelCount = panelCount
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
                lblFloorSpaceCount.text = getString(R.string.floor_space_count_format, floorSpaceList.size)
                applyFloorFilter()
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error loading floor spaces", it) }
    }

    private fun addFloorSpace(roomId: String) {
        showMeasurementDialog(
            getString(R.string.add_floor_space_title),
            getString(R.string.new_floor_space_name),
            getString(R.string.width_mm_label),
            0,
            getString(R.string.depth_mm_label),
            0
        ) { name, d1, d2 ->
            val f = FloorSpace(roomId = roomId, name = name, widthMm = d1, depthMm = d2)
            Firebase.firestore.collection("floorspaces").add(f)
                .addOnSuccessListener {
                    f.id = it.id
                    floorSpaceList.add(0, f)
                    lblFloorSpaceCount.text = getString(R.string.floor_space_count_format, floorSpaceList.size)
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
        AlertDialog.Builder(this).setMessage(R.string.delete_floor_space_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                Firebase.firestore.collection("floorspaces").document(fId).delete()
                    .addOnSuccessListener {
                        floorSpaceList.removeAll { it.id == fId }
                        if (floorSpaceList.size <= 2) floorSpacesExpanded = false
                        floorAdapter.setExpanded(floorSpacesExpanded)
                        lblFloorSpaceCount.text = getString(R.string.floor_space_count_format, floorSpaceList.size)
                        applyFloorFilter()
                    }
                    .addOnFailureListener { Log.e(FIREBASE_TAG, "Error deleting floor space", it) }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun openFloorSpaceEdit(pos: Int) {
        if (pos < 0 || pos >= filteredFloorSpaceList.size) return
        val f = filteredFloorSpaceList[pos]; val fId = f.id ?: return
        showMeasurementDialog(
            getString(R.string.edit_floor_space_title),
            f.name ?: "",
            getString(R.string.width_mm_label),
            f.widthMm,
            getString(R.string.depth_mm_label),
            f.depthMm
        ) { name, d1, d2 ->
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

    private fun saveFloorSpaceProductById(floorId: String, productId: String, productName: String, variant: String?) {
        val f = floorSpaceList.firstOrNull { it.id == floorId } ?: return
        val fId = f.id ?: return
        Firebase.firestore.collection("floorspaces").document(fId)
            .update(
                mapOf(
                    "selectedProductId" to productId,
                    "selectedProductName" to productName,
                    "selectedProductVariant" to variant.orEmpty()
                )
            )
            .addOnSuccessListener {
                f.selectedProductId = productId
                f.selectedProductName = productName
                f.selectedProductVariant = variant
                applyFloorFilter()
                Log.d(FIREBASE_TAG, "FloorSpace product saved: $productName")
            }
            .addOnFailureListener { Log.e(FIREBASE_TAG, "Error saving floor space product", it) }
    }

    private fun formatProductDisplay(productName: String?, variant: String?): String? {
        val name = productName?.takeIf { it.isNotBlank() } ?: return null
        val selectedVariant = variant?.takeIf { it.isNotBlank() } ?: return name
        return getString(R.string.product_with_variant_format, name, selectedVariant)
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
            if (numeric) {
                inputType = InputType.TYPE_CLASS_NUMBER
                // Block letters/symbols at input time.
                keyListener = DigitsKeyListener.getInstance("0123456789")
                filters = arrayOf(InputFilter.LengthFilter(5))
            }
        }
        val etName = et(getString(R.string.measurement_name_hint), name, false)
        val etD1   = et(dim1Label, if (dim1Value > 0) dim1Value.toString() else "", true)
        val etD2   = et(dim2Label, if (dim2Value > 0) dim2Value.toString() else "", true)
        layout.addView(etName); layout.addView(etD1); layout.addView(etD2)

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val dim1 = validateDimensionInput(etD1, dim1Label)
                val dim2 = validateDimensionInput(etD2, dim2Label)
                if (dim1 == null || dim2 == null) return@setOnClickListener

                onSave(
                    etName.text.toString().trim().ifBlank { getString(R.string.unnamed) },
                    dim1,
                    dim2
                )
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun validateDimensionInput(input: EditText, label: String): Int? {
        val raw = input.text?.toString()?.trim().orEmpty()
        val parsed = raw.toIntOrNull()

        return when {
            raw.isBlank() -> {
                input.error = getString(R.string.error_dimension_required, label)
                null
            }
            parsed == null -> {
                input.error = getString(R.string.error_dimension_whole_number, label)
                null
            }
            parsed < MIN_DIMENSION_MM || parsed > MAX_DIMENSION_MM -> {
                input.error = getString(R.string.error_dimension_range, label, MIN_DIMENSION_MM, MAX_DIMENSION_MM)
                null
            }
            else -> {
                input.error = null
                parsed
            }
        }
    }

    private fun updateToggleButtons() {
        // Keep toggle available based on total list size (not search result size)
        // so users can always expand/collapse the main list clearly.
        btnToggleWindows.visibility = if (windowList.size > 2) View.VISIBLE else View.GONE
        btnToggleWindows.text = if (windowsExpanded) getString(R.string.quote_show_less_windows) else getString(R.string.quote_show_more_windows)

        btnToggleFloor.visibility = if (floorSpaceList.size > 2) View.VISIBLE else View.GONE
        btnToggleFloor.text = if (floorSpacesExpanded) getString(R.string.quote_show_less_floors) else getString(R.string.quote_show_more_floors)
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

    class MeasurementHolder(val root: View) : RecyclerView.ViewHolder(root) {
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
            holder.txtProduct.text = if (!prod.isNullOrBlank()) {
                holder.root.context.getString(R.string.product_selected_format, prod)
            } else {
                holder.root.context.getString(R.string.no_product_selected)
            }
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
            // Item count changes when expanding/collapsing, so force a full refresh
            // to avoid RecyclerView position inconsistencies/crashes.
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

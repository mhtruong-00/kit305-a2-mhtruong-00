package au.edu.utas.kit305.tutorial05

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import au.edu.utas.kit305.tutorial05.databinding.ActivityMainBinding
import au.edu.utas.kit305.tutorial05.databinding.HouseListItemBinding

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase

const val FIREBASE_TAG = "FirebaseLogging"
const val HOUSE_INDEX = "House_Index"
const val MOVIE_INDEX = "Movie_Index"
const val HOUSE_NAME_EXTRA = "house_name"
val items = mutableListOf<Movie>()
val houses = mutableListOf<House>()

class MainActivity : AppCompatActivity() {
    private lateinit var ui: ActivityMainBinding
    private var housesExpanded = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        ui.lblMovieCount.text = getString(R.string.house_count_format, houses.size)
        val houseAdapter = HouseAdapter(houseList = houses, onToggleExpand = { housesExpanded = !housesExpanded; houseAdapter.setExpanded(housesExpanded) })
        ui.myList.adapter = houseAdapter

        ui.myList.layoutManager = LinearLayoutManager(this)
        ui.btnAddHouse.setOnClickListener { addHouse() }

        val db = Firebase.firestore
        Log.d("FIREBASE", "Firebase connected: ${db.app.name}")

        val housesCollection = db.collection("houses")
        ui.lblMovieCount.text = "Loading..."
        housesCollection
            .get()
            .addOnSuccessListener { result ->
                houses.clear()
                for (document in result) {
                    val house = document.toObject<House>()
                    house.id = document.id
                    houses.add(house)
                }
                ui.myList.adapter?.notifyDataSetChanged()
                ui.lblMovieCount.text = getString(R.string.house_count_format, houses.size)
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error reading houses", it)
                ui.lblMovieCount.text = getString(R.string.house_count_format, 0)
            }
    }

    override fun onResume() {
        super.onResume()
        if (houses.isNotEmpty()) {
            ui.myList.adapter?.notifyItemRangeChanged(0, houses.size)
        }
    }

    private fun addHouse() {
        val newHouse = House(customerName = "New Customer", address = "New Address")

        Firebase.firestore.collection("houses")
            .add(newHouse)
            .addOnSuccessListener {
                newHouse.id = it.id
                houses.add(0, newHouse)
                ui.myList.adapter?.notifyItemInserted(0)
                ui.lblMovieCount.text = getString(R.string.house_count_format, houses.size)
                ui.myList.scrollToPosition(0)
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error creating house", it)
            }
    }

    private fun promptDeleteHouse(position: Int) {
        if (position < 0 || position >= houses.size) return

        AlertDialog.Builder(this)
            .setMessage(R.string.delete_house_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> deleteHouse(position) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditHouseDialog(position: Int) {
        if (position < 0 || position >= houses.size) return
        val house = houses[position]

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, 0)
        }

        val nameEdit = EditText(this).apply {
            hint = "Customer name"
            setText(house.customerName ?: "")
        }

        val addressEdit = EditText(this).apply {
            hint = "Address"
            setText(house.address ?: "")
        }

        layout.addView(nameEdit)
        layout.addView(addressEdit)

        AlertDialog.Builder(this)
            .setTitle("Edit House")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newName = nameEdit.text.toString().trim()
                val newAddress = addressEdit.text.toString().trim()
                if (newName.isBlank() || newAddress.isBlank()) return@setPositiveButton
                updateHouse(position, newName, newAddress)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateHouse(position: Int, customerName: String, address: String) {
        if (position < 0 || position >= houses.size) return
        val house = houses[position]
        val houseId = house.id ?: return

        val updates = mapOf(
            "customerName" to customerName,
            "address" to address
        )

        Firebase.firestore.collection("houses").document(houseId)
            .update(updates)
            .addOnSuccessListener {
                house.customerName = customerName
                house.address = address
                ui.myList.adapter?.notifyItemChanged(position)
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error updating house", it)
            }
    }

    private fun deleteHouse(position: Int) {
        val house = houses[position]
        val houseId = house.id ?: return

        val db = Firebase.firestore
        db.collection("rooms")
            .whereEqualTo("houseId", houseId)
            .get()
            .addOnSuccessListener { result ->
                val batch = db.batch()
                for (document in result.documents) {
                    batch.delete(document.reference)
                }
                batch.delete(db.collection("houses").document(houseId))

                batch.commit()
                    .addOnSuccessListener {
                        houses.removeAt(position)
                        ui.myList.adapter?.notifyItemRemoved(position)
                        ui.lblMovieCount.text = getString(R.string.house_count_format, houses.size)
                    }
                    .addOnFailureListener {
                        Log.e(FIREBASE_TAG, "Error deleting house and rooms", it)
                    }
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error finding rooms for house delete", it)
            }
    }

    private fun openQuoteFromHouse(position: Int) {
        if (position < 0 || position >= houses.size) return
        val house = houses[position]
        val houseId = house.id ?: return

        val i = Intent(this, QuoteActivity::class.java)
        i.putExtra(HOUSE_ID_EXTRA, houseId)
        i.putExtra(HOUSE_NAME_EXTRA, house.customerName ?: getString(R.string.quote_title_default))
        startActivity(i)
    }

    private fun openRoomsFromHouse(position: Int) {
        if (position < 0 || position >= houses.size) return
        val house = houses[position]
        val i = Intent(this, HouseDetails::class.java)
        i.putExtra(HOUSE_INDEX, position)
        i.putExtra(HOUSE_ID_EXTRA, house.id)
        i.putExtra(HOUSE_NAME_EXTRA, house.customerName ?: "House")
        startActivity(i)
    }

    private fun showHouseTapOptions(position: Int) {
        if (position < 0 || position >= houses.size) return

        AlertDialog.Builder(this)
            .setTitle(R.string.house_actions_title)
            .setItems(arrayOf(getString(R.string.edit_house_action), getString(R.string.open_rooms_action))) { _, which ->
                when (which) {
                    0 -> showEditHouseDialog(position)
                    1 -> {
                        val i = Intent(this, HouseDetails::class.java)
                        i.putExtra(HOUSE_INDEX, position)
                        i.putExtra(HOUSE_ID_EXTRA, houses[position].id)
                        i.putExtra(HOUSE_NAME_EXTRA, houses[position].customerName ?: "House")
                        startActivity(i)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    inner class HouseHolder(var ui: HouseListItemBinding) : RecyclerView.ViewHolder(ui.root)

    inner class HouseAdapter(
        private val houseList: MutableList<House>,
        private var isExpanded: Boolean = false,
        private val onToggleExpand: (() -> Unit)? = null
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val ITEM_TYPE_HOUSE = 0
        private val ITEM_TYPE_MORE = 1
        private val ITEMS_PER_PAGE = 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == ITEM_TYPE_HOUSE) {
                val ui = HouseListItemBinding.inflate(layoutInflater, parent, false)
                HouseHolder(ui)
            } else {
                val button = android.widget.Button(parent.context)
                button.text = "Show More Houses"
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
            val visibleCount = if (isExpanded) houseList.size else minOf(ITEMS_PER_PAGE, houseList.size)
            val hasMore = !isExpanded && houseList.size > ITEMS_PER_PAGE
            return visibleCount + (if (hasMore) 1 else 0)
        }

        override fun getItemViewType(position: Int): Int {
            val visibleCount = if (isExpanded) houseList.size else minOf(ITEMS_PER_PAGE, houseList.size)
            return if (position < visibleCount) ITEM_TYPE_HOUSE else ITEM_TYPE_MORE
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is HouseHolder && position < houseList.size) {
                val house = houseList[position]
                holder.ui.txtName.text = house.customerName ?: "Unnamed customer"
                holder.ui.txtAddress.text = house.address ?: "No address"

                holder.ui.root.setOnClickListener {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition == RecyclerView.NO_POSITION || currentPosition >= houseList.size) return@setOnClickListener
                    openRoomsFromHouse(currentPosition)
                }

                holder.ui.btnEditHouse.setOnClickListener {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition == RecyclerView.NO_POSITION || currentPosition >= houseList.size) return@setOnClickListener
                    showEditHouseDialog(currentPosition)
                }

                holder.ui.btnCreateRoomHouse.setOnClickListener {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition == RecyclerView.NO_POSITION || currentPosition >= houseList.size) return@setOnClickListener
                    openRoomsFromHouse(currentPosition)
                }

                holder.ui.btnDeleteHouse.setOnClickListener {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition == RecyclerView.NO_POSITION || currentPosition >= houseList.size) return@setOnClickListener
                    promptDeleteHouse(currentPosition)
                }

                holder.ui.btnViewQuoteHouse.setOnClickListener {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition == RecyclerView.NO_POSITION || currentPosition >= houseList.size) return@setOnClickListener
                    openQuoteFromHouse(currentPosition)
                }
            }
        }

        fun setExpanded(expanded: Boolean) {
            isExpanded = expanded
            notifyDataSetChanged()
        }
    }

    inner class MoreHolder(itemView: android.widget.Button) : RecyclerView.ViewHolder(itemView)
}

// With support from copilot
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
import androidx.core.widget.doAfterTextChanged
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
    private val filteredHouses = mutableListOf<House>()
    private var houseSearchQuery: String = ""

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        ui.lblMovieCount.text = getString(R.string.house_count_format, houses.size)
        val houseAdapter = HouseAdapter(houseList = filteredHouses)
        houseAdapter.setToggleCallback {
            housesExpanded = !housesExpanded
            houseAdapter.setExpanded(housesExpanded)
        }
        ui.myList.adapter = houseAdapter

        ui.myList.layoutManager = LinearLayoutManager(this)
        ui.btnAddHouse.setOnClickListener { showAddHouseDialog() }
        ui.txtSearchHouses.doAfterTextChanged {
            houseSearchQuery = it?.toString()?.trim().orEmpty()
            applyHouseFilter()
        }

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
                applyHouseFilter()
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error reading houses", it)
                ui.lblMovieCount.text = getString(R.string.house_count_format, 0)
            }
    }

    override fun onResume() {
        super.onResume()
        applyHouseFilter()
    }

    private fun showAddHouseDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, 0)
        }

        val nameEdit = EditText(this).apply {
            hint = getString(R.string.house_dialog_customer_name_hint)
        }

        val addressEdit = EditText(this).apply {
            hint = getString(R.string.house_dialog_address_hint)
        }

        layout.addView(nameEdit)
        layout.addView(addressEdit)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.house_dialog_add_title)
            .setView(layout)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = nameEdit.text.toString().trim()
                val newAddress = addressEdit.text.toString().trim()

                val valid = validateHouseInput(nameEdit, addressEdit, newName, newAddress)
                if (!valid) return@setOnClickListener

                addHouse(newName, newAddress)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun addHouse(customerName: String, address: String) {
        val newHouse = House(customerName = customerName, address = address)

        Firebase.firestore.collection("houses")
            .add(newHouse)
            .addOnSuccessListener {
                newHouse.id = it.id
                houses.add(0, newHouse)
                applyHouseFilter()
                ui.myList.scrollToPosition(0)
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error creating house", it)
            }
    }

    private fun promptDeleteHouse(house: House) {

        AlertDialog.Builder(this)
            .setMessage(R.string.delete_house_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> deleteHouse(house) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditHouseDialog(house: House) {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, 0)
        }

        val nameEdit = EditText(this).apply {
            hint = getString(R.string.house_dialog_customer_name_hint)
            setText(house.customerName ?: "")
        }

        val addressEdit = EditText(this).apply {
            hint = getString(R.string.house_dialog_address_hint)
            setText(house.address ?: "")
        }

        layout.addView(nameEdit)
        layout.addView(addressEdit)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.house_dialog_edit_title)
            .setView(layout)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = nameEdit.text.toString().trim()
                val newAddress = addressEdit.text.toString().trim()

                val valid = validateHouseInput(nameEdit, addressEdit, newName, newAddress)
                if (!valid) return@setOnClickListener

                updateHouse(house, newName, newAddress)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun containsLetter(value: String): Boolean = value.any { it.isLetter() }

    private fun validateHouseInput(
        nameEdit: EditText,
        addressEdit: EditText,
        newName: String,
        newAddress: String
    ): Boolean {
        var valid = true

        if (newName.isBlank()) {
            nameEdit.error = getString(R.string.error_customer_name_required)
            valid = false
        } else {
            nameEdit.error = null
        }

        when {
            newAddress.isBlank() -> {
                addressEdit.error = getString(R.string.error_address_required)
                valid = false
            }
            !containsLetter(newAddress) -> {
                // Prevent numeric-only addresses like "12345".
                addressEdit.error = getString(R.string.error_address_requires_letter)
                valid = false
            }
            else -> addressEdit.error = null
        }

        return valid
    }

    private fun updateHouse(house: House, customerName: String, address: String) {
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
                applyHouseFilter()
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error updating house", it)
            }
    }

    private fun deleteHouse(house: House) {
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
                        houses.removeAll { it.id == houseId }
                        applyHouseFilter()
                    }
                    .addOnFailureListener {
                        Log.e(FIREBASE_TAG, "Error deleting house and rooms", it)
                    }
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error finding rooms for house delete", it)
            }
    }

    private fun openQuoteFromHouse(house: House) {
        val houseId = house.id ?: return

        val i = Intent(this, QuoteActivity::class.java)
        i.putExtra(HOUSE_ID_EXTRA, houseId)
        i.putExtra(HOUSE_NAME_EXTRA, house.customerName ?: getString(R.string.quote_title_default))
        startActivity(i)
    }

    private fun openRoomsFromHouse(house: House) {
        val i = Intent(this, HouseDetails::class.java)
        i.putExtra(HOUSE_INDEX, -1)
        i.putExtra(HOUSE_ID_EXTRA, house.id)
        i.putExtra(HOUSE_NAME_EXTRA, house.customerName ?: "House")
        startActivity(i)
    }

    private fun applyHouseFilter() {
        val q = houseSearchQuery.lowercase()
        val oldSize = filteredHouses.size
        filteredHouses.clear()
        ui.myList.adapter?.notifyItemRangeRemoved(0, oldSize)
        if (q.isBlank()) {
            filteredHouses.addAll(houses)
        } else {
            filteredHouses.addAll(
                houses.filter { house ->
                    val name = house.customerName.orEmpty().lowercase()
                    val address = house.address.orEmpty().lowercase()
                    name.contains(q) || address.contains(q)
                }
            )
        }
        ui.myList.adapter?.notifyItemRangeInserted(0, filteredHouses.size)
        ui.lblMovieCount.text = getString(R.string.house_count_format, filteredHouses.size)
    }

    private fun showHouseTapOptions(position: Int) {
        if (position < 0 || position >= houses.size) return

        AlertDialog.Builder(this)
            .setTitle(R.string.house_actions_title)
            .setItems(arrayOf(getString(R.string.edit_house_action), getString(R.string.open_rooms_action))) { _, which ->
                when (which) {
                    0 -> showEditHouseDialog(houses[position])
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
    inner class MoreHolder(itemView: android.widget.Button) : RecyclerView.ViewHolder(itemView)

    inner class HouseAdapter(private val houseList: MutableList<House>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val itemTypeHouse = 0
        private val itemTypeToggle = 1
        private val itemsPerPage = 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == itemTypeHouse) {
                val ui = HouseListItemBinding.inflate(layoutInflater, parent, false)
                HouseHolder(ui)
            } else {
                val button = android.widget.Button(parent.context)
                button.setOnClickListener { onToggleExpand?.invoke() }
                button.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                MoreHolder(button)
            }
        }

        override fun getItemCount(): Int {
            val visibleCount = if (isExpanded) houseList.size else minOf(itemsPerPage, houseList.size)
            val hasToggle = houseList.size > itemsPerPage
            return visibleCount + (if (hasToggle) 1 else 0)
        }

        override fun getItemViewType(position: Int): Int {
            val visibleCount = if (isExpanded) houseList.size else minOf(itemsPerPage, houseList.size)
            return if (position < visibleCount) itemTypeHouse else itemTypeToggle
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is HouseHolder && position < houseList.size) {
                val house = houseList[position]
                holder.ui.txtName.text = house.customerName ?: getString(R.string.unnamed_customer)
                holder.ui.txtAddress.text = house.address ?: getString(R.string.no_address)

                holder.ui.root.setOnClickListener {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                    openRoomsFromHouse(house)
                }

                holder.ui.btnEditHouse.setOnClickListener {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                    showEditHouseDialog(house)
                }

                holder.ui.btnCreateRoomHouse.setOnClickListener {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                    openRoomsFromHouse(house)
                }

                holder.ui.btnDeleteHouse.setOnClickListener {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                    promptDeleteHouse(house)
                }

                holder.ui.btnViewQuoteHouse.setOnClickListener {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                    openQuoteFromHouse(house)
                }
            } else if (holder is MoreHolder) {
                val button = holder.itemView as android.widget.Button
                button.text = if (isExpanded) getString(R.string.show_less_houses) else getString(R.string.show_more_houses)
            }
        }

        fun setExpanded(expanded: Boolean) {
            isExpanded = expanded
            notifyDataSetChanged()
        }

        fun setToggleCallback(callback: (() -> Unit)?) {
            onToggleExpand = callback
        }

        private var isExpanded: Boolean = false
        private var onToggleExpand: (() -> Unit)? = null
    }
}

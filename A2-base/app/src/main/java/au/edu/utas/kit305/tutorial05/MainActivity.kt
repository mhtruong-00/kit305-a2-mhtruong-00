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

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        ui.lblMovieCount.text = getString(R.string.house_count_format, houses.size)
        ui.myList.adapter = HouseAdapter(houseList = houses)

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

    inner class HouseAdapter(private val houseList: MutableList<House>) : RecyclerView.Adapter<HouseHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HouseHolder {
            val ui = HouseListItemBinding.inflate(layoutInflater, parent, false)
            return HouseHolder(ui)
        }

        override fun getItemCount(): Int = houseList.size

        override fun onBindViewHolder(holder: HouseHolder, position: Int) {
            val house = houseList[position]
            holder.ui.txtName.text = house.customerName ?: "Unnamed customer"
            holder.ui.txtAddress.text = house.address ?: "No address"

            holder.ui.root.setOnClickListener {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                showHouseTapOptions(currentPosition)
            }

            holder.ui.btnDeleteHouse.setOnClickListener {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                promptDeleteHouse(currentPosition)
            }

            holder.ui.btnViewQuoteHouse.setOnClickListener {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                openQuoteFromHouse(currentPosition)
            }
        }
    }
}

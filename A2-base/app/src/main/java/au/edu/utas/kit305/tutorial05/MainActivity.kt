package au.edu.utas.kit305.tutorial05

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import au.edu.utas.kit305.tutorial05.databinding.ActivityMainBinding
import au.edu.utas.kit305.tutorial05.databinding.MyListItemBinding

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
const val FIREBASE_TAG = "FirebaseLogging"
const val HOUSE_INDEX = "House_Index"
const val MOVIE_INDEX = "Movie_Index"
val items = mutableListOf<Movie>()
val houses = mutableListOf<House>()


class MainActivity : AppCompatActivity()
{
    private lateinit var ui : ActivityMainBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        ui.lblMovieCount.text = getString(R.string.house_count_format, houses.size)
        ui.myList.adapter = HouseAdapter(houseList = houses)

//vertical list
        ui.myList.layoutManager = LinearLayoutManager(this)
        ui.btnAddHouse.setOnClickListener { addHouse() }

//get db connection
        val db = Firebase.firestore
        Log.d("FIREBASE", "Firebase connected: ${db.app.name}")

//get all houses
        val housesCollection = db.collection("houses")
        ui.lblMovieCount.text = "Loading..."
        housesCollection
            .get()
            .addOnSuccessListener { result ->
                houses.clear() //clear before reload to avoid duplicates after config changes
                for (document in result)
                {
                    val house = document.toObject<House>()
                    house.id = document.id
                    houses.add(house)
                }
                // this is fine for now while the list is simple; we will optimize with specific events later
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
        val newHouse = House(
            customerName = "New Customer",
            address = "New Address"
        )

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

    inner class HouseHolder(var ui: MyListItemBinding) : RecyclerView.ViewHolder(ui.root) {}

    inner class HouseAdapter(private val houseList: MutableList<House>) : RecyclerView.Adapter<HouseHolder>()
    {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HouseHolder {
            val ui = MyListItemBinding.inflate(layoutInflater, parent, false)
            return HouseHolder(ui)
        }

        override fun getItemCount(): Int {
            return houseList.size
        }

        override fun onBindViewHolder(holder: HouseHolder, position: Int) {
            val house = houseList[position]
            holder.ui.txtName.text = house.customerName ?: "Unnamed customer"
            holder.ui.txtYear.text = house.address ?: "No address"

            holder.ui.root.setOnClickListener {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                val i = Intent(holder.ui.root.context, HouseDetails::class.java)
                i.putExtra(HOUSE_INDEX, currentPosition)
                this@MainActivity.startActivity(i)
            }

            holder.ui.root.setOnLongClickListener {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnLongClickListener true
                promptDeleteHouse(currentPosition)
                true
            }
        }
    }
}

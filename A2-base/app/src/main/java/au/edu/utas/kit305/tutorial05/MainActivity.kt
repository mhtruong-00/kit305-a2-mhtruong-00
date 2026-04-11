package au.edu.utas.kit305.tutorial05

import android.annotation.SuppressLint
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

        ui.lblMovieCount.text = "${houses.size} Houses"
        ui.myList.adapter = HouseAdapter(houseList = houses)

//vertical list
        ui.myList.layoutManager = LinearLayoutManager(this)

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
                Log.d(FIREBASE_TAG, "--- all houses ---")
                for (document in result)
                {
                    Log.d(FIREBASE_TAG, document.toString())
                    val house = document.toObject<House>()
                    house.id = document.id
                    Log.d(FIREBASE_TAG, house.toString())

                    houses.add(house)
                }
                // this is fine for now while the list is simple; we will optimize with specific events later
                ui.myList.adapter?.notifyDataSetChanged()
                ui.lblMovieCount.text = "${houses.size} Houses"
            }
            .addOnFailureListener {
                Log.e(FIREBASE_TAG, "Error reading houses", it)
                ui.lblMovieCount.text = "0 Houses"
            }
    }

    override fun onResume() {
        super.onResume()
        ui.myList.adapter?.notifyDataSetChanged()
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
        }
    }
}

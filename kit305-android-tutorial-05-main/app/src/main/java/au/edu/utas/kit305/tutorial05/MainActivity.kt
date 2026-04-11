package au.edu.utas.kit305.tutorial05

import android.annotation.SuppressLint
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
const val MOVIE_INDEX = "Movie_Index"
val items = mutableListOf<Movie>()


class MainActivity : AppCompatActivity()
{
    private lateinit var ui : ActivityMainBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        ui.lblMovieCount.text = "${items.size} Movies"
        ui.myList.adapter = MovieAdapter(movies = items)

        //vertical list
        ui.myList.layoutManager = LinearLayoutManager(this)

//get db connection
        val db = Firebase.firestore
        Log.d("FIREBASE", "Firebase connected: ${db.app.name}")

//add some data (comment this out after running the program once and confirming your data is there)
//        val lotr = Movie(
//            title = "Lord of the Rings: Fellowship of the Ring",
//            year = 2001,
//            duration = 9001F
//        )
//        val moviesCollection = db.collection("movies")
//        moviesCollection
//            .add(lotr)
//            .addOnSuccessListener {
//                Log.d(FIREBASE_TAG, "Document created with id ${it.id}")
//                lotr.id = it.id
//            }
//            .addOnFailureListener {
//                Log.e(FIREBASE_TAG, "Error writing document", it)
//            }

//get all movies
        val moviesCollection = db.collection("movies")
        ui.lblMovieCount.text = "Loading..."
        moviesCollection
            .get()
            .addOnSuccessListener { result ->
                items.clear() //this line clears the list, and prevents a bug where items would be duplicated upon rotation of screen
                Log.d(FIREBASE_TAG, "--- all movies ---")
                for (document in result)
                {
                    Log.d(FIREBASE_TAG, document.toString())
                    val movie = document.toObject<Movie>()
                    movie.id = document.id
                    Log.d(FIREBASE_TAG, movie.toString())

                    items.add(movie)
                }
//                (ui.myList.adapter as MovieAdapter).notifyDataSetChanged()
                //you may choose to fix the warning that notifyDataSetChanged() is not specific enough using:
                (ui.myList.adapter as MovieAdapter).notifyItemRangeInserted(0, items.size)
                ui.lblMovieCount.text = "${items.size} Movies"
            }
    }

    override fun onResume() {
        super.onResume()
        ui.myList.adapter?.notifyDataSetChanged()
    }
    inner class MovieHolder(var ui: MyListItemBinding) : RecyclerView.ViewHolder(ui.root) {}

    inner class MovieAdapter(private val movies: MutableList<Movie>) : RecyclerView.Adapter<MovieHolder>()
    {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainActivity.MovieHolder {
            val ui = MyListItemBinding.inflate(layoutInflater, parent, false)   //inflate a new row from the my_list_item.xml
            return MovieHolder(ui)                                                            //wrap it in a ViewHolder
        }

        override fun getItemCount(): Int {
            return movies.size
        }

        override fun onBindViewHolder(holder: MainActivity.MovieHolder, position: Int) {
            val movie = movies[position]
            holder.ui.txtName.text = movie.title
            holder.ui.txtYear.text = movie.year.toString()//get the data at the requested position


            holder.ui.root.setOnClickListener {
                val i = Intent(holder.ui.root.context, MovieDetails::class.java)
                i.putExtra(MOVIE_INDEX, position)
                startActivity(i)
            }
        }
    }
}


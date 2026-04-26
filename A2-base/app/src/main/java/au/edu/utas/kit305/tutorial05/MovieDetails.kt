// With support from copilot
package au.edu.utas.kit305.tutorial05

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import au.edu.utas.kit305.tutorial05.databinding.ActivityMovieDetailsBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MovieDetails : AppCompatActivity() {
    private lateinit var ui : ActivityMovieDetailsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMovieDetailsBinding.inflate(layoutInflater)
        setContentView(ui.root)

        // Keep this legacy screen safe even if opened with invalid extras/data.
        val movieID = intent.getIntExtra(MOVIE_INDEX, -1)
        if (movieID !in items.indices) {
            Log.e(FIREBASE_TAG, "Invalid movie index: $movieID")
            finish()
            return
        }

        val movieObject = items[movieID]
        // populate the EditTexts
        ui.txtTitle.setText(movieObject.title)
        ui.txtYear.setText(movieObject.year.toString())
        ui.txtDuration.setText(movieObject.duration.toString())
        val db = Firebase.firestore
        val moviesCollection = db.collection("movies")

        ui.btnSave.setOnClickListener {
            //get the user input
            movieObject.title = ui.txtTitle.text.toString()
            val parsedYear = ui.txtYear.text.toString().trim().toIntOrNull()
            val parsedDuration = ui.txtDuration.text.toString().trim().toFloatOrNull()

            if (parsedYear == null) {
                ui.txtYear.error = "Enter a valid whole number"
                return@setOnClickListener
            }
            ui.txtYear.error = null

            if (parsedDuration == null) {
                ui.txtDuration.error = "Enter a valid number"
                return@setOnClickListener
            }
            ui.txtDuration.error = null

            movieObject.year = parsedYear
            movieObject.duration = parsedDuration

            val movieId = movieObject.id
            if (movieId.isNullOrBlank()) {
                Log.e(FIREBASE_TAG, "Cannot save movie with null/blank id")
                finish()
                return@setOnClickListener
            }

            //update the database
            moviesCollection.document(movieId)
                .set(movieObject)
                .addOnSuccessListener {
                    Log.d(FIREBASE_TAG, "Successfully updated movie ${movieObject.id}")
                    //return to the list
                    finish()
                }
                .addOnFailureListener {
                    Log.e(FIREBASE_TAG, "Failed to update movie $movieId", it)
                }
        }
    }
}
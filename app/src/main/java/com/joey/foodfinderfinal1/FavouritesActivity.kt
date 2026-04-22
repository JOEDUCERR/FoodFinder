package com.joey.foodfinderfinal1

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// ─────────────────────────────────────────────────────────────────────────────
// FavouritesActivity — NEW screen
//
// Shows all restaurants the logged-in user has starred/saved.
// Reads from Firestore: users/{uid}/favourites
//
// Add to AndroidManifest.xml:
//   <activity android:name=".FavouritesActivity" />
//
// Add to activity_main.xml (or your bottom nav):
//   A button/tab that launches this Activity.
// ─────────────────────────────────────────────────────────────────────────────

class FavouritesActivity : AppCompatActivity() {

    private lateinit var adapter: RestaurantAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favourites)

        supportActionBar?.title = "My Favourites ⭐"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        progressBar = findViewById(R.id.progressBarFav)
        tvEmpty     = findViewById(R.id.tvEmptyFav)

        val rvFavourites = findViewById<RecyclerView>(R.id.rvFavourites)
        adapter = RestaurantAdapter { openDetail(it) }
        rvFavourites.layoutManager = LinearLayoutManager(this)
        rvFavourites.adapter = adapter

        loadFavourites()
    }

    private fun loadFavourites() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            tvEmpty.text = "Please log in to see your favourites."
            tvEmpty.visibility = View.VISIBLE
            return
        }

        progressBar.visibility = View.VISIBLE

        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("favourites")
            .get()
            .addOnSuccessListener { snapshot ->
                progressBar.visibility = View.GONE
                val restaurants = snapshot.documents.mapNotNull { doc ->
                    val id      = doc.getString("id")      ?: return@mapNotNull null
                    val name    = doc.getString("name")    ?: return@mapNotNull null
                    val address = doc.getString("address") ?: ""
                    val lat     = doc.getDouble("lat")     ?: 0.0
                    val lon     = doc.getDouble("lon")     ?: 0.0
                    Restaurant(id = id, name = name, address = address, lat = lat, lon = lon)
                }

                if (restaurants.isEmpty()) {
                    tvEmpty.text = "No favourites yet.\nTap ⭐ on any restaurant to save it!"
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    tvEmpty.visibility = View.GONE
                    adapter.submitList(restaurants)
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                tvEmpty.text = "Failed to load favourites."
                tvEmpty.visibility = View.VISIBLE
            }
    }

    private fun openDetail(restaurant: Restaurant) {
        startActivity(Intent(this, RestaurantDetailActivity::class.java).apply {
            putExtra("EXTRA_RESTAURANT", restaurant)
        })
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

package com.example.foodfinderfinal1

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var rvRestaurants: RecyclerView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val area = intent.getStringExtra("EXTRA_AREA") ?: "New York"
        val state = intent.getStringExtra("EXTRA_STATE") ?: "NY"
        val country = intent.getStringExtra("EXTRA_COUNTRY") ?: "USA"

        val tvLocationTitle = findViewById<TextView>(R.id.tvLocationTitle)
        tvLocationTitle.text = "Restaurants near $area, $state, $country"

        rvRestaurants = findViewById(R.id.rvRestaurants)
        rvRestaurants.layoutManager = LinearLayoutManager(this)
        
        progressBar = findViewById(R.id.progressBar)
        val fabChat = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabChat)
        val etSearch = findViewById<EditText>(R.id.etSearch)

        fabChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        // Handle Search Bar Input
        etSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchRestaurants(query, "", "")
                }
                true
            } else {
                false
            }
        }

        // Initial Load
        searchRestaurants(area, state, country)
    }

    private fun searchRestaurants(area: String, state: String, country: String) {
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val restaurants = OverpassApiClient.getRestaurants(area, state, country)
            progressBar.visibility = View.GONE
            
            if (restaurants.isEmpty()) {
                Toast.makeText(this@MainActivity, "No results for '\$area'. Try another location.", Toast.LENGTH_LONG).show()
                rvRestaurants.adapter = RestaurantAdapter(emptyList()) {}
            } else {
                rvRestaurants.adapter = RestaurantAdapter(restaurants) { restaurant ->
                    openDetail(restaurant)
                }
            }
        }
    }

    private fun openDetail(restaurant: Restaurant) {
        val intent = Intent(this, RestaurantDetailActivity::class.java).apply {
            putExtra("EXTRA_RESTAURANT", restaurant)
        }
        startActivity(intent)
    }
}

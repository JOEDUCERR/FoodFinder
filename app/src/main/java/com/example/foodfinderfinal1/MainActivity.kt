package com.example.foodfinderfinal1

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val area = intent.getStringExtra("EXTRA_AREA") ?: "New York"
        val state = intent.getStringExtra("EXTRA_STATE") ?: "NY"
        val country = intent.getStringExtra("EXTRA_COUNTRY") ?: "USA"

        val tvLocationTitle = findViewById<TextView>(R.id.tvLocationTitle)
        tvLocationTitle.text = "Restaurants near $area, $state, $country"

        val rvRestaurants = findViewById<RecyclerView>(R.id.rvRestaurants)
        rvRestaurants.layoutManager = LinearLayoutManager(this)
        
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val restaurants = OverpassApiClient.getRestaurants(area, state, country)
            progressBar.visibility = View.GONE
            
            if (restaurants.isEmpty()) {
                Toast.makeText(this@MainActivity, "No restaurants found or API error. Trying fallback data.", Toast.LENGTH_LONG).show()
                // Provide fallback list
                val dummyFoods = listOf(FoodItem("Pizza", "$12.00"), FoodItem("Burger", "$8.00"))
                val dummyList = listOf(
                    Restaurant("1", "Local Cafe ($area)", "123 Main St, $area", 0.0, 0.0, dummyFoods),
                    Restaurant("2", "Pizza Palace", "456 Oak St, $area", 0.0, 0.0, dummyFoods),
                    Restaurant("3", "Burger Joint", "789 Pine St, $area", 0.0, 0.0, dummyFoods),
                    Restaurant("4", "Sushi Bar", "101 Elm St, $area", 0.0, 0.0, dummyFoods),
                    Restaurant("5", "Taco Truck", "202 Maple St, $area", 0.0, 0.0, dummyFoods)
                )
                
                rvRestaurants.adapter = RestaurantAdapter(dummyList) { restaurant ->
                    openDetail(restaurant)
                }
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

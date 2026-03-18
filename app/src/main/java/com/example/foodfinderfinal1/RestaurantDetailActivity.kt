package com.example.foodfinderfinal1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RestaurantDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restaurant_detail)

        val restaurant = intent.getSerializableExtra("EXTRA_RESTAURANT") as? Restaurant ?: return

        val tvDetailName = findViewById<TextView>(R.id.tvDetailName)
        val tvDetailInfo = findViewById<TextView>(R.id.tvDetailInfo)
        val btnOpenMaps = findViewById<Button>(R.id.btnOpenMaps)
        val rvFoodItems = findViewById<RecyclerView>(R.id.rvFoodItems)

        tvDetailName.text = restaurant.name
        tvDetailInfo.text = "Address: ${restaurant.address}"

        rvFoodItems.layoutManager = LinearLayoutManager(this)
        rvFoodItems.adapter = FoodAdapter(restaurant.foodItems)

        btnOpenMaps.setOnClickListener {
            val uri = "geo:${restaurant.lat},${restaurant.lon}?q=${restaurant.lat},${restaurant.lon}(${Uri.encode(restaurant.name)})"
            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            mapIntent.setPackage("com.google.android.apps.maps")
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                // Fallback to browser if Maps app is not installed
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${restaurant.lat},${restaurant.lon}"))
                startActivity(browserIntent)
            }
        }
    }
}

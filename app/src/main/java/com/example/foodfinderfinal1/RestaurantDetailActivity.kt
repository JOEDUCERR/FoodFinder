//package com.example.foodfinderfinal1
//
//import android.content.Intent
//import android.net.Uri
//import android.os.Bundle
//import android.widget.Button
//import android.widget.TextView
//import androidx.appcompat.app.AppCompatActivity
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//
//class RestaurantDetailActivity : AppCompatActivity() {
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_restaurant_detail)
//
//        @Suppress("DEPRECATION")
//        val restaurant = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
//            intent.getSerializableExtra("EXTRA_RESTAURANT", Restaurant::class.java)
//        } else {
//            intent.getSerializableExtra("EXTRA_RESTAURANT") as? Restaurant
//        } ?: return
//
//        val tvDetailName = findViewById<TextView>(R.id.tvDetailName)
//        val tvDetailInfo = findViewById<TextView>(R.id.tvDetailInfo)
//        val btnOpenMaps = findViewById<Button>(R.id.btnOpenMaps)
//        val rvFoodItems = findViewById<RecyclerView>(R.id.rvFoodItems)
//
//        tvDetailName.text = restaurant.name
//        tvDetailInfo.text = "Address: ${restaurant.address}"
//
//        rvFoodItems.layoutManager = LinearLayoutManager(this)
//        rvFoodItems.adapter = FoodAdapter(restaurant.foodItems)
//
//        btnOpenMaps.setOnClickListener {
//            val uri = "geo:${restaurant.lat},${restaurant.lon}?q=${restaurant.lat},${restaurant.lon}(${Uri.encode(restaurant.name)})"
//            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
//            mapIntent.setPackage("com.google.android.apps.maps")
//            if (mapIntent.resolveActivity(packageManager) != null) {
//                startActivity(mapIntent)
//            } else {
//                // Fallback to browser if Maps app is not installed
//                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${restaurant.lat},${restaurant.lon}"))
//                startActivity(browserIntent)
//            }
//        }
//    }
//}

package com.example.foodfinderfinal1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// ─────────────────────────────────────────────────────────────────────────────
// RestaurantDetailActivity — upgrades applied:
//
// 1. Embedded Google MapFragment instead of launching external Maps app
//    The map shows inside the detail screen with a pin on the restaurant.
//
// 2. Favourite / heart button — saves to Firestore under the user's account
//    Tapping again removes the favourite (toggle).
//
// 3. Share button — opens the native Android share sheet with restaurant info.
//
// 4. Real tag data displayed — phone, website, opening hours from Overpass.
//
// Layout changes needed in activity_restaurant_detail.xml:
//   - Add <fragment android:id="@+id/mapFragment" android:name="com.google.android.gms.maps.SupportMapFragment" .../>
//   - Add <ImageButton android:id="@+id/btnFavourite" .../> (heart icon)
//   - Add <ImageButton android:id="@+id/btnShare" .../>
//   - Add <TextView android:id="@+id/tvPhone" .../>
//   - Add <TextView android:id="@+id/tvWebsite" .../>
//   - Add <TextView android:id="@+id/tvOpeningHours" .../>
// ─────────────────────────────────────────────────────────────────────────────

class RestaurantDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var restaurant: Restaurant
    private var isFavourite = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restaurant_detail)

        restaurant = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("EXTRA_RESTAURANT", Restaurant::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("EXTRA_RESTAURANT")
        } ?: return

        // ── Basic info ────────────────────────────────────────────────────────
        findViewById<TextView>(R.id.tvDetailName).text    = restaurant.name
        findViewById<TextView>(R.id.tvDetailInfo).text    = restaurant.address

        // ── Upgrade 4: Show real Overpass tag data ────────────────────────────
        val tvPhone        = findViewById<TextView>(R.id.tvPhone)
        val tvWebsite      = findViewById<TextView>(R.id.tvWebsite)
        val tvOpeningHours = findViewById<TextView>(R.id.tvOpeningHours)

        if (restaurant.phone.isNotBlank()) {
            tvPhone.text = "📞 ${restaurant.phone}"
            tvPhone.setOnClickListener {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${restaurant.phone}")))
            }
        } else tvPhone.visibility = View.GONE

        if (restaurant.website.isNotBlank()) {
            tvWebsite.text = "🌐 ${restaurant.website}"
            tvWebsite.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(restaurant.website)))
            }
        } else tvWebsite.visibility = View.GONE

        if (restaurant.openingHours.isNotBlank()) {
            tvOpeningHours.text = "🕐 ${restaurant.openingHours}"
        } else tvOpeningHours.visibility = View.GONE

        // ── Food list ─────────────────────────────────────────────────────────
        val rvFoodItems = findViewById<RecyclerView>(R.id.rvFoodItems)
        val foodAdapter = FoodAdapter()
        rvFoodItems.layoutManager = LinearLayoutManager(this)
        rvFoodItems.adapter = foodAdapter
        foodAdapter.submitList(restaurant.foodItems)

        // ── Upgrade 1: Embedded map ───────────────────────────────────────────
        // SupportMapFragment sits in the layout — it calls onMapReady() when ready
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Keep the external Maps button as a fallback / directions button
        findViewById<Button>(R.id.btnOpenMaps).setOnClickListener {
            val uri = "geo:${restaurant.lat},${restaurant.lon}?q=${restaurant.lat},${restaurant.lon}(${Uri.encode(restaurant.name)})"
            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            mapIntent.setPackage("com.google.android.apps.maps")
            if (mapIntent.resolveActivity(packageManager) != null) startActivity(mapIntent)
            else startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://maps.google.com/?q=${restaurant.lat},${restaurant.lon}")))
        }

        // ── Upgrade 2: Favourite button ───────────────────────────────────────
        val btnFavourite = findViewById<ImageButton>(R.id.btnFavourite)
        checkIfFavourite(btnFavourite)

        btnFavourite.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, "Please log in to save favourites", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleFavourite(uid, btnFavourite)
        }

        // ── Upgrade 3: Share button ───────────────────────────────────────────
        findViewById<ImageButton>(R.id.btnShare).setOnClickListener {
            val text = buildString {
                append("🍽️ ${restaurant.name}\n")
                append("📍 ${restaurant.address}\n")
                if (restaurant.phone.isNotBlank())   append("📞 ${restaurant.phone}\n")
                if (restaurant.website.isNotBlank()) append("🌐 ${restaurant.website}\n")
                append("https://maps.google.com/?q=${restaurant.lat},${restaurant.lon}")
            }
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }, "Share ${restaurant.name}"
            ))
        }
    }

    // ── OnMapReadyCallback — called when the MapFragment is ready ─────────────
    override fun onMapReady(googleMap: GoogleMap) {
        val position = LatLng(restaurant.lat, restaurant.lon)
        googleMap.apply {
            addMarker(MarkerOptions().position(position).title(restaurant.name))
            moveCamera(CameraUpdateFactory.newLatLngZoom(position, 16f))
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isMyLocationButtonEnabled = false
        }
    }

    // ── Firestore favourite helpers ───────────────────────────────────────────
    private fun favouriteRef(uid: String) = FirebaseFirestore.getInstance()
        .collection("users").document(uid)
        .collection("favourites").document(restaurant.id)

    private fun checkIfFavourite(btn: ImageButton) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        favouriteRef(uid).get().addOnSuccessListener { doc ->
            isFavourite = doc.exists()
            btn.setImageResource(
                if (isFavourite) android.R.drawable.btn_star_big_on
                else             android.R.drawable.btn_star_big_off
            )
        }
    }

    private fun toggleFavourite(uid: String, btn: ImageButton) {
        val ref = favouriteRef(uid)
        if (isFavourite) {
            ref.delete().addOnSuccessListener {
                isFavourite = false
                btn.setImageResource(android.R.drawable.btn_star_big_off)
                Toast.makeText(this, "Removed from favourites", Toast.LENGTH_SHORT).show()
            }
        } else {
            val data = mapOf(
                "id"      to restaurant.id,
                "name"    to restaurant.name,
                "address" to restaurant.address,
                "lat"     to restaurant.lat,
                "lon"     to restaurant.lon
            )
            ref.set(data).addOnSuccessListener {
                isFavourite = true
                btn.setImageResource(android.R.drawable.btn_star_big_on)
                Toast.makeText(this, "Saved to favourites ⭐", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

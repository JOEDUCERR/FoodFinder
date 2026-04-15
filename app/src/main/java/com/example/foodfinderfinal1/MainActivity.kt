//package com.example.foodfinderfinal1
//
//import android.content.Intent
//import android.os.Bundle
//import android.view.View
//import android.view.inputmethod.EditorInfo
//import android.view.inputmethod.InputMethodManager
//import android.widget.EditText
//import android.widget.ProgressBar
//import android.widget.TextView
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.lifecycle.lifecycleScope
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import com.google.android.material.chip.Chip
//import com.google.android.material.chip.ChipGroup
//import com.google.android.material.floatingactionbutton.FloatingActionButton
//import kotlinx.coroutines.launch
//
//class MainActivity : AppCompatActivity() {
//
//    private lateinit var rvRestaurants: RecyclerView
//    private lateinit var progressBar: ProgressBar
//    private lateinit var tvLocationTitle: TextView
//    private lateinit var etSearch: EditText
//    private lateinit var chipGroup: ChipGroup
//
//    private var allRestaurants: List<Restaurant> = emptyList()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        // Build initial location from login screen
//        val area    = intent.getStringExtra("EXTRA_AREA")    ?: ""
//        val state   = intent.getStringExtra("EXTRA_STATE")   ?: ""
//        val country = intent.getStringExtra("EXTRA_COUNTRY") ?: ""
//        val initialQuery = listOf(area, state, country)
//            .filter { it.isNotBlank() }
//            .joinToString(", ")
//            .ifBlank { "New York" }
//
//        tvLocationTitle = findViewById(R.id.tvLocationTitle)
//        rvRestaurants   = findViewById(R.id.rvRestaurants)
//        progressBar     = findViewById(R.id.progressBar)
//        etSearch        = findViewById(R.id.etSearch)
//        chipGroup       = findViewById(R.id.chipGroupCuisine)
//
//        rvRestaurants.layoutManager = LinearLayoutManager(this)
//        tvLocationTitle.text = initialQuery
//
//        // FAB → ChatActivity
//        findViewById<FloatingActionButton>(R.id.fabChat).setOnClickListener {
//            startActivity(Intent(this, ChatActivity::class.java))
//        }
//
//        // Cuisine chip filter
//        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
//            val label = if (checkedIds.isEmpty()) "All"
//            else group.findViewById<Chip>(checkedIds[0])?.text?.toString() ?: "All"
//            applyFilter(label)
//        }
//
//        // ── Search bar ────────────────────────────────────────────────────────
//        // Triggers on keyboard Search/Go action key
//        etSearch.setOnEditorActionListener { _, actionId, _ ->
//            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
//                actionId == EditorInfo.IME_ACTION_GO ||
//                actionId == EditorInfo.IME_ACTION_DONE) {
//                performSearch()
//                true
//            } else false
//        }
//
//        // Search icon button inside the bar — works even if keyboard action doesn't fire
//        findViewById<android.widget.ImageView>(R.id.btnSearch).setOnClickListener {
//            performSearch()
//        }
//
//        // Load initial location
//        loadRestaurants(initialQuery)
//    }
//
//    private fun performSearch() {
//        val query = etSearch.text.toString().trim()
//        if (query.isBlank()) {
//            Toast.makeText(this, "Please enter a city name", Toast.LENGTH_SHORT).show()
//            return
//        }
//        hideKeyboard(etSearch)
//        tvLocationTitle.text = query
//        // Don't clear the search box — user can see what they searched
//        loadRestaurants(query)
//    }
//
//    private fun loadRestaurants(query: String) {
//        showLoading(true)
//        lifecycleScope.launch {
//            val restaurants = OverpassApiClient.getRestaurants(query)
//            allRestaurants  = restaurants
//            showLoading(false)
//
//            if (restaurants.isEmpty()) {
//                Toast.makeText(
//                    this@MainActivity,
//                    "No restaurants found for \"$query\".\n" +
//                            "Try: \"City, Country\" e.g. \"Paris, France\"",
//                    Toast.LENGTH_LONG
//                ).show()
//                rvRestaurants.adapter = RestaurantAdapter(emptyList()) {}
//            } else {
//                chipGroup.check(R.id.chipAll)
//                applyFilter("All")
//                Toast.makeText(
//                    this@MainActivity,
//                    "Found ${restaurants.size} restaurants near $query",
//                    Toast.LENGTH_SHORT
//                ).show()
//            }
//        }
//    }
//
//    private fun applyFilter(cuisine: String) {
//        val filtered = if (cuisine == "All") {
//            allRestaurants
//        } else {
//            val match = allRestaurants.filter { r ->
//                r.name.contains(cuisine, ignoreCase = true) ||
//                        r.foodItems.any { it.name.contains(cuisine, ignoreCase = true) }
//            }
//            if (match.isEmpty()) allRestaurants else match
//        }
//        rvRestaurants.adapter = RestaurantAdapter(filtered) { openDetail(it) }
//    }
//
//    private fun showLoading(show: Boolean) {
//        progressBar.visibility   = if (show) View.VISIBLE   else View.GONE
//        rvRestaurants.visibility = if (show) View.INVISIBLE else View.VISIBLE
//    }
//
//    private fun hideKeyboard(view: View) {
//        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
//            .hideSoftInputFromWindow(view.windowToken, 0)
//    }
//
//    private fun openDetail(restaurant: Restaurant) {
//        startActivity(Intent(this, RestaurantDetailActivity::class.java).apply {
//            putExtra("EXTRA_RESTAURANT", restaurant)
//        })
//    }
//}

package com.example.foodfinderfinal1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// MainActivity — upgrades applied:
//
// 1. GPS auto-detect on launch using FusedLocationProviderClient
//    Replaces the location fields that were on the login screen.
//    If permission denied, falls back to a manual search box.
//
// 2. Adapter created once as a field → submitList() used everywhere
//    (no more re-creating the adapter on every filter/load)
//
// 3. Logout button added so users can switch accounts
//
// Dependencies to add in app/build.gradle.kts:
//   implementation("com.google.android.gms:play-services-location:21.3.0")
// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : AppCompatActivity() {

    private lateinit var rvRestaurants: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLocationTitle: TextView
    private lateinit var etSearch: EditText
    private lateinit var chipGroup: ChipGroup

    // ── Adapter created ONCE as a field ──────────────────────────────────────
    private lateinit var restaurantAdapter: RestaurantAdapter
    private var allRestaurants: List<Restaurant> = emptyList()

    // ── GPS permission launcher ───────────────────────────────────────────────
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) detectLocationAndLoad()
        else {
            // Permission denied — show search bar, load default city
            tvLocationTitle.text = "Search for a city"
            loadRestaurants("New Delhi")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLocationTitle = findViewById(R.id.tvLocationTitle)
        rvRestaurants   = findViewById(R.id.rvRestaurants)
        progressBar     = findViewById(R.id.progressBar)
        etSearch        = findViewById(R.id.etSearch)
        chipGroup       = findViewById(R.id.chipGroupCuisine)

        // ── Create adapter ONCE — reuse via submitList() ──────────────────────
        restaurantAdapter = RestaurantAdapter { openDetail(it) }
        rvRestaurants.layoutManager = LinearLayoutManager(this)
        rvRestaurants.adapter = restaurantAdapter

        // FAB → ChatActivity
        findViewById<FloatingActionButton>(R.id.fabChat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        // ── Logout button ─────────────────────────────────────────────────────
        // Add an ImageButton with id btnLogout to your toolbar in activity_main.xml
        findViewById<android.widget.ImageButton>(R.id.btnLogout).setOnClickListener {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
            .setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        // Already on home
                        true
                    }
                    R.id.nav_nearby -> {
                        requestLocationAndLoad()
                        true
                    }
                    R.id.nav_saved -> {
                        startActivity(Intent(this, FavouritesActivity::class.java))
                        true
                    }
                    R.id.nav_profile -> {
                        startActivity(Intent(this, ProfileActivity::class.java))
                        true
                    }
                    else -> false
                }
            }

        // Cuisine chip filter
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val label = if (checkedIds.isEmpty()) "All"
            else group.findViewById<Chip>(checkedIds[0])?.text?.toString() ?: "All"
            applyFilter(label)
        }

        // Search bar
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_GO     ||
                actionId == EditorInfo.IME_ACTION_DONE) {
                performSearch(); true
            } else false
        }
        findViewById<android.widget.ImageView>(R.id.btnSearch).setOnClickListener {
            performSearch()
        }

        // ── Upgrade 1: GPS auto-detect on launch ──────────────────────────────
        requestLocationAndLoad()
    }

    // ── GPS: request permission then detect ──────────────────────────────────
    private fun requestLocationAndLoad() {
        val fine   = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        if (ContextCompat.checkSelfPermission(this, fine) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, coarse) == PackageManager.PERMISSION_GRANTED) {
            detectLocationAndLoad()
        } else {
            locationPermissionRequest.launch(arrayOf(fine, coarse))
        }
    }

    private fun detectLocationAndLoad() {
        tvLocationTitle.text = "Detecting location…"
        showLoading(true)

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        // Reverse-geocode lat/lon to a city name using Nominatim
                        lifecycleScope.launch {
                            val cityName = OverpassApiClient.reverseGeocode(location.latitude, location.longitude)
                            tvLocationTitle.text = cityName
                            loadRestaurants(cityName)
                        }
                    } else {
                        // Location null (emulator or no fix) — fall back
                        loadRestaurants("New Delhi")
                    }
                }
                .addOnFailureListener {
                    loadRestaurants("New Delhi")
                }
        } catch (e: SecurityException) {
            loadRestaurants("New Delhi")
        }
    }

    private fun performSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isBlank()) {
            Toast.makeText(this, "Please enter a city name", Toast.LENGTH_SHORT).show()
            return
        }
        hideKeyboard(etSearch)
        tvLocationTitle.text = query
        loadRestaurants(query)
    }

    private fun loadRestaurants(query: String) {
        showLoading(true)
        lifecycleScope.launch {
            val restaurants = OverpassApiClient.getRestaurants(query)
            allRestaurants  = restaurants
            showLoading(false)

            if (restaurants.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    "No restaurants found for \"$query\".\nTry: \"City, Country\"",
                    Toast.LENGTH_LONG
                ).show()
                restaurantAdapter.submitList(emptyList())
            } else {
                chipGroup.check(R.id.chipAll)
                applyFilter("All")
                Toast.makeText(
                    this@MainActivity,
                    "Found ${restaurants.size} restaurants near $query",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun applyFilter(cuisine: String) {
        val filtered = if (cuisine == "All") allRestaurants
        else {
            val match = allRestaurants.filter { r ->
                r.name.contains(cuisine, ignoreCase = true)           ||
                        r.cuisineTag.contains(cuisine, ignoreCase = true)     ||
                        r.foodItems.any { it.name.contains(cuisine, ignoreCase = true) }
            }
            if (match.isEmpty()) allRestaurants else match
        }
        // ✅ submitList() instead of recreating the adapter
        restaurantAdapter.submitList(filtered)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility   = if (show) View.VISIBLE   else View.GONE
        rvRestaurants.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    private fun hideKeyboard(view: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun openDetail(restaurant: Restaurant) {
        startActivity(Intent(this, RestaurantDetailActivity::class.java).apply {
            putExtra("EXTRA_RESTAURANT", restaurant)
        })
    }
}

package com.joey.foodfinderfinal1

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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var rvRestaurants: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLocationTitle: TextView
    private lateinit var etSearch: EditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var restaurantAdapter: RestaurantAdapter
    private var allRestaurants: List<Restaurant> = emptyList()
    
    // Track loading state to prevent redundant requests
    private var isCurrentlyLoading = false

    private val cuisineKeywords = setOf(
        "all", "pizza", "italian", "burger", "american", "biryani", "indian",
        "punjabi", "chinese", "asian", "coffee", "cafe", "sushi", "japanese",
        "mexican", "thai", "french", "mediterranean", "kebab", "bbq", "vegan",
        "vegetarian", "seafood", "steak", "sandwich", "noodles", "ramen"
    )

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) detectLocationAndLoad()
        else {
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
        bottomNav       = findViewById(R.id.bottomNav)

        restaurantAdapter = RestaurantAdapter { openDetail(it) }
        rvRestaurants.layoutManager = LinearLayoutManager(this)
        rvRestaurants.adapter = restaurantAdapter

        findViewById<FloatingActionButton>(R.id.fabChat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        findViewById<android.widget.ImageButton>(R.id.btnLogout).setOnClickListener {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val label = if (checkedIds.isEmpty()) "All"
            else group.findViewById<Chip>(checkedIds[0])?.text?.toString() ?: "All"
            applyFilter(label)
        }

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

        setupBottomNavigation()
        requestLocationAndLoad()
    }

    private fun setupBottomNavigation() {
        // Ensure Home is selected
        bottomNav.selectedItemId = R.id.nav_home
        
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    rvRestaurants.smoothScrollToPosition(0)
                    true
                }
                R.id.nav_nearby -> {
                    detectLocationAndLoad()
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
    }

    private fun performSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isBlank()) {
            Toast.makeText(this, "Please enter a city or cuisine", Toast.LENGTH_SHORT).show()
            return
        }
        hideKeyboard(etSearch)

        if (cuisineKeywords.contains(query.lowercase())) {
            if (allRestaurants.isEmpty()) {
                Toast.makeText(
                    this,
                    "Load a city first, then filter by cuisine",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                applyFilter(query)
            }
            return
        }

        val inPattern = Regex("^(.+?)\\s+in\\s+(.+)$", RegexOption.IGNORE_CASE)
        val match = inPattern.find(query)
        if (match != null) {
            val cuisine = match.groupValues[1].trim()
            val city    = match.groupValues[2].trim()
            tvLocationTitle.text = city
            loadRestaurants(city, filterAfterLoad = cuisine)
            return
        }

        tvLocationTitle.text = query
        loadRestaurants(query)
    }

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
        // Don't call showLoading(true) here — loadRestaurants() will do it

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        lifecycleScope.launch {
                            val cityName = OverpassApiClient.reverseGeocode(
                                location.latitude, location.longitude
                            )
                            tvLocationTitle.text = cityName
                            loadRestaurants(cityName)
                        }
                    } else {
                        tvLocationTitle.text = "New Delhi"
                        loadRestaurants("New Delhi")
                    }
                }
                .addOnFailureListener {
                    tvLocationTitle.text = "New Delhi"
                    loadRestaurants("New Delhi")
                }
        } catch (e: SecurityException) {
            tvLocationTitle.text = "New Delhi"
            loadRestaurants("New Delhi")
        }
    }

    private fun loadRestaurants(query: String, filterAfterLoad: String = "") {
        if (isCurrentlyLoading) return

        isCurrentlyLoading = true
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val restaurants = OverpassApiClient.getRestaurants(query)
                allRestaurants = restaurants
                
                if (restaurants.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "No restaurants found for \"$query\". try another city.",
                        Toast.LENGTH_LONG
                    ).show()
                    restaurantAdapter.submitList(emptyList())
                } else {
                    if (filterAfterLoad.isNotBlank()) {
                        applyFilter(filterAfterLoad)
                    } else {
                        chipGroup.check(R.id.chipAll)
                        applyFilter("All")
                    }
                    Toast.makeText(
                        this@MainActivity,
                        "Found ${restaurants.size} restaurants near $query",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading restaurants: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isCurrentlyLoading = false
                showLoading(false)
            }
        }
    }

    private fun applyFilter(cuisine: String) {
        val filtered = if (cuisine.equals("All", ignoreCase = true)) {
            allRestaurants
        } else {
            allRestaurants.filter { r ->
                r.name.contains(cuisine, ignoreCase = true)       ||
                        r.cuisineTag.contains(cuisine, ignoreCase = true) ||
                        r.foodItems.any { it.name.contains(cuisine, ignoreCase = true) }
            }
        }

        restaurantAdapter.submitList(filtered)

        if (filtered.isEmpty() && !cuisine.equals("All", ignoreCase = true)) {
            Toast.makeText(
                this,
                "No \"$cuisine\" restaurants found in this area",
                Toast.LENGTH_SHORT
            ).show()
        }
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

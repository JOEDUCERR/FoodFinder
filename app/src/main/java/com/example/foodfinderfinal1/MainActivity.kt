package com.example.foodfinderfinal1

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    private var allRestaurants: List<Restaurant> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Build initial location from login screen
        val area    = intent.getStringExtra("EXTRA_AREA")    ?: ""
        val state   = intent.getStringExtra("EXTRA_STATE")   ?: ""
        val country = intent.getStringExtra("EXTRA_COUNTRY") ?: ""
        val initialQuery = listOf(area, state, country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { "New York" }

        tvLocationTitle = findViewById(R.id.tvLocationTitle)
        rvRestaurants   = findViewById(R.id.rvRestaurants)
        progressBar     = findViewById(R.id.progressBar)
        etSearch        = findViewById(R.id.etSearch)
        chipGroup       = findViewById(R.id.chipGroupCuisine)

        rvRestaurants.layoutManager = LinearLayoutManager(this)
        tvLocationTitle.text = initialQuery

        // FAB → ChatActivity
        findViewById<FloatingActionButton>(R.id.fabChat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        // Cuisine chip filter
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val label = if (checkedIds.isEmpty()) "All"
            else group.findViewById<Chip>(checkedIds[0])?.text?.toString() ?: "All"
            applyFilter(label)
        }

        // ── Search bar ────────────────────────────────────────────────────────
        // Triggers on keyboard Search/Go action key
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE) {
                performSearch()
                true
            } else false
        }

        // Search icon button inside the bar — works even if keyboard action doesn't fire
        findViewById<android.widget.ImageView>(R.id.btnSearch).setOnClickListener {
            performSearch()
        }

        // Load initial location
        loadRestaurants(initialQuery)
    }

    private fun performSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isBlank()) {
            Toast.makeText(this, "Please enter a city name", Toast.LENGTH_SHORT).show()
            return
        }
        hideKeyboard(etSearch)
        tvLocationTitle.text = query
        // Don't clear the search box — user can see what they searched
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
                    "No restaurants found for \"$query\".\n" +
                            "Try: \"City, Country\" e.g. \"Paris, France\"",
                    Toast.LENGTH_LONG
                ).show()
                rvRestaurants.adapter = RestaurantAdapter(emptyList()) {}
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
        val filtered = if (cuisine == "All") {
            allRestaurants
        } else {
            val match = allRestaurants.filter { r ->
                r.name.contains(cuisine, ignoreCase = true) ||
                        r.foodItems.any { it.name.contains(cuisine, ignoreCase = true) }
            }
            if (match.isEmpty()) allRestaurants else match
        }
        rvRestaurants.adapter = RestaurantAdapter(filtered) { openDetail(it) }
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
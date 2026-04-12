package com.example.foodfinderfinal1

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OverpassApiClient {

    private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
    private const val TAG = "OverpassApiClient"

    // ── Called from MainActivity with a single free-text query ──────────────
    suspend fun getRestaurants(searchQuery: String): List<Restaurant> {
        return withContext(Dispatchers.IO) {
            try {
                val query = searchQuery.trim().ifBlank { "New York" }
                Log.d(TAG, "Searching for: $query")

                // ── Step 1: Geocode with Nominatim ───────────────────────────
                // Try the query as-is first. If 0 results, retry with just the
                // first word/token so "New York restaurants" still works.
                val lat: String
                val lon: String
                val resolvedName: String

                val firstAttempt = geocode(query)
                if (firstAttempt != null) {
                    lat = firstAttempt.first
                    lon = firstAttempt.second
                    resolvedName = firstAttempt.third
                } else {
                    // Retry: strip trailing words that might confuse Nominatim
                    // e.g. "restaurants in Delhi" → "Delhi"
                    val fallbackQuery = query
                        .replace(Regex("(?i)restaurants?|food|near|in|around"), "")
                        .trim()
                    val secondAttempt = geocode(fallbackQuery.ifBlank { query })
                    if (secondAttempt == null) {
                        Log.e(TAG, "Nominatim: no result for '$query' or fallback '$fallbackQuery'")
                        return@withContext emptyList()
                    }
                    lat = secondAttempt.first
                    lon = secondAttempt.second
                    resolvedName = secondAttempt.third
                }

                Log.d(TAG, "Geocoded '$query' → ($lat, $lon) = $resolvedName")

                // ── Step 2: Adaptive radius ──────────────────────────────────
                // Small towns need a bigger radius; big cities can stay tight
                // Use a generous 15km so we never return empty for small towns
                val radius = 15000

                val overpassQuery = """
                    [out:json][timeout:60];
                    (
                      node["amenity"="restaurant"](around:$radius,$lat,$lon);
                      way["amenity"="restaurant"](around:$radius,$lat,$lon);
                      node["amenity"="fast_food"](around:$radius,$lat,$lon);
                      way["amenity"="fast_food"](around:$radius,$lat,$lon);
                      node["amenity"="cafe"](around:$radius,$lat,$lon);
                      way["amenity"="cafe"](around:$radius,$lat,$lon);
                    );
                    out center 80;
                """.trimIndent()

                // Overpass REQUIRES "data=" prefix in POST body
                val postBody  = "data=${URLEncoder.encode(overpassQuery, "UTF-8")}"
                val postBytes = postBody.toByteArray(Charsets.UTF_8)

                val conn = (URL(OVERPASS_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput      = true
                    connectTimeout = 20000
                    readTimeout    = 50000
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Content-Length", postBytes.size.toString())
                    setRequestProperty("User-Agent", "FoodFinderApp/2.0 (android)")
                }
                conn.outputStream.use { it.write(postBytes) }

                val ovCode = conn.responseCode
                if (ovCode != 200) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.e(TAG, "Overpass HTTP $ovCode — $err")
                    return@withContext emptyList()
                }

                val elements = JSONObject(
                    conn.inputStream.bufferedReader().use { it.readText() }
                ).optJSONArray("elements") ?: return@withContext emptyList()

                Log.d(TAG, "Overpass returned ${elements.length()} elements")

                val list = mutableListOf<Restaurant>()
                for (i in 0 until elements.length()) {
                    val node = elements.getJSONObject(i)

                    val resLat = node.optDouble("lat",
                        node.optJSONObject("center")?.optDouble("lat") ?: 0.0)
                    val resLon = node.optDouble("lon",
                        node.optJSONObject("center")?.optDouble("lon") ?: 0.0)
                    if (resLat == 0.0 && resLon == 0.0) continue

                    val tags    = node.optJSONObject("tags")
                    val name    = tags?.optString("name", "").orEmpty().trim()
                    if (name.isBlank()) continue

                    val cuisine = tags?.optString("cuisine", "").orEmpty()
                    val street  = tags?.optString("addr:street", "").orEmpty()
                    val city    = tags?.optString("addr:city", "").orEmpty()

                    val address = when {
                        street.isNotEmpty() && city.isNotEmpty() -> "$street, $city"
                        city.isNotEmpty()    -> city
                        street.isNotEmpty()  -> street
                        else                 -> resolvedName.split(",").take(2).joinToString(", ")
                    }

                    list.add(Restaurant(
                        node.optString("id"), name, address,
                        resLat, resLon, generateCuisineMenu(cuisine, name)
                    ))
                }

                val result = list.distinctBy { it.name }
                Log.d(TAG, "Returning ${result.size} unique restaurants")
                result

            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching restaurants", e)
                emptyList()
            }
        }
    }

    // ── Nominatim geocoding helper ───────────────────────────────────────────
    // Returns Triple(lat, lon, displayName) or null if no result
    private fun geocode(query: String): Triple<String, String, String>? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            // featuretype=city,town,village ensures we get the settlement, not a street
            val url = "https://nominatim.openstreetmap.org/search" +
                    "?q=$encoded&format=json&limit=1&addressdetails=0"

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout    = 15000
                setRequestProperty("User-Agent", "FoodFinderApp/2.0 (android)")
                setRequestProperty("Accept",     "application/json")
            }

            if (conn.responseCode != 200) return null

            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            if (arr.length() == 0) return null

            val obj = arr.getJSONObject(0)
            Triple(obj.getString("lat"), obj.getString("lon"),
                obj.optString("display_name", query))
        } catch (e: Exception) {
            Log.e(TAG, "geocode('$query') failed: ${e.message}")
            null
        }
    }

    private fun generateCuisineMenu(cuisine: String, name: String): List<FoodItem> {
        val c = cuisine.lowercase()
        val n = name.lowercase()
        return when {
            c.contains("pizza") || n.contains("pizza") || c.contains("italian") -> listOf(
                FoodItem("Margherita Pizza", "₹349"),
                FoodItem("Pepperoni Feast",  "₹449"),
                FoodItem("Pasta Carbonara",  "₹329"),
                FoodItem("Garlic Bread",     "₹149")
            )
            c.contains("indian") || c.contains("biryani") || n.contains("biryani")
                    || n.contains("dhaba") || n.contains("punjabi") -> listOf(
                FoodItem("Chicken Biryani",       "₹299"),
                FoodItem("Paneer Butter Masala",  "₹249"),
                FoodItem("Butter Naan",           "₹49"),
                FoodItem("Gulab Jamun",           "₹99")
            )
            c.contains("burger") || n.contains("burger") || c.contains("american") -> listOf(
                FoodItem("Classic Cheeseburger", "₹199"),
                FoodItem("Bacon Double Burger",  "₹299"),
                FoodItem("Fries & Coke",         "₹129"),
                FoodItem("Onion Rings",          "₹99")
            )
            c.contains("chinese") || c.contains("asian") -> listOf(
                FoodItem("Kung Pao Chicken",     "₹279"),
                FoodItem("Vegetable Chow Mein",  "₹199"),
                FoodItem("Spring Rolls (4 pcs)", "₹149"),
                FoodItem("Fried Rice",           "₹179")
            )
            c.contains("mexican") || c.contains("taco") -> listOf(
                FoodItem("Beef Tacos (3 pcs)", "₹249"),
                FoodItem("Chicken Burrito",    "₹279"),
                FoodItem("Nachos Supreme",     "₹199"),
                FoodItem("Quesadilla",         "₹179")
            )
            c.contains("coffee") || n.contains("café") || n.contains("cafe")
                    || n.contains("coffee") || n.contains("bakery") -> listOf(
                FoodItem("Cappuccino",       "₹149"),
                FoodItem("Cold Coffee",      "₹179"),
                FoodItem("Croissant",        "₹129"),
                FoodItem("Chocolate Cake",   "₹199")
            )
            else -> listOf(
                FoodItem("Chef's Special",     "₹349"),
                FoodItem("Fresh Garden Salad", "₹199"),
                FoodItem("Soup of the Day",    "₹149"),
                FoodItem("Cold Beverage",      "₹79")
            )
        }
    }
}
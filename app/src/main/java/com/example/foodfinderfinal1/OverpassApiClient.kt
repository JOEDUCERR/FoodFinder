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

    suspend fun getRestaurants(area: String, state: String, country: String): List<Restaurant> {
        return withContext(Dispatchers.IO) {
            try {
                // Build a dynamic query based on user input
                val parts = mutableListOf<String>()
                if (area.isNotBlank()) parts.add(area)
                if (state.isNotBlank()) parts.add(state)
                if (country.isNotBlank()) parts.add(country)

                // If everything is empty, default to a global city like New York for testing
                val locationQuery = if (parts.isEmpty()) "New York, USA" else parts.joinToString(", ")

                Log.d("OverpassApiClient", "Searching for: $locationQuery")

                val encodedQuery = URLEncoder.encode(locationQuery, "UTF-8")
                val nominatimUrl = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1"

                val geoUrl = URL(nominatimUrl)
                val geoConn = geoUrl.openConnection() as HttpURLConnection
                geoConn.setRequestProperty("User-Agent", "FoodFinderApp/2.0")
                geoConn.connectTimeout = 10000
                geoConn.readTimeout = 10000

                if (geoConn.responseCode != 200) {
                    Log.e("OverpassApiClient", "Geocoding failed with code: ${geoConn.responseCode}")
                    return@withContext emptyList()
                }

                val geoResponse = geoConn.inputStream.bufferedReader().use { it.readText() }
                val geoJson = JSONArray(geoResponse)

                if (geoJson.length() == 0) {
                    Log.e("OverpassApiClient", "No coordinates found for: $locationQuery")
                    return@withContext emptyList()
                }

                val location = geoJson.getJSONObject(0)
                val lat = location.getString("lat")
                val lon = location.getString("lon")

                // Query for restaurants within 50km of the found coordinates (Increased for better results)
                val overpassQuery = """
                    [out:json][timeout:60];
                    (
                      node["amenity"="restaurant"](around:50000,$lat,$lon);
                      way["amenity"="restaurant"](around:50000,$lat,$lon);
                      relation["amenity"="restaurant"](around:50000,$lat,$lon);
                      node["amenity"="cafe"](around:50000,$lat,$lon);
                      node["amenity"="fast_food"](around:50000,$lat,$lon);
                    );
                    out center 100;
                """.trimIndent()
                
                val postData = overpassQuery.toByteArray()

                val url = URL(OVERPASS_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 20000
                conn.readTimeout = 20000
                conn.outputStream.write(postData)

                if (conn.responseCode != 200) {
                    Log.e("OverpassApiClient", "Overpass API failed with code: ${conn.responseCode}")
                    return@withContext emptyList()
                }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(response)
                val elements = jsonObj.optJSONArray("elements") ?: return@withContext emptyList()

                val list = mutableListOf<Restaurant>()
                for (i in 0 until elements.length()) {
                    val node = elements.getJSONObject(i)
                    val id = node.optString("id")
                    val resLat = node.optDouble("lat", node.optJSONObject("center")?.optDouble("lat") ?: 0.0)
                    val resLon = node.optDouble("lon", node.optJSONObject("center")?.optDouble("lon") ?: 0.0)

                    val tags = node.optJSONObject("tags")
                    val name = tags?.optString("name", "Unknown Restaurant") ?: "Unknown Restaurant"
                    val cuisine = tags?.optString("cuisine", "").orEmpty()
                    
                    val street = tags?.optString("addr:street", "") ?: ""
                    val cityTag = tags?.optString("addr:city", "") ?: ""
                    
                    val address = when {
                        street.isNotEmpty() && cityTag.isNotEmpty() -> "$street, $cityTag"
                        cityTag.isNotEmpty() -> cityTag
                        else -> "Near requested location"
                    }

                    val foods = generateCuisineMenu(cuisine, name)

                    if (name != "Unknown Restaurant" && resLat != 0.0) {
                        list.add(Restaurant(id, name, address, resLat, resLon, foods))
                    }
                }

                list.distinctBy { it.name }
            } catch (e: Exception) {
                Log.e("OverpassApiClient", "Error fetching restaurants", e)
                emptyList()
            }
        }
    }

    private fun generateCuisineMenu(cuisine: String, name: String): List<FoodItem> {
        val lowerCuisine = cuisine.lowercase()
        val lowerName = name.lowercase()
        
        return when {
            lowerCuisine.contains("pizza") || lowerName.contains("pizza") || lowerCuisine.contains("italian") -> listOf(
                FoodItem("Margherita Pizza", "$12.99"),
                FoodItem("Pepperoni Feast", "$15.99"),
                FoodItem("Pasta Carbonara", "$13.49"),
                FoodItem("Garlic Bread", "$5.99")
            )
            lowerCuisine.contains("indian") || lowerCuisine.contains("biryani") || lowerName.contains("biryani") -> listOf(
                FoodItem("Chicken Biryani", "$13.99"),
                FoodItem("Paneer Butter Masala", "$11.99"),
                FoodItem("Butter Naan", "$2.49"),
                FoodItem("Gulab Jamun", "$4.99")
            )
            lowerCuisine.contains("burger") || lowerName.contains("burger") || lowerCuisine.contains("american") -> listOf(
                FoodItem("Classic Cheeseburger", "$9.99"),
                FoodItem("Bacon Double Burger", "$12.99"),
                FoodItem("Fries & Coke", "$5.49"),
                FoodItem("Onion Rings", "$4.99")
            )
            lowerCuisine.contains("chinese") || lowerCuisine.contains("asian") -> listOf(
                FoodItem("Kung Pao Chicken", "$11.99"),
                FoodItem("Vegetable Chow Mein", "$9.99"),
                FoodItem("Spring Rolls (4pcs)", "$4.99"),
                FoodItem("Fried Rice", "$8.99")
            )
            lowerCuisine.contains("mexican") || lowerCuisine.contains("taco") -> listOf(
                FoodItem("Beef Tacos (3pcs)", "$9.99"),
                FoodItem("Chicken Burrito", "$10.49"),
                FoodItem("Nachos Supreme", "$8.99"),
                FoodItem("Quesadilla", "$7.99")
            )
            else -> listOf(
                FoodItem("Chef's Special", "$14.99"),
                FoodItem("Fresh Garden Salad", "$7.99"),
                FoodItem("Soup of the Day", "$5.49"),
                FoodItem("Cold Beverage", "$2.49")
            )
        }
    }
}

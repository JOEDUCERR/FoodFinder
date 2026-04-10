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
                // Build clean query
                val locationQuery = listOf(area, state, country)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")

                val encodedQuery = URLEncoder.encode(locationQuery, "UTF-8")
                val nominatimUrl = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1"

                val geoUrl = URL(nominatimUrl)
                val geoConn = geoUrl.openConnection() as HttpURLConnection
                geoConn.setRequestProperty("User-Agent", "FoodFinderApp/1.0")

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

                // Now query Overpass API for restaurants near (lat, lon) within ~5km
                val overpassQuery = "[out:json];node[\"amenity\"=\"restaurant\"](around:5000,$lat,$lon);out 15;"
                val postData = overpassQuery.toByteArray()

                val url = URL(OVERPASS_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
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
                    val resLat = node.optDouble("lat", 0.0)
                    val resLon = node.optDouble("lon", 0.0)

                    val tags = node.optJSONObject("tags")
                    val name = tags?.optString("name", "Unknown Restaurant") ?: "Unknown Restaurant"
                    val street = tags?.optString("addr:street", "") ?: ""
                    val city = tags?.optString("addr:city", "") ?: ""
                    val address = if (street.isNotEmpty()) "$street, $city" else "Address not available"

                    // Add dummy food items
                    val foods = listOf(
                        FoodItem("Classic Burger", "$5.99"),
                        FoodItem("Margherita Pizza", "$12.99"),
                        FoodItem("French Fries", "$2.99"),
                        FoodItem("Soft Drink", "$1.99"),
                        FoodItem("Garden Salad", "$4.99")
                    )

                    if (name != "Unknown Restaurant") {
                        list.add(Restaurant(id, name, address, resLat, resLon, foods))
                    }
                }

                list
            } catch (e: Exception) {
                Log.e("OverpassApiClient", "Error fetching restaurants", e)
                emptyList()
            }
        }
    }
}

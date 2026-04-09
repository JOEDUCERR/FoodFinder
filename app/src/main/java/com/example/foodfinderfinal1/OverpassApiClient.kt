package com.example.foodfinderfinal1

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OverpassApiClient {

    // Overpass API URL
    private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"

    suspend fun getRestaurants(area: String, state: String, country: String): List<Restaurant> {
        return withContext(Dispatchers.IO) {
            try {
                // First get coordinates for the given area using Nominatim
                val geocodeQuery = "$area, $state, $country".replace(" ", "+")
                val nominatimUrl = "https://nominatim.openstreetmap.org/search?q=$geocodeQuery&format=json&limit=1"

                val geoUrl = URL(nominatimUrl)
                val geoConn = geoUrl.openConnection() as HttpURLConnection
                geoConn.setRequestProperty("User-Agent", "FoodFinderApp")

                if (geoConn.responseCode != 200) return@withContext emptyList()

                val geoResponse = geoConn.inputStream.bufferedReader().use { it.readText() }
                val geoJson = JSONArray(geoResponse)

                if (geoJson.length() == 0) return@withContext emptyList()

                val location = geoJson.getJSONObject(0)
                val lat = location.getString("lat")
                val lon = location.getString("lon")

                // Now query Overpass API for restaurants near (lat, lon) within ~5km
                // query: node["amenity"="restaurant"](around:5000,lat,lon); out;
                val overpassQuery = "[out:json];node[\"amenity\"=\"restaurant\"](around:5000,$lat,$lon);out 10;"
                val postData = overpassQuery.toByteArray()

                val url = URL(OVERPASS_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.outputStream.write(postData)

                if (conn.responseCode != 200) return@withContext emptyList()

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
                    val address = tags?.optString("addr:street", "Address not available") ?: "Address not available"

                    // Add dummy food items
                    val foods = listOf(
                        FoodItem("Burger", "$5.99"),
                        FoodItem("Pizza", "$12.99"),
                        FoodItem("Fries", "$2.99"),
                        FoodItem("Drink", "$1.99"),
                        FoodItem("Salad", "$4.99")
                    )

                    if (name != "Unknown Restaurant") {
                        list.add(Restaurant(id, name, address, resLat, resLon, foods))
                    }
                }

                list
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}







//package com.example.foodfinderfinal1
//
//import android.util.Log
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import org.json.JSONArray
//import org.json.JSONObject
//import java.net.HttpURLConnection
//import java.net.URL
//import java.net.URLEncoder
//
//object OverpassApiClient {
//
//    private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
//
//    suspend fun getRestaurants(
//        area: String,
//        state: String,
//        country: String
//    ): List<Restaurant> {
//        return withContext(Dispatchers.IO) {
//            try {
//
//                // Build clean query
//                val locationQuery = listOf(area, state, country)
//                    .filter { it.isNotBlank() }
//                    .joinToString(", ")
//
//                val geocodeQuery = URLEncoder.encode(locationQuery, "UTF-8")
//
//                val nominatimUrl =
//                    "https://nominatim.openstreetmap.org/search?q=$geocodeQuery&format=jsonv2&limit=1"
//
//                Log.d("NOMINATIM_URL", nominatimUrl)
//
//                val geoConn = URL(nominatimUrl).openConnection() as HttpURLConnection
//                geoConn.setRequestProperty("User-Agent", "FoodFinderApp")
//
//                if (geoConn.responseCode != 200) {
//                    Log.e("GEO", "Geocode failed: ${geoConn.responseCode}")
//                    return@withContext emptyList()
//                }
//
//                val geoResponse =
//                    geoConn.inputStream.bufferedReader().use { it.readText() }
//
//                Log.d("GEO_RESPONSE", geoResponse)
//
//                val geoJson = JSONArray(geoResponse)
//
//                if (geoJson.length() == 0) {
//                    Log.e("GEO", "No location found")
//                    return@withContext emptyList()
//                }
//
//                val location = geoJson.getJSONObject(0)
//                val lat = location.getString("lat")
//                val lon = location.getString("lon")
//
//                val overpassQuery = """
//                    [out:json];
//                    (
//                      node["amenity"="restaurant"](around:5000,$lat,$lon);
//                      way["amenity"="restaurant"](around:5000,$lat,$lon);
//                      relation["amenity"="restaurant"](around:5000,$lat,$lon);
//                    );
//                    out center 10;
//                """.trimIndent()
//
//                val conn = URL(OVERPASS_URL).openConnection() as HttpURLConnection
//                conn.requestMethod = "POST"
//                conn.doOutput = true
//                conn.outputStream.write(overpassQuery.toByteArray())
//
//                if (conn.responseCode != 200) {
//                    Log.e("OVERPASS", "Failed: ${conn.responseCode}")
//                    return@withContext emptyList()
//                }
//
//                val response =
//                    conn.inputStream.bufferedReader().use { it.readText() }
//
//                val jsonObj = JSONObject(response)
//                val elements =
//                    jsonObj.optJSONArray("elements") ?: return@withContext emptyList()
//
//                val list = mutableListOf<Restaurant>()
//
//                for (i in 0 until elements.length()) {
//                    val node = elements.getJSONObject(i)
//
//                    val id = node.optString("id")
//
//                    val resLat =
//                        node.optDouble("lat", node.optJSONObject("center")?.optDouble("lat") ?: 0.0)
//
//                    val resLon =
//                        node.optDouble("lon", node.optJSONObject("center")?.optDouble("lon") ?: 0.0)
//
//                    val tags = node.optJSONObject("tags")
//
//                    val name =
//                        tags?.optString("name", "Unknown Restaurant")
//                            ?: "Unknown Restaurant"
//
//                    val address =
//                        tags?.optString("addr:street", "Address not available")
//                            ?: "Address not available"
//
//                    val foods = listOf(
//                        FoodItem("Burger", "$5.99"),
//                        FoodItem("Pizza", "$12.99"),
//                        FoodItem("Fries", "$2.99")
//                    )
//
//                    if (name != "Unknown Restaurant") {
//                        list.add(
//                            Restaurant(
//                                id,
//                                name,
//                                address,
//                                resLat,
//                                resLon,
//                                foods
//                            )
//                        )
//                    }
//                }
//
//                list
//
//            } catch (e: Exception) {
//                e.printStackTrace()
//                emptyList()
//            }
//        }
//    }
//}
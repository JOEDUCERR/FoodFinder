package com.example.foodfinderfinal1

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OverpassApiClient {

    private val MIRRORS = listOf(
        "https://lz4.overpass-api.de/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.osm.ch/api/interpreter"
    )
    private const val TAG = "OverpassApiClient"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"

    private val memCache = mutableMapOf<String, List<Restaurant>>()

    // ── Entry point ────────────────────────────────────────────────────────────
    suspend fun getRestaurants(searchQuery: String): List<Restaurant> =
        withContext(Dispatchers.IO) {
            val query = searchQuery.trim().ifBlank { "New Delhi" }
            val key   = query.lowercase()

            memCache[key]?.let { return@withContext it }

            val geo = geocodeCity(query)
                ?: return@withContext emptyList()

            // Area query covers the entire city boundary.
            // Radius query (5 km) is the fallback for POIs / villages that aren't OSM relations.
            val overpassQuery = if (geo.relationId != null)
                buildAreaQuery(geo.relationId)
            else
                buildRadiusQuery(geo.lat, geo.lon, radius = 5000)

            val result = fetchFirstMirrorWins(overpassQuery, geo.displayName)
            if (result.isNotEmpty()) memCache[key] = result
            result
        }

    // ── Geocode ────────────────────────────────────────────────────────────────
    private data class GeoResult(
        val lat: String,
        val lon: String,
        val relationId: Long?,   // OSM relation ID → becomes Overpass area ID
        val displayName: String
    )

    /**
     * Three-pass geocode: city → settlement → unrestricted.
     * Each pass is a separate Nominatim call so we don't miss
     * cities that aren't tagged as "city" in OSM.
     */
    private fun geocodeCity(query: String): GeoResult? =
        nominatim(query, "city")
            ?: nominatim(query, "settlement")
            ?: nominatim(query, null)

    private fun nominatim(query: String, featureType: String?): GeoResult? {
        return try {
            val encoded   = URLEncoder.encode(query, "UTF-8")
            val typeParam = if (featureType != null) "&featuretype=$featureType" else ""
            val url = "https://nominatim.openstreetmap.org/search" +
                    "?q=$encoded&format=json&limit=1$typeParam"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", UA)
                connectTimeout = 8_000; readTimeout = 8_000
            }
            if (conn.responseCode != 200) return null
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            if (arr.length() == 0) return null
            val obj = arr.getJSONObject(0)
            // Only OSM *relations* map to Overpass areas (nodes/ways do not)
            val osmType    = obj.optString("osm_type")
            val osmId      = obj.optLong("osm_id", -1L)
            val relationId = if (osmType == "relation" && osmId > 0) osmId else null
            GeoResult(obj.getString("lat"), obj.getString("lon"),
                relationId, obj.optString("display_name", query))
        } catch (e: Exception) { null }
    }

    // ── Query builders ─────────────────────────────────────────────────────────
    /**
     * Area query — searches WITHIN the city's administrative boundary.
     * Overpass area IDs = OSM relation ID + 3,600,000,000
     */
    private fun buildAreaQuery(relationId: Long): String {
        val areaId = 3_600_000_000L + relationId
        return """
            [out:json][timeout:20];
            area($areaId)->.city;
            (
              node["amenity"="restaurant"](area.city);
              node["amenity"="fast_food"](area.city);
              node["amenity"="cafe"](area.city);
              way["amenity"="restaurant"](area.city);
              way["amenity"="fast_food"](area.city);
              way["amenity"="cafe"](area.city);
            );
            out center 100;
        """.trimIndent()
    }

    /** Fallback when Nominatim didn't return a relation (e.g. small village). */
    private fun buildRadiusQuery(lat: String, lon: String, radius: Int) = """
        [out:json][timeout:20];
        (
          node["amenity"="restaurant"](around:$radius,$lat,$lon);
          node["amenity"="fast_food"](around:$radius,$lat,$lon);
          node["amenity"="cafe"](around:$radius,$lat,$lon);
          way["amenity"="restaurant"](around:$radius,$lat,$lon);
          way["amenity"="fast_food"](around:$radius,$lat,$lon);
        );
        out center 100;
    """.trimIndent()

    // ── Parallel fetch — first mirror to respond wins ──────────────────────────
    /**
     * All 4 mirrors are hit simultaneously in separate IO coroutines.
     * The first one to return a non-empty list is used; the rest are cancelled.
     * Total wait time ≈ time of the *fastest* healthy mirror, not the slowest.
     */
    private suspend fun fetchFirstMirrorWins(
        overpassQuery: String,
        displayName: String
    ): List<Restaurant> {
        val postData = "data=${URLEncoder.encode(overpassQuery, "UTF-8")}"
        // Channel with capacity 1 — only the winning result matters
        val winner   = Channel<List<Restaurant>>(1)
        val scope    = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val jobs = MIRRORS.map { url ->
            scope.launch {
                try {
                    val result = fetchFromMirror(url, postData, displayName)
                    if (!result.isNullOrEmpty()) winner.trySend(result)
                } catch (e: Exception) {
                    Log.w(TAG, "$url → ${e.message}")
                }
            }
        }

        return try {
            withTimeoutOrNull(30_000L) { winner.receive() } ?: emptyList()
        } finally {
            jobs.forEach { it.cancel() }
            scope.cancel()
            winner.close()
        }
    }

    private fun fetchFromMirror(
        mirrorUrl: String,
        postData: String,
        displayName: String
    ): List<Restaurant>? {
        return try {
            val conn = (URL(mirrorUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 8_000; readTimeout = 25_000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.use { it.write(postData.toByteArray()) }
            if (conn.responseCode != 200) return null
            val elements = JSONObject(
                conn.inputStream.bufferedReader().use { it.readText() }
            ).optJSONArray("elements") ?: return null
            parseElements(elements, displayName).takeIf { it.isNotEmpty() }
        } catch (e: Exception) { null }
    }

    // ── Parsing ────────────────────────────────────────────────────────────────
    private fun parseElements(elements: JSONArray, resolvedName: String): List<Restaurant> {
        val list = mutableListOf<Restaurant>()
        for (i in 0 until elements.length()) {
            val node   = elements.getJSONObject(i)
            val tags   = node.optJSONObject("tags") ?: continue
            val name   = tags.optString("name", "").trim()
            if (name.isEmpty()) continue

            val center = node.optJSONObject("center")
            val lat    = node.optDouble("lat", center?.optDouble("lat") ?: 0.0)
            val lon    = node.optDouble("lon", center?.optDouble("lon") ?: 0.0)

            val cuisine = tags.optString("cuisine", "Food")
            val street  = tags.optString("addr:street", "")
            val city    = tags.optString("addr:city", "")
            val address = when {
                street.isNotEmpty() && city.isNotEmpty() -> "$street, $city"
                street.isNotEmpty() -> street
                city.isNotEmpty()   -> city
                else -> resolvedName.split(",").take(2).joinToString(", ")
            }

            list.add(Restaurant(
                id           = node.optString("id"),
                name         = name,
                address      = address,
                lat          = lat,
                lon          = lon,
                foodItems    = generateCuisineMenu(cuisine, name),
                cuisineTag   = cuisine,
                phone        = tags.optString("contact:phone",
                    tags.optString("phone", "")),
                website      = tags.optString("website",
                    tags.optString("contact:website", "")),
                openingHours = tags.optString("opening_hours", ""),
                rating       = (3..5).random().toFloat()
            ))
        }
        return list.distinctBy { it.name }
    }

    // ── Reverse geocode ────────────────────────────────────────────────────────
    suspend fun reverseGeocode(lat: Double, lon: Double): String =
        withContext(Dispatchers.IO) {
            try {
                val conn = (URL(
                    "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&zoom=10"
                ).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", UA)
                    connectTimeout = 8_000; readTimeout = 8_000
                }
                val addr = JSONObject(
                    conn.inputStream.bufferedReader().use { it.readText() }
                ).optJSONObject("address")
                addr?.optString("city")?.takeIf    { it.isNotBlank() }
                    ?: addr?.optString("town")?.takeIf    { it.isNotBlank() }
                    ?: addr?.optString("village")?.takeIf { it.isNotBlank() }
                    ?: "Current Location"
            } catch (e: Exception) { "Current Location" }
        }

    // ── Menu generator ─────────────────────────────────────────────────────────
    private fun generateCuisineMenu(cuisine: String, name: String): List<FoodItem> {
        val c = cuisine.lowercase(); val n = name.lowercase()
        return when {
            c.contains("pizza")  || n.contains("pizza")  ->
                listOf(FoodItem("Margherita Pizza", "₹349"), FoodItem("Farmhouse Pizza", "₹449"))
            c.contains("indian") || c.contains("biryani") ->
                listOf(FoodItem("Special Biryani", "₹299"), FoodItem("Paneer Tikka", "₹249"))
            c.contains("burger") ->
                listOf(FoodItem("Cheese Burger", "₹149"), FoodItem("Veggie Burger", "₹129"))
            c.contains("chinese") || c.contains("asian") ->
                listOf(FoodItem("Fried Rice", "₹199"), FoodItem("Hakka Noodles", "₹179"))
            c.contains("cafe") || c.contains("coffee") ->
                listOf(FoodItem("Cappuccino", "₹129"), FoodItem("Club Sandwich", "₹199"))
            else ->
                listOf(FoodItem("House Special", "₹199"), FoodItem("Chef's Combo", "₹399"))
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
//import java.io.IOException
//import java.net.HttpURLConnection
//import java.net.URL
//import java.net.URLEncoder
//
//object OverpassApiClient {
//
//    // prioritizing lz4 and adding more robust mirrors
//    private val OVERPASS_MIRRORS = listOf(
//        "https://lz4.overpass-api.de/api/interpreter",
//        "https://overpass-api.de/api/interpreter",
//        "https://overpass.kumi.systems/api/interpreter",
//        "https://overpass.osm.ch/api/interpreter"
//    )
//
//    private const val TAG = "OverpassApiClient"
//    // Using a standard browser User-Agent to prevent 403/504 throttling
//    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
//
//    private val cache = mutableMapOf<String, List<Restaurant>>()
//
//    suspend fun getRestaurants(searchQuery: String): List<Restaurant> {
//        return withContext(Dispatchers.IO) {
//            val query = searchQuery.trim().ifBlank { "New Delhi" }
//            val cacheKey = query.lowercase()
//
//            cache[cacheKey]?.let {
//                Log.d(TAG, "Cache hit for '$query'")
//                return@withContext it
//            }
//
//            // ── Geocode ───────────────────────────────────────────────────
//            val resultTriple = geocode(query) ?: run {
//                val fallback = query.replace(Regex("(?i)restaurants?|food|near|in|around"), "").trim()
//                if (fallback != query) geocode(fallback) else null
//            }
//
//            if (resultTriple == null) return@withContext emptyList()
//            val (lat, lon, resolvedName) = resultTriple
//
//            // ── Overpass Query (Optimized for Speed) ──────────────────────
//            // Radius 1500m is the "sweet spot" for speed vs results in cities.
//            val radius = 1500
//            val overpassQuery = """
//                [out:json][timeout:25];
//                (
//                  node["amenity"="restaurant"](around:$radius,$lat,$lon);
//                  node["amenity"="fast_food"](around:$radius,$lat,$lon);
//                  node["amenity"="cafe"](around:$radius,$lat,$lon);
//                  way["amenity"="restaurant"](around:$radius,$lat,$lon);
//                  way["amenity"="fast_food"](around:$radius,$lat,$lon);
//                  way["amenity"="cafe"](around:$radius,$lat,$lon);
//                );
//                out center 60;
//            """.trimIndent()
//
//            val postData = "data=${URLEncoder.encode(overpassQuery, "UTF-8")}"
//            var lastError: Exception? = null
//
//            for (baseUrl in OVERPASS_MIRRORS) {
//                try {
//                    val conn = (URL(baseUrl).openConnection() as HttpURLConnection).apply {
//                        requestMethod = "POST"
//                        doOutput = true
//                        connectTimeout = 10000
//                        readTimeout = 30000
//                        setRequestProperty("User-Agent", USER_AGENT)
//                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
//                    }
//
//                    conn.outputStream.use { it.write(postData.toByteArray()) }
//
//                    if (conn.responseCode == 200) {
//                        val response = conn.inputStream.bufferedReader().use { it.readText() }
//                        val elements = JSONObject(response).optJSONArray("elements") ?: JSONArray()
//                        val result = parseElements(elements, resolvedName)
//                        if (result.isNotEmpty()) cache[cacheKey] = result
//                        return@withContext result
//                    } else {
//                        Log.w(TAG, "Mirror $baseUrl failed with code ${conn.responseCode}")
//                    }
//                } catch (e: Exception) {
//                    Log.e(TAG, "Mirror $baseUrl error: ${e.message}")
//                    lastError = e
//                }
//            }
//
//            throw lastError ?: IOException("Network timeout. Please try again in a moment.")
//        }
//    }
//
//    private fun parseElements(elements: JSONArray, resolvedName: String): List<Restaurant> {
//        val list = mutableListOf<Restaurant>()
//        for (i in 0 until elements.length()) {
//            val node = elements.getJSONObject(i)
//            val tags = node.optJSONObject("tags") ?: continue
//            val name = tags.optString("name", "").trim()
//            if (name.isEmpty()) continue
//
//            val resLat = node.optDouble("lat", node.optJSONObject("center")?.optDouble("lat") ?: 0.0)
//            val resLon = node.optDouble("lon", node.optJSONObject("center")?.optDouble("lon") ?: 0.0)
//
//            val cuisine = tags.optString("cuisine", "Food")
//            val city = tags.optString("addr:city", "")
//            val street = tags.optString("addr:street", "")
//            val address = if (street.isNotEmpty()) "$street, $city".trim(',', ' ')
//                          else resolvedName.split(",").take(2).joinToString(", ")
//
//            list.add(Restaurant(
//                id = node.optString("id"),
//                name = name,
//                address = address,
//                lat = resLat,
//                lon = resLon,
//                foodItems = generateCuisineMenu(cuisine, name),
//                cuisineTag = cuisine,
//                rating = (3..5).random().toFloat() // Fallback rating if none in OSM
//            ))
//        }
//        return list.distinctBy { it.name }
//    }
//
//    private fun geocode(query: String): Triple<String, String, String>? {
//        return try {
//            val url = "https://nominatim.openstreetmap.org/search?q=${URLEncoder.encode(query, "UTF-8")}&format=json&limit=1"
//            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
//                setRequestProperty("User-Agent", USER_AGENT)
//                connectTimeout = 8000
//                readTimeout = 8000
//            }
//            if (conn.responseCode != 200) return null
//            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
//            if (arr.length() == 0) return null
//            val obj = arr.getJSONObject(0)
//            Triple(obj.getString("lat"), obj.getString("lon"), obj.optString("display_name", query))
//        } catch (e: Exception) {
//            null
//        }
//    }
//
//    suspend fun reverseGeocode(lat: Double, lon: Double): String {
//        return withContext(Dispatchers.IO) {
//            try {
//                val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&zoom=10"
//                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
//                    setRequestProperty("User-Agent", USER_AGENT)
//                }
//                val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
//                val addr = obj.optJSONObject("address")
//                addr?.optString("city") ?: addr?.optString("town") ?: addr?.optString("village") ?: "Current Location"
//            } catch (e: Exception) { "Current Location" }
//        }
//    }
//
//    private fun generateCuisineMenu(cuisine: String, name: String): List<FoodItem> {
//        val c = cuisine.lowercase(); val n = name.lowercase()
//        return when {
//            c.contains("pizza") || n.contains("pizza") -> listOf(FoodItem("Margherita Pizza", "₹349"), FoodItem("Farmhouse Pizza", "₹449"))
//            c.contains("indian") || c.contains("biryani") -> listOf(FoodItem("Special Biryani", "₹299"), FoodItem("Paneer Tikka", "₹249"))
//            c.contains("burger") -> listOf(FoodItem("Cheese Burger", "₹149"), FoodItem("Veggie Burger", "₹129"))
//            else -> listOf(FoodItem("House Special", "₹199"), FoodItem("Chef's Combo", "₹399"))
//        }
//    }
//}

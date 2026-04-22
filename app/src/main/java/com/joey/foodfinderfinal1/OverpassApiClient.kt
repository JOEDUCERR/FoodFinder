package com.joey.foodfinderfinal1

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OverpassApiClient {

//    Coroutines (asynchronous programming)
//    Caching (performance optimization)
//    Rate limiting (API safety)
//    REST API handling
//    JSON parsing
//    Parallel execution
//    Fallback mechanisms

    private val MIRRORS = listOf(
        "https://lz4.overpass-api.de/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.osm.ch/api/interpreter"
    )
    private const val TAG = "OverpassApiClient"
    private const val UA  = "FoodFinderApp/1.0 (Android; contact=yourname@email.com)"
    //  ^^^ IMPORTANT: Nominatim requires a real identifying User-Agent.
    //      A fake Chrome UA is actually against their policy and can get you blocked faster.
    //      Replace yourname@email.com with your actual email.

    // ── In-memory caches (survive for the app session) ─────────────────────────
    private val restaurantCache = mutableMapOf<String, List<Restaurant>>()
    private val geocodeCache    = mutableMapOf<String, GeoResult>()   // key = lowercase query
    private val reverseCache    = mutableMapOf<String, String>()       // key = "lat,lon" rounded

    // ── Nominatim rate-limit guard ──────────────────────────────────────────────
    // Nominatim policy: max 1 request per second. We enforce a 1.1s gap.
    private var lastNominatimCallMs = 0L
    private val nominatimLock = Any()

    private fun waitForNominatimSlot() {
        synchronized(nominatimLock) {
            val now     = System.currentTimeMillis()
            val elapsed = now - lastNominatimCallMs
            if (elapsed < 1100L) {
                Thread.sleep(1100L - elapsed)
            }
            lastNominatimCallMs = System.currentTimeMillis()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────────
    suspend fun getRestaurants(searchQuery: String): List<Restaurant> =
        withContext(Dispatchers.IO) {
            val query = searchQuery.trim().ifBlank { "New Delhi" }
            val key   = query.lowercase()

            Log.d(TAG, "=== getRestaurants: '$query' ===")

            restaurantCache[key]?.let {
                Log.d(TAG, "Restaurant cache hit: ${it.size} results")
                return@withContext it
            }

            val geo = geocodeCity(query)
            if (geo == null) {
                Log.e(TAG, "Geocode FAILED for '$query'")
                return@withContext emptyList()
            }
            Log.d(TAG, "Geocode ok: lat=${geo.lat} lon=${geo.lon} relation=${geo.relationId}")

            val overpassQuery = if (geo.relationId != null)
                buildAreaQuery(geo.relationId)
            else
                buildRadiusQuery(geo.lat, geo.lon, radius = 8000)

            val result = fetchFirstMirrorWins(overpassQuery, geo.displayName)
            Log.d(TAG, "Final result: ${result.size} restaurants")

            if (result.isNotEmpty()) restaurantCache[key] = result
            result
        }

    // ─────────────────────────────────────────────────────────────────────────────
    // Geocode — with in-memory cache so we NEVER hit Nominatim twice for same city
    // ─────────────────────────────────────────────────────────────────────────────
    private data class GeoResult(
        val lat: String,
        val lon: String,
        val relationId: Long?,
        val displayName: String
    )

    private fun geocodeCity(query: String): GeoResult? {
        val key = query.lowercase().trim()

        // 1. Check geocode cache first — avoids ALL Nominatim calls for repeated queries
        geocodeCache[key]?.let {
            Log.d(TAG, "Geocode cache hit for '$query'")
            return it
        }

        // 2. Single Nominatim call with limit=5 so we can pick the best result
        //    (no more 2-3 sequential calls that burn rate limit)
        val result = nominatim(query)
        if (result != null) {
            geocodeCache[key] = result
            return result
        }

        // 3. One retry after a 2-second pause (handles transient 429s)
        Log.w(TAG, "Geocode first attempt failed, retrying after 2s...")
        Thread.sleep(2000L)
        val retry = nominatim(query)
        if (retry != null) {
            geocodeCache[key] = retry
        }
        return retry
    }

    private fun nominatim(query: String): GeoResult? {
        return try {
            waitForNominatimSlot()   // enforce 1-req/sec policy

            val encoded = URLEncoder.encode(query, "UTF-8")
            // limit=5 so we can choose the best OSM relation from the results
            val url = "https://nominatim.openstreetmap.org/search" +
                    "?q=$encoded&format=json&limit=5&addressdetails=1"

            Log.d(TAG, "Nominatim → $url")

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", UA)
                connectTimeout = 12_000
                readTimeout    = 12_000
            }

            val code = conn.responseCode
            Log.d(TAG, "Nominatim response: $code")

            if (code == 429) {
                Log.e(TAG, "Nominatim rate-limited (429) — sleeping 5s")
                Thread.sleep(5_000L)
                return null   // caller will retry once
            }
            if (code != 200) {
                Log.e(TAG, "Nominatim non-200: $code")
                return null
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "Nominatim body (first 300): ${body.take(300)}")

            val arr = JSONArray(body)
            if (arr.length() == 0) {
                Log.w(TAG, "Nominatim: empty result for '$query'")
                return null
            }

            // Prefer OSM relations (they map to Overpass areas covering the whole city)
            // Fall back to the first result if no relation found
            var best: JSONObject? = null
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("osm_type") == "relation") {
                    best = obj
                    break
                }
            }
            if (best == null) best = arr.getJSONObject(0)

            val osmType    = best.optString("osm_type")
            val osmId      = best.optLong("osm_id", -1L)
            val relationId = if (osmType == "relation" && osmId > 0) osmId else null

            Log.d(TAG, "Best result: type=$osmType id=$osmId relation=$relationId name=${best.optString("display_name")}")

            GeoResult(
                best.getString("lat"),
                best.getString("lon"),
                relationId,
                best.optString("display_name", query)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Nominatim exception: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Overpass query builders searches entire city
    // ─────────────────────────────────────────────────────────────────────────────
    private fun buildAreaQuery(relationId: Long): String {
        val areaId = 3_600_000_000L + relationId
        return """
            [out:json][timeout:30];
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

    //searches lat and lon
    private fun buildRadiusQuery(lat: String, lon: String, radius: Int) = """
        [out:json][timeout:30];
        (
          node["amenity"="restaurant"](around:$radius,$lat,$lon);
          node["amenity"="fast_food"](around:$radius,$lat,$lon);
          node["amenity"="cafe"](around:$radius,$lat,$lon);
          way["amenity"="restaurant"](around:$radius,$lat,$lon);
          way["amenity"="fast_food"](around:$radius,$lat,$lon);
          way["amenity"="cafe"](around:$radius,$lat,$lon);
        );
        out center 100;
    """.trimIndent()

    // ─────────────────────────────────────────────────────────────────────────────
    // Parallel mirror fetch — first non-empty result wins
    // ─────────────────────────────────────────────────────────────────────────────
    private suspend fun fetchFirstMirrorWins(
        overpassQuery: String,
        displayName: String
    ): List<Restaurant> {
        val postData = "data=${URLEncoder.encode(overpassQuery, "UTF-8")}"
        val winner   = Channel<List<Restaurant>>(1)
        val scope    = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val jobs = MIRRORS.map { url ->
            scope.launch {
                try {
                    Log.d(TAG, "Trying mirror: $url")
                    val result = fetchFromMirror(url, postData, displayName)
                    if (!result.isNullOrEmpty()) {
                        Log.d(TAG, "Mirror $url won with ${result.size} results")
                        winner.trySend(result)
                    } else {
                        Log.w(TAG, "Mirror $url: null or empty")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Mirror $url exception: ${e.message}")
                }
            }
        }

        return try {
            withTimeoutOrNull(45_000L) { winner.receive() } ?: run {
                Log.e(TAG, "All mirrors failed/timed out")
                emptyList()
            }
        } finally {
            jobs.forEach { it.cancel() }
            scope.cancel()
            winner.close()
        }
    }

    //main way to call restaurants
    private fun fetchFromMirror(
        mirrorUrl: String,
        postData: String,
        displayName: String
    ): List<Restaurant>? {
        return try {
            val conn = (URL(mirrorUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput      = true
                connectTimeout = 12_000
                readTimeout    = 35_000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.use { it.write(postData.toByteArray()) }

            val code = conn.responseCode
            Log.d(TAG, "Mirror $mirrorUrl → $code")
            if (code != 200) return null

            val body     = conn.inputStream.bufferedReader().use { it.readText() }
            val elements = JSONObject(body).optJSONArray("elements") ?: return null

            Log.d(TAG, "Mirror $mirrorUrl: ${elements.length()} elements")
            parseElements(elements, displayName).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Mirror $mirrorUrl: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Parse Overpass elements → Restaurant list (api data to res objects)
    // ─────────────────────────────────────────────────────────────────────────────
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
                phone        = tags.optString("contact:phone", tags.optString("phone", "")),
                website      = tags.optString("website", tags.optString("contact:website", "")),
                openingHours = tags.optString("opening_hours", ""),
                rating       = (3..5).random().toFloat()
            ))
        }
        return list.distinctBy { it.name }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Reverse geocode — cached so it only hits Nominatim ONCE per location
    // ─────────────────────────────────────────────────────────────────────────────
    suspend fun reverseGeocode(lat: Double, lon: Double): String =
        withContext(Dispatchers.IO) {
            // Round to ~1km grid so nearby locations share a cache entry
            val cacheKey = "${"%.2f".format(lat)},${"%.2f".format(lon)}"
            reverseCache[cacheKey]?.let {
                Log.d(TAG, "Reverse geocode cache hit: $it")
                return@withContext it
            }

            try {
                waitForNominatimSlot()

                val conn = (URL(
                    "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&zoom=10"
                ).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", UA)
                    connectTimeout = 10_000
                    readTimeout    = 10_000
                }

                if (conn.responseCode != 200) return@withContext "New Delhi"

                val addr = JSONObject(
                    conn.inputStream.bufferedReader().use { it.readText() }
                ).optJSONObject("address")

                val city = addr?.optString("city")?.takeIf    { it.isNotBlank() }
                    ?: addr?.optString("town")?.takeIf         { it.isNotBlank() }
                    ?: addr?.optString("village")?.takeIf      { it.isNotBlank() }
                    ?: addr?.optString("county")?.takeIf       { it.isNotBlank() }
                    ?: "New Delhi"

                reverseCache[cacheKey] = city
                Log.d(TAG, "Reverse geocode: ($lat,$lon) → $city")
                city
            } catch (e: Exception) {
                Log.e(TAG, "reverseGeocode exception: ${e.message}")
                "New Delhi"
            }
        }

    // ─────────────────────────────────────────────────────────────────────────────
    // Menu generator
    // ─────────────────────────────────────────────────────────────────────────────
    private fun generateCuisineMenu(cuisine: String, name: String): List<FoodItem> {
        val c = cuisine.lowercase()
        val n = name.lowercase()
        return when {
            c.contains("pizza")   || n.contains("pizza")   ->
                listOf(FoodItem("Margherita Pizza", "₹349"), FoodItem("Farmhouse Pizza", "₹449"))
            c.contains("indian")  || c.contains("biryani") ->
                listOf(FoodItem("Special Biryani", "₹299"), FoodItem("Paneer Tikka", "₹249"))
            c.contains("burger")  ->
                listOf(FoodItem("Cheese Burger", "₹149"), FoodItem("Veggie Burger", "₹129"))
            c.contains("chinese") || c.contains("asian")   ->
                listOf(FoodItem("Fried Rice", "₹199"), FoodItem("Hakka Noodles", "₹179"))
            c.contains("cafe")    || c.contains("coffee")  ->
                listOf(FoodItem("Cappuccino", "₹129"), FoodItem("Club Sandwich", "₹199"))
            else ->
                listOf(FoodItem("House Special", "₹199"), FoodItem("Chef's Combo", "₹399"))
        }
    }
}



//package com.joey.foodfinderfinal1
//
//import android.util.Log
//import kotlinx.coroutines.*
//import kotlinx.coroutines.channels.Channel
//import org.json.JSONArray
//import org.json.JSONObject
//import java.net.HttpURLConnection
//import java.net.URL
//import java.net.URLEncoder
//
//object OverpassApiClient {
//
//    private val MIRRORS = listOf(
//        "https://lz4.overpass-api.de/api/interpreter",
//        "https://overpass-api.de/api/interpreter",
//        "https://overpass.kumi.systems/api/interpreter",
//        "https://overpass.osm.ch/api/interpreter"
//    )
//    private const val TAG = "OverpassApiClient"
//    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
//
//    private val memCache = mutableMapOf<String, List<Restaurant>>()
//
//    // ── Entry point ────────────────────────────────────────────────────────────
//    suspend fun getRestaurants(searchQuery: String): List<Restaurant> =
//        withContext(Dispatchers.IO) {
//            val query = searchQuery.trim().ifBlank { "New Delhi" }
//            val key   = query.lowercase()
//
//            Log.d(TAG, "=== getRestaurants called with: '$query' ===")
//
//            memCache[key]?.let {
//                Log.d(TAG, "Cache hit, returning ${it.size} results")
//                return@withContext it
//            }
//
//            Log.d(TAG, "No cache hit, starting geocode...")
//            val geo = geocodeCity(query)
//
//            if (geo == null) {
//                Log.e(TAG, "Geocode FAILED for '$query' — returning empty list")
//                return@withContext emptyList()
//            }
//
//            Log.d(TAG, "Geocode success: lat=${geo.lat}, lon=${geo.lon}, relationId=${geo.relationId}, name=${geo.displayName}")
//
//            val overpassQuery = if (geo.relationId != null) {
//                Log.d(TAG, "Using AREA query with relationId=${geo.relationId}")
//                buildAreaQuery(geo.relationId)
//            } else {
//                Log.d(TAG, "Using RADIUS query (no relation found)")
//                buildRadiusQuery(geo.lat, geo.lon, radius = 5000)
//            }
//
//            Log.d(TAG, "Fetching from mirrors...")
//            val result = fetchFirstMirrorWins(overpassQuery, geo.displayName)
//            Log.d(TAG, "Final result: ${result.size} restaurants")
//
//            if (result.isNotEmpty()) memCache[key] = result
//            result
//        }
//
//    // ── Geocode ────────────────────────────────────────────────────────────────
//    private data class GeoResult(
//        val lat: String,
//        val lon: String,
//        val relationId: Long?,
//        val displayName: String
//    )
//
//    private fun geocodeCity(query: String): GeoResult? {
//        Log.d(TAG, "Geocoding: $query")
//        val r1 = nominatim(query, null)
//        if (r1 != null) return r1
//
//        // Try with "city" appended as fallback for ambiguous names
//        val r2 = nominatim("$query city", null)
//        if (r2 != null) return r2
//
//        Log.e(TAG, "Geocode failed for '$query'")
//        return null
//    }
//
//    private fun nominatim(query: String, featureType: String?): GeoResult? {
//        return try {
//            val encoded = URLEncoder.encode(query, "UTF-8")
//            // featuretype is NOT a valid Nominatim param — use countrycodes + addressdetails instead
//            val url = "https://nominatim.openstreetmap.org/search" +
//                    "?q=$encoded&format=json&limit=3&addressdetails=1"
//
//            Log.d(TAG, "Nominatim URL: $url")
//
//            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
//                setRequestProperty("User-Agent", UA)
//                connectTimeout = 10_000; readTimeout = 10_000
//            }
//
//            val responseCode = conn.responseCode
//            if (responseCode != 200) {
//                Log.e(TAG, "Nominatim returned non-200: $responseCode")
//                return null
//            }
//
//            val body = conn.inputStream.bufferedReader().use { it.readText() }
//            Log.d(TAG, "Nominatim body: $body")
//
//            val arr = JSONArray(body)
//            if (arr.length() == 0) {
//                Log.w(TAG, "Nominatim returned empty array for: $query")
//                return null
//            }
//
//            // Pick the best result: prefer relations, then anything with a place_rank <= 16 (city level)
//            var best: JSONObject? = null
//            for (i in 0 until arr.length()) {
//                val obj = arr.getJSONObject(i)
//                if (obj.optString("osm_type") == "relation") {
//                    best = obj
//                    break
//                }
//            }
//            if (best == null) best = arr.getJSONObject(0)
//
//            val osmType    = best.optString("osm_type")
//            val osmId      = best.optLong("osm_id", -1L)
//            val relationId = if (osmType == "relation" && osmId > 0) osmId else null
//
//            Log.d(TAG, "Nominatim parsed: osmType=$osmType, osmId=$osmId, relationId=$relationId")
//
//            GeoResult(
//                best.getString("lat"),
//                best.getString("lon"),
//                relationId,
//                best.optString("display_name", query)
//            )
//        } catch (e: Exception) {
//            Log.e(TAG, "Nominatim exception: ${e.javaClass.simpleName}: ${e.message}")
//            null
//        }
//    }
//
//    // ── Query builders ─────────────────────────────────────────────────────────
//    private fun buildAreaQuery(relationId: Long): String {
//        val areaId = 3_600_000_000L + relationId
//        return """
//        [out:json][timeout:30];
//        area($areaId)->.city;
//        (
//          node["amenity"="restaurant"](area.city);
//          node["amenity"="fast_food"](area.city);
//          node["amenity"="cafe"](area.city);
//          way["amenity"="restaurant"](area.city);
//          way["amenity"="fast_food"](area.city);
//          way["amenity"="cafe"](area.city);
//        );
//        out center 100;
//    """.trimIndent()
//    }
//
//    private fun buildRadiusQuery(lat: String, lon: String, radius: Int) = """
//        [out:json][timeout:20];
//        (
//          node["amenity"="restaurant"](around:$radius,$lat,$lon);
//          node["amenity"="fast_food"](around:$radius,$lat,$lon);
//          node["amenity"="cafe"](around:$radius,$lat,$lon);
//          way["amenity"="restaurant"](around:$radius,$lat,$lon);
//          way["amenity"="fast_food"](around:$radius,$lat,$lon);
//        );
//        out center 100;
//    """.trimIndent()
//
//    // ── Parallel fetch — first mirror to respond wins ──────────────────────────
//    private suspend fun fetchFirstMirrorWins(
//        overpassQuery: String,
//        displayName: String
//    ): List<Restaurant> {
//        val postData = "data=${URLEncoder.encode(overpassQuery, "UTF-8")}"
//        val winner   = Channel<List<Restaurant>>(1)
//        val scope    = CoroutineScope(Dispatchers.IO + SupervisorJob())
//
//        val jobs = MIRRORS.map { url ->
//            scope.launch {
//                try {
//                    Log.d(TAG, "Trying mirror: $url")
//                    val result = fetchFromMirror(url, postData, displayName)
//                    if (!result.isNullOrEmpty()) {
//                        Log.d(TAG, "Mirror $url succeeded with ${result.size} results")
//                        winner.trySend(result)
//                    } else {
//                        Log.w(TAG, "Mirror $url returned null or empty")
//                    }
//                } catch (e: Exception) {
//                    Log.w(TAG, "Mirror $url exception: ${e.message}")
//                }
//            }
//        }
//
//        return try {
//            withTimeoutOrNull(45_000L) { winner.receive() } ?: run {
//                Log.e(TAG, "All mirrors timed out or returned empty")
//                emptyList()
//            }
//        } finally {
//            jobs.forEach { it.cancel() }
//            scope.cancel()
//            winner.close()
//        }
//    }
//
//    private fun fetchFromMirror(
//        mirrorUrl: String,
//        postData: String,
//        displayName: String
//    ): List<Restaurant>? {
//        return try {
//            val conn = (URL(mirrorUrl).openConnection() as HttpURLConnection).apply {
//                requestMethod = "POST"; doOutput = true
////                connectTimeout = 8_000; readTimeout = 25_000
//                connectTimeout = 10_000; readTimeout = 35_000
//                setRequestProperty("User-Agent", UA)
//                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
//            }
//            conn.outputStream.use { it.write(postData.toByteArray()) }
//
//            val responseCode = conn.responseCode
//            Log.d(TAG, "Mirror $mirrorUrl response code: $responseCode")
//
//            if (responseCode != 200) {
//                Log.e(TAG, "Mirror $mirrorUrl returned non-200: $responseCode")
//                return null
//            }
//
//            val body = conn.inputStream.bufferedReader().use { it.readText() }
//            Log.d(TAG, "Mirror $mirrorUrl body length: ${body.length} chars")
//
//            val elements = JSONObject(body).optJSONArray("elements")
//            if (elements == null) {
//                Log.e(TAG, "Mirror $mirrorUrl: no 'elements' key in response")
//                return null
//            }
//
//            Log.d(TAG, "Mirror $mirrorUrl: elements count = ${elements.length()}")
//            val parsed = parseElements(elements, displayName)
//            Log.d(TAG, "Mirror $mirrorUrl: parsed ${parsed.size} named restaurants")
//
//            parsed.takeIf { it.isNotEmpty() }
//        } catch (e: Exception) {
//            Log.e(TAG, "Mirror $mirrorUrl exception: ${e.javaClass.simpleName}: ${e.message}")
//            null
//        }
//    }
//
//    // ── Parsing ────────────────────────────────────────────────────────────────
//    private fun parseElements(elements: JSONArray, resolvedName: String): List<Restaurant> {
//        val list = mutableListOf<Restaurant>()
//        for (i in 0 until elements.length()) {
//            val node   = elements.getJSONObject(i)
//            val tags   = node.optJSONObject("tags") ?: continue
//            val name   = tags.optString("name", "").trim()
//            if (name.isEmpty()) continue
//
//            val center = node.optJSONObject("center")
//            val lat    = node.optDouble("lat", center?.optDouble("lat") ?: 0.0)
//            val lon    = node.optDouble("lon", center?.optDouble("lon") ?: 0.0)
//
//            val cuisine = tags.optString("cuisine", "Food")
//            val street  = tags.optString("addr:street", "")
//            val city    = tags.optString("addr:city", "")
//            val address = when {
//                street.isNotEmpty() && city.isNotEmpty() -> "$street, $city"
//                street.isNotEmpty() -> street
//                city.isNotEmpty()   -> city
//                else -> resolvedName.split(",").take(2).joinToString(", ")
//            }
//
//            list.add(Restaurant(
//                id           = node.optString("id"),
//                name         = name,
//                address      = address,
//                lat          = lat,
//                lon          = lon,
//                foodItems    = generateCuisineMenu(cuisine, name),
//                cuisineTag   = cuisine,
//                phone        = tags.optString("contact:phone",
//                    tags.optString("phone", "")),
//                website      = tags.optString("website",
//                    tags.optString("contact:website", "")),
//                openingHours = tags.optString("opening_hours", ""),
//                rating       = (3..5).random().toFloat()
//            ))
//        }
//        return list.distinctBy { it.name }
//    }
//
//    // ── Reverse geocode ────────────────────────────────────────────────────────
//    suspend fun reverseGeocode(lat: Double, lon: Double): String =
//        withContext(Dispatchers.IO) {
//            try {
//                val conn = (URL(
//                    "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&zoom=10"
//                ).openConnection() as HttpURLConnection).apply {
//                    setRequestProperty("User-Agent", UA)
//                    connectTimeout = 8_000; readTimeout = 8_000
//                }
//                val addr = JSONObject(
//                    conn.inputStream.bufferedReader().use { it.readText() }
//                ).optJSONObject("address")
//                addr?.optString("city")?.takeIf    { it.isNotBlank() }
//                    ?: addr?.optString("town")?.takeIf    { it.isNotBlank() }
//                    ?: addr?.optString("village")?.takeIf { it.isNotBlank() }
//                    ?: "Current Location"
//            } catch (e: Exception) {
//                Log.e(TAG, "reverseGeocode exception: ${e.message}")
//                "Current Location"
//            }
//        }
//
//    // ── Menu generator ─────────────────────────────────────────────────────────
//    private fun generateCuisineMenu(cuisine: String, name: String): List<FoodItem> {
//        val c = cuisine.lowercase(); val n = name.lowercase()
//        return when {
//            c.contains("pizza")  || n.contains("pizza")  ->
//                listOf(FoodItem("Margherita Pizza", "₹349"), FoodItem("Farmhouse Pizza", "₹449"))
//            c.contains("indian") || c.contains("biryani") ->
//                listOf(FoodItem("Special Biryani", "₹299"), FoodItem("Paneer Tikka", "₹249"))
//            c.contains("burger") ->
//                listOf(FoodItem("Cheese Burger", "₹149"), FoodItem("Veggie Burger", "₹129"))
//            c.contains("chinese") || c.contains("asian") ->
//                listOf(FoodItem("Fried Rice", "₹199"), FoodItem("Hakka Noodles", "₹179"))
//            c.contains("cafe") || c.contains("coffee") ->
//                listOf(FoodItem("Cappuccino", "₹129"), FoodItem("Club Sandwich", "₹199"))
//            else ->
//                listOf(FoodItem("House Special", "₹199"), FoodItem("Chef's Combo", "₹399"))
//        }
//    }
//}



//package com.joey.foodfinderfinal1
//
//import android.util.Log
//import kotlinx.coroutines.*
//import kotlinx.coroutines.channels.Channel
//import org.json.JSONArray
//import org.json.JSONObject
//import java.net.HttpURLConnection
//import java.net.URL
//import java.net.URLEncoder
//
//object OverpassApiClient {
//
//    private val MIRRORS = listOf(
//        "https://lz4.overpass-api.de/api/interpreter",
//        "https://overpass-api.de/api/interpreter",
//        "https://overpass.kumi.systems/api/interpreter",
//        "https://overpass.osm.ch/api/interpreter"
//    )
//    private const val TAG = "OverpassApiClient"
//    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
//
//    private val memCache = mutableMapOf<String, List<Restaurant>>()
//
//    // ── Entry point ────────────────────────────────────────────────────────────
//    suspend fun getRestaurants(searchQuery: String): List<Restaurant> =
//        withContext(Dispatchers.IO) {
//            val query = searchQuery.trim().ifBlank { "New Delhi" }
//            val key   = query.lowercase()
//
//            memCache[key]?.let { return@withContext it }
//
//            val geo = geocodeCity(query)
//                ?: return@withContext emptyList()
//
//            // Area query covers the entire city boundary.
//            // Radius query (5 km) is the fallback for POIs / villages that aren't OSM relations.
//            val overpassQuery = if (geo.relationId != null)
//                buildAreaQuery(geo.relationId)
//            else
//                buildRadiusQuery(geo.lat, geo.lon, radius = 5000)
//
//            val result = fetchFirstMirrorWins(overpassQuery, geo.displayName)
//            if (result.isNotEmpty()) memCache[key] = result
//            result
//        }
//
//    // ── Geocode ────────────────────────────────────────────────────────────────
//    private data class GeoResult(
//        val lat: String,
//        val lon: String,
//        val relationId: Long?,   // OSM relation ID → becomes Overpass area ID
//        val displayName: String
//    )
//
//    /**
//     * Three-pass geocode: city → settlement → unrestricted.
//     * Each pass is a separate Nominatim call so we don't miss
//     * cities that aren't tagged as "city" in OSM.
//     */
//    private fun geocodeCity(query: String): GeoResult? =
//        nominatim(query, "city")
//            ?: nominatim(query, "settlement")
//            ?: nominatim(query, null)
//
//    private fun nominatim(query: String, featureType: String?): GeoResult? {
//        return try {
//            val encoded   = URLEncoder.encode(query, "UTF-8")
//            val typeParam = if (featureType != null) "&featuretype=$featureType" else ""
//            val url = "https://nominatim.openstreetmap.org/search" +
//                    "?q=$encoded&format=json&limit=1$typeParam"
//            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
//                setRequestProperty("User-Agent", UA)
//                connectTimeout = 8_000; readTimeout = 8_000
//            }
//            if (conn.responseCode != 200) return null
//            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
//            if (arr.length() == 0) return null
//            val obj = arr.getJSONObject(0)
//            // Only OSM *relations* map to Overpass areas (nodes/ways do not)
//            val osmType    = obj.optString("osm_type")
//            val osmId      = obj.optLong("osm_id", -1L)
//            val relationId = if (osmType == "relation" && osmId > 0) osmId else null
//            GeoResult(obj.getString("lat"), obj.getString("lon"),
//                relationId, obj.optString("display_name", query))
//        } catch (e: Exception) { null }
//    }
//
//    // ── Query builders ─────────────────────────────────────────────────────────
//    /**
//     * Area query — searches WITHIN the city's administrative boundary.
//     * Overpass area IDs = OSM relation ID + 3,600,000,000
//     */
//    private fun buildAreaQuery(relationId: Long): String {
//        val areaId = 3_600_000_000L + relationId
//        return """
//            [out:json][timeout:20];
//            area($areaId)->.city;
//            (
//              node["amenity"="restaurant"](area.city);
//              node["amenity"="fast_food"](area.city);
//              node["amenity"="cafe"](area.city);
//              way["amenity"="restaurant"](area.city);
//              way["amenity"="fast_food"](area.city);
//              way["amenity"="cafe"](area.city);
//            );
//            out center 100;
//        """.trimIndent()
//    }
//
//    /** Fallback when Nominatim didn't return a relation (e.g. small village). */
//    private fun buildRadiusQuery(lat: String, lon: String, radius: Int) = """
//        [out:json][timeout:20];
//        (
//          node["amenity"="restaurant"](around:$radius,$lat,$lon);
//          node["amenity"="fast_food"](around:$radius,$lat,$lon);
//          node["amenity"="cafe"](around:$radius,$lat,$lon);
//          way["amenity"="restaurant"](around:$radius,$lat,$lon);
//          way["amenity"="fast_food"](around:$radius,$lat,$lon);
//        );
//        out center 100;
//    """.trimIndent()
//
//    // ── Parallel fetch — first mirror to respond wins ──────────────────────────
//    /**
//     * All 4 mirrors are hit simultaneously in separate IO coroutines.
//     * The first one to return a non-empty list is used; the rest are cancelled.
//     * Total wait time ≈ time of the *fastest* healthy mirror, not the slowest.
//     */
//    private suspend fun fetchFirstMirrorWins(
//        overpassQuery: String,
//        displayName: String
//    ): List<Restaurant> {
//        val postData = "data=${URLEncoder.encode(overpassQuery, "UTF-8")}"
//        // Channel with capacity 1 — only the winning result matters
//        val winner   = Channel<List<Restaurant>>(1)
//        val scope    = CoroutineScope(Dispatchers.IO + SupervisorJob())
//
//        val jobs = MIRRORS.map { url ->
//            scope.launch {
//                try {
//                    val result = fetchFromMirror(url, postData, displayName)
//                    if (!result.isNullOrEmpty()) winner.trySend(result)
//                } catch (e: Exception) {
//                    Log.w(TAG, "$url → ${e.message}")
//                }
//            }
//        }
//
//        return try {
//            withTimeoutOrNull(30_000L) { winner.receive() } ?: emptyList()
//        } finally {
//            jobs.forEach { it.cancel() }
//            scope.cancel()
//            winner.close()
//        }
//    }
//
//    private fun fetchFromMirror(
//        mirrorUrl: String,
//        postData: String,
//        displayName: String
//    ): List<Restaurant>? {
//        return try {
//            val conn = (URL(mirrorUrl).openConnection() as HttpURLConnection).apply {
//                requestMethod = "POST"; doOutput = true
//                connectTimeout = 8_000; readTimeout = 25_000
//                setRequestProperty("User-Agent", UA)
//                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
//            }
//            conn.outputStream.use { it.write(postData.toByteArray()) }
//            if (conn.responseCode != 200) return null
//            val elements = JSONObject(
//                conn.inputStream.bufferedReader().use { it.readText() }
//            ).optJSONArray("elements") ?: return null
//            parseElements(elements, displayName).takeIf { it.isNotEmpty() }
//        } catch (e: Exception) { null }
//    }
//
//    // ── Parsing ────────────────────────────────────────────────────────────────
//    private fun parseElements(elements: JSONArray, resolvedName: String): List<Restaurant> {
//        val list = mutableListOf<Restaurant>()
//        for (i in 0 until elements.length()) {
//            val node   = elements.getJSONObject(i)
//            val tags   = node.optJSONObject("tags") ?: continue
//            val name   = tags.optString("name", "").trim()
//            if (name.isEmpty()) continue
//
//            val center = node.optJSONObject("center")
//            val lat    = node.optDouble("lat", center?.optDouble("lat") ?: 0.0)
//            val lon    = node.optDouble("lon", center?.optDouble("lon") ?: 0.0)
//
//            val cuisine = tags.optString("cuisine", "Food")
//            val street  = tags.optString("addr:street", "")
//            val city    = tags.optString("addr:city", "")
//            val address = when {
//                street.isNotEmpty() && city.isNotEmpty() -> "$street, $city"
//                street.isNotEmpty() -> street
//                city.isNotEmpty()   -> city
//                else -> resolvedName.split(",").take(2).joinToString(", ")
//            }
//
//            list.add(Restaurant(
//                id           = node.optString("id"),
//                name         = name,
//                address      = address,
//                lat          = lat,
//                lon          = lon,
//                foodItems    = generateCuisineMenu(cuisine, name),
//                cuisineTag   = cuisine,
//                phone        = tags.optString("contact:phone",
//                    tags.optString("phone", "")),
//                website      = tags.optString("website",
//                    tags.optString("contact:website", "")),
//                openingHours = tags.optString("opening_hours", ""),
//                rating       = (3..5).random().toFloat()
//            ))
//        }
//        return list.distinctBy { it.name }
//    }
//
//    // ── Reverse geocode ────────────────────────────────────────────────────────
//    suspend fun reverseGeocode(lat: Double, lon: Double): String =
//        withContext(Dispatchers.IO) {
//            try {
//                val conn = (URL(
//                    "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&zoom=10"
//                ).openConnection() as HttpURLConnection).apply {
//                    setRequestProperty("User-Agent", UA)
//                    connectTimeout = 8_000; readTimeout = 8_000
//                }
//                val addr = JSONObject(
//                    conn.inputStream.bufferedReader().use { it.readText() }
//                ).optJSONObject("address")
//                addr?.optString("city")?.takeIf    { it.isNotBlank() }
//                    ?: addr?.optString("town")?.takeIf    { it.isNotBlank() }
//                    ?: addr?.optString("village")?.takeIf { it.isNotBlank() }
//                    ?: "Current Location"
//            } catch (e: Exception) { "Current Location" }
//        }
//
//    // ── Menu generator ─────────────────────────────────────────────────────────
//    private fun generateCuisineMenu(cuisine: String, name: String): List<FoodItem> {
//        val c = cuisine.lowercase(); val n = name.lowercase()
//        return when {
//            c.contains("pizza")  || n.contains("pizza")  ->
//                listOf(FoodItem("Margherita Pizza", "₹349"), FoodItem("Farmhouse Pizza", "₹449"))
//            c.contains("indian") || c.contains("biryani") ->
//                listOf(FoodItem("Special Biryani", "₹299"), FoodItem("Paneer Tikka", "₹249"))
//            c.contains("burger") ->
//                listOf(FoodItem("Cheese Burger", "₹149"), FoodItem("Veggie Burger", "₹129"))
//            c.contains("chinese") || c.contains("asian") ->
//                listOf(FoodItem("Fried Rice", "₹199"), FoodItem("Hakka Noodles", "₹179"))
//            c.contains("cafe") || c.contains("coffee") ->
//                listOf(FoodItem("Cappuccino", "₹129"), FoodItem("Club Sandwich", "₹199"))
//            else ->
//                listOf(FoodItem("House Special", "₹199"), FoodItem("Chef's Combo", "₹399"))
//        }
//    }
//}





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

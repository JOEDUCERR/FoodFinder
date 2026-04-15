//package com.example.foodfinderfinal1
//
//data class Restaurant(
//    val id: String,
//    val name: String,
//    val address: String,
//    val lat: Double,
//    val lon: Double,
//    val foodItems: List<FoodItem> = listOf()
//) : java.io.Serializable
//
//data class FoodItem(
//    val name: String,
//    val price: String
//) : java.io.Serializable
//
//data class ChatMessage(
//    val text: String,
//    val isUser: Boolean
//)

package com.example.foodfinderfinal1

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ─────────────────────────────────────────────────────────────────────────────
// Models.kt — two upgrades applied:
//
// 1. @Parcelize replaces java.io.Serializable
//    Serializable uses reflection at runtime → slow, generates lots of garbage.
//    Parcelize generates the marshalling code at compile time → 10x faster,
//    less memory, and the Android-standard way to pass objects between Activities.
//
//    Add this to app/build.gradle.kts plugins block to enable:
//       id("kotlin-parcelize")
//
// 2. Restaurant enriched with real fields from Overpass tags:
//    phone, website, openingHours, cuisineTag, imageUrl
//    These are already returned by the API — we just weren't storing them.
// ─────────────────────────────────────────────────────────────────────────────

@Parcelize
data class Restaurant(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val foodItems: List<FoodItem> = emptyList(),
    // ── New fields parsed from Overpass tags ──────────────────────────────────
    val phone: String        = "",      // contact:phone tag
    val website: String      = "",      // website tag
    val openingHours: String = "",      // opening_hours tag  e.g. "Mo-Fr 09:00-22:00"
    val cuisineTag: String   = "",      // cuisine tag        e.g. "indian;biryani"
    val rating: Float        = 0f,      // stars:quality tag (rare but exists)
    val imageUrl: String     = ""       // for future Glide loading
) : Parcelable

@Parcelize
data class FoodItem(
    val name: String,
    val price: String
) : Parcelable

// ChatMessage does NOT need Parcelable — it's never passed between Activities
data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

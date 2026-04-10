package com.example.foodfinderfinal1

data class Restaurant(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val foodItems: List<FoodItem> = listOf()
) : java.io.Serializable

data class FoodItem(
    val name: String,
    val price: String
) : java.io.Serializable

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

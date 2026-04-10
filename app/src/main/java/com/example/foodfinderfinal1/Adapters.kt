package com.example.foodfinderfinal1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RestaurantAdapter(
    private val restaurants: List<Restaurant>,
    private val onClick: (Restaurant) -> Unit
) : RecyclerView.Adapter<RestaurantAdapter.RestaurantViewHolder>() {

    class RestaurantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvRestaurantName)
        val address: TextView = view.findViewById(R.id.tvRestaurantAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RestaurantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_restaurant, parent, false)
        return RestaurantViewHolder(view)
    }

    override fun onBindViewHolder(holder: RestaurantViewHolder, position: Int) {
        val item = restaurants[position]
        holder.name.text = item.name
        holder.address.text = item.address
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = restaurants.size
}

class FoodAdapter(private val foods: List<FoodItem>) : RecyclerView.Adapter<FoodAdapter.FoodViewHolder>() {

    class FoodViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvFoodName)
        val price: TextView = view.findViewById(R.id.tvFoodPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_food, parent, false)
        return FoodViewHolder(view)
    }

    override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
        val item = foods[position]
        holder.name.text = item.name
        holder.price.text = item.price
    }

    override fun getItemCount() = foods.size
}

class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.tvChatMessage)
        val card: androidx.cardview.widget.CardView = view.findViewById(R.id.cardMessage)
        val layout: android.widget.LinearLayout = view as android.widget.LinearLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.text = message.text
        
        val params = holder.card.layoutParams as android.widget.LinearLayout.LayoutParams
        if (message.isUser) {
            holder.layout.gravity = android.view.Gravity.END
            holder.card.setCardBackgroundColor(0xFFE8F5E9.toInt()) // Light Green
            params.marginStart = 100
            params.marginEnd = 0
        } else {
            holder.layout.gravity = android.view.Gravity.START
            holder.card.setCardBackgroundColor(0xFFFFFFFF.toInt())
            params.marginStart = 0
            params.marginEnd = 100
        }
        holder.card.layoutParams = params
    }

    override fun getItemCount() = messages.size
}

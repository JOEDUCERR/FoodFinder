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

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI = 0
    }

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.tvChatMessage)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layout = if (viewType == TYPE_USER) R.layout.item_chat_user else R.layout.item_chat_ai
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.text = message.text
    }

    override fun getItemCount() = messages.size
}

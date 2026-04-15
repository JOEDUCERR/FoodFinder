//package com.example.foodfinderfinal1
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.TextView
//import androidx.recyclerview.widget.RecyclerView
//
//class RestaurantAdapter(
//    private val restaurants: List<Restaurant>,
//    private val onClick: (Restaurant) -> Unit
//) : RecyclerView.Adapter<RestaurantAdapter.RestaurantViewHolder>() {
//
//    class RestaurantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        val name: TextView = view.findViewById(R.id.tvRestaurantName)
//        val address: TextView = view.findViewById(R.id.tvRestaurantAddress)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RestaurantViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_restaurant, parent, false)
//        return RestaurantViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: RestaurantViewHolder, position: Int) {
//        val item = restaurants[position]
//        holder.name.text = item.name
//        holder.address.text = item.address
//        holder.itemView.setOnClickListener { onClick(item) }
//    }
//
//    override fun getItemCount() = restaurants.size
//}
//
//class FoodAdapter(private val foods: List<FoodItem>) : RecyclerView.Adapter<FoodAdapter.FoodViewHolder>() {
//
//    class FoodViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        val name: TextView = view.findViewById(R.id.tvFoodName)
//        val price: TextView = view.findViewById(R.id.tvFoodPrice)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_food, parent, false)
//        return FoodViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
//        val item = foods[position]
//        holder.name.text = item.name
//        holder.price.text = item.price
//    }
//
//    override fun getItemCount() = foods.size
//}
//
//class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
//
//    companion object {
//        private const val TYPE_USER = 1
//        private const val TYPE_AI = 0
//    }
//
//    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        val messageText: TextView = view.findViewById(R.id.tvChatMessage)
//    }
//
//    override fun getItemViewType(position: Int): Int {
//        return if (messages[position].isUser) TYPE_USER else TYPE_AI
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
//        val layout = if (viewType == TYPE_USER) R.layout.item_chat_user else R.layout.item_chat_ai
//        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
//        return ChatViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
//        val message = messages[position]
//        holder.messageText.text = message.text
//    }
//
//    override fun getItemCount() = messages.size
//}

package com.example.foodfinderfinal1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

// ─────────────────────────────────────────────────────────────────────────────
// RestaurantAdapter — upgraded to ListAdapter + DiffUtil
// submitList() diffs on background thread → smooth animations, no flicker
// ─────────────────────────────────────────────────────────────────────────────
class RestaurantAdapter(
    private val onClick: (Restaurant) -> Unit
) : ListAdapter<Restaurant, RestaurantAdapter.RestaurantViewHolder>(RestaurantDiffCallback()) {

    class RestaurantDiffCallback : DiffUtil.ItemCallback<Restaurant>() {
        override fun areItemsTheSame(oldItem: Restaurant, newItem: Restaurant) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Restaurant, newItem: Restaurant) =
            oldItem == newItem
    }

    class RestaurantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView    = view.findViewById(R.id.tvRestaurantName)
        val address: TextView = view.findViewById(R.id.tvRestaurantAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RestaurantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_restaurant, parent, false)
        return RestaurantViewHolder(view)
    }

    override fun onBindViewHolder(holder: RestaurantViewHolder, position: Int) {
        val item = getItem(position)
        holder.name.text    = item.name
        holder.address.text = item.address
        holder.itemView.setOnClickListener { onClick(item) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FoodAdapter — upgraded to ListAdapter
// ─────────────────────────────────────────────────────────────────────────────
class FoodAdapter : ListAdapter<FoodItem, FoodAdapter.FoodViewHolder>(FoodDiffCallback()) {

    class FoodDiffCallback : DiffUtil.ItemCallback<FoodItem>() {
        override fun areItemsTheSame(oldItem: FoodItem, newItem: FoodItem) =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: FoodItem, newItem: FoodItem) =
            oldItem == newItem
    }

    class FoodViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView  = view.findViewById(R.id.tvFoodName)
        val price: TextView = view.findViewById(R.id.tvFoodPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_food, parent, false)
        return FoodViewHolder(view)
    }

    override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
        val item = getItem(position)
        holder.name.text  = item.name
        holder.price.text = item.price
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ChatAdapter — kept as plain Adapter (messages only append, never reorder)
// ─────────────────────────────────────────────────────────────────────────────
class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI   = 0
    }

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.tvChatMessage)
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].isUser) TYPE_USER else TYPE_AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layout = if (viewType == TYPE_USER) R.layout.item_chat_user else R.layout.item_chat_ai
        return ChatViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.messageText.text = messages[position].text
    }

    override fun getItemCount() = messages.size
}

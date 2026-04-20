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
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

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
        val rating: TextView       = view.findViewById(R.id.tvRating)
        val cuisineTag: TextView   = view.findViewById(R.id.tvCuisineTag)
        val ivRestaurantImage: ImageView = view.findViewById(R.id.ivRestaurantImage)
    }

    //Converts XML to View
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RestaurantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_restaurant, parent, false)
        return RestaurantViewHolder(view)
    }

    override fun onBindViewHolder(holder: RestaurantViewHolder, position: Int) {
        val item = getItem(position)
        //Binding data together
        holder.name.text    = item.name
        holder.address.text = item.address

        holder.rating.text = if (item.rating > 0f) "★ ${"%.1f".format(item.rating)}" else "★ 4.0"

        val cuisineDisplay = item.cuisineTag
            .split(";", ",").firstOrNull()?.trim()
            ?.replaceFirstChar { it.uppercase() } ?: "Food"
        holder.cuisineTag.text = cuisineDisplay

        // ── Image: only load for first 10 items to stay lightweight ──────────
        if (position < 10) {
            val keyword = buildImageKeyword(item.cuisineTag, item.foodItems)
//            val imageUrl = "https://source.unsplash.com/400x200/?$keyword,food"
            val imageUrl = "https://picsum.photos/seed/${item.id}/400/200"
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.bg_restaurant_placeholder)
                .error(R.drawable.bg_restaurant_placeholder)
                .into(holder.ivRestaurantImage)
        } else {
            // Beyond position 10 — just show placeholder, skip network call
            holder.ivRestaurantImage.setImageResource(R.drawable.bg_restaurant_placeholder)
        }

        holder.itemView.setOnClickListener { onClick(item) } //handles clicks outside adapter
    }
    private fun buildImageKeyword(cuisineTag: String, foodItems: List<FoodItem>): String {
        // Try cuisine tag first
        val cuisine = cuisineTag.split(";", ",").firstOrNull()?.trim()?.lowercase()
        if (!cuisine.isNullOrBlank() && cuisine != "food") return cuisine

        // Fall back to first food item name
        val foodName = foodItems.firstOrNull()?.name?.lowercase()
            ?.replace("₹", "")?.trim()
        if (!foodName.isNullOrBlank()) return foodName

        return "restaurant"
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

    //XML to view or inflating layout
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layout = if (viewType == TYPE_USER) R.layout.item_chat_user else R.layout.item_chat_ai
        return ChatViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.messageText.text = messages[position].text
    }

    override fun getItemCount() = messages.size
}

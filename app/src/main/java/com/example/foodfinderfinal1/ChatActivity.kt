package com.example.foodfinderfinal1

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ChatActivity : AppCompatActivity() {

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val rvChat = findViewById<RecyclerView>(R.id.rvChat)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)

        adapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = adapter

        // Welcome message
        messages.add(ChatMessage("Hello! I'm your FoodFinder AI assistant. How can I help you today?", false))
        adapter.notifyDataSetChanged()

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                messages.add(ChatMessage(text, true))
                etMessage.setText("")
                adapter.notifyItemInserted(messages.size - 1)
                rvChat.scrollToPosition(messages.size - 1)

                // Mock AI response
                generateAIResponse(text)
            }
        }
    }

    private fun generateAIResponse(userText: String) {
        val response = when {
            userText.contains("pizza", ignoreCase = true) -> "I see you like pizza! I recommend checking out Pizza Palace in our listings."
            userText.contains("burger", ignoreCase = true) -> "Burgers are great! The Burger Joint usually has high ratings."
            userText.contains("hello", ignoreCase = true) || userText.contains("hi", ignoreCase = true) -> "Hi there! Looking for some tasty food recommendations?"
            userText.contains("near me", ignoreCase = true) -> "I can help you find restaurants based on the area you entered at login!"
            else -> "That sounds interesting! I'm still learning, but I can help you find the best places to eat in your area."
        }

        findViewById<RecyclerView>(R.id.rvChat).postDelayed({
            messages.add(ChatMessage(response, false))
            adapter.notifyItemInserted(messages.size - 1)
            findViewById<RecyclerView>(R.id.rvChat).scrollToPosition(messages.size - 1)
        }, 1000)
    }
}

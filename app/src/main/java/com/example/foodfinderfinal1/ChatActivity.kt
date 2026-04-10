package com.example.foodfinderfinal1

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.*

class ChatActivity : AppCompatActivity() {

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter

    // ✅ Gemini AI Setup (FIXED MODEL NAME)
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",   // ✅ Correct model name
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val rvChat = findViewById<RecyclerView>(R.id.rvChat)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)

        adapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = adapter

        // ✅ Welcome message
        if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    "Hello! I'm your FoodFinder AI assistant 🍔. How can I help you today?",
                    false
                )
            )
            adapter.notifyDataSetChanged()
        }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                messages.add(ChatMessage(text, true))
                etMessage.setText("")
                adapter.notifyItemInserted(messages.size - 1)
                rvChat.scrollToPosition(messages.size - 1)

                generateGeminiResponse(text)
            }
        }
    }

    private fun generateGeminiResponse(userText: String) {

        val rvChat = findViewById<RecyclerView>(R.id.rvChat)

        // ✅ Typing message
        val typingMessage = ChatMessage("Typing...", false)
        messages.add(typingMessage)
        val typingIndex = messages.size - 1
        adapter.notifyItemInserted(typingIndex)
        rvChat.scrollToPosition(typingIndex)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val result = generativeModel.generateContent(userText)
                    result.text ?: "Sorry, I couldn't understand that."
                }

                // ✅ Remove typing
                messages.removeAt(typingIndex)
                adapter.notifyItemRemoved(typingIndex)

                // ✅ Add response
                messages.add(ChatMessage(response, false))
                adapter.notifyItemInserted(messages.size - 1)
                rvChat.scrollToPosition(messages.size - 1)

            } catch (e: Exception) {

                // ✅ Remove typing safely
                if (typingIndex < messages.size) {
                    messages.removeAt(typingIndex)
                    adapter.notifyItemRemoved(typingIndex)
                }

                // ✅ Friendly error message
                messages.add(
                    ChatMessage(
                        "⚠️ Something went wrong. Please try again.",
                        false
                    )
                )
                adapter.notifyItemInserted(messages.size - 1)
                rvChat.scrollToPosition(messages.size - 1)

                android.util.Log.e("GeminiError", "Chat failed", e)
            }
        }
    }
}
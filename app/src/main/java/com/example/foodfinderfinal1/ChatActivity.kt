package com.example.foodfinderfinal1

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ChatActivity : AppCompatActivity() {

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var rvChat: RecyclerView

    // Full conversation history sent to OpenAI each turn for context
    private val history = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val toolbar = findViewById<Toolbar>(R.id.toolbarChat)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "FoodFinder AI 🍔"
        }
        toolbar.setNavigationOnClickListener { finish() }

        rvChat = findViewById(R.id.rvChat)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend   = findViewById<ImageButton>(R.id.btnSend)

        adapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvChat.adapter = adapter

        val apiKey = BuildConfig.OPENAI_API_KEY.trim()
        if (apiKey.isBlank() || apiKey == "your_key_here") {
            addMessage(ChatMessage(
                "⚠️ OpenAI API key not configured.\n\n" +
                        "Steps to fix:\n" +
                        "1. Go to platform.openai.com/api-keys\n" +
                        "2. Click \"Create new secret key\"\n" +
                        "3. Open local.properties in Android Studio\n" +
                        "4. Add this line:\n" +
                        "   OPENAI_API_KEY=sk-...your_key...\n" +
                        "5. Sync Gradle and rebuild",
                false
            ))
        } else {
            addMessage(ChatMessage(
                "Hello! I'm your FoodFinder AI 🍔\n" +
                        "Ask me about restaurants, dishes, or cuisines!",
                false
            ))
        }

        val sendAction = {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                etMessage.setText("")
                hideKeyboard(etMessage)
                addMessage(ChatMessage(text, true))
                sendMessage(text)
            }
        }

        btnSend.setOnClickListener { sendAction() }
        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO) {
                sendAction(); true
            } else false
        }
    }

    private fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        adapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }

    private fun sendMessage(userText: String) {
        val apiKey = BuildConfig.OPENAI_API_KEY.trim()
        if (apiKey.isBlank() || apiKey == "your_key_here") {
            addMessage(ChatMessage("⚠️ Please add OPENAI_API_KEY to local.properties first.", false))
            return
        }

        // Add user turn to history
        history.add(JSONObject().apply {
            put("role", "user")
            put("content", userText)
        })

        // Typing indicator
        addMessage(ChatMessage("Typing…", false))
        val typingIndex = messages.size - 1

        lifecycleScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    callOpenAI(apiKey)
                }

                // Save assistant reply to history for next turn
                history.add(JSONObject().apply {
                    put("role", "assistant")
                    put("content", reply)
                })

                messages.removeAt(typingIndex)
                adapter.notifyItemRemoved(typingIndex)
                addMessage(ChatMessage(reply, false))

            } catch (e: Exception) {
                // Remove failed user message from history so it doesn't corrupt next call
                if (history.isNotEmpty()) history.removeAt(history.size - 1)

                messages.removeAt(typingIndex)
                adapter.notifyItemRemoved(typingIndex)

                val errMsg = when {
                    e.message?.contains("401") == true ->
                        "⚠️ Invalid API key.\nCheck OPENAI_API_KEY in local.properties.\nMake sure it starts with 'sk-'"
                    e.message?.contains("429") == true ->
                        "⚠️ Rate limit or quota exceeded.\nCheck your OpenAI billing at platform.openai.com"
                    e.message?.contains("403") == true ->
                        "⚠️ Access denied. Your API key may not have GPT access."
                    e.message?.contains("UnknownHostException") == true ->
                        "⚠️ No internet connection."
                    else -> "⚠️ Error: ${e.message}"
                }
                addMessage(ChatMessage(errMsg, false))
                android.util.Log.e("ChatActivity", "OpenAI error", e)
            }
        }
    }

    private fun callOpenAI(apiKey: String): String {
        val url = URL("https://api.openai.com/v1/chat/completions")

        // Build full messages array: system prompt + conversation history
        val allMessages = JSONArray()

        // System message — sets the assistant's persona
        allMessages.put(JSONObject().apply {
            put("role", "system")
            put("content",
                "You are a helpful food and restaurant assistant for the FoodFinder app. " +
                        "Help users find restaurants, suggest dishes, explain cuisines and food items. " +
                        "Be concise, friendly, and use emojis occasionally."
            )
        })

        // Append full conversation history (already includes current user message)
        for (i in 0 until history.size) {
            allMessages.put(history[i])
        }

        val body = JSONObject().apply {
            put("model", "gpt-3.5-turbo")   // cheapest & fastest; change to "gpt-4o" for better answers
            put("messages", allMessages)
            put("max_tokens", 500)
            put("temperature", 0.7)
        }.toString()

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput      = true
            connectTimeout = 15000
            readTimeout    = 30000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }

        val code = conn.responseCode
        if (code != 200) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            throw Exception("$code $err")
        }

        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        return json
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    // Extension to iterate JSONArray easily
    private operator fun JSONArray.iterator(): Iterator<JSONObject> =
        (0 until length()).asSequence().map { getJSONObject(it) }.iterator()

    private fun hideKeyboard(view: android.view.View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }
}
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

    // Full conversation history for multi-turn context
    private val history = mutableListOf<Pair<String, String>>()

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

        // Check API key on startup and show clear instructions if missing
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank() || apiKey == "your_key_here") {
            addMessage(ChatMessage(
                "⚠️ Gemini API key is not configured.\n\n" +
                        "Steps to fix:\n" +
                        "1. Go to aistudio.google.com\n" +
                        "2. Click \"Get API key\" → Create key\n" +
                        "3. Open local.properties in Android Studio\n" +
                        "4. Add this line:\n" +
                        "   GEMINI_API_KEY=paste_your_key_here\n" +
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
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank() || apiKey == "your_key_here") {
            addMessage(ChatMessage("⚠️ Please add your GEMINI_API_KEY to local.properties first.", false))
            return
        }

        // Typing indicator
        addMessage(ChatMessage("Typing…", false))
        val typingIndex = messages.size - 1

        lifecycleScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    callGeminiApi(apiKey, userText)
                }
                history.add(Pair(userText, reply))

                messages.removeAt(typingIndex)
                adapter.notifyItemRemoved(typingIndex)
                addMessage(ChatMessage(reply, false))

            } catch (e: Exception) {
                messages.removeAt(typingIndex)
                adapter.notifyItemRemoved(typingIndex)

                val msg = when {
                    e.message?.contains("401") == true ->
                        "⚠️ Invalid API key.\nDouble-check GEMINI_API_KEY in local.properties."
                    e.message?.contains("403") == true ->
                        "⚠️ API key doesn't have Gemini access.\nCheck your Google AI Studio project."
                    e.message?.contains("429") == true ->
                        "⚠️ Rate limit reached. Wait a moment and try again."
                    e.message?.contains("404") == true ->
                        "⚠️ Model not found. Your API key may not have Gemini 1.5 access yet."
                    e.message?.contains("UnknownHostException") == true ->
                        "⚠️ No internet connection."
                    else -> "⚠️ Error: ${e.message}"
                }
                addMessage(ChatMessage(msg, false))
                android.util.Log.e("ChatActivity", "Gemini error", e)
            }
        }
    }

    private fun callGeminiApi(apiKey: String, userText: String): String {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-1.5-flash:generateContent?key=$apiKey"
        )

        // Build contents: system context + conversation history + new message
        val contents = JSONArray()

        // Inject a system persona as the first user/model exchange
        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text",
                "You are a helpful food and restaurant assistant for the FoodFinder app. " +
                        "Help users find restaurants, suggest dishes, explain cuisines and food items. " +
                        "Be concise, friendly, and use emojis occasionally."
            )))
        })
        contents.put(JSONObject().apply {
            put("role", "model")
            put("parts", JSONArray().put(JSONObject().put("text",
                "Got it! I'm ready to help you find great food. 🍽️"
            )))
        })

        // Append conversation history for context
        for ((u, m) in history) {
            contents.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", u)))
            })
            contents.put(JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().put(JSONObject().put("text", m)))
            })
        }

        // Current user message
        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userText)))
        })

        val body = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 500)
            })
        }.toString()

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput      = true
            connectTimeout = 15000
            readTimeout    = 30000
            setRequestProperty("Content-Type", "application/json")
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }

        val code = conn.responseCode
        if (code != 200) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            throw Exception("$code $err")
        }

        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        return json
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    private fun hideKeyboard(view: android.view.View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }
}
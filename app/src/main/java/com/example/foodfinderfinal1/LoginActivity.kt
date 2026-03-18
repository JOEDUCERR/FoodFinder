package com.example.foodfinderfinal1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth
        // NOTE: Make sure to add google-services.json to the app/ directory
        // and uncomment the google-services plugin in app/build.gradle.kts to fully connect Firebase.
        try {
            auth = FirebaseAuth.getInstance()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback if Firebase isn't set up yet
        }

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etArea = findViewById<EditText>(R.id.etArea)
        val etState = findViewById<EditText>(R.id.etState)
        val etCountry = findViewById<EditText>(R.id.etCountry)
        
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val area = etArea.text.toString().trim()
            val state = etState.text.toString().trim()
            val country = etCountry.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || area.isEmpty() || state.isEmpty() || country.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                // Real Firebase Login
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            goToMain(area, state, country)
                        } else {
                            Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } catch (e: Exception) {
                // Dummy login fallback for when Firebase is not yet connected
                Toast.makeText(this, "Firebase not connected yet. Logging in with Dummy mode.", Toast.LENGTH_SHORT).show()
                goToMain(area, state, country)
            }
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun goToMain(area: String, state: String, country: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("EXTRA_AREA", area)
            putExtra("EXTRA_STATE", state)
            putExtra("EXTRA_COUNTRY", country)
        }
        startActivity(intent)
        finish()
    }
}

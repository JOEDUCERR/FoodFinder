//package com.example.foodfinderfinal1
//
//import android.os.Bundle
//import android.widget.Button
//import android.widget.EditText
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import com.google.firebase.auth.FirebaseAuth
//
//class RegisterActivity : AppCompatActivity() {
//
//    private lateinit var auth: FirebaseAuth
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_register)
//
//        try {
//            auth = FirebaseAuth.getInstance()
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//
//        val etEmail = findViewById<EditText>(R.id.etRegEmail)
//        val etPassword = findViewById<EditText>(R.id.etRegPassword)
//        val btnRegister = findViewById<Button>(R.id.btnDoRegister)
//
//        btnRegister.setOnClickListener {
//            val email = etEmail.text.toString().trim()
//            val password = etPassword.text.toString().trim()
//
//            if (email.isEmpty() || password.isEmpty()) {
//                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//            try {
//                // Real Firebase Register
//                auth.createUserWithEmailAndPassword(email, password)
//                    .addOnCompleteListener(this) { task ->
//                        if (task.isSuccessful) {
//                            Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
//                            finish() // Go back to Login Activity
//                        } else {
//                            Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
//                        }
//                    }
//            } catch (e: Exception) {
//                // Dummy fallback
//                Toast.makeText(this, "Firebase not connected yet. Dummy registration success!", Toast.LENGTH_SHORT).show()
//                finish() // Go back to Login
//            }
//        }
//    }
//}

package com.joey.foodfinderfinal1

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// ─────────────────────────────────────────────────────────────────────────────
// RegisterActivity — upgrades applied:
//
// 1. Input validation before hitting Firebase:
//    - Email format check
//    - Password minimum 6 chars (Firebase requirement)
//    - Password confirmation field
//
// 2. On successful registration, save user profile to Firestore
//    so FavouritesActivity and ProfileActivity can read it later.
//
// 3. Dummy fallback removed — if Firebase isn't set up,
//    show a clear error instead of pretending it worked.
//
// Layout change needed: add <EditText android:id="@+id/etRegConfirmPassword" .../>
// ─────────────────────────────────────────────────────────────────────────────

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val etEmail           = findViewById<EditText>(R.id.etRegEmail)
        val etPassword        = findViewById<EditText>(R.id.etRegPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etRegConfirmPassword)
        val btnRegister       = findViewById<Button>(R.id.btnDoRegister)

        btnRegister.setOnClickListener {
            val email    = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirm  = etConfirmPassword.text.toString().trim()

            // ── Upgrade 1: Validate before calling Firebase ───────────────────
            if (email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirm) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ── Register with Firebase ────────────────────────────────────────
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // ── Upgrade 2: Save profile to Firestore ──────────────
                        val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                        FirebaseFirestore.getInstance()
                            .collection("users").document(uid)
                            .set(mapOf(
                                "email"     to email,
                                "createdAt" to com.google.firebase.Timestamp.now()
                            ))
                        Toast.makeText(this, "Registration successful! Please log in.", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        // ── Upgrade 3: Show real error, no dummy fallback ──────
                        val msg = when {
                            task.exception?.message?.contains("email address is already in use") == true ->
                                "This email is already registered. Try logging in."
                            task.exception?.message?.contains("network") == true ->
                                "No internet connection. Please try again."
                            else -> "Registration failed: ${task.exception?.message}"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}

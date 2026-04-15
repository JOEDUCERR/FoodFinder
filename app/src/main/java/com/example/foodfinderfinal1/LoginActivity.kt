//package com.example.foodfinderfinal1
//
//import android.content.Intent
//import android.os.Bundle
//import android.widget.Button
//import android.widget.EditText
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import com.google.firebase.auth.FirebaseAuth
//
//class LoginActivity : AppCompatActivity() {
//
//    private lateinit var auth: FirebaseAuth
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_login)
//
//        // Initialize Firebase Auth
//        // NOTE: Make sure to add google-services.json to the app/ directory
//        // and uncomment the google-services plugin in app/build.gradle.kts to fully connect Firebase.
//        try {
//            auth = FirebaseAuth.getInstance()
//        } catch (e: Exception) {
//            e.printStackTrace()
//            // Fallback if Firebase isn't set up yet
//        }
//
//        val etEmail = findViewById<EditText>(R.id.etEmail)
//        val etPassword = findViewById<EditText>(R.id.etPassword)
//        val etArea = findViewById<EditText>(R.id.etArea)
//        val etState = findViewById<EditText>(R.id.etState)
//        val etCountry = findViewById<EditText>(R.id.etCountry)
//
//        val btnLogin = findViewById<Button>(R.id.btnLogin)
//        val btnRegister = findViewById<Button>(R.id.btnRegister)
//        val tvForgotPassword = findViewById<android.widget.TextView>(R.id.tvForgotPassword)
//
//        btnLogin.setOnClickListener {
//            val email = etEmail.text.toString().trim()
//            val password = etPassword.text.toString().trim()
//            val area = etArea.text.toString().trim()
//            val state = etState.text.toString().trim()
//            val country = etCountry.text.toString().trim()
//
//            if (email.isEmpty() || password.isEmpty() || area.isEmpty() || state.isEmpty() || country.isEmpty()) {
//                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//            try {
//                // Real Firebase Login
//                auth.signInWithEmailAndPassword(email, password)
//                    .addOnCompleteListener(this) { task ->
//                        if (task.isSuccessful) {
//                            goToMain(area, state, country)
//                        } else {
//                            Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
//                        }
//                    }
//            } catch (e: Exception) {
//                // Dummy login fallback for when Firebase is not yet connected
//                Toast.makeText(this, "Firebase not connected yet. Logging in with Dummy mode.", Toast.LENGTH_SHORT).show()
//                goToMain(area, state, country)
//            }
//        }
//
//        btnRegister.setOnClickListener {
//            startActivity(Intent(this, RegisterActivity::class.java))
//        }
//
//        tvForgotPassword.setOnClickListener {
//            val email = etEmail.text.toString().trim()
//            if (email.isEmpty()) {
//                Toast.makeText(this, "Please enter your email to reset password", Toast.LENGTH_SHORT).show()
//            } else {
//                try {
//                    auth.sendPasswordResetEmail(email)
//                        .addOnCompleteListener { task ->
//                            if (task.isSuccessful) {
//                                Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_SHORT).show()
//                            } else {
//                                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
//                            }
//                        }
//                } catch (e: Exception) {
//                    Toast.makeText(this, "Firebase not connected.", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//    private fun goToMain(area: String, state: String, country: String) {
//        val intent = Intent(this, MainActivity::class.java).apply {
//            putExtra("EXTRA_AREA", area)
//            putExtra("EXTRA_STATE", state)
//            putExtra("EXTRA_COUNTRY", country)
//        }
//        startActivity(intent)
//        finish()
//    }
//}

package com.example.foodfinderfinal1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

// ─────────────────────────────────────────────────────────────────────────────
// LoginActivity — three upgrades:
//
// 1. Auto-login: if Firebase says the user is already signed in, skip straight
//    to MainActivity. No need to log in again on every app launch.
//
// 2. Google Sign-In button added — one tap instead of typing email + password.
//
// 3. Area / State / Country fields REMOVED from login screen.
//    Location is now handled by GPS in MainActivity (FusedLocationProvider).
//    Login should only care about identity, not location.
//
// Dependencies to add in app/build.gradle.kts:
//   implementation("com.google.android.gms:play-services-auth:21.2.0")
// ─────────────────────────────────────────────────────────────────────────────

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val RC_GOOGLE_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // ── Upgrade 1: Auto-login ─────────────────────────────────────────────
        // If Firebase already has a logged-in user (token is still valid),
        // skip the login screen entirely.
        if (auth.currentUser != null) {
            goToMain()
            return
        }

        val etEmail    = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin   = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<android.widget.TextView>(R.id.btnRegister)
        val tvForgotPassword = findViewById<android.widget.TextView>(R.id.tvForgotPassword)

        // ── Upgrade 2: Google Sign-In ─────────────────────────────────────────
        // Add a SignInButton to your activity_login.xml layout:
        //   <com.google.android.gms.common.SignInButton
        //       android:id="@+id/btnGoogleSignIn"
        //       android:layout_width="match_parent"
        //       android:layout_height="wrap_content" />
        val btnGoogleSignIn = findViewById<SignInButton>(R.id.btnGoogleSignIn)
        btnGoogleSignIn.setOnClickListener { launchGoogleSignIn() }

        // ── Email / Password login (unchanged logic, location fields removed) ──
        btnLogin.setOnClickListener {
            val email    = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        goToMain()
                    } else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email first", Toast.LENGTH_SHORT).show()
            } else {
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        val msg = if (task.isSuccessful) "Reset email sent to $email"
                        else "Error: ${task.exception?.message}"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    // ── Google Sign-In flow ───────────────────────────────────────────────────
    private fun launchGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // auto-generated by google-services.json
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        @Suppress("DEPRECATION")
        startActivityForResult(client.signInIntent, RC_GOOGLE_SIGN_IN)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(Exception::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener(this) { authTask ->
                        if (authTask.isSuccessful) goToMain()
                        else Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(this, "Google sign-in error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Upgrade 3: No more area/state/country — GPS handles it in MainActivity ─
    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

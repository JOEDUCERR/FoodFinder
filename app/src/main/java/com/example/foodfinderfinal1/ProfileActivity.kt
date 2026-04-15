package com.example.foodfinderfinal1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        
        findViewById<TextView>(R.id.tvProfileEmail).text = currentUser?.email ?: "Not logged in"

        findViewById<Button>(R.id.btnProfileLogout).setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarProfile).setNavigationOnClickListener {
            finish()
        }
    }
}
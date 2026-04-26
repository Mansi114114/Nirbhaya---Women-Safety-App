package com.example.women_safety

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase


class SettingsActivity : AppCompatActivity() {


    private lateinit var sosMessageInput: TextInputEditText
    private lateinit var saveMessageButton: Button
    private lateinit var logoutButton: Button
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        // Initialize Firebase Auth and Database
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("SafeGuardPrefs", MODE_PRIVATE)

        // Initialize views
        sosMessageInput = findViewById(R.id.sos_message_input)
        saveMessageButton = findViewById(R.id.save_sos_message_button)
        logoutButton = findViewById(R.id.logout_button)

        val suggestionButton = findViewById<Button>(R.id.buttonSuggestions)
        suggestionButton.setOnClickListener {
            val intent = Intent(this, SuggestionActivity::class.java)
            startActivity(intent)
        }

        // Load saved SOS message if exists
        val savedMessage = sharedPreferences.getString("sos_message", "")
        sosMessageInput.setText(savedMessage)

        // Save custom SOS message to Firebase Database
        saveMessageButton.setOnClickListener {
            val message = sosMessageInput.text.toString().trim()

            if (message.isNotEmpty()) {
                // Get the current user
                val userId = auth.currentUser?.uid

                if (userId != null) {
                    // Save the message to Firebase Realtime Database
                    val userRef = database.getReference("users").child(userId)
                    userRef.child("sos_message").setValue(message)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(this, "SOS message saved successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Failed to save message", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            }
        }

        logoutButton.setOnClickListener {
            // Sign out from Firebase Authentication
            FirebaseAuth.getInstance().signOut()

            // Clear SharedPreferences session data
            val sharedPreferences = getSharedPreferences("WomenSafetyPrefs", MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putBoolean("isLoggedIn", false)
            editor.remove("userName")
            editor.remove("userEmail")
            editor.apply()

            // Redirect to Login screen after logging out
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

    }
}

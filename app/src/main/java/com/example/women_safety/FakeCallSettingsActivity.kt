package com.example.women_safety

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FakeCallSettingsActivity : AppCompatActivity() {

    private lateinit var callerNameEditText: EditText
    private lateinit var callerNumberEditText: EditText
    private lateinit var callerLocationEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_call_settings)

        // Initialize views
        callerNameEditText = findViewById(R.id.edit_caller_name)
        callerNumberEditText = findViewById(R.id.edit_caller_number)
        callerLocationEditText = findViewById(R.id.edit_caller_location)
        saveButton = findViewById(R.id.save_button)
        val startCallButton = findViewById<Button>(R.id.start_call_button)
        startCallButton.setOnClickListener {
            startActivity(Intent(this, FakeCallActivity::class.java))
        }


        // Load current settings
        loadSettings()

        // Set up save button
        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val databaseReference = FirebaseDatabase.getInstance()
                .getReference("users").child(userId).child("fake_call_settings")

            databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val callerName = snapshot.child("caller_name").getValue(String::class.java) ?: "Mom"
                    val callerNumber = snapshot.child("caller_number").getValue(String::class.java) ?: "+91 9722399473"
                    val callerLocation = snapshot.child("caller_location").getValue(String::class.java) ?: "Mobile • Delhi, India"

                    callerNameEditText.setText(callerName)
                    callerNumberEditText.setText(callerNumber)
                    callerLocationEditText.setText(callerLocation)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@FakeCallSettingsActivity,
                        "Failed to load settings: ${error.message}",
                        Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun saveSettings() {
        val callerName = callerNameEditText.text.toString().trim()
        val callerNumber = callerNumberEditText.text.toString().trim()
        val callerLocation = callerLocationEditText.text.toString().trim()

        if (callerName.isEmpty()) {
            callerNameEditText.error = "Please enter a name"
            return
        }

        if (callerNumber.isEmpty()) {
            callerNumberEditText.error = "Please enter a number"
            return
        }

        if (callerLocation.isEmpty()) {
            callerLocationEditText.error = "Please enter a location"
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val databaseReference = FirebaseDatabase.getInstance()
                .getReference("users").child(userId).child("fake_call_settings")

            val settings = HashMap<String, Any>()
            settings["caller_name"] = callerName
            settings["caller_number"] = callerNumber
            settings["caller_location"] = callerLocation

            databaseReference.updateChildren(settings)
                .addOnSuccessListener {
                    Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save settings: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "You must be logged in to save settings", Toast.LENGTH_SHORT).show()
        }
    }
}
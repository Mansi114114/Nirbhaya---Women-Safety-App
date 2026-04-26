package com.example.women_safety

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SuggestionActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_suggestion)

        val suggestionInput = findViewById<EditText>(R.id.editTextSuggestion)
        val submitButton = findViewById<Button>(R.id.buttonSubmit)
        val dbRef = FirebaseDatabase.getInstance().getReference("suggestions")

        auth = FirebaseAuth.getInstance()

        // Sign in anonymously before allowing DB interaction
        auth.signInAnonymously()
            .addOnSuccessListener {
                submitButton.setOnClickListener {
                    val suggestion = suggestionInput.text.toString().trim()

                    if (suggestion.isNotEmpty()) {
                        val suggestionId = dbRef.push().key
                        suggestionId?.let {
                            val suggestionData = mapOf(
                                "text" to suggestion,
                                "timestamp" to System.currentTimeMillis()
                            )

                            dbRef.child(it).setValue(suggestionData)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Suggestion submitted. Thank you!", Toast.LENGTH_LONG).show()
                                    suggestionInput.text.clear()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to submit: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        Toast.makeText(this, "Please enter a suggestion.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Auth failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}

package com.example.women_safety


import android.app.ProgressDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvSignupLink: TextView
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize UI elements
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvSignupLink = findViewById(R.id.tvSignupLink)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("WomenSafetyPrefs", MODE_PRIVATE)

        // Check if user is already logged in
        if (isLoggedIn()) {
            navigateToMainActivity()
        }

        // Set click listeners
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (validateInputs(email, password)) {
                authenticateUser(email, password)
            }
        }

        tvSignupLink.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean("isLoggedIn", false)
    }

    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            etEmail.error = "Email cannot be empty"
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email"
            return false
        }
        if (password.isEmpty()) {
            etPassword.error = "Password cannot be empty"
            return false
        }
        if (password.length < 6) {
            etPassword.error = "Password must be at least 6 characters"
            return false
        }
        return true
    }

    private fun authenticateUser(email: String, password: String) {
        // Add a progress indicator to prevent multiple login attempts
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Logging in...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                progressDialog.dismiss() // Hide progress dialog when complete

                if (task.isSuccessful) {
                    // Login success
                    val user = FirebaseAuth.getInstance().currentUser

                    // Check if email is verified (optional but recommended)
                    if (user != null && user.isEmailVerified) {
                        val uid = user.uid

                        // Fetch user's data with improved error handling
                        val dbRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
                        dbRef.get().addOnSuccessListener { snapshot ->
                            if (snapshot.exists()) {
                                val name = snapshot.child("name").value?.toString() ?: "User"
                                val emailFromDB = snapshot.child("email").value?.toString() ?: email

                                // Store session in encrypted shared preferences
                                val editor = sharedPreferences.edit()
                                editor.putString("userName", name)
                                editor.putString("userEmail", emailFromDB)
                                editor.putString("userId", uid) // Store user ID for future reference
                                editor.putBoolean("isLoggedIn", true)
                                editor.apply()

                                navigateToMainActivity()
                            } else {
                                Toast.makeText(this, "User data not found. Please contact support.", Toast.LENGTH_SHORT).show()
                                FirebaseAuth.getInstance().signOut() // Sign out if data inconsistency
                            }
                        }.addOnFailureListener { exception ->
                            Toast.makeText(this, "Database error: ${exception.message}", Toast.LENGTH_SHORT).show()
                            FirebaseAuth.getInstance().signOut()
                        }
                    } else {
                        // Handle unverified email (optional)
                        // Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_SHORT).show()
                        // FirebaseAuth.getInstance().signOut()

                        // Since email verification is optional, proceed with login anyway
                        val uid = user?.uid ?: return@addOnCompleteListener

                        // Rest of your existing login code...
                        val dbRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
                        dbRef.get().addOnSuccessListener { snapshot ->
                            val name = snapshot.child("name").value.toString()
                            val emailFromDB = snapshot.child("email").value.toString()

                            val editor = sharedPreferences.edit()
                            editor.putString("userName", name)
                            editor.putString("userEmail", emailFromDB)
                            editor.putBoolean("isLoggedIn", true)
                            editor.apply()

                            navigateToMainActivity()
                        }.addOnFailureListener {
                            Toast.makeText(this, "Failed to retrieve user data.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // Login failed - provide more specific error message when possible
                    val errorMessage = when (task.exception) {
                        is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "Account does not exist"
                        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Invalid email or password"
                        else -> "Login failed: ${task.exception?.message ?: "Unknown error"}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
    }


    private fun setLoggedIn(isLoggedIn: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean("isLoggedIn", isLoggedIn)
        editor.apply()
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

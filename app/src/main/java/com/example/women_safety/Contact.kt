package com.example.women_safety

// 👇 Move this to a separate file (e.g., Contact.kt) OR above your activity class
data class Contact(
    val name: String = "",
    val phoneNumber: String = "",
    val firebaseKey: String = "" // Needed for Firebase syncing/deletion
)

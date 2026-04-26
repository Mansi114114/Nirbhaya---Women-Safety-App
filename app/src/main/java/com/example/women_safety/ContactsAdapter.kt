package com.example.women_safety

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import android.widget.Toast
import android.util.Log
import android.content.Context


class ContactsAdapter(
    private val contacts: MutableList<Contact>,  // Use MutableList for adding/removing
    private val onDeleteClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount() = contacts.size

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val contactName: TextView = itemView.findViewById(R.id.contact_name)
        private val contactPhone: TextView = itemView.findViewById(R.id.contact_phone)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_contact_button)

        fun bind(contact: Contact) {
            contactName.text = contact.name
            contactPhone.text = contact.phoneNumber  // Updated to use phoneNumber

            // Delete contact
            deleteButton.setOnClickListener {
                onDeleteClick(contact)
            }

            // Save contact to Firebase on item click (or any other trigger as per your use case)
            itemView.setOnClickListener {
                saveContactToFirebase(contact, itemView.context)
            }
        }
    }

    // Save contact to Firebase directly from the adapter
    private fun saveContactToFirebase(contact: Contact, context: Context) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val contactRef = FirebaseDatabase.getInstance().getReference("users").child(userId).child("contacts")
            val contactId = contactRef.push().key // Generate a unique ID for each contact

            if (contactId != null) {
                contactRef.child(contactId).setValue(contact)  // Directly use Contact object here
                    .addOnSuccessListener {
                        Log.d("Firebase", "Contact saved successfully")
                        Toast.makeText(context, "Contact saved successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Log.e("Firebase", "Failed to save contact: ${it.message}")
                        Toast.makeText(context, "Failed to save contact", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
}

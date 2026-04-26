package com.example.women_safety

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class ContactsActivity : AppCompatActivity() {

    private lateinit var contactsRecyclerView: RecyclerView
    private lateinit var emptyContactsText: TextView
    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var addContactButton: Button
    private lateinit var addFromContactsButton: Button

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth


    private lateinit var adapter: ContactsAdapter
    private val contactsList = mutableListOf<Contact>()

    companion object {
        private const val CONTACT_PERMISSION_CODE = 100
        private const val CONTACT_PICK_CODE = 101
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        // Initialize views
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        contactsRecyclerView = findViewById(R.id.contacts_recycler_view)
        emptyContactsText = findViewById(R.id.empty_contacts_text)
        nameInput = findViewById(R.id.name_input)
        phoneInput = findViewById(R.id.phone_input)
        addContactButton = findViewById(R.id.add_contact_button)
        addFromContactsButton = findViewById(R.id.add_from_contacts_button)

        setupRecyclerView()
        setupListeners()
        loadSavedContacts()
    }

    private fun setupRecyclerView() {
        adapter = ContactsAdapter(contactsList) { contact ->
            // Handle delete contact
            removeContact(contact)
        }
        contactsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ContactsActivity)
            adapter = this@ContactsActivity.adapter
        }
        updateEmptyView()
    }

    private fun setupListeners() {
        // Add contact manually
        addContactButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please enter both name and phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            addContact(Contact(name, phone))
            clearInputFields()
        }

        // Add contact from phone
        addFromContactsButton.setOnClickListener {
            // Check permission
            if (checkContactPermission()) {
                pickContact()
            } else {
                requestContactPermission()
            }
        }
    }

    private fun loadSavedContacts() {
        contactsList.clear()

        val uid = auth.currentUser?.uid ?: return
        val databaseRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(uid)
            .child("contacts")

        databaseRef.get()
            .addOnSuccessListener { snapshot ->
                for (contactSnapshot in snapshot.children) {
                    val key = contactSnapshot.key ?: continue
                    val name = contactSnapshot.child("name").getValue(String::class.java) ?: ""
                    val phone = contactSnapshot.child("phoneNumber").getValue(String::class.java) ?: ""

                    val contact = Contact(name, phone, key)
                    contactsList.add(contact)
                }
                adapter.notifyDataSetChanged()
                updateEmptyView()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load contacts from Firebase", Toast.LENGTH_SHORT).show()
                loadFromSharedPreferences()
            }
    }


    private fun loadFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences("SafeGuardPrefs", MODE_PRIVATE)
        val contactsSet = sharedPreferences.getStringSet("emergency_contacts", HashSet()) ?: HashSet()

        for (contactStr in contactsSet) {
            val parts = contactStr.split("|")
            if (parts.size == 2) {
                contactsList.add(Contact(parts[0], parts[1]))
            }
        }

        adapter.notifyDataSetChanged()
        updateEmptyView()
    }


    private fun saveContacts() {
        // Save contacts to SharedPreferences or Database
        val sharedPreferences = getSharedPreferences("SafeGuardPrefs", MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val contactsSet = HashSet<String>()
        for (contact in contactsList) {
            contactsSet.add("${contact.name}|${contact.phoneNumber}")
        }

        editor.putStringSet("emergency_contacts", contactsSet)
        editor.apply()
    }

    private fun addContact(contact: Contact) {
        val uid = auth.currentUser?.uid ?: return

        val contactData = hashMapOf(
            "name" to contact.name,
            "phoneNumber" to contact.phoneNumber
        )

        // Using Firebase Realtime Database instead of Firestore
        val contactRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("contacts")

        val contactId = contactRef.push().key  // Generate a unique ID for each contact

        if (contactId != null) {
            contactRef.child(contactId).setValue(contactData)
                .addOnSuccessListener {
                    // Successfully added contact to Firebase
                    contactsList.add(contact)
                    adapter.notifyItemInserted(contactsList.size - 1)
                    updateEmptyView()
                    Toast.makeText(this, "Contact added to Firebase", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    // Failure to add contact to Firebase
                    Toast.makeText(this, "Failed to add contact to Firebase", Toast.LENGTH_SHORT).show()
                }
        } else {
            // If the contact ID was not generated properly
            Toast.makeText(this, "Failed to generate contact ID", Toast.LENGTH_SHORT).show()
        }
    }


    private fun removeContact(contact: Contact) {
        val uid = auth.currentUser?.uid ?: return
        val contactKey = contact.firebaseKey

        if (contactKey.isNotEmpty()) {
            val contactRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .child("contacts")
                .child(contactKey)

            contactRef.removeValue()
                .addOnSuccessListener {
                    val position = contactsList.indexOf(contact)
                    if (position != -1) {
                        contactsList.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        updateEmptyView()
                    }
                    Toast.makeText(this, "Contact deleted from Firebase", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to delete contact from Firebase", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Invalid Firebase key", Toast.LENGTH_SHORT).show()
        }
    }


    private fun clearInputFields() {
        nameInput.text.clear()
        phoneInput.text.clear()
    }

    private fun updateEmptyView() {
        if (contactsList.isEmpty()) {
            emptyContactsText.visibility = View.VISIBLE
            contactsRecyclerView.visibility = View.GONE
        } else {
            emptyContactsText.visibility = View.GONE
            contactsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showDeleteConfirmationDialog(contact: Contact) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete ${contact.name}?")
            .setPositiveButton("Delete") { _, _ ->
                removeContact(contact)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Permission handling
    private fun checkContactPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestContactPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_CONTACTS),
            CONTACT_PERMISSION_CODE
        )
    }

    private fun pickContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        startActivityForResult(intent, CONTACT_PICK_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CONTACT_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pickContact()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CONTACT_PICK_CODE && resultCode == RESULT_OK) {
            val contactUri = data?.data ?: return
            val cursor: Cursor? = contentResolver.query(contactUri, null, null, null, null)

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    val name = it.getString(nameIndex)
                    val number = it.getString(numberIndex)

                    addContact(Contact(name, number))
                }
            }
        }
    }
}



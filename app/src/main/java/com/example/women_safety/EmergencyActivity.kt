package com.example.women_safety

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EmergencyActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EmergencyContactAdapter
    private lateinit var searchView: SearchView

    private val CALL_PERMISSION_REQUEST_CODE = 200
    private var selectedPhoneNumber: String? = null

    // List of emergency contacts
    private val allEmergencyContacts = listOf(
        Pair("Police Control Room (PCR)", "112"),
        Pair("Delhi Women Helpline", "1091"),
        Pair("Special Cell (NE States)", "1093"),
        Pair("Missing Persons Helpline", "1094"),
        Pair("Traffic Helpline", "1095"),
        Pair("Senior Citizen Helpline", "1291"),
        Pair("Cybercrime", "1930"),
        Pair("Railway Police Helpline", "1512"),
        Pair("Eyes and Ears (Citizen Reporting)", "14547"),
        Pair("Upload Evidence (WhatsApp)", "9910641064"),
        Pair("Fire Brigade", "101"),
        Pair("Ambulance", "102"),
        Pair("AIIMS Hospital", "01126588500"),
        Pair("Safdarjung Hospital", "01126165060"),
        Pair("Delhi Disaster Management", "1077"),
        Pair("Delhi Metro Helpline", "155370"),
        Pair("National Emergency Number", "112"),
        Pair("Childline", "1098")
    )

    // Filtered contacts list that will be used by the adapter
    private var filteredContacts = allEmergencyContacts.toList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency)

        recyclerView = findViewById(R.id.emergency_recycler_view)
        searchView = findViewById(R.id.search_view)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize adapter with filtered contacts
        adapter = EmergencyContactAdapter(filteredContacts) { phoneNumber ->
            selectedPhoneNumber = phoneNumber
            checkAndRequestCallPermission()
        }

        recyclerView.adapter = adapter

        // Set up search functionality
        setupSearchView()
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterContacts(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterContacts(newText)
                return true
            }
        })
    }

    private fun filterContacts(query: String?) {
        if (query.isNullOrBlank()) {
            // If query is empty, show all contacts
            filteredContacts = allEmergencyContacts.toList()
        } else {
            // Filter contacts that match the query in name or number
            filteredContacts = allEmergencyContacts.filter { contact ->
                contact.first.contains(query, ignoreCase = true) ||
                        contact.second.contains(query, ignoreCase = true)
            }
        }

        // Update the adapter with new filtered list
        updateAdapter()
    }

    private fun updateAdapter() {
        adapter = EmergencyContactAdapter(filteredContacts) { phoneNumber ->
            selectedPhoneNumber = phoneNumber
            checkAndRequestCallPermission()
        }
        recyclerView.adapter = adapter
    }

    private fun checkAndRequestCallPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                CALL_PERMISSION_REQUEST_CODE
            )
        } else {
            makePhoneCall()
        }
    }

    private fun makePhoneCall() {
        selectedPhoneNumber?.let { number ->
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$number")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to make call: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALL_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                makePhoneCall()
            } else {
                Toast.makeText(this, "Call permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
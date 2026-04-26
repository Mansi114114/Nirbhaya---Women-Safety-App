package com.example.women_safety

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EmergencyContactAdapter(
    private val contacts: List<Pair<String, String>>,
    private val onCallButtonClick: (String) -> Unit
) : RecyclerView.Adapter<EmergencyContactAdapter.ContactViewHolder>() {

    class ContactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val contactName: TextView = view.findViewById(R.id.contact_name)
        val contactNumber: TextView = view.findViewById(R.id.contact_number)
        val callButton: Button = view.findViewById(R.id.call_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emergency_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        holder.contactName.text = contact.first
        holder.contactNumber.text = contact.second

        holder.callButton.setOnClickListener {
            onCallButtonClick(contact.second)
        }
    }

    override fun getItemCount() = contacts.size
}
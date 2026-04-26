package com.example.women_safety

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit

class SafetyTimerActivity : AppCompatActivity() {

    private lateinit var timerTextView: TextView
    private lateinit var startTimerButton: Button
    private lateinit var cancelTimerButton: Button
    private lateinit var timerDurationEditText: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var statusTextView: TextView

    private var countDownTimer: CountDownTimer? = null
    private var timerRunning = false
    private var timeSelected = 0L // in minutes
    private var timeRemaining = 0L // in milliseconds

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_safety_timer)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize views
        timerTextView = findViewById(R.id.timer_text_view)
        startTimerButton = findViewById(R.id.start_timer_button)
        cancelTimerButton = findViewById(R.id.cancel_timer_button)
        timerDurationEditText = findViewById(R.id.timer_duration_edit_text)
        progressBar = findViewById(R.id.timer_progress_bar)
        statusTextView = findViewById(R.id.status_text_view)

        // Initial UI state
        cancelTimerButton.visibility = View.GONE
        statusTextView.visibility = View.GONE

        // Set up listeners
        startTimerButton.setOnClickListener {
            if (!timerRunning) {
                val inputTime = timerDurationEditText.text.toString()
                if (inputTime.isNotEmpty()) {
                    timeSelected = inputTime.toLong()
                    startTimer(timeSelected * 60 * 1000) // Convert minutes to milliseconds
                } else {
                    Toast.makeText(this, "Please enter a time in minutes", Toast.LENGTH_SHORT).show()
                }
            }
        }

        cancelTimerButton.setOnClickListener {
            showPINDialog()
        }
    }

    private fun startTimer(duration: Long) {
        progressBar.max = duration.toInt() / 1000
        progressBar.progress = progressBar.max

        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                updateTimerUI(millisUntilFinished)

                // Update progress bar
                progressBar.progress = (millisUntilFinished / 1000).toInt()
            }

            override fun onFinish() {
                timerRunning = false
                statusTextView.visibility = View.VISIBLE
                statusTextView.text = "Time's up! Sending SOS..."

                // Send SOS when timer expires
                sendSOSMessage()

                resetTimer()
            }
        }.start()

        timerRunning = true
        startTimerButton.visibility = View.GONE
        timerDurationEditText.visibility = View.GONE
        cancelTimerButton.visibility = View.VISIBLE
        statusTextView.visibility = View.VISIBLE
        statusTextView.text = "Timer running..."
    }

    private fun updateTimerUI(millisUntilFinished: Long) {
        val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

        timerTextView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        timerRunning = false
        startTimerButton.visibility = View.VISIBLE
        timerDurationEditText.visibility = View.VISIBLE
        cancelTimerButton.visibility = View.GONE
        statusTextView.visibility = View.GONE
        timerTextView.text = "00:00:00"
        progressBar.progress = 0
    }

    private fun showPINDialog() {
        val editText = EditText(this)
        editText.hint = "Enter your security PIN"

        AlertDialog.Builder(this)
            .setTitle("Verify Identity")
            .setMessage("Enter your security PIN to cancel the timer")
            .setView(editText)
            .setPositiveButton("Verify") { dialog, _ ->
                // In a real app, you'd verify against a stored PIN
                // For now, we'll use "1234" as an example PIN
                val enteredPin = editText.text.toString()
                if (enteredPin == "1234") {
                    resetTimer()
                    Toast.makeText(this, "Timer cancelled", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun sendSOSMessage() {
        // Check location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                // Generate SOS message
                val message = "🚨 SAFETY TIMER ALERT! I didn't check in on time and may need help.\n" +
                        "📍 Last known location: https://maps.google.com/?q=${location.latitude},${location.longitude}"

                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    // Get emergency contacts
                    val database = FirebaseDatabase.getInstance()
                    val contactsRef = database.getReference("users").child(userId).child("contacts")

                    contactsRef.get().addOnSuccessListener { snapshot ->
                        val phoneNumbers = mutableListOf<String>()

                        for (contactSnapshot in snapshot.children) {
                            val phone = contactSnapshot.child("phoneNumber").getValue(String::class.java)
                            phone?.let { phoneNumbers.add(it) }
                        }

                        if (phoneNumbers.isNotEmpty()) {
                            sendSOSToContacts(phoneNumbers, message)
                        } else {
                            Toast.makeText(this, "No emergency contacts found", Toast.LENGTH_SHORT).show()
                        }
                    }.addOnFailureListener {
                        Toast.makeText(this, "Failed to fetch contacts", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendSOSToContacts(phoneNumbers: List<String>, message: String) {
        for (phoneNumber in phoneNumbers) {
            try {
                val smsManager = android.telephony.SmsManager.getDefault()
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to send SMS to $phoneNumber", Toast.LENGTH_SHORT).show()
            }
        }
        Toast.makeText(this, "SOS sent to ${phoneNumbers.size} contacts", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
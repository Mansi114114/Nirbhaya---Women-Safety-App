package com.example.women_safety

import android.Manifest
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import android.widget.Button
import android.view.animation.AnimationUtils
import android.content.IntentFilter
import android.telephony.SmsManager
import android.util.Log
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {
    private lateinit var sosButton: Button
    // Initialize it as nullable to avoid lateinit issues
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private lateinit var sentPI: android.app.PendingIntent
    private lateinit var deliveredPI: android.app.PendingIntent
    private lateinit var smsSentReceiver: BroadcastReceiver
    private lateinit var smsDeliveredReceiver: BroadcastReceiver

    private val locationPermissionRequestCode = 100
    private val smsPermissionRequestCode = 123
    private val smsSentAction = "SMS_SENT"
    private val smsDeliveredAction = "SMS_DELIVERED"
    private var lastKnownLocation: Location? = null

    // Flag to track if we need to process SOS intent after init
    private var pendingSosIntent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check FIRST if we're launching with an SOS intent
        if (intent?.getBooleanExtra("trigger_sos", false) == true) {
            pendingSosIntent = true
            Log.d("MainActivity", "SOS intent detected, will process after init")
        }

        // Initialize location client BEFORE setContentView to ensure it's ready
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            Log.d("MainActivity", "Location client initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to initialize location client: ${e.message}")
        }


        setContentView(R.layout.activity_main)

        // Start service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, NotificationService::class.java))
        } else {
            startService(Intent(this, NotificationService::class.java))
        }

        // Initialize UI components and SMS-related stuff
        initializeComponents()

        // Check permissions
        checkLocationPermission()
        checkSmsPermission()

        // Now process the pending SOS intent if we have one
        if (pendingSosIntent) {
            Log.d("MainActivity", "Processing pending SOS intent")
            processSosIntent()
            pendingSosIntent = false
        }
    }

    private fun initializeComponents() {
        try {
            // Initialize UI components
            sosButton = findViewById(R.id.sos_button)
            val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse)
            sosButton.startAnimation(pulseAnimation)

            sosButton.setOnClickListener {
                sosButton.clearAnimation()
                getCurrentLocation()
                sosButton.postDelayed({
                    sosButton.startAnimation(pulseAnimation)
                }, 300)
            }

            findViewById<CardView>(R.id.settings_card).setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            findViewById<CardView>(R.id.contacts_card).setOnClickListener {
                startActivity(Intent(this, ContactsActivity::class.java))
            }

            findViewById<CardView>(R.id.emergency_card).setOnClickListener {
                startActivity(Intent(this, EmergencyActivity::class.java))
            }

            findViewById<CardView>(R.id.fake_call_card)?.setOnClickListener {
                try {
                    // Show options dialog
                    val options = arrayOf("Start Fake Call", "Settings")
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Fake Call")
                        .setItems(options) { _, which ->
                            when (which) {
                                0 -> startActivity(Intent(this, FakeCallActivity::class.java))
                                1 -> startActivity(Intent(this, FakeCallSettingsActivity::class.java))
                            }
                        }
                        .show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error with fake call feature: ${e.message}")
                    Toast.makeText(this, "Could not access fake call feature", Toast.LENGTH_SHORT).show()
                }
            }

            // Initialize SMS components
            sentPI = android.app.PendingIntent.getBroadcast(
                this, 0, Intent(smsSentAction), android.app.PendingIntent.FLAG_IMMUTABLE
            )
            deliveredPI = android.app.PendingIntent.getBroadcast(
                this, 0, Intent(smsDeliveredAction), android.app.PendingIntent.FLAG_IMMUTABLE
            )

            smsSentReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (resultCode) {
                        RESULT_OK -> Toast.makeText(this@MainActivity, "✅ SMS Sent Successfully", Toast.LENGTH_SHORT).show()
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> Toast.makeText(this@MainActivity, "❌ SMS Failed: Generic Failure", Toast.LENGTH_SHORT).show()
                        SmsManager.RESULT_ERROR_NO_SERVICE -> Toast.makeText(this@MainActivity, "❌ SMS Failed: No Service", Toast.LENGTH_SHORT).show()
                        SmsManager.RESULT_ERROR_NULL_PDU -> Toast.makeText(this@MainActivity, "❌ SMS Failed: Null PDU", Toast.LENGTH_SHORT).show()
                        SmsManager.RESULT_ERROR_RADIO_OFF -> Toast.makeText(this@MainActivity, "❌ SMS Failed: Radio Off", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            smsDeliveredReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (resultCode) {
                        RESULT_OK -> Toast.makeText(this@MainActivity, "✅ SMS Delivered", Toast.LENGTH_SHORT).show()
                        else -> Toast.makeText(this@MainActivity, "❌ SMS Delivery Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            registerReceiver(
                smsSentReceiver,
                IntentFilter(smsSentAction),
                Context.RECEIVER_NOT_EXPORTED
            )

            registerReceiver(
                smsDeliveredReceiver,
                IntentFilter(smsDeliveredAction),
                Context.RECEIVER_NOT_EXPORTED
            )

            Log.d("MainActivity", "Components initialized successfully")

        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing components: ${e.message}", e)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        try {
            // Check if this is an SOS intent
            if (intent?.getBooleanExtra("trigger_sos", false) == true) {
                Log.d("MainActivity", "New SOS intent received")

                // Make sure fusedLocationClient is initialized
                if (fusedLocationClient == null) {
                    Log.d("MainActivity", "Reinitializing fusedLocationClient in onNewIntent")
                    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
                }

                processSosIntent()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling new intent: ${e.message}", e)
            Toast.makeText(this, "Error processing SOS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processSosIntent() {
        try {
            Toast.makeText(this, "SOS Activated", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Processing SOS intent")

            // Double check location client is initialized
            if (fusedLocationClient == null) {
                Log.d("MainActivity", "Initializing fusedLocationClient in processSosIntent")
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            }

            getCurrentLocation()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error processing SOS intent: ${e.message}", e)
            Toast.makeText(this, "Error activating SOS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentLocation() {
        try {
            // Ensure location client is initialized
            if (fusedLocationClient == null) {
                Log.d("MainActivity", "Initializing fusedLocationClient in getCurrentLocation")
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
                return
            }

            Log.d("MainActivity", "Requesting last location")
            fusedLocationClient?.lastLocation
                ?.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        Log.d("MainActivity", "Location obtained: ${location.latitude}, ${location.longitude}")
                        val message = "SOS Signal Activated!\nLat: ${location.latitude}, Lng: ${location.longitude}"
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        checkAndRequestSmsPermission(location)
                    } else {
                        Log.e("MainActivity", "Location is null")
                        Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
                    }
                }
                ?.addOnFailureListener { e ->
                    Log.e("MainActivity", "Failed to get location: ${e.message}")
                    Toast.makeText(this, "Failed to get location: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in getCurrentLocation: ${e.message}", e)
            Toast.makeText(this, "Error getting location: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionRequestCode
            )
        }
    }

    private fun checkSmsPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_PHONE_STATE
                ),
                smsPermissionRequestCode
            )
        }
    }

    private fun checkAndRequestSmsPermission(location: Location) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                smsPermissionRequestCode
            )
            lastKnownLocation = location
        } else {
            sendSmsWithLocation(location)
        }
    }

    private fun sendSmsWithLocation(location: Location) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        getContactsPhoneNumbers { phoneNumbers ->
            if (phoneNumbers.isEmpty()) {
                Toast.makeText(this, "No contacts to send SMS to", Toast.LENGTH_SHORT).show()
                return@getContactsPhoneNumbers
            }

            val sosRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userId)
                .child("sos_message")

            sosRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val customMessage = snapshot.getValue(String::class.java)
                    val messageToSend = if (!customMessage.isNullOrBlank()) {
                        "$customMessage\n📍 Location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                    } else {
                        "🚨 SOS! I need help.\n📍 Location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                    }

                    sendSmsToNumbers(phoneNumbers, messageToSend)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FIREBASE", "Error fetching SOS message: ${error.message}")
                    val fallbackMessage = "🚨 SOS! I need help.\n📍 Location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                    sendSmsToNumbers(phoneNumbers, fallbackMessage)
                }
            })
        }
    }

    private fun getContactsPhoneNumbers(callback: (List<String>) -> Unit) {
        val phoneNumbers = mutableListOf<String>()
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            val databaseReference = FirebaseDatabase.getInstance()
                .getReference("users").child(userId).child("contacts")

            databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (contactSnapshot in snapshot.children) {
                        val phone = contactSnapshot.child("phoneNumber").getValue(String::class.java)
                        phone?.let {
                            phoneNumbers.add(it)
                        }
                    }
                    callback(phoneNumbers)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Error fetching contacts: ${error.message}")
                    callback(emptyList())
                }
            })
        } else {
            callback(emptyList())
        }
    }

    private fun sendSmsToNumbers(phoneNumbers: List<String>, message: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            for (number in phoneNumbers) {
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(number, null, message, null, null)
                }
                Log.d("SMS_DEBUG", "SMS sent to $number")
            }

            vibratePhone()

        } catch (e: Exception) {
            Log.e("SMS_DEBUG", "SMS sending failed: ${e.message}")
            Toast.makeText(this, "❌ Failed to send SOS", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibratePhone() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(smsSentReceiver)
            unregisterReceiver(smsDeliveredReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receivers: ${e.message}")
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == smsPermissionRequestCode) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                lastKnownLocation?.let { sendSmsWithLocation(it) }
            } else {
                Toast.makeText(this, "SMS permission denied ❌", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
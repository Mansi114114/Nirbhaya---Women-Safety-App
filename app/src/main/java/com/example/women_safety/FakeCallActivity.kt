package com.example.women_safety

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random

class FakeCallActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var callerNameTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var acceptButton: ImageView
    private lateinit var rejectCallButton: ImageView
    private lateinit var endCallButton: Button
    private lateinit var callStatusText: TextView
    private lateinit var callerNumberTextView: TextView
    private lateinit var callerLocationTextView: TextView
    private lateinit var callerLocationOngoingTextView: TextView

    // Call controls
    private lateinit var callControlsLayout: LinearLayout
    private lateinit var muteButton: ImageView
    private lateinit var speakerButton: ImageView
    private lateinit var keypadButton: ImageView

    private var ringtonePlayer: MediaPlayer? = null
    private var voicePlayer: MediaPlayer? = null
    private var soundPool: SoundPool? = null
    private var callConnectSoundId = 0
    private var backgroundNoiseId = 0
    private var callTimerHandler: Handler? = null
    private var callTimerRunnable: Runnable? = null
    private var callTimeSeconds = 0

    // For blinking effect
    private var blinkAnimation: Animation? = null
    private var isCallConnecting = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // Random delays for realism
    private val random = Random()
    private var audioManager: AudioManager? = null
    private var isSpeakerOn = false
    private var isMuted = false

    // Proximity sensor related
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var isCallActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make the activity fullscreen with immersive mode
        setupFullscreenMode()

        try {
            // Keep screen on during call
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            setContentView(R.layout.activity_fake_call)
            Log.d("FakeCallActivity", "Content view set successfully")

            // Initialize audio manager
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Initialize sound effects
            initSoundEffects()

            // Initialize proximity sensor
            initProximitySensor()

            // Initialize views
            try {
                callerNameTextView = findViewById(R.id.caller_name_ongoing)
                timerTextView = findViewById(R.id.call_timer)
                acceptButton = findViewById(R.id.accept_call_button)
                rejectCallButton = findViewById(R.id.reject_call_button)
                endCallButton = findViewById(R.id.end_call_button)
                callStatusText = findViewById(R.id.call_status)
                callerNumberTextView = findViewById(R.id.caller_number)
                callerLocationTextView = findViewById(R.id.caller_location)
                callerLocationOngoingTextView = findViewById(R.id.caller_location_ongoing)

                // Additional call controls
                callControlsLayout = findViewById(R.id.call_controls)
                muteButton = findViewById(R.id.mute_button)
                speakerButton = findViewById(R.id.speaker_button)
                keypadButton = findViewById(R.id.keypad_button)

                // Initialize blinking animation
                blinkAnimation = AlphaAnimation(1.0f, 0.3f).apply {
                    duration = 750
                    repeatCount = Animation.INFINITE
                    repeatMode = Animation.REVERSE
                }

                Log.d("FakeCallActivity", "Views initialized successfully")
            } catch (e: Exception) {
                Log.e("FakeCallActivity", "Error initializing views: ${e.message}")
                Toast.makeText(this, "Error initializing UI elements", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // Set up periodic vibration for incoming call
            startIncomingCallVibration()

            // Set up ringtone
            setupRingtone()

            // Load caller details from settings
            loadCallerDetails()

            // Set up button listeners
            setupButtons()

        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Fatal error in onCreate: ${e.message}")
            Toast.makeText(this, "Failed to initialize call screen", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initProximitySensor() {
        try {
            // Initialize sensor manager
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

            // Get proximity sensor
            proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

            if (proximitySensor == null) {
                Log.w("FakeCallActivity", "No proximity sensor found on this device")
                return
            }

            // Initialize PowerManager for screen control
            powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

            Log.d("FakeCallActivity", "Proximity sensor initialized successfully")
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error initializing proximity sensor: ${e.message}")
        }
    }

    private fun setupFullscreenMode() {
        // Hide status bar and navigation bar
        supportActionBar?.hide()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Immersive mode for API 30+ (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // For older Android versions
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }

    private fun initSoundEffects() {
        try {
            // Initialize SoundPool for quick sound effects
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                soundPool = SoundPool.Builder()
                    .setMaxStreams(3)
                    .setAudioAttributes(audioAttributes)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                soundPool = SoundPool(3, AudioManager.STREAM_MUSIC, 0)
            }

            // Load sounds
            callConnectSoundId = soundPool!!.load(this, R.raw.call_connect, 1)
            backgroundNoiseId = soundPool!!.load(this, R.raw.background_noise, 1)

            Log.d("FakeCallActivity", "Sound effects initialized")
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error initializing sound effects: ${e.message}")
        }
    }

    private fun startIncomingCallVibration() {
        coroutineScope.launch {
            try {
                while (isActive && findViewById<View>(R.id.incoming_call_layout).visibility == View.VISIBLE) {
                    vibratePhone(500)
                    delay(1000)
                    vibratePhone(500)
                    delay(1000)
                    vibratePhone(500)
                    delay(2000)
                }
            } catch (e: Exception) {
                Log.e("FakeCallActivity", "Error in vibration pattern: ${e.message}")
            }
        }
    }

    private fun setupRingtone() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtonePlayer = MediaPlayer.create(applicationContext, notification)
            ringtonePlayer?.isLooping = true
            ringtonePlayer?.start()
            Log.d("FakeCallActivity", "Ringtone setup successfully")
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error playing ringtone: ${e.message}")
            // Continue even if ringtone fails - it's not critical
        }
    }

    private fun loadCallerDetails() {
        // Same implementation as before
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                val databaseReference = FirebaseDatabase.getInstance()
                    .getReference("users").child(userId).child("fake_call_settings")

                // Load caller name
                databaseReference.child("caller_name").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val callerName = snapshot.getValue(String::class.java) ?: "Mom"
                        callerNameTextView.text = callerName
                        // Also update the incoming call name
                        findViewById<TextView>(R.id.caller_name).text = callerName
                        Log.d("FakeCallActivity", "Caller name loaded: $callerName")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("FakeCallActivity", "Failed to load caller name: ${error.message}")
                        callerNameTextView.text = "Mom" // Default name
                        findViewById<TextView>(R.id.caller_name).text = "Mom"
                    }
                })

                // Load caller number
                databaseReference.child("caller_number").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val callerNumber = snapshot.getValue(String::class.java) ?: "+91 9722399473"
                        callerNumberTextView.text = callerNumber
                        Log.d("FakeCallActivity", "Caller number loaded: $callerNumber")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("FakeCallActivity", "Failed to load caller number: ${error.message}")
                        callerNumberTextView.text = "+91 9722399473" // Default number
                    }
                })

                // Load caller location
                databaseReference.child("caller_location").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val callerLocation = snapshot.getValue(String::class.java) ?: "Mobile • Delhi, India"
                        callerLocationTextView.text = callerLocation
                        callerLocationOngoingTextView.text = callerLocation
                        Log.d("FakeCallActivity", "Caller location loaded: $callerLocation")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("FakeCallActivity", "Failed to load caller location: ${error.message}")
                        callerLocationTextView.text = "Mobile • Delhi, India" // Default location
                        callerLocationOngoingTextView.text = "Mobile • Delhi, India"
                    }
                })
            } else {
                // Set defaults if no user logged in
                callerNameTextView.text = "Mom"
                findViewById<TextView>(R.id.caller_name).text = "Mom"
                callerNumberTextView.text = "+91 9722399473"
                callerLocationTextView.text = "Mobile • Delhi, India"
                callerLocationOngoingTextView.text = "Mobile • Delhi, India"
            }
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error loading caller details: ${e.message}")
            // Set default values even if there's an error
            try {
                callerNameTextView.text = "Mom"
                findViewById<TextView>(R.id.caller_name).text = "Mom"
                callerNumberTextView.text = "+91 9722399473"
                callerLocationTextView.text = "Mobile • Delhi, India"
                callerLocationOngoingTextView.text = "Mobile • Delhi, India"
            } catch (e2: Exception) {
                Log.e("FakeCallActivity", "Error setting default values: ${e2.message}")
            }
        }
    }

    private fun setupButtons() {
        try {
            // Accept call button (ImageView)
            acceptButton.setOnClickListener {
                try {
                    // Stop incoming call vibration
                    coroutineScope.cancel()

                    // Stop ringtone
                    ringtonePlayer?.stop()
                    ringtonePlayer?.release()
                    ringtonePlayer = null

                    // Show connecting status
                    callStatusText.text = "Connecting..."
                    callStatusText.startAnimation(blinkAnimation)
                    isCallConnecting = true

                    // Add a realistic delay before connecting the call
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isFinishing && isCallConnecting) {
                            // Play connection sound
                            soundPool?.play(callConnectSoundId, 1f, 1f, 1, 0, 1f)

                            // Show call in progress UI
                            findViewById<View>(R.id.incoming_call_layout).visibility = View.GONE
                            findViewById<View>(R.id.ongoing_call_layout).visibility = View.VISIBLE
                            callStatusText.clearAnimation()
                            isCallConnecting = false
                            isCallActive = true

                            // Register proximity sensor listener now that call is active
                            registerProximitySensor()

                            // Show call controls
                            callControlsLayout.visibility = View.VISIBLE

                            // Show timer
                            timerTextView.visibility = View.VISIBLE
                            startCallTimer()

                            // Play background conversation sounds after a short delay
                            Handler(Looper.getMainLooper()).postDelayed({
                                startBackgroundConversation()
                            }, 1500)

                            // Set up call control buttons functionality
                            setupCallControls()

                            Log.d("FakeCallActivity", "Call connected successfully")
                        }
                    }, 1500 + random.nextInt(1000).toLong())

                    // Set up end call button
                    endCallButton.setOnClickListener {
                        endCall()
                    }

                    Log.d("FakeCallActivity", "Call accept initiated")
                } catch (e: Exception) {
                    Log.e("FakeCallActivity", "Error accepting call: ${e.message}")
                    endCall() // Safely end the call if something goes wrong
                }
            }

            // Decline call button
            rejectCallButton.setOnClickListener {
                endCall()
            }

            Log.d("FakeCallActivity", "Button listeners set up successfully")
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error setting up buttons: ${e.message}")
            Toast.makeText(this, "Error setting up call controls", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun registerProximitySensor() {
        if (proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("FakeCallActivity", "Proximity sensor listener registered")
        }
    }

    private fun unregisterProximitySensor() {
        try {
            sensorManager.unregisterListener(this)
            releaseWakeLock()
            Log.d("FakeCallActivity", "Proximity sensor listener unregistered")
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error unregistering proximity sensor: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val isNear = distance < event.sensor.maximumRange

            if (isCallActive) {
                if (isNear) {
                    // Phone is near ear - turn off screen
                    acquireWakeLock()
                    Log.d("FakeCallActivity", "Proximity sensor: Near ear, turning screen off")
                } else {
                    // Phone is away from ear - turn on screen
                    releaseWakeLock()
                    Log.d("FakeCallActivity", "Proximity sensor: Away from ear, turning screen on")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    private fun acquireWakeLock() {
        try {
            releaseWakeLock() // Release any existing wakelock first

            // Create a new wake lock to turn off the screen
            wakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "FakeCallActivity:ProximityWakeLock"
            )

            wakeLock?.acquire(10*60*1000L) // 10 minutes max
            Log.d("FakeCallActivity", "Wake lock acquired - screen off")
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error acquiring wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d("FakeCallActivity", "Wake lock released - screen on")
            }
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error releasing wake lock: ${e.message}")
        }
    }

    private fun setupCallControls() {
        // Mute button
        muteButton.setOnClickListener {
            isMuted = !isMuted
            if (isMuted) {
                muteButton.alpha = 0.5f
                // If we have voice playback, pause or lower volume
                voicePlayer?.setVolume(0f, 0f)
            } else {
                muteButton.alpha = 1.0f
                voicePlayer?.setVolume(1f, 1f)
            }
        }

        // Speaker button
        speakerButton.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            if (isSpeakerOn) {
                speakerButton.alpha = 0.5f
                audioManager?.isSpeakerphoneOn = true
                // Increase volume for speaker effect
                soundPool?.play(backgroundNoiseId, 0.8f, 0.8f, 1, 0, 1f)
            } else {
                speakerButton.alpha = 1.0f
                audioManager?.isSpeakerphoneOn = false
            }
        }

        // Keypad button - just for show
        keypadButton.setOnClickListener {
            // Optional: Show a keypad overlay and play DTMF tones
            Toast.makeText(this, "Keypad pressed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBackgroundConversation() {
        try {
            // Play ambient noise to simulate a real call
            soundPool?.play(backgroundNoiseId, 0.1f, 0.1f, 1, -1, 1f)

            // Optionally load and play conversation audio if you have it
            // This could be pre-recorded audio clips that sound like someone talking
            try {
                voicePlayer = MediaPlayer.create(this, R.raw.conversation_audio)
                voicePlayer?.isLooping = true
                voicePlayer?.setVolume(0.7f, 0.7f)
                voicePlayer?.start()
            } catch (e: Exception) {
                Log.e("FakeCallActivity", "No conversation audio found: ${e.message}")
                // Not critical if this fails
            }

            // Occasionally simulate network issues
            scheduleNetworkGlitches()

        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error starting background audio: ${e.message}")
        }
    }

    private fun scheduleNetworkGlitches() {
        // Only do this sometimes to be realistic
        if (random.nextInt(3) != 0) return

        coroutineScope.launch {
            try {
                // Wait a random time before first glitch
                delay(15000 + random.nextInt(30000).toLong())

                while (isActive) {
                    // Simulate brief audio dropout
                    voicePlayer?.setVolume(0.1f, 0.1f)
                    delay(300)
                    voicePlayer?.setVolume(0.7f, 0.7f)

                    // Show "Poor connection" briefly
                    withContext(Dispatchers.Main) {
                        val originalText = timerTextView.text
                        timerTextView.text = "Poor connection..."
                        delay(1500)
                        timerTextView.text = originalText
                    }

                    // Wait long time before next glitch
                    delay(20000 + random.nextInt(40000).toLong())
                }
            } catch (e: Exception) {
                Log.e("FakeCallActivity", "Error in network glitch simulation: ${e.message}")
            }
        }
    }

    private fun startCallTimer() {
        try {
            callTimerHandler = Handler(Looper.getMainLooper())
            callTimerRunnable = object : Runnable {
                override fun run() {
                    callTimeSeconds++
                    val minutes = callTimeSeconds / 60
                    val seconds = callTimeSeconds % 60
                    timerTextView.text = String.format("%02d:%02d", minutes, seconds)
                    callTimerHandler?.postDelayed(this, 1000)
                }
            }
            callTimerHandler?.postDelayed(callTimerRunnable!!, 1000)
            Log.d("FakeCallActivity", "Call timer started")
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error starting timer: ${e.message}")
        }
    }

    private fun vibratePhone(duration: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error vibrating phone: ${e.message}")
        }
    }

    private fun endCall() {
        try {
            // Mark call as inactive
            isCallActive = false

            // Unregister proximity sensor
            unregisterProximitySensor()

            // Release wake lock if held
            releaseWakeLock()

            // Stop all coroutines
            coroutineScope.cancel()

            // Stop timer if running
            callTimerHandler?.removeCallbacks(callTimerRunnable!!)

            // Stop ringtone if still playing
            ringtonePlayer?.stop()
            ringtonePlayer?.release()
            ringtonePlayer = null

            // Stop voice player if running
            voicePlayer?.stop()
            voicePlayer?.release()
            voicePlayer = null

            // Release sound pool
            soundPool?.release()
            soundPool = null

            // Reset audio settings
            audioManager?.isSpeakerphoneOn = false

            // One last short vibration for call end feel
            vibratePhone(200)

            Log.d("FakeCallActivity", "Call ended successfully")

            // Finish activity
            finish()
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error ending call: ${e.message}")
            // Finish anyway
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullscreenMode()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-register proximity sensor if call is active
        if (isCallActive) {
            registerProximitySensor()
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister sensor to save battery
        unregisterProximitySensor()
    }

    override fun onDestroy() {
        try {
            // Unregister sensor
            unregisterProximitySensor()

            // Clean up resources
            coroutineScope.cancel()

            ringtonePlayer?.stop()
            ringtonePlayer?.release()
            ringtonePlayer = null

            voicePlayer?.stop()
            voicePlayer?.release()
            voicePlayer = null

            soundPool?.release()
            soundPool = null

            callTimerHandler?.removeCallbacks(callTimerRunnable!!)

            // Clear any animation
            callStatusText.clearAnimation()

            // Reset audio settings
            audioManager?.isSpeakerphoneOn = false

            Log.d("FakeCallActivity", "Resources cleaned up successfully")
        } catch (e: Exception) {
            Log.e("FakeCallActivity", "Error cleaning up resources: ${e.message}")
        }
        super.onDestroy()
    }
}
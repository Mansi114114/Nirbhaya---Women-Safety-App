package com.example.women_safety

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.graphics.Color
import android.app.PendingIntent

class NotificationService : Service() {

    private val CHANNEL_ID = "WomenSafetySOS"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createSOSNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle the notification action if triggered
        if (intent?.action == "SOS_ACTION") {
            // Launch MainActivity with SOS trigger
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // For launching activity from service
                putExtra("trigger_sos", true)
            }
            startActivity(mainIntent)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SOS Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Used for SOS alerts"
                enableLights(true)
                lightColor = Color.RED
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createSOSNotification(): Notification {
        // Create the SOS action
        val sosIntent = Intent(this, MainActivity::class.java).apply {
            action = "SOS_ACTION"
            putExtra("trigger_sos", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sosPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sosIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create the notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sos) // Make sure to create this icon
            .setContentTitle("SOS Emergency Button")
            .setContentText("Tap to send emergency alert")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(Color.RED)
            .setOngoing(true)
            .setContentIntent(sosPendingIntent)
            .addAction(
                R.drawable.ic_sos,
                "SEND SOS",
                sosPendingIntent
            )
            .build()
    }
}
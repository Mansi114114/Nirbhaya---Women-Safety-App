package com.example.women_safety

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class PowerButtonReceiver : BroadcastReceiver() {
    companion object {
        var pressCount = 0
        var firstPressTime: Long = 0
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF || intent?.action == Intent.ACTION_SCREEN_ON) {
            val currentTime = System.currentTimeMillis()

            if (pressCount == 0) {
                firstPressTime = currentTime
            }

            pressCount++

            if (pressCount == 3 && (currentTime - firstPressTime) <= 5000) {
                Toast.makeText(context, "SOS Triggered!", Toast.LENGTH_LONG).show()
                // You can call an SOS function here (e.g. send SMS or start EmergencyActivity)
                pressCount = 0
            } else if ((currentTime - firstPressTime) > 5000) {
                pressCount = 1
                firstPressTime = currentTime
            }
        }
    }
}

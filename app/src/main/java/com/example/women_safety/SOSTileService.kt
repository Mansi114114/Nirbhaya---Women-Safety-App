package com.example.women_safety

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class SOSTileService : TileService() {

    private val TAG = "SOSTileService"

    override fun onStartListening() {
        super.onStartListening()
        try {
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.updateTile()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartListening: ${e.message}")
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()

        try {
            // Visual feedback
            qsTile?.state = Tile.STATE_ACTIVE
            qsTile?.updateTile()

            // Create explicit intent with component name to ensure it finds the right activity
            val intent = Intent().apply {
                setClassName(
                    "com.example.women_safety",
                    "com.example.women_safety.MainActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("trigger_sos", true)
            }

            Log.d(TAG, "Launching SOS intent")

            // Handle locked device
            if (isLocked) {
                Log.d(TAG, "Device is locked, using unlockAndRun")
                unlockAndRun {
                    try {
                        startActivityAndCollapse(intent)
                        Log.d(TAG, "SOS intent launched while locked")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting activity while locked: ${e.message}")
                    }
                }
            } else {
                try {
                    startActivityAndCollapse(intent)
                    Log.d(TAG, "SOS intent launched while unlocked")
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting activity: ${e.message}")
                }
            }

            // Reset tile visual state
            Handler(Looper.getMainLooper()).postDelayed({
                qsTile?.state = Tile.STATE_INACTIVE
                qsTile?.updateTile()
            }, 1000)

        } catch (e: Exception) {
            Log.e(TAG, "Error in onClick: ${e.message}", e)
        }
    }
}

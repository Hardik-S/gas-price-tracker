package com.gasprice.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives geofence transition events from the system.
 * Delegates to MonitoringController to handle the prompt flow.
 *
 * NOTE: Microphone capture is NOT initiated here — this only posts a notification.
 * Audio capture happens only after explicit user interaction in PriceCaptureActivity.
 */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var monitoringController: MonitoringController

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return

        if (event.hasError()) {
            val errorMsg = GeofenceStatusCodes.getStatusCodeString(event.errorCode)
            Log.e(TAG, "Geofence error: $errorMsg")
            return
        }

        val triggeringGeofences = event.triggeringGeofences ?: return
        val transition = event.geofenceTransition
        val location = event.triggeringLocation

        Log.d(TAG, "Geofence transition=$transition, fences=${triggeringGeofences.map { it.requestId }}")

        // Only act on ENTER or DWELL — not EXIT
        // DWELL is preferred as it confirms user is near station, not just passing
        if (transition == com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER ||
            transition == com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_DWELL
        ) {
            // Use goAsync if needed for longer work, but MonitoringController is fast
            val pendingResult = goAsync()
            try {
                monitoringController.onGeofenceTriggered(
                    geofenceIds = triggeringGeofences.map { it.requestId },
                    triggerLocation = location
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "GeofenceReceiver"
    }
}

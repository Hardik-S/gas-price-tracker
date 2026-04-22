package com.gasprice.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Registers Activity Recognition Transition API for vehicle enter/exit events.
 *
 * Only registers IN_VEHICLE transitions to minimize battery impact.
 * Transitions fire a PendingIntent received by ActivityTransitionReceiver.
 *
 * Requires ACTIVITY_RECOGNITION permission (granted by user).
 */
@Singleton
class ActivityRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val client = ActivityRecognition.getClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, ActivityTransitionReceiver::class.java).apply {
            action = ActivityTransitionReceiver.TRANSITION_ACTION
        }
        PendingIntent.getBroadcast(
            context, 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    suspend fun registerTransitions(): Boolean {
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build()
        )
        val request = ActivityTransitionRequest(transitions)

        return suspendCancellableCoroutine { cont ->
            client.requestActivityTransitionUpdates(request, pendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Activity transitions registered")
                    cont.resume(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to register transitions: ${e.message}")
                    cont.resume(false)
                }
        }
    }

    suspend fun unregisterTransitions() {
        suspendCancellableCoroutine<Unit> { cont ->
            client.removeActivityTransitionUpdates(pendingIntent)
                .addOnCompleteListener {
                    Log.d(TAG, "Activity transitions unregistered")
                    cont.resume(Unit)
                }
        }
    }

    companion object {
        private const val TAG = "ActivityRecognitionMgr"
    }
}

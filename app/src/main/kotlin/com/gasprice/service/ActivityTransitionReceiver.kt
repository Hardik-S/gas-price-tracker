package com.gasprice.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives Activity Recognition Transition API events.
 * Notifies MonitoringController of vehicle enter/exit so it can
 * start or stop geofence monitoring accordingly.
 */
@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var monitoringController: MonitoringController

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            Log.d(TAG, "Activity transition: activity=${event.activityType}, transition=${event.transitionType}")

            val isVehicle = event.activityType == DetectedActivity.IN_VEHICLE
            val isEnter = event.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER
            val isExit = event.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_EXIT

            when {
                isVehicle && isEnter -> monitoringController.onDrivingStarted()
                isVehicle && isExit -> monitoringController.onDrivingStopped()
            }
        }
    }

    companion object {
        private const val TAG = "ActivityTransitionRx"
        const val TRANSITION_ACTION = "com.gasprice.ACTIVITY_TRANSITION"
    }
}

package com.gasprice.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.gasprice.domain.model.GasStation
import com.gasprice.domain.model.MonitoringSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manages a rolling window of geofences for candidate gas stations.
 * Clears old geofences and registers new ones as the user moves.
 *
 * Android caps active geofences at 100 per app.
 * We stay well under that (maxActiveGeofences default = 20).
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geofencingClient: GeofencingClient
) {

    private val activeGeofenceIds = mutableSetOf<String>()

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * Registers geofences for [stations], replacing all currently active geofences.
     * Safe to call frequently — clears then re-registers atomically.
     */
    suspend fun updateGeofences(stations: List<GasStation>, settings: MonitoringSettings) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Cannot register geofences — location permission missing")
            return
        }

        clearAllGeofences()

        if (stations.isEmpty()) return

        val geofences = stations.map { station ->
            val id = geofenceId(station)
            Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(
                    station.latitude,
                    station.longitude,
                    settings.geofenceRadiusMeters
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(settings.dwellDelaySeconds * 1000)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofences(geofences)
            .build()

        suspendCancellableCoroutine<Unit> { cont ->
            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    activeGeofenceIds.clear()
                    activeGeofenceIds.addAll(stations.map { geofenceId(it) })
                    Log.d(TAG, "Registered ${geofences.size} geofences")
                    cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Geofence registration failed: ${e.message}")
                    cont.resume(Unit)
                }
        }
    }

    suspend fun clearAllGeofences() {
        if (activeGeofenceIds.isEmpty()) return
        suspendCancellableCoroutine<Unit> { cont ->
            geofencingClient.removeGeofences(geofencePendingIntent)
                .addOnCompleteListener {
                    activeGeofenceIds.clear()
                    Log.d(TAG, "Cleared all geofences")
                    cont.resume(Unit)
                }
        }
    }

    fun geofenceId(station: GasStation): String {
        return station.placeId ?: "geo_${station.latitude}_${station.longitude}"
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "GeofenceManager"
        const val EXTRA_GEOFENCE_TRANSITIONS = "geofence_transitions"
    }
}

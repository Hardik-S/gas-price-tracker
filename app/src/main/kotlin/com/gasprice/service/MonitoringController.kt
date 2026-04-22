package com.gasprice.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.gasprice.GasPriceApp
import com.gasprice.MainActivity
import com.gasprice.data.repository.GasPriceRepository
import com.gasprice.data.repository.PlacesRepository
import com.gasprice.domain.model.GasStation
import com.gasprice.domain.model.MonitoringSettings
import com.gasprice.domain.model.MonitoringState
import com.gasprice.domain.usecase.CooldownTracker
import com.gasprice.domain.usecase.StationRanker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central orchestrator for gas price monitoring.
 *
 * State machine:
 *   IDLE -> (driving detected or manual start) -> DRIVING_DETECTED
 *   DRIVING_DETECTED -> (geofences registered) -> MONITORING_STATIONS
 *   MONITORING_STATIONS -> (geofence triggered) -> AWAITING_PRICE_INPUT
 *   AWAITING_PRICE_INPUT -> (price saved or dismissed) -> MONITORING_STATIONS
 *   Any state -> (driving stopped or manual stop) -> IDLE
 *
 * Location is polled at a battery-conscious cadence (default 30s) only while driving.
 * Geofences are refreshed when user has moved significantly (>200m) or on timer.
 */
@Singleton
class MonitoringController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val geofenceManager: GeofenceManager,
    private val placesRepository: PlacesRepository,
    private val repository: GasPriceRepository,
    private val notificationHelper: NotificationHelper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _monitoringState = MutableStateFlow(MonitoringState.IDLE)
    val monitoringState: StateFlow<MonitoringState> = _monitoringState.asStateFlow()

    private val _currentStation = MutableStateFlow<GasStation?>(null)
    val currentStation: StateFlow<GasStation?> = _currentStation.asStateFlow()

    private val cooldownTracker = CooldownTracker()
    private var settings = MonitoringSettings()

    private var locationPollingJob: Job? = null
    private var lastGeofenceRefreshLocation: Location? = null

    // Map of geofence ID -> GasStation for triggered lookup
    private val registeredStations = mutableMapOf<String, GasStation>()

    // --- Driving state transitions ---

    fun onDrivingStarted() {
        Log.d(TAG, "Driving started")
        if (_monitoringState.value != MonitoringState.IDLE) return
        _monitoringState.value = MonitoringState.DRIVING_DETECTED
        cooldownTracker.resetSession()
        startLocationPolling()
    }

    fun onDrivingStopped() {
        Log.d(TAG, "Driving stopped")
        stopMonitoring()
    }

    /** Manual override — user explicitly starts monitoring */
    fun startMonitoringManually() {
        if (_monitoringState.value == MonitoringState.IDLE) {
            onDrivingStarted()
        }
    }

    /** Manual override — user explicitly stops monitoring */
    fun stopMonitoringManually() {
        stopMonitoring()
    }

    fun updateSettings(newSettings: MonitoringSettings) {
        settings = newSettings
    }

    // --- Location polling ---

    private fun startLocationPolling() {
        locationPollingJob?.cancel()
        locationPollingJob = scope.launch {
            while (isActive) {
                refreshLocationAndGeofences()
                delay(settings.locationIntervalSeconds * 1000L)
            }
        }
    }

    private suspend fun refreshLocationAndGeofences() {
        val location = getLastKnownLocation() ?: return

        // Only refresh geofences if user has moved >200m or this is the first refresh
        val lastLoc = lastGeofenceRefreshLocation
        val movedEnough = lastLoc == null || location.distanceTo(lastLoc) > 200f

        if (!movedEnough) return
        lastGeofenceRefreshLocation = location

        Log.d(TAG, "Refreshing geofences at ${location.latitude}, ${location.longitude}")
        _monitoringState.value = MonitoringState.DRIVING_DETECTED

        val bearing = if (location.hasBearing()) location.bearing else null
        val candidates = placesRepository.getNearbyGasStations(
            location = location,
            radiusMeters = 1500.0,
            currentBearingDegrees = bearing
        )

        val recentIds = repository.getRecentlyCapturedPlaceIds(
            sinceTimestamp = System.currentTimeMillis() - 2 * 60 * 60 * 1000L // last 2h
        )

        val ranked = StationRanker.rank(
            candidates = candidates,
            settings = settings,
            cooldownTracker = cooldownTracker,
            recentlyCapturedPlaceIds = recentIds,
            currentBearingDegrees = bearing
        )

        registeredStations.clear()
        ranked.forEach { station ->
            val id = geofenceManager.geofenceId(station)
            registeredStations[id] = station
        }

        geofenceManager.updateGeofences(ranked, settings)

        if (ranked.isNotEmpty()) {
            _monitoringState.value = MonitoringState.MONITORING_STATIONS
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? {
        return try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                null
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get location: ${e.message}")
            null
        }
    }

    // --- Geofence trigger ---

    /**
     * Called by GeofenceBroadcastReceiver when a station geofence fires.
     * Posts a notification — does NOT start microphone or foreground capture.
     */
    fun onGeofenceTriggered(geofenceIds: List<String>, triggerLocation: Location?) {
        scope.launch {
            // Find the first triggering station that is not on cooldown
            val station = geofenceIds
                .mapNotNull { registeredStations[it] }
                .firstOrNull { !cooldownTracker.isOnCooldown(it, settings) }
                ?: return@launch.also {
                    Log.d(TAG, "Geofence triggered but all stations on cooldown")
                }

            Log.d(TAG, "Prompting for station: ${station.name}")
            cooldownTracker.recordPrompt(station)
            _currentStation.value = station
            _monitoringState.value = MonitoringState.AWAITING_PRICE_INPUT

            postStationNotification(station)
        }
    }

    /** Call when user completes or dismisses the price capture screen */
    fun onPriceCaptureComplete() {
        _currentStation.value = null
        if (_monitoringState.value == MonitoringState.AWAITING_PRICE_INPUT) {
            _monitoringState.value = MonitoringState.MONITORING_STATIONS
        }
    }

    // --- Notification ---

    private fun postStationNotification(station: GasStation) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_PRICE_CAPTURE
            putExtra(EXTRA_STATION_NAME, station.name)
            putExtra(EXTRA_STATION_PLACE_ID, station.placeId)
            putExtra(EXTRA_STATION_ADDRESS, station.address)
            putExtra(EXTRA_STATION_LAT, station.latitude)
            putExtra(EXTRA_STATION_LNG, station.longitude)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_REQUEST_CODE, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val notification = NotificationCompat.Builder(context, GasPriceApp.CHANNEL_STATION_PROMPT)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("⛽ Gas station detected")
            .setContentText("${station.name} — tap to log the price")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(GasPriceApp.NOTIFICATION_ID_STATION_PROMPT, notification)
    }

    // --- Cleanup ---

    private fun stopMonitoring() {
        locationPollingJob?.cancel()
        locationPollingJob = null
        registeredStations.clear()
        lastGeofenceRefreshLocation = null
        _currentStation.value = null
        _monitoringState.value = MonitoringState.IDLE
        scope.launch { geofenceManager.clearAllGeofences() }
    }

    companion object {
        private const val TAG = "MonitoringController"
        private const val NOTIFICATION_REQUEST_CODE = 42
        const val ACTION_OPEN_PRICE_CAPTURE = "com.gasprice.OPEN_PRICE_CAPTURE"
        const val EXTRA_STATION_NAME = "station_name"
        const val EXTRA_STATION_PLACE_ID = "station_place_id"
        const val EXTRA_STATION_ADDRESS = "station_address"
        const val EXTRA_STATION_LAT = "station_lat"
        const val EXTRA_STATION_LNG = "station_lng"
    }
}

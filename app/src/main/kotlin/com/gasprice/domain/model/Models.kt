package com.gasprice.domain.model

import java.util.UUID

/**
 * Represents a single gas price observation captured by the user.
 */
data class GasPriceObservation(
    val id: String = UUID.randomUUID().toString(),
    val stationPlaceId: String? = null,
    val stationName: String,
    val stationAddress: String? = null,
    val latitude: Double,
    val longitude: Double,
    val detectionTimestamp: Long = System.currentTimeMillis(),
    val entrySource: EntrySource,
    val rawTranscript: String? = null,
    val parsedPrice: Double?,
    val parsingStatus: ParsingStatus,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class EntrySource { VOICE, MANUAL }

enum class ParsingStatus {
    SUCCESS,        // Parsed with high confidence
    LOW_CONFIDENCE, // Parsed but needs user confirmation
    FAILED,         // Could not parse
    MANUAL          // User typed directly
}

/**
 * Represents a nearby gas station candidate for geofencing.
 */
data class GasStation(
    val placeId: String?,
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float? = null,
    val bearing: Float? = null  // Degrees from current heading (negative = behind)
)

/**
 * App-wide monitoring state machine.
 */
enum class MonitoringState {
    IDLE,               // Not monitoring
    DRIVING_DETECTED,   // In-vehicle detected, about to set up geofences
    MONITORING_STATIONS,// Geofences active, watching for gas stations
    AWAITING_PRICE_INPUT// Geofence triggered, waiting for user price entry
}

/**
 * Result of parsing a spoken or typed price string.
 */
data class ParsedPrice(
    val value: Double?,
    val status: ParsingStatus,
    val rawInput: String
)

/**
 * Settings that control monitoring behavior.
 */
data class MonitoringSettings(
    val geofenceRadiusMeters: Float = 150f,
    val dwellDelaySeconds: Int = 30,          // How long before dwell geofence fires
    val stationCooldownMinutes: Int = 60,     // Don't re-prompt same station within N minutes
    val globalCooldownSeconds: Int = 120,     // Minimum gap between any two prompts
    val maxPromptsPerSession: Int = 10,       // Hard cap per driving session
    val maxActiveGeofences: Int = 20,         // Well under Android's 100 cap; leave room
    val locationIntervalSeconds: Long = 30    // How often to poll location while driving
)

package com.gasprice.domain.usecase

import com.gasprice.domain.model.GasStation
import com.gasprice.domain.model.MonitoringSettings
import kotlin.math.abs
import java.util.Locale

/**
 * Manages per-station and global prompt cooldowns.
 * In-memory only — survives a single monitoring session.
 * For V1, this is sufficient; can persist to Room if needed later.
 */
class CooldownTracker {

    // placeId (or name+coord hash) -> last prompt timestamp
    private val stationLastPrompt = mutableMapOf<String, Long>()
    private var lastGlobalPrompt: Long = 0L
    private var sessionPromptCount: Int = 0

    fun stationKey(station: GasStation): String {
        return station.placeId ?: listOf(
            station.name.trim().lowercase(Locale.US),
            station.latitude.toStableCoordinateKey(),
            station.longitude.toStableCoordinateKey()
        ).joinToString("_")
    }

    fun isOnCooldown(station: GasStation, settings: MonitoringSettings): Boolean {
        val now = System.currentTimeMillis()
        val key = stationKey(station)

        // Check global cooldown
        val globalCooldownMs = settings.globalCooldownSeconds * 1000L
        if (now - lastGlobalPrompt < globalCooldownMs) return true

        // Check session cap
        if (sessionPromptCount >= settings.maxPromptsPerSession) return true

        // Check per-station cooldown
        val stationCooldownMs = settings.stationCooldownMinutes * 60 * 1000L
        val lastPrompt = stationLastPrompt[key] ?: 0L
        return now - lastPrompt < stationCooldownMs
    }

    fun recordPrompt(station: GasStation) {
        val now = System.currentTimeMillis()
        stationLastPrompt[stationKey(station)] = now
        lastGlobalPrompt = now
        sessionPromptCount++
    }

    fun resetSession() {
        sessionPromptCount = 0
        lastGlobalPrompt = 0L
        // Keep per-station cooldowns — they survive across sessions
    }
}

private fun Double.toStableCoordinateKey(): String {
    // Five decimals is roughly metre-level precision and avoids whole-degree collisions.
    return String.format(Locale.US, "%.5f", this)
}

/**
 * Ranks candidate gas stations for geofence registration.
 *
 * Ranking criteria (higher score = higher priority):
 * 1. Not recently prompted (binary gate)
 * 2. Closer distance (lower = better)
 * 3. Ahead of current bearing (if bearing available)
 * 4. Not recently captured (deprioritize if we already have recent price data)
 *
 * Returns stations sorted descending by score, capped at [maxCount].
 */
object StationRanker {

    data class ScoredStation(val station: GasStation, val score: Double)

    /**
     * @param candidates All nearby gas stations from Places API
     * @param settings Monitoring settings (radius, max geofences)
     * @param cooldownTracker Current cooldown state
     * @param recentlyCapturedPlaceIds Place IDs for stations captured in the last session
     * @param currentBearingDegrees Device bearing/heading (null if unavailable)
     */
    fun rank(
        candidates: List<GasStation>,
        settings: MonitoringSettings,
        cooldownTracker: CooldownTracker,
        recentlyCapturedPlaceIds: Set<String> = emptySet(),
        currentBearingDegrees: Float? = null
    ): List<GasStation> {

        return candidates
            .filter { !cooldownTracker.isOnCooldown(it, settings) }
            .map { station -> ScoredStation(station, score(station, recentlyCapturedPlaceIds, currentBearingDegrees)) }
            .sortedByDescending { it.score }
            .take(settings.maxActiveGeofences)
            .map { it.station }
    }

    private fun score(
        station: GasStation,
        recentlyCapturedPlaceIds: Set<String>,
        currentBearingDegrees: Float?
    ): Double {
        var score = 1000.0

        // Distance penalty: lose up to 400 points over 2km
        val distM = station.distanceMeters ?: 2000f
        score -= (distM / 2000f) * 400.0

        // Direction bonus: if station is ahead (+/- 60 degrees), add 200 points
        // If behind (> 120 degrees off heading), subtract 150
        if (station.bearing != null) {
            val angleDiff = abs(station.bearing)  // station.bearing is relative to heading
            score += when {
                angleDiff <= 60f -> 200.0
                angleDiff <= 90f -> 50.0
                angleDiff > 120f -> -150.0
                else -> 0.0
            }
        }

        // Penalize already-captured stations this session
        if (station.placeId != null && station.placeId in recentlyCapturedPlaceIds) {
            score -= 300.0
        }

        return score
    }
}

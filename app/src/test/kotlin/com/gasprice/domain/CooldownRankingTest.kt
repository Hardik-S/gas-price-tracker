package com.gasprice.domain

import com.gasprice.domain.model.GasStation
import com.gasprice.domain.model.MonitoringSettings
import com.gasprice.domain.usecase.CooldownTracker
import com.gasprice.domain.usecase.StationRanker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CooldownTrackerTest {

    private lateinit var tracker: CooldownTracker
    private val settings = MonitoringSettings(
        stationCooldownMinutes = 60,
        globalCooldownSeconds = 120,
        maxPromptsPerSession = 5
    )

    private fun station(
        name: String,
        placeId: String? = null,
        latitude: Double = 43.0,
        longitude: Double = -79.0
    ) = GasStation(
        placeId = placeId,
        name = name,
        address = null,
        latitude = latitude,
        longitude = longitude
    )

    @Before
    fun setup() {
        tracker = CooldownTracker()
    }

    @Test
    fun `new station is not on cooldown`() {
        val s = station("Shell", "place_1")
        assertFalse(tracker.isOnCooldown(s, settings))
    }

    @Test
    fun `station on cooldown after prompt`() {
        val s = station("Shell", "place_1")
        tracker.recordPrompt(s)
        assertTrue(tracker.isOnCooldown(s, settings))
    }

    @Test
    fun `global cooldown applies immediately after any prompt`() {
        val s1 = station("Shell", "place_1")
        val s2 = station("Esso", "place_2")
        tracker.recordPrompt(s1)
        // s2 was never prompted, but global cooldown should block it
        assertTrue(tracker.isOnCooldown(s2, settings))
    }

    @Test
    fun `session cap blocks further prompts`() {
        val cappedSettings = settings.copy(maxPromptsPerSession = 2)
        tracker.recordPrompt(station("A", "p1"))
        tracker.recordPrompt(station("B", "p2"))
        val s3 = station("C", "p3")
        assertTrue(tracker.isOnCooldown(s3, cappedSettings))
    }

    @Test
    fun `resetSession clears session count but not station cooldowns`() {
        val s = station("Shell", "place_1")
        tracker.recordPrompt(s)
        tracker.resetSession()
        // Station still on cooldown
        assertTrue(tracker.isOnCooldown(s, settings))
    }

    @Test
    fun `station key uses placeId when available`() {
        val withId = station("Shell", "place_abc")
        val withoutId = station("Shell_${43.0}_${-79.0}".hashCode().toString())
        // Different keys should not interfere
        tracker.recordPrompt(withId)
        assertFalse(tracker.isOnCooldown(withoutId, settings.copy(globalCooldownSeconds = 0)))
    }

    @Test
    fun `fallback station key distinguishes nearby same-name stations`() {
        val firstStation = station("Independent", latitude = 43.65321, longitude = -79.38321)
        val secondStation = station("Independent", latitude = 43.65419, longitude = -79.38418)

        tracker.recordPrompt(firstStation)

        assertFalse(tracker.isOnCooldown(secondStation, settings.copy(globalCooldownSeconds = 0)))
    }
}

class StationRankerTest {

    private val settings = MonitoringSettings(maxActiveGeofences = 5)
    private val tracker = CooldownTracker()

    private fun station(name: String, distM: Float, bearing: Float? = null, placeId: String? = null) =
        GasStation(
            placeId = placeId ?: name,
            name = name,
            address = null,
            latitude = 43.0,
            longitude = -79.0,
            distanceMeters = distM,
            bearing = bearing
        )

    @Test
    fun `closer station ranks higher`() {
        val near = station("Near", 100f)
        val far = station("Far", 900f)
        val ranked = StationRanker.rank(listOf(far, near), settings, tracker)
        assertEquals("Near", ranked.first().name)
    }

    @Test
    fun `station ahead of heading ranks higher than one behind`() {
        val ahead = station("Ahead", 300f, bearing = 20f)   // 20° off heading = ahead
        val behind = station("Behind", 300f, bearing = 170f) // 170° off = almost behind
        val ranked = StationRanker.rank(listOf(behind, ahead), settings, tracker)
        assertEquals("Ahead", ranked.first().name)
    }

    @Test
    fun `cooldown station excluded`() {
        val s1 = station("S1", 100f)
        val s2 = station("S2", 200f)
        tracker.recordPrompt(s1)
        // With zero global cooldown, only per-station matters
        val zeroGlobalSettings = settings.copy(globalCooldownSeconds = 0)
        val ranked = StationRanker.rank(listOf(s1, s2), zeroGlobalSettings, tracker)
        assertTrue(ranked.none { it.name == "S1" })
    }

    @Test
    fun `capped at maxActiveGeofences`() {
        val stations = (1..20).map { station("S$it", it * 100f) }
        val ranked = StationRanker.rank(stations, settings, tracker)
        assertTrue(ranked.size <= settings.maxActiveGeofences)
    }

    @Test
    fun `recently captured station deprioritized`() {
        val captured = station("Captured", 100f, placeId = "place_captured")
        val notCaptured = station("Fresh", 200f, placeId = "place_fresh")
        val ranked = StationRanker.rank(
            listOf(captured, notCaptured),
            settings,
            tracker,
            recentlyCapturedPlaceIds = setOf("place_captured")
        )
        assertEquals("Fresh", ranked.first().name)
    }
}

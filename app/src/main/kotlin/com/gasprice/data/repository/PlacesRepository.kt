package com.gasprice.data.repository

import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.gasprice.domain.model.GasStation
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fetches nearby gas stations using the Places Nearby Search API.
 *
 * NOTE: Requires a valid Google API key with Places API enabled.
 * If Places is not initialized or key is missing, returns empty list
 * and logs a warning — app degrades gracefully (no station detection, but no crash).
 */
@Singleton
class PlacesRepository @Inject constructor(
    private val placesClient: PlacesClient
) {

    /**
     * @param location Current user location
     * @param radiusMeters Search radius (typically 500-2000m while driving)
     * @param currentBearingDegrees Device heading in degrees (0=North), used to compute relative bearing
     * @return List of nearby gas stations, unsorted (caller handles ranking)
     */
    suspend fun getNearbyGasStations(
        location: Location,
        radiusMeters: Double = 1000.0,
        currentBearingDegrees: Float? = null
    ): List<GasStation> {
        return try {
            val placeFields = listOf(
                Place.Field.ID,
                Place.Field.DISPLAY_NAME,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.LOCATION,
            )

            val center = LatLng(location.latitude, location.longitude)
            val circle = CircularBounds.newInstance(center, radiusMeters)

            val request = SearchNearbyRequest.builder(circle, placeFields)
                .setIncludedPrimaryTypes(listOf("gas_station"))
                .setMaxResultCount(20)
                .build()

            suspendCancellableCoroutine { cont ->
                placesClient.searchNearby(request)
                    .addOnSuccessListener { response ->
                        val stations = response.places.mapNotNull { place ->
                            val latLng = place.location ?: return@mapNotNull null
                            val distance = FloatArray(1)
                            Location.distanceBetween(
                                location.latitude, location.longitude,
                                latLng.latitude, latLng.longitude,
                                distance
                            )
                            val relativeBearing = if (currentBearingDegrees != null) {
                                computeRelativeBearing(
                                    from = location,
                                    toLat = latLng.latitude,
                                    toLng = latLng.longitude,
                                    headingDegrees = currentBearingDegrees
                                )
                            } else null

                            GasStation(
                                placeId = place.id,
                                name = place.displayName ?: "Gas Station",
                                address = place.formattedAddress,
                                latitude = latLng.latitude,
                                longitude = latLng.longitude,
                                distanceMeters = distance[0],
                                bearing = relativeBearing
                            )
                        }
                        cont.resume(stations)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Nearby gas station search failed: ${e.message}")
                        cont.resume(emptyList())  // Degrade gracefully
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PlacesRepository error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Compute the bearing from [from] to [toLat]/[toLng], expressed as degrees
     * relative to [headingDegrees]. Negative means behind, positive means ahead.
     * Range: approximately -180..180 degrees.
     */
    private fun computeRelativeBearing(
        from: Location,
        toLat: Double,
        toLng: Double,
        headingDegrees: Float
    ): Float {
        val dLng = Math.toRadians(toLng - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(toLat)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val absoluteBearing = Math.toDegrees(atan2(y, x)).toFloat()
        var relative = absoluteBearing - headingDegrees
        // Normalize to -180..180
        while (relative > 180f) relative -= 360f
        while (relative < -180f) relative += 360f
        return relative
    }

    companion object {
        private const val TAG = "PlacesRepository"
    }
}

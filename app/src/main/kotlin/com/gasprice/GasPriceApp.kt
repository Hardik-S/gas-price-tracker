package com.gasprice

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GasPriceApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initPlaces()
        createNotificationChannels()
    }

    private fun initPlaces() {
        val apiKey = BuildConfig.GOOGLE_API_KEY
        if (apiKey.isNotBlank() && !Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }
        // If no key, Places queries will fail gracefully — see PlacesRepository
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Channel for gas station prompts
        NotificationChannel(
            CHANNEL_STATION_PROMPT,
            "Gas Station Detected",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when you pass a gas station while driving"
            enableVibration(true)
            nm.createNotificationChannel(this)
        }

        // Channel for ongoing monitoring status
        NotificationChannel(
            CHANNEL_MONITORING_STATUS,
            "Monitoring Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows active gas price monitoring status"
            setShowBadge(false)
            nm.createNotificationChannel(this)
        }
    }

    companion object {
        const val CHANNEL_STATION_PROMPT = "channel_station_prompt"
        const val CHANNEL_MONITORING_STATUS = "channel_monitoring_status"
        const val NOTIFICATION_ID_MONITORING = 1001
        const val NOTIFICATION_ID_STATION_PROMPT = 1002
    }
}

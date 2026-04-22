package com.gasprice.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gasprice.GasPriceApp
import com.gasprice.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service for active monitoring sessions.
 *
 * Declared with foregroundServiceType="location" in the manifest (Android 14+ requirement).
 * Shows a persistent low-priority notification informing the user monitoring is active.
 *
 * Lifecycle:
 *   - Started by MonitoringForegroundService.start(context)
 *   - Stopped by MonitoringForegroundService.stop(context)
 *   - Destroyed when monitoring is disabled or app is killed
 *
 * NOTE: MonitoringController owns the actual monitoring logic.
 * This service only keeps the process alive and satisfies OS requirements
 * for background location access while driving.
 */
@AndroidEntryPoint
class MonitoringForegroundService : Service() {

    @Inject
    lateinit var monitoringController: MonitoringController

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Starting foreground service")
                startForeground(
                    GasPriceApp.NOTIFICATION_ID_MONITORING,
                    buildStatusNotification()
                )
                monitoringController.startMonitoringManually()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping foreground service")
                monitoringController.stopMonitoringManually()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitoringController.stopMonitoringManually()
    }

    private fun buildStatusNotification() = run {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MonitoringForegroundService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationCompat.Builder(this, GasPriceApp.CHANNEL_MONITORING_STATUS)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Gas Price Tracker")
            .setContentText("Monitoring for nearby gas stations…")
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    companion object {
        private const val TAG = "MonitoringFGS"
        const val ACTION_START = "com.gasprice.START_MONITORING"
        const val ACTION_STOP = "com.gasprice.STOP_MONITORING"

        fun start(context: Context) {
            val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

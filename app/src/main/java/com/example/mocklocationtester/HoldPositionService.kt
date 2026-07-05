package com.example.mocklocationtester

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat

class HoldPositionService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var accuracyMeters = 5f

    private val holdTick = object : Runnable {
        override fun run() {
            pushLastMockLocation()
            handler.postDelayed(this, LOCATION_UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!hasFineLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val lastLocation = LastMockLocationState.load(this)
        if (lastLocation == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        accuracyMeters = MockLocationController.latestState(this).accuracyMeters
        if (!startForegroundNotification()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val beginResult = MockLocationController.beginMode(
            context = this,
            mode = MockMode.HOLD_POSITION,
            latitude = lastLocation.latitude,
            longitude = lastLocation.longitude,
            accuracyMeters = accuracyMeters
        )
        if (!beginResult.success) {
            stopSelf()
            return START_NOT_STICKY
        }

        handler.removeCallbacks(holdTick)
        pushLastMockLocation()
        handler.postDelayed(holdTick, LOCATION_UPDATE_INTERVAL_MS)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        handler.removeCallbacks(holdTick)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun pushLastMockLocation() {
        val lastLocation = LastMockLocationState.load(this) ?: return
        MockLocationController.pushLocation(
            context = this,
            latitude = lastLocation.latitude,
            longitude = lastLocation.longitude,
            accuracyMeters = accuracyMeters,
            speedMetersPerSecond = 0f,
            bearingDegrees = lastLocation.bearingDegrees,
            mode = MockMode.HOLD_POSITION
        )
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startForegroundNotification(): Boolean {
        return try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (exception: SecurityException) {
            false
        } catch (exception: RuntimeException) {
            false
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            pendingIntentFlags
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("模擬定位測試器")
            .setContentText("停留在最後模擬位置")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "停留在最後模擬位置",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "停留在最後模擬位置"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.example.mocklocationtester.action.START_HOLD_POSITION"
        const val ACTION_STOP = "com.example.mocklocationtester.action.STOP_HOLD_POSITION"

        private const val NOTIFICATION_ID = 2002
        private const val NOTIFICATION_CHANNEL_ID = "mock_location_hold_position"
        private const val LOCATION_UPDATE_INTERVAL_MS = 1000L
    }
}

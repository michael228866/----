package com.example.mocklocationtester

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val DEFAULT_MAX_SPEED_KMH = 5f

class FloatingJoystickService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayParams: WindowManager.LayoutParams
    private lateinit var statusTextView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var currentLatitude = 25.033964
    private var currentLongitude = 121.564468
    private var currentAccuracyMeters = 5f
    private var maxSpeedKmh = DEFAULT_MAX_SPEED_KMH
    private var currentSpeedMetersPerSecond = 0f
    private var currentBearingDegrees = 0f
    private var lastLocationUpdateElapsedMillis = 0L
    private var lastError: String? = null

    private val locationTick = object : Runnable {
        override fun run() {
            tickMockLocation()
            handler.postDelayed(this, LOCATION_UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        currentLatitude = intent?.getDoubleExtra(EXTRA_LATITUDE, currentLatitude) ?: currentLatitude
        currentLongitude = intent?.getDoubleExtra(EXTRA_LONGITUDE, currentLongitude) ?: currentLongitude
        currentAccuracyMeters = intent?.getFloatExtra(EXTRA_ACCURACY, currentAccuracyMeters)
            ?: currentAccuracyMeters
        maxSpeedKmh = intent?.getFloatExtra(EXTRA_MAX_SPEED_KMH, maxSpeedKmh) ?: maxSpeedKmh
        maxSpeedKmh = maxSpeedKmh.coerceIn(1f, 100f)

        if (!hasRequiredRuntimePermissions() || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!startForegroundNotification()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val beginResult = MockLocationController.beginMode(
            context = this,
            mode = MockMode.FLOATING_JOYSTICK,
            latitude = currentLatitude,
            longitude = currentLongitude,
            accuracyMeters = currentAccuracyMeters
        )
        lastError = beginResult.message

        if (overlayView == null) {
            addOverlayView()
        }

        lastLocationUpdateElapsedMillis = SystemClock.elapsedRealtime()
        updateOverlayText()
        handler.removeCallbacks(locationTick)
        handler.post(locationTick)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        handler.removeCallbacks(locationTick)
        removeOverlayView()
        MockLocationController.endMode(this, MockMode.FLOATING_JOYSTICK)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun hasRequiredRuntimePermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun addOverlayView() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(170, 18, 20, 24))
            elevation = 12f
            setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
        }

        statusTextView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            includeFontPadding = false
        }
        root.addView(
            statusTextView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = 4.dp()
                topMargin = 4.dp()
            }
        )

        val closeButton = Button(this).apply {
            text = "關閉"
            textSize = 12f
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                stopSelf()
            }
        }
        root.addView(
            closeButton,
            FrameLayout.LayoutParams(58.dp(), 34.dp(), Gravity.TOP or Gravity.END)
        )

        val joystickView = FloatingJoystickView(this).apply {
            setJoystickListener { ratio, bearing, released ->
                advanceLocationByElapsedTime()
                currentSpeedMetersPerSecond = (maxSpeedKmh / KMH_PER_MPS) * ratio.coerceIn(0f, 1f)
                if (ratio > 0.01f) {
                    currentBearingDegrees = normalizeBearing(bearing)
                }
                if (released) {
                    pushCurrentMockLocation()
                }
                updateOverlayText()
            }
        }
        root.addView(
            joystickView,
            FrameLayout.LayoutParams(176.dp(), 176.dp(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                .apply {
                    bottomMargin = 10.dp()
                }
        )

        overlayParams = WindowManager.LayoutParams(
            252.dp(),
            300.dp(),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 48.dp()
            y = 180.dp()
        }

        root.setOnTouchListener(OverlayDragTouchListener())
        windowManager.addView(root, overlayParams)
        overlayView = root
    }

    private fun removeOverlayView() {
        val view = overlayView ?: return
        runCatching {
            windowManager.removeView(view)
        }
        overlayView = null
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun tickMockLocation() {
        advanceLocationByElapsedTime()
        pushCurrentMockLocation()
        updateOverlayText()
    }

    private fun advanceLocationByElapsedTime() {
        val now = SystemClock.elapsedRealtime()
        val last = lastLocationUpdateElapsedMillis
        if (last <= 0L) {
            lastLocationUpdateElapsedMillis = now
            return
        }

        val elapsedSeconds = (now - last) / 1000.0
        if (elapsedSeconds <= 0.0) {
            return
        }

        val distanceMeters = currentSpeedMetersPerSecond.toDouble() * elapsedSeconds
        if (distanceMeters > 0.0) {
            val moved = moveLatLng(
                latDeg = currentLatitude,
                lonDeg = currentLongitude,
                distanceMeters = distanceMeters,
                bearingDeg = currentBearingDegrees.toDouble()
            )
            currentLatitude = moved.first
            currentLongitude = moved.second
        }
        lastLocationUpdateElapsedMillis = now
    }

    private fun pushCurrentMockLocation() {
        val result = MockLocationController.pushLocation(
            context = this,
            latitude = currentLatitude,
            longitude = currentLongitude,
            accuracyMeters = currentAccuracyMeters,
            speedMetersPerSecond = currentSpeedMetersPerSecond,
            bearingDegrees = currentBearingDegrees,
            mode = MockMode.FLOATING_JOYSTICK
        )
        lastError = result.message
        if (result.success) {
            currentLatitude = MockLocationController.currentLat
            currentLongitude = MockLocationController.currentLng
            currentSpeedMetersPerSecond = MockLocationController.currentSpeed
            currentBearingDegrees = MockLocationController.currentBearing
        }
    }

    private fun updateOverlayText() {
        if (!::statusTextView.isInitialized) {
            return
        }

        val baseText = String.format(
            Locale.US,
            "模式 虛擬搖桿\n緯度 %.6f\n經度 %.6f\n目前速度：%.1f km/h\n方向 %.1f 度\n最大速度：%.1f km/h",
            currentLatitude,
            currentLongitude,
            currentSpeedMetersPerSecond * KMH_PER_MPS,
            currentBearingDegrees,
            maxSpeedKmh
        )
        statusTextView.text = lastError?.let { "$baseText\n$it" } ?: baseText
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
            .setContentText("模擬定位搖桿執行中")
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
            "模擬定位搖桿",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "模擬定位搖桿執行中"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).roundToInt()
    }

    private inner class OverlayDragTouchListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = overlayParams.x
                    startY = overlayParams.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    overlayParams.x = startX + (event.rawX - downRawX).roundToInt()
                    overlayParams.y = startY + (event.rawY - downRawY).roundToInt()
                    windowManager.updateViewLayout(view, overlayParams)
                    return true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> return true
            }
            return false
        }
    }

    companion object {
        const val ACTION_START = "com.example.mocklocationtester.action.START_FLOATING_JOYSTICK"
        const val ACTION_STOP = "com.example.mocklocationtester.action.STOP_FLOATING_JOYSTICK"
        const val EXTRA_LATITUDE = "com.example.mocklocationtester.extra.LATITUDE"
        const val EXTRA_LONGITUDE = "com.example.mocklocationtester.extra.LONGITUDE"
        const val EXTRA_ACCURACY = "com.example.mocklocationtester.extra.ACCURACY"
        const val EXTRA_MAX_SPEED_KMH = "com.example.mocklocationtester.extra.MAX_SPEED_KMH"

        private const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_CHANNEL_ID = "mock_location_joystick"
        private const val LOCATION_UPDATE_INTERVAL_MS = 1000L
    }
}

private class FloatingJoystickView(context: Context) : View(context) {
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(95, 0, 150, 136)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private var ratio = 0f
    private var bearingDegrees = 0f
    private var listener: ((Float, Float, Boolean) -> Unit)? = null

    fun setJoystickListener(listener: (Float, Float, Boolean) -> Unit) {
        this.listener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - 12.dp()
        val angleRad = Math.toRadians(bearingDegrees.toDouble())
        val knobDistance = radius * ratio
        val knobX = centerX + sin(angleRad).toFloat() * knobDistance
        val knobY = centerY - cos(angleRad).toFloat() * knobDistance

        canvas.drawCircle(centerX, centerY, radius, outerPaint)
        canvas.drawCircle(centerX, centerY, radius, borderPaint)
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, axisPaint)
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, axisPaint)
        canvas.drawCircle(knobX, knobY, 22.dp().toFloat(), knobPaint)
        canvas.drawCircle(knobX, knobY, 22.dp().toFloat(), borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                updateJoystick(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                ratio = 0f
                listener?.invoke(0f, bearingDegrees, true)
                invalidate()
                return true
            }
        }
        return true
    }

    private fun updateJoystick(x: Float, y: Float) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx * dx + dy * dy).coerceAtMost(radius)

        ratio = (distance / radius).coerceIn(0f, 1f)
        if (ratio > 0.01f) {
            bearingDegrees = normalizeBearing(Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat())
        }

        listener?.invoke(ratio, bearingDegrees, false)
        invalidate()
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).roundToInt()
    }
}

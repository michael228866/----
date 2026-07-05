package com.example.mocklocationtester

import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock

enum class MockMode {
    IDLE,
    MANUAL_JOYSTICK,
    FLOATING_JOYSTICK,
    ROUTE_CRUISE,
    AREA_CRUISE,
    HOLD_POSITION,
    DESTINATION_WALK
}

data class MockLocationState(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float,
    val bearingDegrees: Float,
    val mode: MockMode
)

data class MockPushResult(
    val success: Boolean,
    val message: String? = null
)

object MockLocationController {
    const val ACTION_LOCATION_UPDATE =
        "com.example.mocklocationtester.action.MOCK_LOCATION_UPDATE"
    const val EXTRA_LATITUDE = "com.example.mocklocationtester.extra.LATITUDE"
    const val EXTRA_LONGITUDE = "com.example.mocklocationtester.extra.LONGITUDE"
    const val EXTRA_ACCURACY = "com.example.mocklocationtester.extra.ACCURACY"
    const val EXTRA_SPEED = "com.example.mocklocationtester.extra.SPEED"
    const val EXTRA_BEARING = "com.example.mocklocationtester.extra.BEARING"
    const val EXTRA_MODE = "com.example.mocklocationtester.extra.MODE"

    const val SHARED_PREFS_NAME = "mock_location_controller_state"
    const val PREF_LATITUDE = "latitude"
    const val PREF_LONGITUDE = "longitude"
    const val PREF_ACCURACY = "accuracy"
    const val PREF_SPEED = "speed"
    const val PREF_BEARING = "bearing"
    const val PREF_MODE = "mode"

    private val PROVIDERS = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )

    @Volatile
    var currentLat: Double = 25.033964
        private set

    @Volatile
    var currentLng: Double = 121.564468
        private set

    @Volatile
    var currentAccuracy: Float = 5f
        private set

    @Volatile
    var currentSpeed: Float = 0f
        private set

    @Volatile
    var currentBearing: Float = 0f
        private set

    @Volatile
    var activeMode: MockMode = MockMode.IDLE
        private set

    private val configuredProviders = mutableSetOf<String>()

    @Synchronized
    fun beginMode(
        context: Context,
        mode: MockMode,
        latitude: Double = currentLat,
        longitude: Double = currentLng,
        accuracyMeters: Float = currentAccuracy
    ): MockPushResult {
        activeMode = mode
        currentLat = latitude
        currentLng = longitude
        currentAccuracy = accuracyMeters
        currentSpeed = 0f
        persistAndBroadcast(context)
        return ensureTestProvider(context)
    }

    @Synchronized
    fun endMode(context: Context, mode: MockMode) {
        if (activeMode == mode) {
            currentSpeed = 0f
            LastMockLocationState.save(
                context = context,
                latitude = currentLat,
                longitude = currentLng,
                speedMetersPerSecond = currentSpeed,
                bearingDegrees = currentBearing
            )
            activeMode = if (mode == MockMode.FLOATING_JOYSTICK) {
                MockMode.HOLD_POSITION
            } else {
                MockMode.IDLE
            }
            persistAndBroadcast(context)
        }
    }

    @Synchronized
    fun forceIdle(context: Context) {
        activeMode = MockMode.IDLE
        currentSpeed = 0f
        LastMockLocationState.save(
            context = context,
            latitude = currentLat,
            longitude = currentLng,
            speedMetersPerSecond = currentSpeed,
            bearingDegrees = currentBearing
        )
        persistAndBroadcast(context)
    }

    @Synchronized
    fun pushLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        speedMetersPerSecond: Float,
        bearingDegrees: Float,
        mode: MockMode
    ): MockPushResult {
        if (activeMode != MockMode.IDLE && activeMode != mode) {
            return MockPushResult(
                success = false,
                message = "另一個模式正在送出模擬定位，請先停止目前模式。"
            )
        }

        val providerResult = ensureTestProvider(context)
        if (configuredProviders.isEmpty()) {
            return providerResult
        }

        val manager = locationManager(context)
        val normalizedLongitude = normalizeLongitude(longitude)
        val normalizedBearing = normalizeBearing(bearingDegrees).toFloat()
        val normalizedSpeed = speedMetersPerSecond.coerceAtLeast(0f)
        val timeMillis = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()
        val failures = mutableListOf<String>()
        var pushedAnyProvider = false

        configuredProviders.toList().forEach { provider ->
            val location = createLocation(
                provider = provider,
                latitude = latitude,
                longitude = normalizedLongitude,
                accuracyMeters = accuracyMeters,
                speedMetersPerSecond = normalizedSpeed,
                bearingDegrees = normalizedBearing,
                timeMillis = timeMillis,
                elapsedNanos = elapsedNanos
            )
            try {
                manager.setTestProviderLocation(provider, location)
                pushedAnyProvider = true
            } catch (exception: SecurityException) {
                configuredProviders.remove(provider)
                failures.add("${providerLabel(provider)} 推送失敗：沒有模擬位置權限")
            } catch (exception: IllegalArgumentException) {
                configuredProviders.remove(provider)
                failures.add("${providerLabel(provider)} 推送失敗：測試定位供應器尚未正確啟用")
            } catch (exception: RuntimeException) {
                configuredProviders.remove(provider)
                failures.add("${providerLabel(provider)} 推送失敗")
            }
        }

        return if (pushedAnyProvider) {
            activeMode = mode
            currentLat = latitude
            currentLng = normalizedLongitude
            currentAccuracy = accuracyMeters
            currentSpeed = normalizedSpeed
            currentBearing = normalizedBearing
            LastMockLocationState.save(
                context = context,
                latitude = currentLat,
                longitude = currentLng,
                speedMetersPerSecond = currentSpeed,
                bearingDegrees = currentBearing,
                timestampMillis = timeMillis
            )
            persistAndBroadcast(context)
            MockPushResult(
                success = true,
                message = listOfNotNull(providerResult.message)
                    .plus(failures)
                    .distinct()
                    .joinToString("；")
                    .takeIf { it.isNotBlank() }
            )
        } else {
            MockPushResult(
                success = false,
                message = buildProviderFailureMessage("無法推送任何測試定位供應器", failures)
            )
        }
    }

    @Synchronized
    fun latestState(context: Context): MockLocationState {
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val persistedMode = runCatching {
            MockMode.valueOf(prefs.getString(PREF_MODE, MockMode.IDLE.name) ?: MockMode.IDLE.name)
        }.getOrDefault(MockMode.IDLE)
        val lastMockLocation = LastMockLocationState.load(context)

        if (lastMockLocation != null) {
            currentLat = lastMockLocation.latitude
            currentLng = lastMockLocation.longitude
            currentAccuracy = prefs.getFloat(PREF_ACCURACY, currentAccuracy)
            currentSpeed = if (persistedMode == MockMode.IDLE) {
                0f
            } else {
                lastMockLocation.speedMetersPerSecond
            }
            currentBearing = lastMockLocation.bearingDegrees
            activeMode = persistedMode
        } else if (persistedMode != MockMode.IDLE &&
            prefs.contains(PREF_LATITUDE) &&
            prefs.contains(PREF_LONGITUDE)
        ) {
            currentLat = Double.fromBits(prefs.getLong(PREF_LATITUDE, currentLat.toRawBits()))
            currentLng = Double.fromBits(prefs.getLong(PREF_LONGITUDE, currentLng.toRawBits()))
            currentAccuracy = prefs.getFloat(PREF_ACCURACY, currentAccuracy)
            currentSpeed = prefs.getFloat(PREF_SPEED, currentSpeed)
            currentBearing = prefs.getFloat(PREF_BEARING, currentBearing)
            activeMode = persistedMode
        } else {
            currentLat = 25.033964
            currentLng = 121.564468
            currentSpeed = 0f
            currentBearing = 0f
            activeMode = MockMode.IDLE
        }

        return MockLocationState(
            latitude = currentLat,
            longitude = currentLng,
            accuracyMeters = currentAccuracy,
            speedMetersPerSecond = currentSpeed,
            bearingDegrees = currentBearing,
            mode = activeMode
        )
    }

    @Synchronized
    fun clearTestProvider(context: Context) {
        val manager = locationManager(context)
        PROVIDERS.forEach { provider ->
            runCatching {
                manager.setTestProviderEnabled(provider, false)
            }
            runCatching {
                manager.removeTestProvider(provider)
            }
        }
        configuredProviders.clear()
    }

    @Synchronized
    fun clearMockLocation(context: Context) {
        currentSpeed = 0f
        activeMode = MockMode.IDLE
        LastMockLocationState.save(
            context = context,
            latitude = currentLat,
            longitude = currentLng,
            speedMetersPerSecond = currentSpeed,
            bearingDegrees = currentBearing
        )
        clearTestProvider(context)
        persistAndBroadcast(context)
    }

    @Synchronized
    fun lastMockLocation(context: Context): LastMockLocationState? {
        return LastMockLocationState.load(context)
    }

    @Synchronized
    fun clearLastMockLocation(context: Context) {
        LastMockLocationState.clear(context)
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_LATITUDE)
            .remove(PREF_LONGITUDE)
            .remove(PREF_SPEED)
            .remove(PREF_BEARING)
            .remove(PREF_MODE)
            .apply()
    }

    @Suppress("DEPRECATION")
    @Synchronized
    fun ensureTestProvider(context: Context): MockPushResult {
        val manager = locationManager(context)
        val failures = mutableListOf<String>()

        PROVIDERS.forEach { provider ->
            if (configuredProviders.contains(provider)) {
                val enabled = runCatching {
                    manager.setTestProviderEnabled(provider, true)
                }.isSuccess
                if (enabled) {
                    return@forEach
                }
                configuredProviders.remove(provider)
            }

            val failure = configureTestProvider(manager, provider)
            if (failure == null) {
                configuredProviders.add(provider)
            } else {
                failures.add(failure)
            }
        }

        return if (configuredProviders.isNotEmpty()) {
            MockPushResult(
                success = true,
                message = failures.distinct().joinToString("；").takeIf { it.isNotBlank() }
            )
        } else {
            MockPushResult(
                success = false,
                message = buildProviderFailureMessage("無法建立任何測試定位供應器", failures)
            )
        }
    }

    private fun createLocation(
        provider: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        speedMetersPerSecond: Float,
        bearingDegrees: Float,
        timeMillis: Long,
        elapsedNanos: Long
    ): Location {
        return Location(provider).apply {
            this.latitude = latitude
            this.longitude = longitude
            this.accuracy = accuracyMeters
            this.speed = speedMetersPerSecond
            this.bearing = bearingDegrees
            this.time = timeMillis
            this.elapsedRealtimeNanos = elapsedNanos
        }
    }

    @Suppress("DEPRECATION")
    private fun configureTestProvider(
        manager: LocationManager,
        provider: String
    ): String? {
        runCatching {
            manager.removeTestProvider(provider)
        }

        return try {
            manager.addTestProvider(
                provider,
                false,
                provider == LocationManager.NETWORK_PROVIDER,
                false,
                false,
                false,
                provider == LocationManager.GPS_PROVIDER,
                true,
                Criteria.POWER_LOW,
                if (provider == LocationManager.GPS_PROVIDER) {
                    Criteria.ACCURACY_FINE
                } else {
                    Criteria.ACCURACY_COARSE
                }
            )
            manager.setTestProviderEnabled(provider, true)
            null
        } catch (exception: SecurityException) {
            "${providerLabel(provider)} 建立失敗：沒有模擬位置權限"
        } catch (exception: IllegalArgumentException) {
            "${providerLabel(provider)} 建立失敗：測試定位供應器不支援或尚未清除"
        } catch (exception: RuntimeException) {
            "${providerLabel(provider)} 建立失敗"
        }
    }

    private fun buildProviderFailureMessage(prefix: String, failures: List<String>): String {
        return if (failures.isEmpty()) {
            "$prefix，請確認模擬位置設定與定位權限。"
        } else {
            "$prefix：${failures.distinct().joinToString("；")}"
        }
    }

    private fun providerLabel(provider: String): String {
        return when (provider) {
            LocationManager.GPS_PROVIDER -> "GPS_PROVIDER"
            LocationManager.NETWORK_PROVIDER -> "NETWORK_PROVIDER"
            else -> provider
        }
    }

    private fun locationManager(context: Context): LocationManager {
        return context.applicationContext.getSystemService(LocationManager::class.java)
    }

    private fun persistAndBroadcast(context: Context) {
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_LATITUDE, currentLat.toRawBits())
            .putLong(PREF_LONGITUDE, currentLng.toRawBits())
            .putFloat(PREF_ACCURACY, currentAccuracy)
            .putFloat(PREF_SPEED, currentSpeed)
            .putFloat(PREF_BEARING, currentBearing)
            .putString(PREF_MODE, activeMode.name)
            .apply()

        val intent = Intent(ACTION_LOCATION_UPDATE).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_LATITUDE, currentLat)
            putExtra(EXTRA_LONGITUDE, currentLng)
            putExtra(EXTRA_ACCURACY, currentAccuracy)
            putExtra(EXTRA_SPEED, currentSpeed)
            putExtra(EXTRA_BEARING, currentBearing)
            putExtra(EXTRA_MODE, activeMode.name)
        }
        context.sendBroadcast(intent)
    }
}

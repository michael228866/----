package com.example.mocklocationtester

import android.content.Context

data class LastMockLocationState(
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Float,
    val bearingDegrees: Float,
    val timestampMillis: Long
) {
    companion object {
        private const val PREFS_NAME = "last_mock_location_state"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
        private const val KEY_SPEED = "speed"
        private const val KEY_BEARING = "bearing"
        private const val KEY_TIMESTAMP = "timestamp"

        fun load(context: Context): LastMockLocationState? {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.contains(KEY_LATITUDE) || !prefs.contains(KEY_LONGITUDE)) {
                return null
            }

            val latitude = Double.fromBits(prefs.getLong(KEY_LATITUDE, 0L))
            val longitude = Double.fromBits(prefs.getLong(KEY_LONGITUDE, 0L))
            if (!isValidLatLng(latitude, longitude)) {
                clear(context)
                return null
            }

            return LastMockLocationState(
                latitude = latitude,
                longitude = longitude,
                speedMetersPerSecond = prefs.getFloat(KEY_SPEED, 0f).coerceAtLeast(0f),
                bearingDegrees = normalizeBearing(prefs.getFloat(KEY_BEARING, 0f)),
                timestampMillis = prefs.getLong(KEY_TIMESTAMP, 0L)
            )
        }

        fun save(
            context: Context,
            latitude: Double,
            longitude: Double,
            speedMetersPerSecond: Float,
            bearingDegrees: Float,
            timestampMillis: Long = System.currentTimeMillis()
        ) {
            if (!isValidLatLng(latitude, longitude)) {
                return
            }

            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LATITUDE, latitude.toRawBits())
                .putLong(KEY_LONGITUDE, longitude.toRawBits())
                .putFloat(KEY_SPEED, speedMetersPerSecond.coerceAtLeast(0f))
                .putFloat(KEY_BEARING, normalizeBearing(bearingDegrees))
                .putLong(KEY_TIMESTAMP, timestampMillis)
                .apply()
        }

        fun clear(context: Context) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }
}

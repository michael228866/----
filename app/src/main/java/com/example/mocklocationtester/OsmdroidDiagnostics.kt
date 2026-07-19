package com.example.mocklocationtester

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import java.util.concurrent.atomic.AtomicInteger

internal object OsmdroidDiagnostics {
    private const val TAG = "OsmdroidDiagnostics"

    private val mapViewCreateCount = AtomicInteger()
    private val tileSourceSetCount = AtomicInteger()
    private val mapViewResumeCount = AtomicInteger()
    private val mapViewPauseCount = AtomicInteger()
    private val mapViewDetachCount = AtomicInteger()
    @Volatile
    private var enabled = false

    fun logApplicationStarted(context: Context, expectedUserAgent: String) {
        enabled = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!enabled) {
            return
        }

        val configuration = Configuration.getInstance()
        val headerName = configuration.userAgentHttpHeader
        val policyUserAgent = if (TileSourceFactory.MAPNIK.tileSourcePolicy.normalizesUserAgent()) {
            configuration.normalizedUserAgent
        } else {
            configuration.userAgentValue
        }
        val finalRequestUserAgent = configuration.additionalHttpRequestProperties[headerName]
            ?: policyUserAgent

        Log.d(TAG, "Application User-Agent=$expectedUserAgent")
        Log.d(TAG, "MAPNIK final HTTP request $headerName=$finalRequestUserAgent")
        if (finalRequestUserAgent != expectedUserAgent) {
            Log.e(TAG, "MAPNIK User-Agent mismatch: expected=$expectedUserAgent actual=$finalRequestUserAgent")
        }
    }

    fun mapViewCreated() = logCount("MapView created", mapViewCreateCount)

    fun tileSourceSet() = logCount("setTileSource executed", tileSourceSetCount)

    fun mapViewResumed() = logCount("MapView onResume", mapViewResumeCount)

    fun mapViewPaused() = logCount("MapView onPause", mapViewPauseCount)

    fun mapViewDetached() = logCount("MapView onDetach", mapViewDetachCount)

    private fun logCount(event: String, counter: AtomicInteger) {
        if (enabled) {
            Log.d(TAG, "$event count=${counter.incrementAndGet()}")
        }
    }
}

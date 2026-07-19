package com.example.mocklocationtester

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.osmdroid.config.Configuration

private const val OSM_USER_AGENT_APP_NAME = "MockLocationTester"
private const val OSM_USER_AGENT_PROJECT_URL = "https://github.com/michael228866/----"
private const val OSM_USER_AGENT_CONTACT_EMAIL = "michael226688@google.com"
private const val OSMDROID_PREFERENCES_NAME = "osmdroid"

class MockLocationTesterApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val configuration = Configuration.getInstance()
        configuration.load(
            this,
            getSharedPreferences(OSMDROID_PREFERENCES_NAME, Context.MODE_PRIVATE)
        )

        val userAgent = buildOpenStreetMapUserAgent()
        configuration.userAgentValue = userAgent

        // MAPNIK normalizes the regular osmdroid User-Agent. This final request
        // property deliberately overrides that normalized value on the connection.
        configuration.additionalHttpRequestProperties[configuration.userAgentHttpHeader] = userAgent
        OsmdroidDiagnostics.logApplicationStarted(this, userAgent)
    }

    private fun buildOpenStreetMapUserAgent(): String {
        return "$OSM_USER_AGENT_APP_NAME/${packageVersionName()} " +
            "(+$OSM_USER_AGENT_PROJECT_URL; contact: $OSM_USER_AGENT_CONTACT_EMAIL)"
    }

    private fun packageVersionName(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName?.takeIf { it.isNotBlank() } ?: "unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }
}

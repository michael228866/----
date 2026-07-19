package com.example.mocklocationtester

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import org.osmdroid.config.Configuration

private const val OSM_USER_AGENT_APP_NAME = "MockLocationTester"
private const val OSM_USER_AGENT_PROJECT_URL = "https://github.com/michael228866/----"

class MockLocationTesterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = buildOpenStreetMapUserAgent()
    }

    private fun buildOpenStreetMapUserAgent(): String {
        val versionName = packageVersionName()
        return "$OSM_USER_AGENT_APP_NAME/$versionName (+$OSM_USER_AGENT_PROJECT_URL)"
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

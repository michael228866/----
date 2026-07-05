package com.example.mocklocationtester

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ConsumerLocationGroup(
    gpsLocation: ConsumerLocationUi?,
    networkLocation: ConsumerLocationUi?,
    fusedLocation: ConsumerLocationUi?
) {
    ConsumerLocationPanel(title = "GPS_PROVIDER 收到的位置", location = gpsLocation)
    ConsumerLocationPanel(title = "NETWORK_PROVIDER 收到的位置", location = networkLocation)
    ConsumerLocationPanel(title = "融合定位服務收到的位置", location = fusedLocation)
}

@Composable
private fun ConsumerLocationPanel(
    title: String,
    location: ConsumerLocationUi?
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
    ConsumerLocationView(location = location)
}

@Composable
private fun ConsumerLocationView(location: ConsumerLocationUi?) {
    if (location == null) {
        Text(
            text = "尚未收到位置",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    InfoRow(label = "緯度", value = formatNumber(location.latitude, 7))
    InfoRow(label = "經度", value = formatNumber(location.longitude, 7))
    InfoRow(label = "精度", value = "${formatNumber(location.accuracyMeters.toDouble(), 1)} 公尺")
    InfoRow(label = "速度", value = "${formatNumber(location.speedMetersPerSecond.toDouble(), 2)} 公尺/秒")
    InfoRow(label = "方向角", value = "${formatNumber(location.bearingDegrees.toDouble(), 1)} 度")
    InfoRow(label = "時間", value = location.timeMillis.toString())
    InfoRow(label = "開機後奈秒", value = location.elapsedRealtimeNanos.toString())
    InfoRow(label = "模擬定位", value = if (location.isMock) "是" else "否")
}

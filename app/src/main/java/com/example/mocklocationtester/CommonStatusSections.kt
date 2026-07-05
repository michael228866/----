package com.example.mocklocationtester

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CommonStatusSections(
    permissionStatusText: String,
    overlayPermissionStatusText: String,
    isMockLocationAppSelected: Boolean,
    hasOverlayPermission: Boolean,
    activeModeText: String,
    mockStatusText: String,
    overlayStatusText: String,
    errorMessages: List<String>,
    onRequestPermissions: () -> Unit,
    onOpenOverlayPermissionSettings: () -> Unit
) {
    Section(title = "狀態") {
        InfoRow(label = "定位權限", value = permissionStatusText)
        InfoRow(label = "懸浮視窗權限", value = overlayPermissionStatusText)
        InfoRow(label = "模擬位置應用程式", value = if (isMockLocationAppSelected) "已設定" else "未設定")
        InfoRow(label = "目前模式", value = activeModeText)
        InfoRow(label = "模擬定位狀態", value = mockStatusText)
        InfoRow(label = "懸浮搖桿狀態", value = overlayStatusText)
        OutlinedButton(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
            Text("重新請求定位權限")
        }
        OutlinedButton(onClick = onOpenOverlayPermissionSettings, modifier = Modifier.fillMaxWidth()) {
            Text(if (hasOverlayPermission) "開啟懸浮視窗設定" else "授權懸浮視窗權限")
        }
    }

    if (errorMessages.isNotEmpty()) {
        ErrorMessages(messages = errorMessages)
    }
}

@Composable
fun CurrentLocationSection(
    currentLatitude: Double,
    currentLongitude: Double,
    currentAccuracyMeters: Float,
    currentSpeedMetersPerSecond: Float,
    currentBearingDegrees: Float
) {
    Section(title = "目前位置") {
        InfoRow(label = "緯度", value = formatNumber(currentLatitude, 7))
        InfoRow(label = "經度", value = formatNumber(currentLongitude, 7))
        InfoRow(label = "精度", value = "${formatNumber(currentAccuracyMeters.toDouble(), 1)} 公尺")
        InfoRow(label = "速度", value = "目前速度：${formatNumber((currentSpeedMetersPerSecond * KMH_PER_MPS).toDouble(), 1)} km/h")
        InfoRow(label = "方向角", value = "${formatNumber(currentBearingDegrees.toDouble(), 1)} 度")
    }
}

@Composable
fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun ErrorMessages(messages: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "錯誤訊息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            messages.forEach { message ->
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

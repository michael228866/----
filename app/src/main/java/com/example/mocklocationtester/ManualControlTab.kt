package com.example.mocklocationtester

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ManualControlTab(
    maxSpeedKmh: Float,
    currentLatitude: Double,
    currentLongitude: Double,
    currentAccuracyMeters: Float,
    currentSpeedMetersPerSecond: Float,
    currentBearingDegrees: Float,
    hasLastMockLocation: Boolean,
    isConsumerRunning: Boolean,
    gpsConsumerLocation: ConsumerLocationUi?,
    networkConsumerLocation: ConsumerLocationUi?,
    consumerLocation: ConsumerLocationUi?,
    onMaxSpeedChange: (Float) -> Unit,
    onUseLastMockLocationAsStart: () -> Unit,
    onUseCurrentPhoneLocationAsStart: () -> Unit,
    onApplySpecifiedCoordinate: (String, String, String) -> Unit,
    onClearLastMockLocation: () -> Unit,
    onResetToTaipei101: () -> Unit,
    onStopMockLocation: () -> Unit,
    onStartFloatingJoystick: () -> Unit,
    onStopFloatingJoystick: () -> Unit,
    onStartConsumer: () -> Unit,
    onStopConsumer: () -> Unit
) {
    var specifiedLatitudeText by rememberSaveable {
        mutableStateOf(formatNumber(currentLatitude, 7))
    }
    var specifiedLongitudeText by rememberSaveable {
        mutableStateOf(formatNumber(currentLongitude, 7))
    }
    var specifiedAccuracyText by rememberSaveable { mutableStateOf("5") }

    Section(title = "定位狀態與起點") {
        InfoRow(label = "目前模擬位置", value = "${formatNumber(currentLatitude, 7)}, ${formatNumber(currentLongitude, 7)}")
        InfoRow(label = "精度", value = "${formatNumber(currentAccuracyMeters.toDouble(), 1)} 公尺")
        InfoRow(label = "速度", value = "目前速度：${formatNumber((currentSpeedMetersPerSecond * KMH_PER_MPS).toDouble(), 1)} km/h")
        InfoRow(label = "方向角", value = "${formatNumber(currentBearingDegrees.toDouble(), 1)} 度")
        OutlinedTextField(
            value = specifiedLatitudeText,
            onValueChange = { specifiedLatitudeText = it },
            label = { Text("指定緯度") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = specifiedLongitudeText,
            onValueChange = { specifiedLongitudeText = it },
            label = { Text("指定經度") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = specifiedAccuracyText,
            onValueChange = { specifiedAccuracyText = it },
            label = { Text("精度（公尺）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                onApplySpecifiedCoordinate(
                    specifiedLatitudeText,
                    specifiedLongitudeText,
                    specifiedAccuracyText
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("套用指定座標")
        }
        OutlinedButton(
            onClick = onUseLastMockLocationAsStart,
            enabled = hasLastMockLocation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("使用最後位置作為起點")
        }
        OutlinedButton(onClick = onUseCurrentPhoneLocationAsStart, modifier = Modifier.fillMaxWidth()) {
            Text("使用目前手機位置作為起點")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onClearLastMockLocation,
                enabled = hasLastMockLocation,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("清除保存位置", textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = onResetToTaipei101,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("重設為台北 101", textAlign = TextAlign.Center)
            }
        }
        Button(onClick = onStopMockLocation, modifier = Modifier.fillMaxWidth()) {
            Text("停止模擬定位")
        }
    }

    Section(title = "懸浮搖桿") {
        InfoRow(label = "最大速度", value = "${formatNumber(maxSpeedKmh.toDouble(), 1)} km/h")
        Slider(
            value = maxSpeedKmh,
            onValueChange = onMaxSpeedChange,
            valueRange = 1f..100f
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onStartFloatingJoystick,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("啟動懸浮搖桿", textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = onStopFloatingJoystick,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("關閉懸浮搖桿", textAlign = TextAlign.Center)
            }
        }
    }

    Section(title = "除錯資訊") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onStartConsumer,
                enabled = !isConsumerRunning,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("開始接收定位", textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = onStopConsumer,
                enabled = isConsumerRunning,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("停止接收定位", textAlign = TextAlign.Center)
            }
        }
        ConsumerLocationGroup(
            gpsLocation = gpsConsumerLocation,
            networkLocation = networkConsumerLocation,
            fusedLocation = consumerLocation
        )
    }
}

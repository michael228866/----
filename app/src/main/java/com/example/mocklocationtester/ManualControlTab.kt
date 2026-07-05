package com.example.mocklocationtester

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ManualControlTab(
    latitudeInput: String,
    longitudeInput: String,
    accuracyInput: String,
    maxSpeedKmh: Float,
    currentLatitude: Double,
    currentLongitude: Double,
    currentAccuracyMeters: Float,
    currentSpeedMetersPerSecond: Float,
    currentBearingDegrees: Float,
    isWalking: Boolean,
    hasLastMockLocation: Boolean,
    isConsumerRunning: Boolean,
    gpsConsumerLocation: ConsumerLocationUi?,
    networkConsumerLocation: ConsumerLocationUi?,
    consumerLocation: ConsumerLocationUi?,
    onLatitudeInputChange: (String) -> Unit,
    onLongitudeInputChange: (String) -> Unit,
    onAccuracyInputChange: (String) -> Unit,
    onMaxSpeedChange: (Float) -> Unit,
    onApplyStartLocation: () -> Unit,
    onUseLastMockLocationAsStart: () -> Unit,
    onUseCurrentPhoneLocationAsStart: () -> Unit,
    onClearLastMockLocation: () -> Unit,
    onHoldPosition: () -> Unit,
    onStopMockLocation: () -> Unit,
    onPushCurrentLocation: () -> Unit,
    onStartWalking: () -> Unit,
    onStopWalking: () -> Unit,
    onStartFloatingJoystick: () -> Unit,
    onStopFloatingJoystick: () -> Unit,
    onJoystickChange: (Float, Float) -> Unit,
    onJoystickRelease: () -> Unit,
    onStartConsumer: () -> Unit,
    onStopConsumer: () -> Unit
) {
    Section(title = "起始位置") {
        OutlinedTextField(
            value = latitudeInput,
            onValueChange = onLatitudeInputChange,
            label = { Text("起始緯度") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = longitudeInput,
            onValueChange = onLongitudeInputChange,
            label = { Text("起始經度") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = accuracyInput,
            onValueChange = onAccuracyInputChange,
            label = { Text("精度，單位公尺") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = onApplyStartLocation, modifier = Modifier.fillMaxWidth()) {
            Text("套用起始座標")
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
        OutlinedButton(
            onClick = onClearLastMockLocation,
            enabled = hasLastMockLocation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("清除最後位置")
        }
    }

    Section(title = "手動速度") {
        InfoRow(label = "搖桿最大速度", value = "最大速度：${formatNumber(maxSpeedKmh.toDouble(), 1)} km/h")
        InfoRow(label = "目前速度", value = "目前速度：${formatNumber((currentSpeedMetersPerSecond * KMH_PER_MPS).toDouble(), 1)} km/h")
        Slider(
            value = maxSpeedKmh,
            onValueChange = onMaxSpeedChange,
            valueRange = 1f..100f
        )
    }

    CurrentLocationSection(
        currentLatitude = currentLatitude,
        currentLongitude = currentLongitude,
        currentAccuracyMeters = currentAccuracyMeters,
        currentSpeedMetersPerSecond = currentSpeedMetersPerSecond,
        currentBearingDegrees = currentBearingDegrees
    )

    Section(title = "虛擬搖桿") {
        JoystickPad(
            maxSpeedMetersPerSecond = maxSpeedKmh / KMH_PER_MPS,
            speedMetersPerSecond = currentSpeedMetersPerSecond,
            bearingDegrees = currentBearingDegrees,
            onChange = onJoystickChange,
            onRelease = onJoystickRelease
        )
    }

    Section(title = "模擬定位控制") {
        Button(onClick = onPushCurrentLocation, modifier = Modifier.fillMaxWidth()) {
            Text("推送目前位置")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onHoldPosition,
                enabled = hasLastMockLocation,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("停留在最後位置", textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = onStopMockLocation,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("停止模擬定位", textAlign = TextAlign.Center)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onStartWalking,
                enabled = !isWalking,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("開始走路模擬", textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = onStopWalking,
                enabled = isWalking,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("停止")
            }
        }
    }

    Section(title = "懸浮搖桿") {
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
                Text("停止懸浮搖桿", textAlign = TextAlign.Center)
            }
        }
    }

    Section(title = "定位接收示範") {
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
        Spacer(modifier = Modifier.height(2.dp))
        ConsumerLocationGroup(
            gpsLocation = gpsConsumerLocation,
            networkLocation = networkConsumerLocation,
            fusedLocation = consumerLocation
        )
    }
}

@Composable
private fun JoystickPad(
    maxSpeedMetersPerSecond: Float,
    speedMetersPerSecond: Float,
    bearingDegrees: Float,
    onChange: (Float, Float) -> Unit,
    onRelease: () -> Unit
) {
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val primarySoft = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val outline = MaterialTheme.colorScheme.outline
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(230.dp)
                .onSizeChanged { padSize = it }
                .pointerInput(maxSpeedMetersPerSecond) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            updateJoystickFromOffset(offset, padSize, onChange)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            updateJoystickFromOffset(change.position, padSize, onChange)
                        },
                        onDragEnd = { onRelease() },
                        onDragCancel = { onRelease() }
                    )
                }
        ) {
            val strokeWidth = with(density) { 2.dp.toPx() }
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - with(density) { 12.dp.toPx() }
            val axisPadding = with(density) { 20.dp.toPx() }
            val ratio = if (maxSpeedMetersPerSecond <= 0f) {
                0f
            } else {
                (speedMetersPerSecond / maxSpeedMetersPerSecond).coerceIn(0f, 1f)
            }
            val angleRad = Math.toRadians(bearingDegrees.toDouble())
            val knobDistance = radius * ratio
            val knob = Offset(
                x = center.x + sin(angleRad).toFloat() * knobDistance,
                y = center.y - cos(angleRad).toFloat() * knobDistance
            )

            drawCircle(color = primarySoft, radius = radius, center = center)
            drawCircle(color = outline, radius = radius, center = center, style = Stroke(strokeWidth))
            drawLine(
                color = outlineVariant,
                start = Offset(center.x, center.y - radius + axisPadding),
                end = Offset(center.x, center.y + radius - axisPadding),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = outlineVariant,
                start = Offset(center.x - radius + axisPadding, center.y),
                end = Offset(center.x + radius - axisPadding, center.y),
                strokeWidth = strokeWidth
            )
            drawCircle(
                color = onSurfaceVariant.copy(alpha = 0.18f),
                radius = with(density) { 34.dp.toPx() },
                center = center
            )
            drawCircle(color = primary, radius = with(density) { 22.dp.toPx() }, center = knob)
            drawCircle(
                color = outline,
                radius = with(density) { 22.dp.toPx() },
                center = knob,
                style = Stroke(strokeWidth)
            )
        }
    }
}

private fun updateJoystickFromOffset(
    offset: Offset,
    size: IntSize,
    onChange: (Float, Float) -> Unit
) {
    if (size.width <= 0 || size.height <= 0) {
        onChange(0f, 0f)
        return
    }

    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val distance = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
    val ratio = (distance / radius).coerceIn(0f, 1f)
    val bearing = if (ratio <= 0.01f) {
        0f
    } else {
        normalizeBearing(Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))).toFloat()
    }

    onChange(ratio, bearing)
}

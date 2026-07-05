package com.example.mocklocationtester

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun MapPatrolTab(
    modifier: Modifier = Modifier,
    statusContent: @Composable () -> Unit,
    currentLatitude: Double,
    currentLongitude: Double,
    currentAccuracyMeters: Float,
    currentSpeedMetersPerSecond: Float,
    currentBearingDegrees: Float,
    routeSpeedKmh: Float,
    areaSpeedKmh: Float,
    routeEndBehavior: RouteEndBehavior,
    routeStartMode: RouteStartMode,
    mapEditMode: MapEditMode,
    areaRouteMode: AreaRouteMode,
    routeWaypoints: List<LatLng>,
    areaPolygonPoints: List<LatLng>,
    generatedAreaWaypoints: List<LatLng>,
    isRouteCruising: Boolean,
    isAreaCruising: Boolean,
    hasLastMockLocation: Boolean,
    mapRecenterRequest: Int,
    onRouteSpeedChange: (Float) -> Unit,
    onAreaSpeedChange: (Float) -> Unit,
    onRouteEndBehaviorChange: (RouteEndBehavior) -> Unit,
    onRouteStartModeChange: (RouteStartMode) -> Unit,
    onMapEditModeChange: (MapEditMode) -> Unit,
    onAreaRouteModeChange: (AreaRouteMode) -> Unit,
    onUseLastMockLocationAsStart: () -> Unit,
    onUseCurrentPhoneLocationAsStart: () -> Unit,
    onClearLastMockLocation: () -> Unit,
    onMapClick: (LatLng) -> Unit,
    onDeleteRouteWaypoint: (Int) -> Unit,
    onDeleteAreaPolygonPoint: (Int) -> Unit,
    onUndoLastMapPoint: () -> Unit,
    onClearRoute: () -> Unit,
    onClearAreaPolygon: () -> Unit,
    onGenerateAreaRoute: () -> Unit,
    onStartRouteCruise: () -> Unit,
    onStartAreaCruise: () -> Unit,
    onStopCruise: () -> Unit
) {
    var selectedPoint by remember { mutableStateOf<MapPointSelection?>(null) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RouteMapView(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            currentPosition = LatLng(currentLatitude, currentLongitude),
            routeWaypoints = routeWaypoints,
            areaPolygonPoints = areaPolygonPoints,
            generatedWaypoints = generatedAreaWaypoints,
            externalRecenterRequest = mapRecenterRequest,
            onMapClick = onMapClick,
            onMapPointClick = { selectedPoint = it }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            statusContent()

            selectedPoint?.let { selection ->
                SelectedMapPointPanel(
                    selection = selection,
                    onDelete = {
                        when (selection.type) {
                            MapPointType.ROUTE -> onDeleteRouteWaypoint(selection.index)
                            MapPointType.AREA -> onDeleteAreaPolygonPoint(selection.index)
                        }
                        selectedPoint = null
                    },
                    onCancel = { selectedPoint = null }
                )
            }

            Section(title = "目前位置") {
                InfoRow(label = "緯度", value = formatNumber(currentLatitude, 7))
                InfoRow(label = "經度", value = formatNumber(currentLongitude, 7))
                InfoRow(label = "精度", value = "${formatNumber(currentAccuracyMeters.toDouble(), 1)} 公尺")
                InfoRow(label = "速度", value = "目前速度：${formatNumber((currentSpeedMetersPerSecond * KMH_PER_MPS).toDouble(), 1)} km/h")
                InfoRow(label = "方向角", value = "${formatNumber(currentBearingDegrees.toDouble(), 1)} 度")
            }

            Section(title = "地圖操作") {
                InfoRow(label = "目前模式", value = mapEditMode.title)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SelectButton(
                        text = "路徑選點",
                        selected = mapEditMode == MapEditMode.ROUTE,
                        onClick = { onMapEditModeChange(MapEditMode.ROUTE) },
                        modifier = Modifier.weight(1f)
                    )
                    SelectButton(
                        text = "區域圈選",
                        selected = mapEditMode == MapEditMode.AREA,
                        onClick = { onMapEditModeChange(MapEditMode.AREA) },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedButton(onClick = onUndoLastMapPoint, modifier = Modifier.fillMaxWidth()) {
                    Text("復原上一個點")
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

            Section(title = "路徑選點") {
                InfoRow(label = "路徑點", value = "${routeWaypoints.size} 個")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(routeWaypoints) { index, point ->
                        PointChip(index = index, point = point)
                    }
                }
                OutlinedButton(onClick = onClearRoute, modifier = Modifier.fillMaxWidth()) {
                    Text("清除全部路徑")
                }
            }

            Section(title = "區域圈選") {
                InfoRow(label = "範圍點", value = "${areaPolygonPoints.size} 個")
                InfoRow(label = "區域路徑點", value = "${generatedAreaWaypoints.size} 個")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(areaPolygonPoints) { index, point ->
                        PointChip(index = index, point = point)
                    }
                }
                Text(text = "區域產生方式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SelectButton(
                        text = "隨機巡航",
                        selected = areaRouteMode == AreaRouteMode.RANDOM,
                        onClick = { onAreaRouteModeChange(AreaRouteMode.RANDOM) },
                        modifier = Modifier.weight(1f)
                    )
                    SelectButton(
                        text = "蛇形掃描",
                        selected = areaRouteMode == AreaRouteMode.SERPENTINE,
                        onClick = { onAreaRouteModeChange(AreaRouteMode.SERPENTINE) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onClearAreaPolygon, modifier = Modifier.weight(1f)) {
                        Text("清除全部範圍", textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = onGenerateAreaRoute,
                        enabled = areaPolygonPoints.size >= 3,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("產生區域路徑", textAlign = TextAlign.Center)
                    }
                }
            }

            Section(title = "巡航設定") {
                InfoRow(label = "路徑巡航速度", value = "目前速度：${formatNumber(routeSpeedKmh.toDouble(), 1)} km/h")
                Slider(
                    value = routeSpeedKmh,
                    onValueChange = onRouteSpeedChange,
                    valueRange = 1f..100f
                )
                Text(text = "路徑起點模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SelectButton(
                        text = "從最近點開始",
                        selected = routeStartMode == RouteStartMode.NEAREST,
                        onClick = { onRouteStartModeChange(RouteStartMode.NEAREST) },
                        modifier = Modifier.weight(1f)
                    )
                    SelectButton(
                        text = "從第一點開始",
                        selected = routeStartMode == RouteStartMode.FIRST,
                        onClick = { onRouteStartModeChange(RouteStartMode.FIRST) },
                        modifier = Modifier.weight(1f)
                    )
                }
                InfoRow(label = "區域巡航速度", value = "目前速度：${formatNumber(areaSpeedKmh.toDouble(), 1)} km/h")
                Slider(
                    value = areaSpeedKmh,
                    onValueChange = onAreaSpeedChange,
                    valueRange = 1f..100f
                )
                Text(text = "結束方式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SelectButton(
                        text = "停止",
                        selected = routeEndBehavior == RouteEndBehavior.STOP,
                        onClick = { onRouteEndBehaviorChange(RouteEndBehavior.STOP) },
                        modifier = Modifier.weight(1f)
                    )
                    SelectButton(
                        text = "循環",
                        selected = routeEndBehavior == RouteEndBehavior.LOOP,
                        onClick = { onRouteEndBehaviorChange(RouteEndBehavior.LOOP) },
                        modifier = Modifier.weight(1f)
                    )
                    SelectButton(
                        text = "折返",
                        selected = routeEndBehavior == RouteEndBehavior.PING_PONG,
                        onClick = { onRouteEndBehaviorChange(RouteEndBehavior.PING_PONG) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Section(title = "巡航控制") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onStartRouteCruise,
                        enabled = !isRouteCruising && !isAreaCruising,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("開始路徑巡航", textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = onStartAreaCruise,
                        enabled = !isRouteCruising && !isAreaCruising && generatedAreaWaypoints.size >= 2,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("開始區域巡航", textAlign = TextAlign.Center)
                    }
                }
                OutlinedButton(
                    onClick = onStopCruise,
                    enabled = isRouteCruising || isAreaCruising,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("停止巡航")
                }
            }
        }
    }
}

@Composable
private fun SelectedMapPointPanel(
    selection: MapPointSelection,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Section(title = if (selection.type == MapPointType.ROUTE) "路徑點操作" else "範圍點操作") {
        Text("第 ${selection.index + 1} 個點", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text("刪除此點")
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun PointChip(index: Int, point: LatLng) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = "${index + 1}：${formatNumber(point.latitude, 5)}, ${formatNumber(point.longitude, 5)}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SelectButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(text, textAlign = TextAlign.Center)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text, textAlign = TextAlign.Center)
        }
    }
}

enum class RouteStartMode(val title: String) {
    NEAREST("從最近點開始"),
    FIRST("從第一點開始")
}

enum class AreaRouteMode(val title: String) {
    RANDOM("隨機巡航"),
    SERPENTINE("蛇形掃描")
}

enum class MapEditMode(val title: String) {
    ROUTE("路徑選點"),
    AREA("區域圈選")
}

enum class MapPointType {
    ROUTE,
    AREA
}

data class MapPointSelection(
    val type: MapPointType,
    val index: Int
)

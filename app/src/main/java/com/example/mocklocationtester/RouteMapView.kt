package com.example.mocklocationtester

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.sqrt

@Composable
fun RouteMapView(
    modifier: Modifier = Modifier,
    currentPosition: LatLng,
    routeWaypoints: List<LatLng>,
    areaPolygonPoints: List<LatLng>,
    generatedWaypoints: List<LatLng>,
    externalRecenterRequest: Int,
    onMapClick: (LatLng) -> Unit,
    onMapPointClick: (MapPointSelection) -> Unit
) {
    var hasInitializedCamera by rememberSaveable { mutableStateOf(false) }
    var userMovedCamera by rememberSaveable { mutableStateOf(false) }
    var followCurrentLocation by rememberSaveable { mutableStateOf(false) }
    var localRecenterRequest by rememberSaveable { mutableStateOf(0) }
    val effectiveRecenterRequest = externalRecenterRequest + localRecenterRequest

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    userMovedCamera = false
                    localRecenterRequest += 1
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("回到目前位置")
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text("跟隨目前位置", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = followCurrentLocation,
                    onCheckedChange = { checked ->
                        followCurrentLocation = checked
                        if (checked) {
                            userMovedCamera = false
                        }
                    }
                )
            }
        }

        AndroidView(
            modifier = modifier,
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(17.0)
                    tag = effectiveRecenterRequest
                    if (!hasInitializedCamera) {
                        controller.setCenter(GeoPoint(currentPosition.latitude, currentPosition.longitude))
                        hasInitializedCamera = true
                    }
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                            }

                            MotionEvent.ACTION_MOVE -> {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                userMovedCamera = true
                                followCurrentLocation = false
                            }

                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }
                }
            },
            update = { map ->
                map.overlays.clear()
                map.overlays.add(MapTapOverlay(onMapClick, routeWaypoints, areaPolygonPoints))

                if (routeWaypoints.size >= 2) {
                    val routeLine = Polyline().apply {
                        setPoints(routeWaypoints.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.color = Color.rgb(80, 120, 220)
                        outlinePaint.strokeWidth = 4f
                    }
                    map.overlays.add(routeLine)
                }

                if (areaPolygonPoints.size >= 2) {
                    val polygonPoints = areaPolygonPoints.map { GeoPoint(it.latitude, it.longitude) }.toMutableList()
                    if (areaPolygonPoints.size >= 3) {
                        val first = areaPolygonPoints.first()
                        polygonPoints.add(GeoPoint(first.latitude, first.longitude))
                    }
                    val areaLine = Polyline().apply {
                        setPoints(polygonPoints)
                        outlinePaint.color = Color.rgb(40, 150, 90)
                        outlinePaint.strokeWidth = 4f
                    }
                    map.overlays.add(areaLine)
                }

                if (generatedWaypoints.size >= 2) {
                    val generatedLine = Polyline().apply {
                        setPoints(generatedWaypoints.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.color = Color.rgb(220, 80, 80)
                        outlinePaint.strokeWidth = 5f
                    }
                    map.overlays.add(generatedLine)
                }

                routeWaypoints.forEachIndexed { index, point ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(point.latitude, point.longitude)
                            title = "路徑點 ${index + 1}"
                            icon = numberedMarkerIcon(map.context, index + 1, Color.rgb(60, 110, 210))
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { _, _ ->
                                onMapPointClick(MapPointSelection(MapPointType.ROUTE, index))
                                true
                            }
                        }
                    )
                }

                areaPolygonPoints.forEachIndexed { index, point ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(point.latitude, point.longitude)
                            title = "範圍點 ${index + 1}"
                            icon = numberedMarkerIcon(map.context, index + 1, Color.rgb(40, 150, 90))
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { _, _ ->
                                onMapPointClick(MapPointSelection(MapPointType.AREA, index))
                                true
                            }
                        }
                    )
                }

                generatedWaypoints.forEachIndexed { index, point ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(point.latitude, point.longitude)
                            title = "區域點 ${index + 1}"
                            icon = numberedMarkerIcon(map.context, index + 1, Color.rgb(210, 75, 75))
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                    )
                }

                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(currentPosition.latitude, currentPosition.longitude)
                        title = "目前位置"
                        icon = currentMarkerIcon(map.context)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                )

                val currentGeoPoint = GeoPoint(currentPosition.latitude, currentPosition.longitude)
                val handledRecenterRequest = map.tag as? Int
                when {
                    handledRecenterRequest != effectiveRecenterRequest -> {
                        map.controller.setCenter(currentGeoPoint)
                        map.tag = effectiveRecenterRequest
                    }

                    followCurrentLocation && !userMovedCamera -> {
                        map.controller.setCenter(currentGeoPoint)
                    }
                }
                map.invalidate()
            }
        )
    }
}

private class MapTapOverlay(
    private val onMapClick: (LatLng) -> Unit,
    private val routeWaypoints: List<LatLng>,
    private val areaPolygonPoints: List<LatLng>
) : Overlay() {
    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        if (isNearEditableMarker(event, mapView)) {
            return false
        }

        val geo = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
        onMapClick(LatLng(geo.latitude, geo.longitude))
        return true
    }

    private fun isNearEditableMarker(event: MotionEvent, mapView: MapView): Boolean {
        val threshold = 36f * mapView.resources.displayMetrics.density
        return (routeWaypoints + areaPolygonPoints).any { point ->
            val screenPoint = mapView.projection.toPixels(GeoPoint(point.latitude, point.longitude), null)
            val dx = event.x - screenPoint.x
            val dy = event.y - screenPoint.y
            sqrt(dx * dx + dy * dy) <= threshold
        }
    }
}

private fun numberedMarkerIcon(context: Context, number: Int, color: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (34 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, circlePaint)
    val baseline = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(number.toString(), size / 2f, baseline, textPaint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun currentMarkerIcon(context: Context): BitmapDrawable {
    return numberedMarkerIcon(context, 0, Color.rgb(0, 150, 136))
}

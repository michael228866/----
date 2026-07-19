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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.sqrt

private const val OPENSTREETMAP_ATTRIBUTION = "\u00A9 OpenStreetMap contributors"

@Composable
fun RouteMapView(
    modifier: Modifier = Modifier,
    currentPosition: LatLng,
    routeWaypoints: List<LatLng>,
    areaPolygonPoints: List<LatLng>,
    generatedWaypoints: List<LatLng>,
    destinationWaypoints: List<LatLng>,
    externalRecenterRequest: Int,
    onMapClick: (LatLng) -> Unit,
    onMapPointClick: (MapPointSelection) -> Unit
) {
    var hasInitializedCamera by rememberSaveable { mutableStateOf(false) }
    var userMovedCamera by rememberSaveable { mutableStateOf(false) }
    var followCurrentLocation by rememberSaveable { mutableStateOf(false) }
    var localRecenterRequest by rememberSaveable { mutableStateOf(0) }
    val effectiveRecenterRequest = externalRecenterRequest + localRecenterRequest
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner, mapView) {
        val activeMapView = mapView
        if (activeMapView == null) {
            onDispose { }
        } else {
            var isResumed = false
            var isDetached = false

            fun resumeMapView() {
                if (!isResumed && !isDetached) {
                    activeMapView.onResume()
                    isResumed = true
                }
            }

            fun pauseMapView() {
                if (isResumed) {
                    activeMapView.onPause()
                    isResumed = false
                }
            }

            fun detachMapView() {
                if (!isDetached) {
                    pauseMapView()
                    activeMapView.onDetach()
                    isDetached = true
                }
            }

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> resumeMapView()
                    Lifecycle.Event.ON_PAUSE -> pauseMapView()
                    Lifecycle.Event.ON_DESTROY -> detachMapView()
                    else -> Unit
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                resumeMapView()
            }

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                detachMapView()
            }
        }
    }

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
                    addOpenStreetMapAttributionIfMissing()
                    mapView = this
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
                val attributionOverlay = map.clearDynamicOverlaysAndTakeAttribution()

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

                if (destinationWaypoints.size >= 2) {
                    val destinationLine = Polyline().apply {
                        setPoints(destinationWaypoints.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.color = Color.rgb(230, 140, 35)
                        outlinePaint.strokeWidth = 5f
                    }
                    map.overlays.add(destinationLine)
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
                            setOnMarkerClickListener { _, _ -> true }
                        }
                    )
                }

                destinationWaypoints.forEachIndexed { index, point ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(point.latitude, point.longitude)
                            title = "目的地 ${index + 1}"
                            icon = numberedMarkerIcon(map.context, index + 1, Color.rgb(230, 140, 35))
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { _, _ -> true }
                        }
                    )
                }

                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(currentPosition.latitude, currentPosition.longitude)
                        title = "目前位置"
                        icon = currentMarkerIcon(map.context)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> true }
                    }
                )

                map.overlays.add(
                    createMapTapOverlay(
                        mapView = map,
                        onMapClick = onMapClick,
                        routeWaypoints = routeWaypoints,
                        areaPolygonPoints = areaPolygonPoints,
                        generatedWaypoints = generatedWaypoints,
                        destinationWaypoints = destinationWaypoints,
                        currentPosition = currentPosition
                    )
                )

                map.overlays.add(attributionOverlay)

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

private fun MapView.addOpenStreetMapAttributionIfMissing() {
    if (overlays.none { it is CopyrightOverlay }) {
        overlays.add(createOpenStreetMapAttribution(context))
    }
}

private fun MapView.clearDynamicOverlaysAndTakeAttribution(): CopyrightOverlay {
    val attributionOverlay = overlays.filterIsInstance<CopyrightOverlay>().firstOrNull()
        ?: createOpenStreetMapAttribution(context)
    overlays.clear()
    return attributionOverlay
}

private fun createOpenStreetMapAttribution(context: Context): CopyrightOverlay {
    return CopyrightOverlay(context).apply {
        setCopyrightNotice(OPENSTREETMAP_ATTRIBUTION)
        setAlignBottom(true)
        setAlignRight(false)
        val offset = (8 * context.resources.displayMetrics.density).toInt()
        setOffset(offset, offset)
    }
}

private fun createMapTapOverlay(
    mapView: MapView,
    onMapClick: (LatLng) -> Unit,
    routeWaypoints: List<LatLng>,
    areaPolygonPoints: List<LatLng>,
    generatedWaypoints: List<LatLng>,
    destinationWaypoints: List<LatLng>,
    currentPosition: LatLng
): MapEventsOverlay {
    val markerPoints = routeWaypoints + areaPolygonPoints + generatedWaypoints + destinationWaypoints + currentPosition
    return MapEventsOverlay(
        object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                if (isNearAnyMarker(point, mapView, markerPoints)) {
                    return false
                }
                onMapClick(LatLng(point.latitude, point.longitude))
                return true
            }

            override fun longPressHelper(point: GeoPoint): Boolean = false
        }
    )
}

private fun isNearAnyMarker(tappedPoint: GeoPoint, mapView: MapView, markerPoints: List<LatLng>): Boolean {
    val threshold = 44f * mapView.resources.displayMetrics.density
    val tappedScreenPoint = mapView.projection.toPixels(tappedPoint, null)
    return markerPoints.any { point ->
        val markerScreenPoint = mapView.projection.toPixels(GeoPoint(point.latitude, point.longitude), null)
        val dx = tappedScreenPoint.x - markerScreenPoint.x
        val dy = tappedScreenPoint.y - markerScreenPoint.y
        sqrt((dx * dx + dy * dy).toFloat()) <= threshold
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

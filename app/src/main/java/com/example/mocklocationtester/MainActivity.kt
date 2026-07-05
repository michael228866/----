package com.example.mocklocationtester

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val MOCK_APP_NOT_SELECTED_MESSAGE =
    "請先到開發人員選項，將本程式設定為「模擬位置應用程式」。"
private const val KMH_PER_MPS = 3.6f

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var selectedTab by mutableStateOf(MainTab.MANUAL)
    private var latitudeInput by mutableStateOf("25.033964")
    private var longitudeInput by mutableStateOf("121.564468")
    private var accuracyInput by mutableStateOf("5")
    private var maxSpeedKmh by mutableStateOf(5f)
    private var routeSpeedKmh by mutableStateOf(5f)
    private var areaSpeedKmh by mutableStateOf(5f)
    private var routeEndBehavior by mutableStateOf(RouteEndBehavior.PING_PONG)
    private var mapEditMode by mutableStateOf(MapEditMode.ROUTE)
    private var mapRecenterRequest by mutableStateOf(0)

    private var currentLatitude by mutableStateOf(25.033964)
    private var currentLongitude by mutableStateOf(121.564468)
    private var currentAccuracyMeters by mutableStateOf(5f)
    private var currentSpeedMetersPerSecond by mutableStateOf(0f)
    private var currentBearingDegrees by mutableStateOf(0f)
    private var activeMode by mutableStateOf(MockMode.IDLE)
    private var joystickRatio by mutableStateOf(0f)

    private var hasFineLocationPermission by mutableStateOf(false)
    private var hasCoarseLocationPermission by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var isMockLocationAppSelected by mutableStateOf(false)
    private var mockStatusText by mutableStateOf("尚未推送模擬定位")
    private var overlayStatusText by mutableStateOf("懸浮搖桿尚未啟動")
    private var mockSelectionError by mutableStateOf<String?>(null)
    private var operationError by mutableStateOf<String?>(null)
    private var isWalking by mutableStateOf(false)
    private var isRouteCruising by mutableStateOf(false)
    private var isAreaCruising by mutableStateOf(false)
    private var hasLastMockLocation by mutableStateOf(false)

    private val routeWaypoints = mutableStateListOf<LatLng>()
    private val areaPolygonPoints = mutableStateListOf<LatLng>()
    private val generatedAreaWaypoints = mutableStateListOf<LatLng>()

    private var isConsumerRunning by mutableStateOf(false)
    private var gpsConsumerLocation by mutableStateOf<ConsumerLocationUi?>(null)
    private var networkConsumerLocation by mutableStateOf<ConsumerLocationUi?>(null)
    private var consumerLocation by mutableStateOf<ConsumerLocationUi?>(null)

    private var walkingJob: Job? = null
    private var cruiseJob: Job? = null
    private var consumerCallback: LocationCallback? = null
    private var gpsConsumerListener: LocationListener? = null
    private var networkConsumerListener: LocationListener? = null
    private var lastWalkingUpdateElapsedMillis = 0L
    private var mockUpdateReceiverRegistered = false

    private val mockLocationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MockLocationController.ACTION_LOCATION_UPDATE) {
                applyMockLocationUpdate(intent)
            }
        }
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissionState()
            if (!hasFineLocationPermission) {
                operationError = "需要授予精確位置權限，系統詢問時請選擇「精確」。"
            } else if (operationError?.contains("精確位置") == true) {
                operationError = null
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 通知權限被拒絕時，前景服務仍可能執行，但系統通知可能不顯示。
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        refreshPermissionState()
        refreshMockAppState()
        refreshOverlayPermissionState()
        applyControllerState(MockLocationController.latestState(this), syncInputs = true)
        refreshLastMockLocationState()
        if (hasLastMockLocation) {
            mockStatusText = "已載入上次停止位置"
        }

        setContent {
            MaterialTheme {
                MockLocationTesterScreen(
                    selectedTab = selectedTab,
                    latitudeInput = latitudeInput,
                    longitudeInput = longitudeInput,
                    accuracyInput = accuracyInput,
                    maxSpeedKmh = maxSpeedKmh,
                    routeSpeedKmh = routeSpeedKmh,
                    areaSpeedKmh = areaSpeedKmh,
                    routeEndBehavior = routeEndBehavior,
                    currentLatitude = currentLatitude,
                    currentLongitude = currentLongitude,
                    currentAccuracyMeters = currentAccuracyMeters,
                    currentSpeedMetersPerSecond = currentSpeedMetersPerSecond,
                    currentBearingDegrees = currentBearingDegrees,
                    activeModeText = activeModeText(activeMode),
                    permissionStatusText = permissionStatusText(),
                    overlayPermissionStatusText = overlayPermissionStatusText(),
                    isMockLocationAppSelected = isMockLocationAppSelected,
                    hasOverlayPermission = hasOverlayPermission,
                    mockStatusText = mockStatusText,
                    overlayStatusText = overlayStatusText,
                    errorMessages = listOfNotNull(mockSelectionError, operationError).distinct(),
                    isWalking = isWalking,
                    isRouteCruising = isRouteCruising,
                    isAreaCruising = isAreaCruising,
                    hasLastMockLocation = hasLastMockLocation,
                    mapEditMode = mapEditMode,
                    mapRecenterRequest = mapRecenterRequest,
                    routeWaypoints = routeWaypoints,
                    areaPolygonPoints = areaPolygonPoints,
                    generatedAreaWaypoints = generatedAreaWaypoints,
                    isConsumerRunning = isConsumerRunning,
                    gpsConsumerLocation = gpsConsumerLocation,
                    networkConsumerLocation = networkConsumerLocation,
                    consumerLocation = consumerLocation,
                    onTabChange = { selectedTab = it },
                    onLatitudeInputChange = { latitudeInput = it },
                    onLongitudeInputChange = { longitudeInput = it },
                    onAccuracyInputChange = { accuracyInput = it },
                    onMaxSpeedChange = ::updateMaxSpeed,
                    onRouteSpeedChange = { routeSpeedKmh = it.coerceIn(1f, 100f) },
                    onAreaSpeedChange = { areaSpeedKmh = it.coerceIn(1f, 100f) },
                    onRouteEndBehaviorChange = { routeEndBehavior = it },
                    onMapEditModeChange = { mapEditMode = it },
                    onApplyStartLocation = ::applyStartLocationFromInputs,
                    onUseLastMockLocationAsStart = ::useLastMockLocationAsStart,
                    onUseCurrentPhoneLocationAsStart = ::useCurrentPhoneLocationAsStart,
                    onClearLastMockLocation = ::clearLastMockLocation,
                    onPushCurrentLocation = ::pushCurrentLocation,
                    onStartWalking = ::startWalkingSimulation,
                    onStopWalking = ::stopWalkingSimulation,
                    onRequestPermissions = ::requestLocationPermissions,
                    onOpenOverlayPermissionSettings = ::openOverlayPermissionSettings,
                    onStartFloatingJoystick = ::startFloatingJoystickService,
                    onStopFloatingJoystick = ::stopFloatingJoystickService,
                    onJoystickChange = ::updateJoystick,
                    onJoystickRelease = ::releaseJoystick,
                    onMapClick = ::addMapPoint,
                    onDeleteRouteWaypoint = ::deleteRouteWaypoint,
                    onDeleteAreaPolygonPoint = ::deleteAreaPolygonPoint,
                    onUndoLastMapPoint = ::undoLastMapPoint,
                    onClearRoute = ::clearRouteWaypoints,
                    onClearAreaPolygon = ::clearAreaPolygon,
                    onGenerateAreaRoute = ::generateAreaRoute,
                    onStartRouteCruise = ::startRouteCruise,
                    onStartAreaCruise = ::startAreaCruise,
                    onStopCruise = ::stopCruise,
                    onStartConsumer = ::startConsumer,
                    onStopConsumer = ::stopConsumer
                )
            }
        }

        requestLocationPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        refreshMockAppState()
        refreshOverlayPermissionState()
        registerMockLocationUpdateReceiver()
        applyControllerState(MockLocationController.latestState(this))
        refreshLastMockLocationState()
    }

    override fun onPause() {
        unregisterMockLocationUpdateReceiver()
        super.onPause()
    }

    override fun onDestroy() {
        stopWalkingSimulation()
        stopCruise()
        stopConsumer()
        super.onDestroy()
    }

    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshPermissionState() {
        hasFineLocationPermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        hasCoarseLocationPermission = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun refreshOverlayPermissionState() {
        hasOverlayPermission = Settings.canDrawOverlays(this)
    }

    private fun registerMockLocationUpdateReceiver() {
        if (mockUpdateReceiverRegistered) {
            return
        }
        ContextCompat.registerReceiver(
            this,
            mockLocationUpdateReceiver,
            IntentFilter(MockLocationController.ACTION_LOCATION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        mockUpdateReceiverRegistered = true
    }

    private fun unregisterMockLocationUpdateReceiver() {
        if (!mockUpdateReceiverRegistered) {
            return
        }
        runCatching {
            unregisterReceiver(mockLocationUpdateReceiver)
        }
        mockUpdateReceiverRegistered = false
    }

    private fun applyMockLocationUpdate(intent: Intent) {
        currentLatitude = intent.getDoubleExtra(MockLocationController.EXTRA_LATITUDE, currentLatitude)
        currentLongitude = intent.getDoubleExtra(MockLocationController.EXTRA_LONGITUDE, currentLongitude)
        currentAccuracyMeters = intent.getFloatExtra(MockLocationController.EXTRA_ACCURACY, currentAccuracyMeters)
        currentSpeedMetersPerSecond = intent.getFloatExtra(MockLocationController.EXTRA_SPEED, currentSpeedMetersPerSecond)
        currentBearingDegrees = intent.getFloatExtra(MockLocationController.EXTRA_BEARING, currentBearingDegrees)
        activeMode = runCatching {
            MockMode.valueOf(intent.getStringExtra(MockLocationController.EXTRA_MODE) ?: MockMode.IDLE.name)
        }.getOrDefault(MockMode.IDLE)
        refreshLastMockLocationState()
        mockStatusText = "已同步模擬定位"
    }

    private fun applyControllerState(state: MockLocationState, syncInputs: Boolean = false) {
        currentLatitude = state.latitude
        currentLongitude = state.longitude
        currentAccuracyMeters = state.accuracyMeters
        currentSpeedMetersPerSecond = state.speedMetersPerSecond
        currentBearingDegrees = state.bearingDegrees
        activeMode = state.mode
        if (syncInputs) {
            syncInputsFromCurrentLocation()
        }
    }

    private fun syncInputsFromCurrentLocation() {
        latitudeInput = formatNumber(currentLatitude, 7)
        longitudeInput = formatNumber(currentLongitude, 7)
        accuracyInput = formatNumber(currentAccuracyMeters.toDouble(), 1)
    }

    private fun refreshLastMockLocationState() {
        hasLastMockLocation = MockLocationController.lastMockLocation(this) != null
    }

    private fun refreshMockAppState() {
        isMockLocationAppSelected = isSelectedAsMockLocationApp()
        mockSelectionError = if (isMockLocationAppSelected) null else MOCK_APP_NOT_SELECTED_MESSAGE
        if (isMockLocationAppSelected && activeMode == MockMode.IDLE) {
            mockStatusText = "已設定為模擬位置應用程式"
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun permissionStatusText(): String {
        return when {
            hasFineLocationPermission -> "精確位置已授權"
            hasCoarseLocationPermission -> "僅概略位置已授權，請改授權精確位置"
            else -> "尚未授權定位權限"
        }
    }

    private fun overlayPermissionStatusText(): String {
        return if (hasOverlayPermission) "懸浮視窗權限已授權" else "尚未授權懸浮視窗權限"
    }

    @Suppress("DEPRECATION")
    private fun isSelectedAsMockLocationApp(): Boolean {
        val appOpsManager = getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                packageName
            )
        } else {
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun applyStartLocationFromInputs() {
        val parsed = parseLocationInputs() ?: return
        currentLatitude = parsed.latitude
        currentLongitude = parsed.longitude
        currentAccuracyMeters = parsed.accuracyMeters
        currentSpeedMetersPerSecond = 0f
        joystickRatio = 0f
        operationError = null
        mockStatusText = "已套用起始座標"
    }

    private fun useLastMockLocationAsStart() {
        val lastLocation = MockLocationController.lastMockLocation(this)
        if (lastLocation == null) {
            operationError = "尚未儲存最後位置。"
            refreshLastMockLocationState()
            return
        }

        currentLatitude = lastLocation.latitude
        currentLongitude = lastLocation.longitude
        currentSpeedMetersPerSecond = 0f
        currentBearingDegrees = lastLocation.bearingDegrees
        joystickRatio = 0f
        syncInputsFromCurrentLocation()
        mapRecenterRequest += 1
        operationError = null
        mockStatusText = "已使用最後位置作為起點"
        refreshLastMockLocationState()
    }

    private fun clearLastMockLocation() {
        MockLocationController.clearLastMockLocation(this)
        refreshLastMockLocationState()
        operationError = null
        mockStatusText = "已清除最後位置"
    }

    @SuppressLint("MissingPermission")
    private fun useCurrentPhoneLocationAsStart() {
        refreshPermissionState()
        if (!hasFineLocationPermission && !hasCoarseLocationPermission) {
            operationError = "使用目前手機位置作為起點需要定位權限。"
            requestLocationPermissions()
            return
        }

        operationError = null
        val priority = if (hasFineLocationPermission) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                if (location != null && location.latitude.isFinite() && location.longitude.isFinite()) {
                    applyPhoneLocationAsStart(location)
                } else {
                    useLastKnownLocationAsStart()
                }
            }
            .addOnFailureListener {
                useLastKnownLocationAsStart()
            }
    }

    @SuppressLint("MissingPermission")
    private fun useLastKnownLocationAsStart() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null && location.latitude.isFinite() && location.longitude.isFinite()) {
                    applyPhoneLocationAsStart(location)
                } else {
                    showCurrentPhoneLocationError()
                }
            }
            .addOnFailureListener {
                showCurrentPhoneLocationError()
            }
    }

    private fun applyPhoneLocationAsStart(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = normalizeLongitude(location.longitude)
        currentSpeedMetersPerSecond = 0f
        joystickRatio = 0f
        syncInputsFromCurrentLocation()
        mapRecenterRequest += 1
        operationError = null
        mockStatusText = "已使用目前手機位置作為起點"
    }

    private fun showCurrentPhoneLocationError() {
        operationError = "無法取得目前位置，請確認定位權限與手機定位已開啟"
    }

    private fun updateMaxSpeed(value: Float) {
        maxSpeedKmh = value.coerceIn(1f, 100f)
        currentSpeedMetersPerSecond = (maxSpeedKmh / KMH_PER_MPS) * joystickRatio
    }

    private fun updateJoystick(ratio: Float, bearingDegrees: Float) {
        if (isWalking) {
            advanceWalkingLocationByElapsedTime()
        }
        joystickRatio = ratio.coerceIn(0f, 1f)
        currentSpeedMetersPerSecond = (maxSpeedKmh / KMH_PER_MPS) * joystickRatio
        if (joystickRatio > 0.01f) {
            currentBearingDegrees = normalizeBearing(bearingDegrees.toDouble()).toFloat()
        }
    }

    private fun releaseJoystick() {
        if (isWalking) {
            advanceWalkingLocationByElapsedTime()
        }
        joystickRatio = 0f
        currentSpeedMetersPerSecond = 0f
        if (isWalking) {
            sendCurrentMockLocation(MockMode.MANUAL_JOYSTICK)
        }
    }

    private fun pushCurrentLocation() {
        if (!applyAccuracyInput()) {
            return
        }
        if (!prepareMockProvider(MockMode.MANUAL_JOYSTICK)) {
            return
        }
        sendCurrentMockLocation(MockMode.MANUAL_JOYSTICK)
    }

    private fun startWalkingSimulation() {
        if (!applyAccuracyInput()) {
            return
        }
        stopCruise()
        stopFloatingJoystickService()

        if (!prepareMockProvider(MockMode.MANUAL_JOYSTICK)) {
            return
        }

        walkingJob?.cancel()
        isWalking = true
        lastWalkingUpdateElapsedMillis = SystemClock.elapsedRealtime()
        operationError = null
        mockStatusText = "走路模擬中"
        sendCurrentMockLocation(MockMode.MANUAL_JOYSTICK)

        walkingJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000L)
                advanceWalkingLocationByElapsedTime()
                val result = sendCurrentMockLocation(MockMode.MANUAL_JOYSTICK)
                if (!result) {
                    stopWalkingSimulation()
                    break
                }
            }
        }
    }

    private fun stopWalkingSimulation() {
        walkingJob?.cancel()
        walkingJob = null
        if (isWalking) {
            releaseJoystick()
            MockLocationController.endMode(this, MockMode.MANUAL_JOYSTICK)
        }
        isWalking = false
        lastWalkingUpdateElapsedMillis = 0L
        if (activeMode == MockMode.MANUAL_JOYSTICK || activeMode == MockMode.IDLE) {
            mockStatusText = "已停止走路模擬"
        }
    }

    private fun advanceWalkingLocationByElapsedTime() {
        val now = SystemClock.elapsedRealtime()
        val last = lastWalkingUpdateElapsedMillis
        if (last <= 0L) {
            lastWalkingUpdateElapsedMillis = now
            return
        }

        val elapsedSeconds = (now - last) / 1000.0
        if (elapsedSeconds <= 0.0) {
            return
        }

        val distanceMeters = currentSpeedMetersPerSecond.toDouble() * elapsedSeconds
        if (distanceMeters > 0.0) {
            val moved = moveLatLng(
                latDeg = currentLatitude,
                lonDeg = currentLongitude,
                distanceMeters = distanceMeters,
                bearingDeg = currentBearingDegrees.toDouble()
            )
            currentLatitude = moved.first
            currentLongitude = moved.second
        }
        lastWalkingUpdateElapsedMillis = now
    }

    private fun startFloatingJoystickService() {
        if (!applyAccuracyInput()) {
            return
        }
        refreshPermissionState()
        refreshMockAppState()
        refreshOverlayPermissionState()

        if (!hasFineLocationPermission) {
            operationError = "啟動懸浮搖桿需要精確位置權限。"
            requestLocationPermissions()
            return
        }
        if (!isMockLocationAppSelected) {
            operationError = "啟動懸浮搖桿前，請先將本程式設為模擬位置應用程式。"
            return
        }
        if (!hasOverlayPermission) {
            operationError = "啟動懸浮搖桿需要懸浮視窗權限。"
            openOverlayPermissionSettings()
            return
        }

        stopWalkingSimulation()
        stopCruise()
        requestNotificationPermissionIfNeeded()

        val intent = Intent(this, FloatingJoystickService::class.java).apply {
            action = FloatingJoystickService.ACTION_START
            putExtra(FloatingJoystickService.EXTRA_LATITUDE, currentLatitude)
            putExtra(FloatingJoystickService.EXTRA_LONGITUDE, currentLongitude)
            putExtra(FloatingJoystickService.EXTRA_ACCURACY, currentAccuracyMeters)
            putExtra(FloatingJoystickService.EXTRA_MAX_SPEED_KMH, maxSpeedKmh)
        }
        ContextCompat.startForegroundService(this, intent)
        overlayStatusText = "懸浮搖桿啟動中"
        operationError = null
    }

    private fun stopFloatingJoystickService() {
        val intent = Intent(this, FloatingJoystickService::class.java).apply {
            action = FloatingJoystickService.ACTION_STOP
        }
        startService(intent)
        if (activeMode == MockMode.FLOATING_JOYSTICK) {
            MockLocationController.endMode(this, MockMode.FLOATING_JOYSTICK)
        }
        overlayStatusText = "已停止懸浮搖桿"
    }

    private fun addMapPoint(point: LatLng) {
        when (mapEditMode) {
            MapEditMode.ROUTE -> {
                routeWaypoints.add(point)
                operationError = null
                mockStatusText = "已加入第 ${routeWaypoints.size} 個路徑點"
                restartRouteCruiseAfterRouteChange()
            }

            MapEditMode.AREA -> {
                areaPolygonPoints.add(point)
                generatedAreaWaypoints.clear()
                operationError = null
                mockStatusText = "已加入第 ${areaPolygonPoints.size} 個範圍點"
                if (isAreaCruising) {
                    stopCruise()
                    operationError = "範圍已變更，已停止區域巡航。"
                }
            }
        }
    }

    private fun deleteRouteWaypoint(index: Int) {
        if (index !in routeWaypoints.indices) {
            return
        }
        routeWaypoints.removeAt(index)
        operationError = null
        mockStatusText = "已刪除路徑第 ${index + 1} 個點"
        restartRouteCruiseAfterRouteChange()
    }

    private fun deleteAreaPolygonPoint(index: Int) {
        if (index !in areaPolygonPoints.indices) {
            return
        }
        areaPolygonPoints.removeAt(index)
        generatedAreaWaypoints.clear()
        operationError = null
        mockStatusText = "已刪除範圍第 ${index + 1} 個點"
        if (isAreaCruising) {
            stopCruise()
            operationError = "範圍點已變更，已停止區域巡航。"
        }
    }

    private fun undoLastMapPoint() {
        when (mapEditMode) {
            MapEditMode.ROUTE -> {
                if (routeWaypoints.isNotEmpty()) {
                    deleteRouteWaypoint(routeWaypoints.lastIndex)
                }
            }

            MapEditMode.AREA -> {
                if (areaPolygonPoints.isNotEmpty()) {
                    deleteAreaPolygonPoint(areaPolygonPoints.lastIndex)
                }
            }
        }
    }

    private fun clearRouteWaypoints() {
        if (isRouteCruising) {
            stopCruise()
        }
        routeWaypoints.clear()
        operationError = null
        mockStatusText = "已清除全部路徑"
    }

    private fun clearAreaPolygon() {
        if (isAreaCruising) {
            stopCruise()
        }
        areaPolygonPoints.clear()
        generatedAreaWaypoints.clear()
        operationError = null
        mockStatusText = "已清除全部範圍"
    }

    private fun generateAreaRoute() {
        if (areaPolygonPoints.size < 3) {
            operationError = "請先切換到區域圈選，並在地圖上點選至少 3 個範圍點。"
            return
        }

        val count = Random.nextInt(10, 51)
        val generated = runCatching {
            generateRandomWaypointsInPolygon(areaPolygonPoints, count)
        }.getOrElse {
            operationError = "產生區域路徑失敗，請重新圈選較大的區域。"
            return
        }

        if (generated.size < 2) {
            operationError = "範圍內可用座標不足，請重新圈選較大的區域。"
            return
        }

        generatedAreaWaypoints.clear()
        generatedAreaWaypoints.addAll(generated)
        operationError = null
        mockStatusText = "已產生 ${generated.size} 個區域巡航座標點"
    }

    private fun startRouteCruise() {
        if (routeWaypoints.size < 2) {
            operationError = "請先在地圖上點選至少 2 個座標點。"
            return
        }
        startCruise(routeWaypoints.toList(), MockMode.ROUTE_CRUISE)
    }

    private fun startAreaCruise() {
        if (generatedAreaWaypoints.size < 2) {
            operationError = "請先產生區域內路徑。"
            return
        }
        startCruise(generatedAreaWaypoints.toList(), MockMode.AREA_CRUISE)
    }

    private fun startCruise(waypoints: List<LatLng>, mode: MockMode) {
        refreshPermissionState()
        refreshMockAppState()
        if (!hasFineLocationPermission) {
            operationError = "巡航模式需要精確位置權限。"
            requestLocationPermissions()
            return
        }
        if (!isMockLocationAppSelected) {
            operationError = "巡航前，請先將本程式設為模擬位置應用程式。"
            return
        }

        stopWalkingSimulation()
        stopFloatingJoystickService()
        stopCruise()

        val initial = LatLng(currentLatitude, currentLongitude)
        val beginResult = MockLocationController.beginMode(
            context = this,
            mode = mode,
            latitude = initial.latitude,
            longitude = initial.longitude,
            accuracyMeters = currentAccuracyMeters
        )
        if (!beginResult.success) {
            operationError = beginResult.message
            return
        }

        val simulator = RouteSimulator(
            waypoints = waypoints,
            endBehavior = routeEndBehavior,
            startPosition = initial
        )
        isRouteCruising = mode == MockMode.ROUTE_CRUISE
        isAreaCruising = mode == MockMode.AREA_CRUISE
        operationError = null
        mockStatusText = if (mode == MockMode.ROUTE_CRUISE) "路徑巡航中" else "區域巡航中"

        cruiseJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000L)
                val cruiseSpeedKmh = if (mode == MockMode.ROUTE_CRUISE) routeSpeedKmh else areaSpeedKmh
                val cruiseSpeedMetersPerSecond = cruiseSpeedKmh / KMH_PER_MPS
                val step = simulator.step(
                    currentPosition = LatLng(currentLatitude, currentLongitude),
                    speedMetersPerSecond = cruiseSpeedMetersPerSecond,
                    updateIntervalSeconds = 1.0
                )
                currentLatitude = step.position.latitude
                currentLongitude = step.position.longitude
                currentSpeedMetersPerSecond = step.speedMetersPerSecond
                currentBearingDegrees = step.bearingDegrees

                val pushed = MockLocationController.pushLocation(
                    context = this@MainActivity,
                    latitude = currentLatitude,
                    longitude = currentLongitude,
                    accuracyMeters = currentAccuracyMeters,
                    speedMetersPerSecond = currentSpeedMetersPerSecond,
                    bearingDegrees = currentBearingDegrees,
                    mode = mode
                )
                if (!pushed.success) {
                    operationError = pushed.message
                    stopCruise()
                    break
                }
                if (step.finished) {
                    stopCruise()
                    mockStatusText = "巡航已完成"
                    break
                }
            }
        }
    }

    private fun stopCruise() {
        cruiseJob?.cancel()
        cruiseJob = null
        val wasRoute = isRouteCruising
        val wasArea = isAreaCruising
        isRouteCruising = false
        isAreaCruising = false
        if (wasRoute) {
            MockLocationController.endMode(this, MockMode.ROUTE_CRUISE)
        }
        if (wasArea) {
            MockLocationController.endMode(this, MockMode.AREA_CRUISE)
        }
        if (wasRoute || wasArea) {
            currentSpeedMetersPerSecond = 0f
            mockStatusText = "已停止巡航"
        }
    }

    private fun restartRouteCruiseAfterRouteChange() {
        if (!isRouteCruising) {
            return
        }
        if (routeWaypoints.size < 2) {
            stopCruise()
            operationError = "路徑點少於 2 個，已自動停止路徑巡航。"
            return
        }
        startCruise(routeWaypoints.toList(), MockMode.ROUTE_CRUISE)
        mockStatusText = "路徑已更新，巡航已從最近點續行"
    }

    private fun parseLocationInputs(): ParsedLocationInput? {
        val latitude = latitudeInput.trim().toDoubleOrNull()
        val longitude = longitudeInput.trim().toDoubleOrNull()
        val accuracy = accuracyInput.trim().toFloatOrNull()

        if (latitude == null || !latitude.isFinite() || latitude !in -90.0..90.0) {
            operationError = "緯度必須是 -90 到 90 之間的有效數字。"
            return null
        }
        if (longitude == null || !longitude.isFinite() || longitude !in -180.0..180.0) {
            operationError = "經度必須是 -180 到 180 之間的有效數字。"
            return null
        }
        if (accuracy == null || !accuracy.isFinite() || accuracy <= 0f || accuracy > 10000f) {
            operationError = "精度必須是 0 到 10000 公尺之間的有效數字。"
            return null
        }

        return ParsedLocationInput(
            latitude = latitude,
            longitude = normalizeLongitude(longitude),
            accuracyMeters = accuracy
        )
    }

    private fun applyAccuracyInput(): Boolean {
        val accuracy = accuracyInput.trim().toFloatOrNull()
        if (accuracy == null || !accuracy.isFinite() || accuracy <= 0f || accuracy > 10000f) {
            operationError = "精度必須是 0 到 10000 公尺之間的有效數字。"
            return false
        }
        currentAccuracyMeters = accuracy
        return true
    }

    private fun prepareMockProvider(mode: MockMode): Boolean {
        refreshPermissionState()
        refreshMockAppState()
        if (!hasFineLocationPermission) {
            operationError = "需要授予精確位置權限，系統詢問時請選擇「精確」。"
            requestLocationPermissions()
            return false
        }
        if (!isMockLocationAppSelected) {
            operationError = "無法推送模擬定位：$MOCK_APP_NOT_SELECTED_MESSAGE"
            return false
        }

        val result = MockLocationController.beginMode(
            context = this,
            mode = mode,
            latitude = currentLatitude,
            longitude = currentLongitude,
            accuracyMeters = currentAccuracyMeters
        )
        operationError = result.message
        return result.success
    }

    private fun sendCurrentMockLocation(mode: MockMode): Boolean {
        val result = MockLocationController.pushLocation(
            context = this,
            latitude = currentLatitude,
            longitude = currentLongitude,
            accuracyMeters = currentAccuracyMeters,
            speedMetersPerSecond = currentSpeedMetersPerSecond,
            bearingDegrees = currentBearingDegrees,
            mode = mode
        )
        operationError = result.message
        if (result.success) {
            mockStatusText = "已推送模擬定位"
        }
        return result.success
    }

    @SuppressLint("MissingPermission")
    private fun startConsumer() {
        refreshPermissionState()
        if (!hasFineLocationPermission && !hasCoarseLocationPermission) {
            operationError = "接收定位需要定位權限。"
            requestLocationPermissions()
            return
        }
        if (isConsumerRunning) {
            return
        }

        gpsConsumerLocation = null
        networkConsumerLocation = null
        consumerLocation = null

        val startupErrors = mutableListOf<String>()
        val manager = getSystemService(LocationManager::class.java)
        val gpsListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                gpsConsumerLocation = location.toConsumerLocationUi()
            }
        }
        val networkListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                networkConsumerLocation = location.toConsumerLocationUi()
            }
        }
        var startedProviderConsumer = false

        if (startProviderConsumer(manager, LocationManager.GPS_PROVIDER, gpsListener, startupErrors)) {
            gpsConsumerListener = gpsListener
            startedProviderConsumer = true
        }
        if (startProviderConsumer(manager, LocationManager.NETWORK_PROVIDER, networkListener, startupErrors)) {
            networkConsumerListener = networkListener
            startedProviderConsumer = true
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val latest = result.locations.lastOrNull() ?: result.lastLocation ?: return
                consumerLocation = latest.toConsumerLocationUi()
            }
        }

        consumerCallback = callback
        isConsumerRunning = true
        operationError = startupErrors.distinct().joinToString("；").takeIf { it.isNotBlank() }
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnSuccessListener {
                if (consumerCallback == callback) {
                    isConsumerRunning = true
                    operationError = startupErrors.distinct()
                        .joinToString("；")
                        .takeIf { it.isNotBlank() }
                }
            }
            .addOnFailureListener { exception ->
                if (consumerCallback == callback) {
                    consumerCallback = null
                    if (!startedProviderConsumer) {
                        isConsumerRunning = false
                    }
                    operationError = startupErrors
                        .plus("融合定位服務接收啟動失敗，請確認定位權限後再試。")
                        .distinct()
                        .joinToString("；")
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun startProviderConsumer(
        manager: LocationManager,
        provider: String,
        listener: LocationListener,
        startupErrors: MutableList<String>
    ): Boolean {
        return try {
            manager.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
            true
        } catch (exception: SecurityException) {
            startupErrors.add("${locationProviderLabel(provider)} 接收啟動失敗：沒有定位權限")
            false
        } catch (exception: IllegalArgumentException) {
            startupErrors.add("${locationProviderLabel(provider)} 接收啟動失敗：裝置不支援此定位供應器")
            false
        } catch (exception: RuntimeException) {
            startupErrors.add("${locationProviderLabel(provider)} 接收啟動失敗")
            false
        }
    }

    private fun stopConsumer() {
        val manager = getSystemService(LocationManager::class.java)
        consumerCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        gpsConsumerListener?.let { listener ->
            runCatching { manager.removeUpdates(listener) }
        }
        networkConsumerListener?.let { listener ->
            runCatching { manager.removeUpdates(listener) }
        }
        consumerCallback = null
        gpsConsumerListener = null
        networkConsumerListener = null
        isConsumerRunning = false
    }

    private fun locationProviderLabel(provider: String): String {
        return when (provider) {
            LocationManager.GPS_PROVIDER -> "GPS_PROVIDER"
            LocationManager.NETWORK_PROVIDER -> "NETWORK_PROVIDER"
            else -> provider
        }
    }
}

@Composable
private fun MockLocationTesterScreen(
    selectedTab: MainTab,
    latitudeInput: String,
    longitudeInput: String,
    accuracyInput: String,
    maxSpeedKmh: Float,
    routeSpeedKmh: Float,
    areaSpeedKmh: Float,
    routeEndBehavior: RouteEndBehavior,
    currentLatitude: Double,
    currentLongitude: Double,
    currentAccuracyMeters: Float,
    currentSpeedMetersPerSecond: Float,
    currentBearingDegrees: Float,
    activeModeText: String,
    permissionStatusText: String,
    overlayPermissionStatusText: String,
    isMockLocationAppSelected: Boolean,
    hasOverlayPermission: Boolean,
    mockStatusText: String,
    overlayStatusText: String,
    errorMessages: List<String>,
    isWalking: Boolean,
    isRouteCruising: Boolean,
    isAreaCruising: Boolean,
    hasLastMockLocation: Boolean,
    mapEditMode: MapEditMode,
    mapRecenterRequest: Int,
    routeWaypoints: List<LatLng>,
    areaPolygonPoints: List<LatLng>,
    generatedAreaWaypoints: List<LatLng>,
    isConsumerRunning: Boolean,
    gpsConsumerLocation: ConsumerLocationUi?,
    networkConsumerLocation: ConsumerLocationUi?,
    consumerLocation: ConsumerLocationUi?,
    onTabChange: (MainTab) -> Unit,
    onLatitudeInputChange: (String) -> Unit,
    onLongitudeInputChange: (String) -> Unit,
    onAccuracyInputChange: (String) -> Unit,
    onMaxSpeedChange: (Float) -> Unit,
    onRouteSpeedChange: (Float) -> Unit,
    onAreaSpeedChange: (Float) -> Unit,
    onRouteEndBehaviorChange: (RouteEndBehavior) -> Unit,
    onMapEditModeChange: (MapEditMode) -> Unit,
    onApplyStartLocation: () -> Unit,
    onUseLastMockLocationAsStart: () -> Unit,
    onUseCurrentPhoneLocationAsStart: () -> Unit,
    onClearLastMockLocation: () -> Unit,
    onPushCurrentLocation: () -> Unit,
    onStartWalking: () -> Unit,
    onStopWalking: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenOverlayPermissionSettings: () -> Unit,
    onStartFloatingJoystick: () -> Unit,
    onStopFloatingJoystick: () -> Unit,
    onJoystickChange: (Float, Float) -> Unit,
    onJoystickRelease: () -> Unit,
    onMapClick: (LatLng) -> Unit,
    onDeleteRouteWaypoint: (Int) -> Unit,
    onDeleteAreaPolygonPoint: (Int) -> Unit,
    onUndoLastMapPoint: () -> Unit,
    onClearRoute: () -> Unit,
    onClearAreaPolygon: () -> Unit,
    onGenerateAreaRoute: () -> Unit,
    onStartRouteCruise: () -> Unit,
    onStartAreaCruise: () -> Unit,
    onStopCruise: () -> Unit,
    onStartConsumer: () -> Unit,
    onStopConsumer: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "模擬定位測試器",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                MainTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { onTabChange(tab) },
                        text = { Text(tab.title) }
                    )
                }
            }

            when (selectedTab) {
                MainTab.MANUAL -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CommonStatusSections(
                            permissionStatusText = permissionStatusText,
                            overlayPermissionStatusText = overlayPermissionStatusText,
                            isMockLocationAppSelected = isMockLocationAppSelected,
                            hasOverlayPermission = hasOverlayPermission,
                            activeModeText = activeModeText,
                            mockStatusText = mockStatusText,
                            overlayStatusText = overlayStatusText,
                            errorMessages = errorMessages,
                            onRequestPermissions = onRequestPermissions,
                            onOpenOverlayPermissionSettings = onOpenOverlayPermissionSettings
                        )
                        ManualControlTab(
                        latitudeInput = latitudeInput,
                        longitudeInput = longitudeInput,
                        accuracyInput = accuracyInput,
                        maxSpeedKmh = maxSpeedKmh,
                        currentLatitude = currentLatitude,
                        currentLongitude = currentLongitude,
                        currentAccuracyMeters = currentAccuracyMeters,
                        currentSpeedMetersPerSecond = currentSpeedMetersPerSecond,
                        currentBearingDegrees = currentBearingDegrees,
                        isWalking = isWalking,
                        hasLastMockLocation = hasLastMockLocation,
                        isConsumerRunning = isConsumerRunning,
                        gpsConsumerLocation = gpsConsumerLocation,
                        networkConsumerLocation = networkConsumerLocation,
                        consumerLocation = consumerLocation,
                        onLatitudeInputChange = onLatitudeInputChange,
                        onLongitudeInputChange = onLongitudeInputChange,
                        onAccuracyInputChange = onAccuracyInputChange,
                        onMaxSpeedChange = onMaxSpeedChange,
                        onApplyStartLocation = onApplyStartLocation,
                        onUseLastMockLocationAsStart = onUseLastMockLocationAsStart,
                        onUseCurrentPhoneLocationAsStart = onUseCurrentPhoneLocationAsStart,
                        onClearLastMockLocation = onClearLastMockLocation,
                        onPushCurrentLocation = onPushCurrentLocation,
                        onStartWalking = onStartWalking,
                        onStopWalking = onStopWalking,
                        onStartFloatingJoystick = onStartFloatingJoystick,
                        onStopFloatingJoystick = onStopFloatingJoystick,
                        onJoystickChange = onJoystickChange,
                        onJoystickRelease = onJoystickRelease,
                        onStartConsumer = onStartConsumer,
                        onStopConsumer = onStopConsumer
                        )
                    }
                }

                MainTab.MAP -> MapPatrolTab(
                    modifier = Modifier.weight(1f),
                    statusContent = {
                        CommonStatusSections(
                            permissionStatusText = permissionStatusText,
                            overlayPermissionStatusText = overlayPermissionStatusText,
                            isMockLocationAppSelected = isMockLocationAppSelected,
                            hasOverlayPermission = hasOverlayPermission,
                            activeModeText = activeModeText,
                            mockStatusText = mockStatusText,
                            overlayStatusText = overlayStatusText,
                            errorMessages = errorMessages,
                            onRequestPermissions = onRequestPermissions,
                            onOpenOverlayPermissionSettings = onOpenOverlayPermissionSettings
                        )
                    },
                        currentLatitude = currentLatitude,
                        currentLongitude = currentLongitude,
                        currentAccuracyMeters = currentAccuracyMeters,
                        currentSpeedMetersPerSecond = currentSpeedMetersPerSecond,
                        currentBearingDegrees = currentBearingDegrees,
                        routeSpeedKmh = routeSpeedKmh,
                        areaSpeedKmh = areaSpeedKmh,
                        routeEndBehavior = routeEndBehavior,
                        mapEditMode = mapEditMode,
                        routeWaypoints = routeWaypoints,
                        areaPolygonPoints = areaPolygonPoints,
                        generatedAreaWaypoints = generatedAreaWaypoints,
                        isRouteCruising = isRouteCruising,
                        isAreaCruising = isAreaCruising,
                        hasLastMockLocation = hasLastMockLocation,
                        mapRecenterRequest = mapRecenterRequest,
                        onRouteSpeedChange = onRouteSpeedChange,
                        onAreaSpeedChange = onAreaSpeedChange,
                        onRouteEndBehaviorChange = onRouteEndBehaviorChange,
                        onMapEditModeChange = onMapEditModeChange,
                        onUseLastMockLocationAsStart = onUseLastMockLocationAsStart,
                        onUseCurrentPhoneLocationAsStart = onUseCurrentPhoneLocationAsStart,
                        onClearLastMockLocation = onClearLastMockLocation,
                        onMapClick = onMapClick,
                        onDeleteRouteWaypoint = onDeleteRouteWaypoint,
                        onDeleteAreaPolygonPoint = onDeleteAreaPolygonPoint,
                        onUndoLastMapPoint = onUndoLastMapPoint,
                        onClearRoute = onClearRoute,
                        onClearAreaPolygon = onClearAreaPolygon,
                        onGenerateAreaRoute = onGenerateAreaRoute,
                        onStartRouteCruise = onStartRouteCruise,
                        onStartAreaCruise = onStartAreaCruise,
                        onStopCruise = onStopCruise
                )
            }
        }
    }
}

@Composable
private fun CommonStatusSections(
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
private fun ManualControlTab(
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
private fun MapPatrolTab(
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
    mapEditMode: MapEditMode,
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
    onMapEditModeChange: (MapEditMode) -> Unit,
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
                    EndBehaviorButton(
                        text = "路徑選點",
                        selected = mapEditMode == MapEditMode.ROUTE,
                        onClick = { onMapEditModeChange(MapEditMode.ROUTE) },
                        modifier = Modifier.weight(1f)
                    )
                    EndBehaviorButton(
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
                InfoRow(label = "區域巡航速度", value = "目前速度：${formatNumber(areaSpeedKmh.toDouble(), 1)} km/h")
                Slider(
                    value = areaSpeedKmh,
                    onValueChange = onAreaSpeedChange,
                    valueRange = 1f..100f
                )
                Text(text = "結束方式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EndBehaviorButton(
                        text = "停止",
                        selected = routeEndBehavior == RouteEndBehavior.STOP,
                        onClick = { onRouteEndBehaviorChange(RouteEndBehavior.STOP) },
                        modifier = Modifier.weight(1f)
                    )
                    EndBehaviorButton(
                        text = "循環",
                        selected = routeEndBehavior == RouteEndBehavior.LOOP,
                        onClick = { onRouteEndBehaviorChange(RouteEndBehavior.LOOP) },
                        modifier = Modifier.weight(1f)
                    )
                    EndBehaviorButton(
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
private fun EndBehaviorButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    }
}

@Composable
private fun RouteMapView(
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
                map.overlays.add(MapTapOverlay(onMapClick))

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
    private val onMapClick: (LatLng) -> Unit
) : Overlay() {
    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        val geo = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
        onMapClick(LatLng(geo.latitude, geo.longitude))
        return true
    }
}

@Composable
private fun CurrentLocationSection(
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
private fun Section(
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
private fun InfoRow(label: String, value: String) {
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
private fun ErrorMessages(messages: List<String>) {
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

@Composable
private fun ConsumerLocationGroup(
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

private data class ParsedLocationInput(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float
)

private data class ConsumerLocationUi(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float,
    val bearingDegrees: Float,
    val timeMillis: Long,
    val elapsedRealtimeNanos: Long,
    val isMock: Boolean
)

private fun Location.toConsumerLocationUi(): ConsumerLocationUi {
    val mockState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        isMock
    } else {
        isFromMockProviderCompat()
    }

    return ConsumerLocationUi(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        speedMetersPerSecond = speed,
        bearingDegrees = bearing,
        timeMillis = time,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        isMock = mockState
    )
}

@Suppress("DEPRECATION")
private fun Location.isFromMockProviderCompat(): Boolean {
    return isFromMockProvider
}

private fun activeModeText(mode: MockMode): String {
    return when (mode) {
        MockMode.IDLE -> "閒置"
        MockMode.MANUAL_JOYSTICK -> "手動搖桿"
        MockMode.FLOATING_JOYSTICK -> "懸浮搖桿"
        MockMode.ROUTE_CRUISE -> "路徑巡航"
        MockMode.AREA_CRUISE -> "區域巡航"
    }
}

private fun formatNumber(value: Double, decimals: Int): String {
    return String.format(Locale.US, "%.${decimals}f", value)
}

private enum class MainTab(val title: String) {
    MANUAL("手動控制"),
    MAP("地圖繞行模式")
}

private enum class MapEditMode(val title: String) {
    ROUTE("路徑選點"),
    AREA("區域圈選")
}

private enum class MapPointType {
    ROUTE,
    AREA
}

private data class MapPointSelection(
    val type: MapPointType,
    val index: Int
)

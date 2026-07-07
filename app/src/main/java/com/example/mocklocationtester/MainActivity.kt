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
private const val TAIPEI_101_LATITUDE = 25.033964
private const val TAIPEI_101_LONGITUDE = 121.564468
const val KMH_PER_MPS = 3.6f

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var selectedTab by mutableStateOf(MainTab.MANUAL)
    private var maxSpeedKmh by mutableStateOf(5f)
    private var routeSpeedKmh by mutableStateOf(5f)
    private var areaSpeedKmh by mutableStateOf(5f)
    private var destinationWalkSpeedKmh by mutableStateOf(5f)
    private var routeEndBehavior by mutableStateOf(RouteEndBehavior.PING_PONG)
    private var routeStartMode by mutableStateOf(RouteStartMode.NEAREST)
    private var mapEditMode by mutableStateOf(MapEditMode.ROUTE)
    private var areaRouteMode by mutableStateOf(AreaRouteMode.RANDOM)
    private var mapRecenterRequest by mutableStateOf(0)

    private var currentLatitude by mutableStateOf(25.033964)
    private var currentLongitude by mutableStateOf(121.564468)
    private var currentAccuracyMeters by mutableStateOf(5f)
    private var currentSpeedMetersPerSecond by mutableStateOf(0f)
    private var currentBearingDegrees by mutableStateOf(0f)
    private var activeMode by mutableStateOf(MockMode.IDLE)

    private var hasFineLocationPermission by mutableStateOf(false)
    private var hasCoarseLocationPermission by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var isMockLocationAppSelected by mutableStateOf(false)
    private var mockStatusText by mutableStateOf("尚未推送模擬定位")
    private var overlayStatusText by mutableStateOf("懸浮搖桿尚未啟動")
    private var mockSelectionError by mutableStateOf<String?>(null)
    private var operationError by mutableStateOf<String?>(null)
    private var isRouteCruising by mutableStateOf(false)
    private var isAreaCruising by mutableStateOf(false)
    private var isDestinationWalking by mutableStateOf(false)
    private var hasLastMockLocation by mutableStateOf(false)
    private var currentDestinationIndex by mutableStateOf<Int?>(null)
    private var destinationRemainingDistanceMeters by mutableStateOf(0.0)
    private var destinationEstimatedArrivalSeconds by mutableStateOf<Double?>(null)

    private val routeWaypoints = mutableStateListOf<LatLng>()
    private val areaPolygonPoints = mutableStateListOf<LatLng>()
    private val generatedAreaWaypoints = mutableStateListOf<LatLng>()
    private val destinationWaypoints = mutableStateListOf<LatLng>()

    private var isConsumerRunning by mutableStateOf(false)
    private var gpsConsumerLocation by mutableStateOf<ConsumerLocationUi?>(null)
    private var networkConsumerLocation by mutableStateOf<ConsumerLocationUi?>(null)
    private var consumerLocation by mutableStateOf<ConsumerLocationUi?>(null)

    private var cruiseJob: Job? = null
    private var destinationWalkJob: Job? = null
    private var consumerCallback: LocationCallback? = null
    private var gpsConsumerListener: LocationListener? = null
    private var networkConsumerListener: LocationListener? = null
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
        applyLatestControllerState(syncInputs = true, allowDefaultFallback = true)
        refreshLastMockLocationState()
        if (hasLastMockLocation) {
            mockStatusText = "已載入上次停止位置"
        }

        setContent {
            MaterialTheme {
                MockLocationTesterScreen(
                    selectedTab = selectedTab,
                    maxSpeedKmh = maxSpeedKmh,
                    routeSpeedKmh = routeSpeedKmh,
                    areaSpeedKmh = areaSpeedKmh,
                    destinationWalkSpeedKmh = destinationWalkSpeedKmh,
                    routeEndBehavior = routeEndBehavior,
                    routeStartMode = routeStartMode,
                    areaRouteMode = areaRouteMode,
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
                    isRouteCruising = isRouteCruising,
                    isAreaCruising = isAreaCruising,
                    isDestinationWalking = isDestinationWalking,
                    hasLastMockLocation = hasLastMockLocation,
                    currentDestinationIndex = currentDestinationIndex,
                    destinationRemainingDistanceMeters = destinationRemainingDistanceMeters,
                    destinationEstimatedArrivalSeconds = destinationEstimatedArrivalSeconds,
                    mapEditMode = mapEditMode,
                    mapRecenterRequest = mapRecenterRequest,
                    routeWaypoints = routeWaypoints,
                    areaPolygonPoints = areaPolygonPoints,
                    generatedAreaWaypoints = generatedAreaWaypoints,
                    destinationWaypoints = destinationWaypoints,
                    isConsumerRunning = isConsumerRunning,
                    gpsConsumerLocation = gpsConsumerLocation,
                    networkConsumerLocation = networkConsumerLocation,
                    consumerLocation = consumerLocation,
                    onTabChange = { selectedTab = it },
                    onMaxSpeedChange = ::updateMaxSpeed,
                    onRouteSpeedChange = { routeSpeedKmh = it.coerceIn(1f, 100f) },
                    onAreaSpeedChange = { areaSpeedKmh = it.coerceIn(1f, 100f) },
                    onDestinationWalkSpeedChange = { destinationWalkSpeedKmh = it.coerceIn(1f, 100f) },
                    onRouteEndBehaviorChange = { routeEndBehavior = it },
                    onRouteStartModeChange = { routeStartMode = it },
                    onMapEditModeChange = { mapEditMode = it },
                    onAreaRouteModeChange = { areaRouteMode = it },
                    onUseLastMockLocationAsStart = ::useLastMockLocationAsStart,
                    onUseCurrentPhoneLocationAsStart = ::useCurrentPhoneLocationAsStart,
                    onApplySpecifiedCoordinate = ::applySpecifiedCoordinate,
                    onClearLastMockLocation = ::clearLastMockLocation,
                    onResetToTaipei101 = ::resetToTaipei101,
                    onHoldPosition = ::startHoldPosition,
                    onStopMockLocation = ::stopMockLocation,
                    onRequestPermissions = ::requestLocationPermissions,
                    onOpenOverlayPermissionSettings = ::openOverlayPermissionSettings,
                    onStartFloatingJoystick = ::startFloatingJoystickService,
                    onStopFloatingJoystick = { stopFloatingJoystickService() },
                    onMapClick = ::addMapPoint,
                    onDeleteRouteWaypoint = ::deleteRouteWaypoint,
                    onDeleteAreaPolygonPoint = ::deleteAreaPolygonPoint,
                    onUndoLastMapPoint = ::undoLastMapPoint,
                    onClearRoute = ::clearRouteWaypoints,
                    onClearAreaPolygon = ::clearAreaPolygon,
                    onClearDestinations = ::clearDestinationWaypoints,
                    onGenerateAreaRoute = ::generateAreaRoute,
                    onStartRouteCruise = ::startRouteCruise,
                    onStartAreaCruise = ::startAreaCruise,
                    onStartDestinationWalk = ::startDestinationWalk,
                    onStopDestinationWalk = { stopDestinationWalk() },
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
        applyLatestControllerState(syncInputs = false, allowDefaultFallback = false)
        refreshLastMockLocationState()
    }

    override fun onPause() {
        unregisterMockLocationUpdateReceiver()
        super.onPause()
    }

    override fun onDestroy() {
        stopCruise()
        stopDestinationWalk(holdLastPosition = false)
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
        mockStatusText = when (activeMode) {
            MockMode.HOLD_POSITION -> "停留在最後位置"
            MockMode.DESTINATION_WALK -> "慢走到目的地執行中"
            else -> "已同步模擬定位"
        }
    }

    private fun applyControllerState(state: MockLocationState, syncInputs: Boolean = false) {
        currentLatitude = state.latitude
        currentLongitude = state.longitude
        currentAccuracyMeters = state.accuracyMeters
        currentSpeedMetersPerSecond = state.speedMetersPerSecond
        currentBearingDegrees = state.bearingDegrees
        activeMode = state.mode
    }

    private fun applyLatestControllerState(syncInputs: Boolean, allowDefaultFallback: Boolean) {
        val hasLastLocation = MockLocationController.lastMockLocation(this) != null
        val state = MockLocationController.latestState(this)
        if (hasLastLocation || state.mode != MockMode.IDLE || allowDefaultFallback) {
            applyControllerState(state, syncInputs = syncInputs)
        }
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

    private fun applySpecifiedCoordinate(
        latitudeText: String,
        longitudeText: String,
        accuracyText: String
    ) {
        val latitude = latitudeText.trim().toDoubleOrNull()
        val longitude = longitudeText.trim().toDoubleOrNull()
        val accuracyMeters = accuracyText.trim().toFloatOrNull()
        if (latitude == null ||
            longitude == null ||
            accuracyMeters == null ||
            !isValidLatLng(latitude, longitude) ||
            !accuracyMeters.isFinite() ||
            accuracyMeters <= 0f
        ) {
            operationError = "指定座標無效，緯度需介於 -90～90，經度需介於 -180～180，精度需大於 0。"
            return
        }

        currentLatitude = latitude
        currentLongitude = longitude
        currentAccuracyMeters = sanitizeAccuracyMeters(accuracyMeters)
        currentSpeedMetersPerSecond = 0f
        mapRecenterRequest += 1
        operationError = null
        mockStatusText = "已套用指定座標"
    }

    private fun resetToTaipei101() {
        currentLatitude = TAIPEI_101_LATITUDE
        currentLongitude = TAIPEI_101_LONGITUDE
        currentAccuracyMeters = 5f
        currentSpeedMetersPerSecond = 0f
        mapRecenterRequest += 1
        operationError = null
        mockStatusText = "已重設為台北 101"
    }

    private fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
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
        mapRecenterRequest += 1
        operationError = null
        mockStatusText = "已使用最後位置作為起點"
        refreshLastMockLocationState()
    }

    private fun clearLastMockLocation() {
        MockLocationController.clearLastMockLocation(this)
        refreshLastMockLocationState()
        operationError = null
        mockStatusText = "已清除保存位置，目前畫面座標不變"
    }

    private fun startHoldPosition() {
        refreshPermissionState()
        refreshMockAppState()
        if (!hasFineLocationPermission) {
            operationError = "停留在最後位置需要精確位置權限。"
            requestLocationPermissions()
            return
        }
        if (!isMockLocationAppSelected) {
            operationError = "停留在最後位置前，請先將本程式設為模擬位置應用程式。"
            return
        }
        val lastLocation = MockLocationController.lastMockLocation(this)
        if (lastLocation == null) {
            operationError = "尚未儲存最後位置。"
            refreshLastMockLocationState()
            return
        }

        stopCruise()
        stopDestinationWalk(holdLastPosition = false)
        stopFloatingJoystickService(holdLastPosition = false)
        requestNotificationPermissionIfNeeded()

        currentLatitude = lastLocation.latitude
        currentLongitude = lastLocation.longitude
        currentSpeedMetersPerSecond = 0f
        currentBearingDegrees = lastLocation.bearingDegrees
        val beginResult = MockLocationController.beginMode(
            context = this,
            mode = MockMode.HOLD_POSITION,
            latitude = currentLatitude,
            longitude = currentLongitude,
            accuracyMeters = currentAccuracyMeters
        )
        if (!beginResult.success) {
            operationError = beginResult.message
            return
        }
        val result = MockLocationController.pushLocation(
            context = this,
            latitude = currentLatitude,
            longitude = currentLongitude,
            accuracyMeters = currentAccuracyMeters,
            speedMetersPerSecond = 0f,
            bearingDegrees = currentBearingDegrees,
            mode = MockMode.HOLD_POSITION
        )
        operationError = result.message
        if (!result.success) {
            return
        }

        ContextCompat.startForegroundService(
            this,
            Intent(this, HoldPositionService::class.java).apply {
                action = HoldPositionService.ACTION_START
            }
        )
        mockStatusText = "已停留在最後位置"
        activeMode = MockMode.HOLD_POSITION
    }

    private fun stopMockLocation() {
        stopCruise()
        stopDestinationWalk(holdLastPosition = false)
        stopFloatingJoystickService(holdLastPosition = false)
        stopHoldPositionService()
        MockLocationController.clearTestProvider(this)
        MockLocationController.forceIdle(this)
        applyControllerState(MockLocationController.latestState(this), syncInputs = false)
        currentSpeedMetersPerSecond = 0f
        operationError = null
        mockStatusText = "已停止模擬定位"
    }

    private fun stopHoldPositionService() {
        stopService(Intent(this, HoldPositionService::class.java))
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
        currentAccuracyMeters = location.accuracy.coerceIn(1f, 10000f)
        currentSpeedMetersPerSecond = 0f
        mapRecenterRequest += 1
        operationError = null
        mockStatusText = "已使用目前手機位置作為起點"
    }

    private fun showCurrentPhoneLocationError() {
        operationError = "無法取得目前位置，請確認定位權限與手機定位已開啟"
    }

    private fun updateMaxSpeed(value: Float) {
        maxSpeedKmh = value.coerceIn(1f, 100f)
    }

    private fun startFloatingJoystickService() {
        if (!isValidLatLng(currentLatitude, currentLongitude)) {
            operationError = "目前座標無效，請先套用指定座標或使用目前手機位置作為起點"
            return
        }

        currentAccuracyMeters = sanitizeAccuracyMeters(currentAccuracyMeters)
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

        stopCruise()
        stopDestinationWalk(holdLastPosition = false)
        requestNotificationPermissionIfNeeded()

        val startText = "懸浮搖桿起點：緯度 ${formatNumber(currentLatitude, 7)}，經度 ${formatNumber(currentLongitude, 7)}"
        overlayStatusText = startText
        val intent = Intent(this, FloatingJoystickService::class.java).apply {
            action = FloatingJoystickService.ACTION_START
            putExtra(FloatingJoystickService.EXTRA_LATITUDE, currentLatitude)
            putExtra(FloatingJoystickService.EXTRA_LONGITUDE, currentLongitude)
            putExtra(FloatingJoystickService.EXTRA_ACCURACY, currentAccuracyMeters)
            putExtra(FloatingJoystickService.EXTRA_MAX_SPEED_KMH, maxSpeedKmh)
        }
        ContextCompat.startForegroundService(this, intent)
        mockStatusText = startText
        operationError = null
    }

    private fun stopFloatingJoystickService(holdLastPosition: Boolean = true) {
        val intent = Intent(this, FloatingJoystickService::class.java).apply {
            action = FloatingJoystickService.ACTION_STOP
            putExtra(FloatingJoystickService.EXTRA_START_HOLD_ON_STOP, holdLastPosition)
        }
        startService(intent)
        overlayStatusText = if (holdLastPosition) {
            "已關閉懸浮搖桿，位置停留在最後座標"
        } else {
            "懸浮搖桿已關閉"
        }
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

            MapEditMode.DESTINATION -> {
                destinationWaypoints.add(point)
                operationError = null
                mockStatusText = "已加入第 ${destinationWaypoints.size} 個目的地"
                if (isDestinationWalking) {
                    stopDestinationWalk(holdLastPosition = true)
                    operationError = "目的地已變更，已停止慢走並停留在最後位置。"
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

            MapEditMode.DESTINATION -> {
                if (destinationWaypoints.isNotEmpty()) {
                    destinationWaypoints.removeAt(destinationWaypoints.lastIndex)
                    operationError = null
                    mockStatusText = "已復原上一個目的地"
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

    private fun clearDestinationWaypoints() {
        if (isDestinationWalking) {
            stopDestinationWalk(holdLastPosition = true)
        }
        destinationWaypoints.clear()
        currentDestinationIndex = null
        destinationRemainingDistanceMeters = 0.0
        destinationEstimatedArrivalSeconds = null
        operationError = null
        mockStatusText = "已清除目的地"
    }

    private fun generateAreaRoute() {
        if (areaPolygonPoints.size < 3) {
            operationError = "請先切換到區域圈選，並在地圖上點選至少 3 個範圍點。"
            return
        }

        val generated = runCatching {
            when (areaRouteMode) {
                AreaRouteMode.RANDOM -> generateRandomWaypointsInPolygon(
                    polygon = areaPolygonPoints,
                    count = Random.nextInt(10, 51)
                )

                AreaRouteMode.SERPENTINE -> generateSerpentineWaypointsInPolygon(areaPolygonPoints)
            }
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
        mockStatusText = "已產生 ${generated.size} 個${areaRouteMode.title}座標點"
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

    private fun startDestinationWalk() {
        if (destinationWaypoints.isEmpty()) {
            operationError = "請先切換到慢走到目的地，並在地圖上新增目的地。"
            return
        }
        refreshPermissionState()
        refreshMockAppState()
        if (!hasFineLocationPermission) {
            operationError = "慢走到目的地需要精確位置權限。"
            requestLocationPermissions()
            return
        }
        if (!isMockLocationAppSelected) {
            operationError = "開始慢走前，請先將本程式設為模擬位置應用程式。"
            return
        }

        stopFloatingJoystickService(holdLastPosition = false)
        stopCruise()
        stopDestinationWalk(holdLastPosition = false)
        stopHoldPositionService()

        val initial = LatLng(currentLatitude, currentLongitude)
        val beginResult = MockLocationController.beginMode(
            context = this,
            mode = MockMode.DESTINATION_WALK,
            latitude = initial.latitude,
            longitude = initial.longitude,
            accuracyMeters = currentAccuracyMeters
        )
        if (!beginResult.success) {
            operationError = beginResult.message
            return
        }

        val simulator = DestinationWalkSimulator(destinationWaypoints.toList())
        val initialStep = simulator.step(
            currentPosition = initial,
            speedMetersPerSecond = 0f,
            updateIntervalSeconds = 1.0
        )
        isDestinationWalking = true
        currentDestinationIndex = initialStep.targetIndex
        destinationRemainingDistanceMeters = initialStep.remainingDistanceMeters
        destinationEstimatedArrivalSeconds = null
        currentSpeedMetersPerSecond = 0f
        operationError = null
        mockStatusText = "慢走到目的地執行中"

        destinationWalkJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000L)
                val speedMetersPerSecond = destinationWalkSpeedKmh / KMH_PER_MPS
                val step = simulator.step(
                    currentPosition = LatLng(currentLatitude, currentLongitude),
                    speedMetersPerSecond = speedMetersPerSecond,
                    updateIntervalSeconds = 1.0
                )
                currentLatitude = step.position.latitude
                currentLongitude = step.position.longitude
                currentSpeedMetersPerSecond = step.speedMetersPerSecond
                currentBearingDegrees = step.bearingDegrees
                currentDestinationIndex = step.targetIndex
                destinationRemainingDistanceMeters = step.remainingDistanceMeters
                destinationEstimatedArrivalSeconds = step.estimatedArrivalSeconds

                val pushed = MockLocationController.pushLocation(
                    context = this@MainActivity,
                    latitude = currentLatitude,
                    longitude = currentLongitude,
                    accuracyMeters = currentAccuracyMeters,
                    speedMetersPerSecond = currentSpeedMetersPerSecond,
                    bearingDegrees = currentBearingDegrees,
                    mode = MockMode.DESTINATION_WALK
                )
                if (!pushed.success) {
                    operationError = pushed.message
                    stopDestinationWalk(holdLastPosition = false)
                    break
                }

                if (step.finished) {
                    finishDestinationWalkToHold()
                    break
                }
            }
        }
    }

    private fun stopDestinationWalk(holdLastPosition: Boolean = true) {
        destinationWalkJob?.cancel()
        destinationWalkJob = null
        val wasWalkingToDestination = isDestinationWalking
        isDestinationWalking = false
        currentDestinationIndex = null
        destinationEstimatedArrivalSeconds = null

        if (!wasWalkingToDestination) {
            return
        }

        currentSpeedMetersPerSecond = 0f
        if (holdLastPosition) {
            enterHoldPositionFromCurrent("已停止慢走，停留在最後位置")
        } else {
            MockLocationController.endMode(this, MockMode.DESTINATION_WALK)
            mockStatusText = "已停止慢走"
        }
    }

    private fun finishDestinationWalkToHold() {
        destinationWalkJob?.cancel()
        destinationWalkJob = null
        isDestinationWalking = false
        currentDestinationIndex = null
        destinationRemainingDistanceMeters = 0.0
        destinationEstimatedArrivalSeconds = 0.0
        currentSpeedMetersPerSecond = 0f
        enterHoldPositionFromCurrent("已抵達所有目的地，已停留在最後位置")
    }

    private fun enterHoldPositionFromCurrent(statusText: String) {
        currentSpeedMetersPerSecond = 0f
        LastMockLocationState.save(
            context = this,
            latitude = currentLatitude,
            longitude = currentLongitude,
            speedMetersPerSecond = 0f,
            bearingDegrees = currentBearingDegrees
        )
        val beginResult = MockLocationController.beginMode(
            context = this,
            mode = MockMode.HOLD_POSITION,
            latitude = currentLatitude,
            longitude = currentLongitude,
            accuracyMeters = currentAccuracyMeters
        )
        if (!beginResult.success) {
            operationError = beginResult.message
            return
        }
        val pushed = MockLocationController.pushLocation(
            context = this,
            latitude = currentLatitude,
            longitude = currentLongitude,
            accuracyMeters = currentAccuracyMeters,
            speedMetersPerSecond = 0f,
            bearingDegrees = currentBearingDegrees,
            mode = MockMode.HOLD_POSITION
        )
        operationError = pushed.message
        if (!pushed.success) {
            return
        }

        requestNotificationPermissionIfNeeded()
        ContextCompat.startForegroundService(
            this,
            Intent(this, HoldPositionService::class.java).apply {
                action = HoldPositionService.ACTION_START
            }
        )
        activeMode = MockMode.HOLD_POSITION
        mockStatusText = statusText
        refreshLastMockLocationState()
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

        stopFloatingJoystickService(holdLastPosition = false)
        stopCruise()
        stopDestinationWalk(holdLastPosition = false)
        stopHoldPositionService()

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

        val simulatorStartPosition = when {
            mode == MockMode.ROUTE_CRUISE && routeStartMode == RouteStartMode.FIRST -> null
            else -> initial
        }
        val simulator = RouteSimulator(
            waypoints = waypoints,
            endBehavior = routeEndBehavior,
            startPosition = simulatorStartPosition
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
    maxSpeedKmh: Float,
    routeSpeedKmh: Float,
    areaSpeedKmh: Float,
    destinationWalkSpeedKmh: Float,
    routeEndBehavior: RouteEndBehavior,
    routeStartMode: RouteStartMode,
    areaRouteMode: AreaRouteMode,
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
    isRouteCruising: Boolean,
    isAreaCruising: Boolean,
    isDestinationWalking: Boolean,
    hasLastMockLocation: Boolean,
    currentDestinationIndex: Int?,
    destinationRemainingDistanceMeters: Double,
    destinationEstimatedArrivalSeconds: Double?,
    mapEditMode: MapEditMode,
    mapRecenterRequest: Int,
    routeWaypoints: List<LatLng>,
    areaPolygonPoints: List<LatLng>,
    generatedAreaWaypoints: List<LatLng>,
    destinationWaypoints: List<LatLng>,
    isConsumerRunning: Boolean,
    gpsConsumerLocation: ConsumerLocationUi?,
    networkConsumerLocation: ConsumerLocationUi?,
    consumerLocation: ConsumerLocationUi?,
    onTabChange: (MainTab) -> Unit,
    onMaxSpeedChange: (Float) -> Unit,
    onRouteSpeedChange: (Float) -> Unit,
    onAreaSpeedChange: (Float) -> Unit,
    onDestinationWalkSpeedChange: (Float) -> Unit,
    onRouteEndBehaviorChange: (RouteEndBehavior) -> Unit,
    onRouteStartModeChange: (RouteStartMode) -> Unit,
    onMapEditModeChange: (MapEditMode) -> Unit,
    onAreaRouteModeChange: (AreaRouteMode) -> Unit,
    onUseLastMockLocationAsStart: () -> Unit,
    onUseCurrentPhoneLocationAsStart: () -> Unit,
    onApplySpecifiedCoordinate: (String, String, String) -> Unit,
    onClearLastMockLocation: () -> Unit,
    onResetToTaipei101: () -> Unit,
    onHoldPosition: () -> Unit,
    onStopMockLocation: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenOverlayPermissionSettings: () -> Unit,
    onStartFloatingJoystick: () -> Unit,
    onStopFloatingJoystick: () -> Unit,
    onMapClick: (LatLng) -> Unit,
    onDeleteRouteWaypoint: (Int) -> Unit,
    onDeleteAreaPolygonPoint: (Int) -> Unit,
    onUndoLastMapPoint: () -> Unit,
    onClearRoute: () -> Unit,
    onClearAreaPolygon: () -> Unit,
    onClearDestinations: () -> Unit,
    onGenerateAreaRoute: () -> Unit,
    onStartRouteCruise: () -> Unit,
    onStartAreaCruise: () -> Unit,
    onStartDestinationWalk: () -> Unit,
    onStopDestinationWalk: () -> Unit,
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
                            maxSpeedKmh = maxSpeedKmh,
                            currentLatitude = currentLatitude,
                            currentLongitude = currentLongitude,
                            currentAccuracyMeters = currentAccuracyMeters,
                            currentSpeedMetersPerSecond = currentSpeedMetersPerSecond,
                            currentBearingDegrees = currentBearingDegrees,
                            hasLastMockLocation = hasLastMockLocation,
                            isConsumerRunning = isConsumerRunning,
                            gpsConsumerLocation = gpsConsumerLocation,
                            networkConsumerLocation = networkConsumerLocation,
                            consumerLocation = consumerLocation,
                            onMaxSpeedChange = onMaxSpeedChange,
                            onUseLastMockLocationAsStart = onUseLastMockLocationAsStart,
                            onUseCurrentPhoneLocationAsStart = onUseCurrentPhoneLocationAsStart,
                            onApplySpecifiedCoordinate = onApplySpecifiedCoordinate,
                            onClearLastMockLocation = onClearLastMockLocation,
                            onResetToTaipei101 = onResetToTaipei101,
                            onStopMockLocation = onStopMockLocation,
                            onStartFloatingJoystick = onStartFloatingJoystick,
                            onStopFloatingJoystick = onStopFloatingJoystick,
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
                        destinationWalkSpeedKmh = destinationWalkSpeedKmh,
                        routeEndBehavior = routeEndBehavior,
                        routeStartMode = routeStartMode,
                        mapEditMode = mapEditMode,
                        areaRouteMode = areaRouteMode,
                        routeWaypoints = routeWaypoints,
                        areaPolygonPoints = areaPolygonPoints,
                        generatedAreaWaypoints = generatedAreaWaypoints,
                        destinationWaypoints = destinationWaypoints,
                        isRouteCruising = isRouteCruising,
                        isAreaCruising = isAreaCruising,
                        isDestinationWalking = isDestinationWalking,
                        hasLastMockLocation = hasLastMockLocation,
                        currentDestinationIndex = currentDestinationIndex,
                        destinationRemainingDistanceMeters = destinationRemainingDistanceMeters,
                        destinationEstimatedArrivalSeconds = destinationEstimatedArrivalSeconds,
                        mapRecenterRequest = mapRecenterRequest,
                        onRouteSpeedChange = onRouteSpeedChange,
                        onAreaSpeedChange = onAreaSpeedChange,
                        onDestinationWalkSpeedChange = onDestinationWalkSpeedChange,
                        onRouteEndBehaviorChange = onRouteEndBehaviorChange,
                        onRouteStartModeChange = onRouteStartModeChange,
                        onMapEditModeChange = onMapEditModeChange,
                        onAreaRouteModeChange = onAreaRouteModeChange,
                        onUseLastMockLocationAsStart = onUseLastMockLocationAsStart,
                        onUseCurrentPhoneLocationAsStart = onUseCurrentPhoneLocationAsStart,
                        onClearLastMockLocation = onClearLastMockLocation,
                        onHoldPosition = onHoldPosition,
                        onStopMockLocation = onStopMockLocation,
                        onMapClick = onMapClick,
                        onDeleteRouteWaypoint = onDeleteRouteWaypoint,
                        onDeleteAreaPolygonPoint = onDeleteAreaPolygonPoint,
                        onUndoLastMapPoint = onUndoLastMapPoint,
                        onClearRoute = onClearRoute,
                        onClearAreaPolygon = onClearAreaPolygon,
                        onClearDestinations = onClearDestinations,
                        onGenerateAreaRoute = onGenerateAreaRoute,
                        onStartRouteCruise = onStartRouteCruise,
                        onStartAreaCruise = onStartAreaCruise,
                        onStartDestinationWalk = onStartDestinationWalk,
                        onStopDestinationWalk = onStopDestinationWalk,
                        onStopCruise = onStopCruise
                )
            }
        }
    }
}

data class ConsumerLocationUi(
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
        MockMode.FLOATING_JOYSTICK -> "懸浮搖桿"
        MockMode.ROUTE_CRUISE -> "路徑巡航"
        MockMode.AREA_CRUISE -> "區域巡航"
        MockMode.HOLD_POSITION -> "停留在最後位置"
        MockMode.DESTINATION_WALK -> "慢走到目的地"
    }
}

fun formatNumber(value: Double, decimals: Int): String {
    return String.format(Locale.US, "%.${decimals}f", value)
}

private enum class MainTab(val title: String) {
    MANUAL("定位狀態"),
    MAP("地圖繞行模式")
}


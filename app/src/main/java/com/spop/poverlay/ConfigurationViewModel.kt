package com.spop.poverlay

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.spop.poverlay.overlay.OverlayService
import com.spop.poverlay.releases.Release
import com.spop.poverlay.releases.ReleaseChecker
import com.spop.poverlay.ride.RideSessionRecord
import com.spop.poverlay.ride.RideSessionStore
import com.spop.poverlay.ride.RideSessionSummary
import com.spop.poverlay.route.ImportedRoute
import com.spop.poverlay.route.GradeResistanceMapper
import com.spop.poverlay.route.ManualResistanceGuidanceEngine
import com.spop.poverlay.route.ManualResistanceTolerance
import com.spop.poverlay.route.RouteGradeSmoother
import com.spop.poverlay.route.RouteHudState
import com.spop.poverlay.route.RouteProgress
import com.spop.poverlay.route.RouteResistancePreset
import com.spop.poverlay.route.RouteRideRuntime
import com.spop.poverlay.route.RouteRideState
import com.spop.poverlay.route.RouteStore
import com.spop.poverlay.route.RouteUploadPortalState
import com.spop.poverlay.route.RouteUploadServer
import com.spop.poverlay.sensor.hr.BluetoothHeartRateMonitor
import com.spop.poverlay.sensor.hr.HeartRatePermissions
import com.spop.poverlay.sensor.hr.WorkoutServicesHeartRateMonitor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import com.spop.poverlay.sensor.interfaces.DummySensorInterface
import com.spop.poverlay.sensor.interfaces.PelotonBikePlusSensorInterface
import com.spop.poverlay.sensor.interfaces.PelotonBikeSensorInterfaceV1New
import com.spop.poverlay.sensor.interfaces.SensorInterface
import com.spop.poverlay.sensor.v2.BikePlusService
import com.spop.poverlay.overlay.AutoResistanceController
import com.spop.poverlay.util.IsBikePlus
import com.spop.poverlay.util.IsRunningOnPeloton
import com.spop.poverlay.util.calculateSpeedFromPelotonV1Power
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val RouteResistanceLogTag = "SwitchbackRouteResistance"

class ConfigurationViewModel(
    application: Application,
    private val configurationRepository: ConfigurationRepository,
    private val releaseChecker: ReleaseChecker
) : AndroidViewModel(application) {
    val finishActivity = MutableLiveData<Unit>()
    val requestOverlayPermission = MutableLiveData<Unit>()
    val requestHeartRatePermissions = MutableLiveData<Unit>()
    val requestGpxImport = MutableLiveData<Unit>()
    val requestRestart = MutableLiveData<Unit>()
    val showPermissionInfo = mutableStateOf(false)
    val infoPopup = MutableLiveData<String>()

    // Map of release names to if they're the currently installed one
    var latestRelease = mutableStateOf<Release?>(null)
    var rideSessionSummaries = mutableStateOf<List<RideSessionSummary>>(emptyList())
    var selectedRideSession = mutableStateOf<RideSessionRecord?>(null)
    var importedRoutes = mutableStateOf<List<ImportedRoute>>(emptyList())
    var selectedRoute = mutableStateOf<ImportedRoute?>(null)
    var routeRideState = mutableStateOf<RouteRideState>(RouteRideState.Idle)
    var routeUploadPortalState = mutableStateOf(RouteUploadPortalState())
    var liveRideDashboardState = mutableStateOf(LiveRideDashboardState())

    private val dashboardGuidanceEngine = ManualResistanceGuidanceEngine()
    private var dashboardGuidanceBaseline: Int? = null

    private val rideSessionStore = RideSessionStore(application)
    private val routeStore = RouteStore(File(application.filesDir, "routes"))
    private val dashboardSensorInterface: SensorInterface = when {
        IsRunningOnPeloton && IsBikePlus -> PelotonBikePlusSensorInterface(application)
        IsRunningOnPeloton -> PelotonBikeSensorInterfaceV1New(application)
        else -> DummySensorInterface()
    }
    private val routeImportDir: File
        get() = File(getApplication<Application>().getExternalFilesDir(null), "route_imports")
    private val routeRideRuntime = RouteRideRuntime()
    private var dashboardBikePlusService: BikePlusService? = null
    private var dashboardRouteResistanceRouteId: String? = null
    private var dashboardRouteResistanceBaseline: Int? = null
    private var dashboardLastRouteResistanceRequest: Int? = null
    private var dashboardRouteResistanceWriteAtMs = 0L
    private val dashboardRouteResistanceWriteInFlight = AtomicBoolean(false)
    private var dashboardResistanceTelemetrySeen = false
    private val dashboardAutoResistanceController = AutoResistanceController()
    private val routeUploadServer: RouteUploadServer = RouteUploadServer(
        routeStore = routeStore,
        onImported = {
            val routes = routeStore.listRoutes()
            viewModelScope.launch(Dispatchers.Main) {
                importedRoutes.value = routes
                routeUploadPortalState.value = RouteUploadPortalState()
                infoPopup.value = "Imported ${it.name}"
            }
        },
        onMessage = { message ->
            viewModelScope.launch(Dispatchers.Main) {
                routeUploadPortalState.value = routeUploadPortalState.value.copy(message = message)
                infoPopup.value = message
            }
        }
    )

    val showTimerWhenMinimized
        get() = configurationRepository.showTimerWhenMinimized
    val bikePlusResistanceControlEnabled
        get() = configurationRepository.bikePlusResistanceControlEnabled
    val bikePlusResistanceControlOverlayVisible
        get() = configurationRepository.bikePlusResistanceControlOverlayVisible
    val routeResistanceSimulationEnabled
        get() = configurationRepository.routeResistanceSimulationEnabled
    val routeResistancePreset
        get() = configurationRepository.routeResistancePreset
    val activeRouteId
        get() = configurationRepository.activeRouteId
    val activeRoutePositionMeters
        get() = configurationRepository.activeRoutePositionMeters
    val heartRateMonitorEnabled
        get() = configurationRepository.heartRateMonitorEnabled
    val rideSessionRecordingEnabled
        get() = configurationRepository.rideSessionRecordingEnabled
    val hudShowPower
        get() = configurationRepository.hudShowPower
    val hudShowSpeed
        get() = configurationRepository.hudShowSpeed
    val hudShowDistance
        get() = configurationRepository.hudShowDistance
    val hudShowTime
        get() = configurationRepository.hudShowTime
    val hudShowResistance
        get() = configurationRepository.hudShowResistance
    val hudShowHeartRate
        get() = configurationRepository.hudShowHeartRate
    val hudShowCalories
        get() = configurationRepository.hudShowCalories
    val manualResistanceGuidanceEnabled
        get() = configurationRepository.manualResistanceGuidanceEnabled
    val manualResistanceTolerance
        get() = configurationRepository.manualResistanceTolerance
    val manualResistanceWarningSeconds
        get() = configurationRepository.manualResistanceWarningSeconds
    val overlayRunning
        get() = OverlayService.isRunning

    init {
        updatePermissionState()
        setupLiveRideDashboard()
    }

    private fun updatePermissionState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            showPermissionInfo.value = !Settings.canDrawOverlays(getApplication())
        } else {
            showPermissionInfo.value = false
        }
    }

    fun onShowTimerWhenMinimizedClicked(isChecked: Boolean) {
        configurationRepository.setShowTimerWhenMinimized(isChecked)
    }

    fun onBikePlusResistanceControlClicked(isChecked: Boolean) {
        configurationRepository.setBikePlusResistanceControlEnabled(isChecked)
    }

    fun onBikePlusResistanceControlOverlayVisibleClicked(isChecked: Boolean) {
        configurationRepository.setBikePlusResistanceControlOverlayVisible(isChecked)
    }

    fun onRouteResistanceSimulationEnabledClicked(isChecked: Boolean) {
        configurationRepository.setRouteResistanceSimulationEnabled(isChecked)
        if (!isChecked) resetDashboardRouteResistanceAutomation()
    }

    fun onRouteResistancePresetClicked(presetId: String) {
        configurationRepository.setRouteResistancePreset(presetId)
    }

    fun onHeartRateMonitorEnabledClicked(isChecked: Boolean) {
        configurationRepository.setHeartRateMonitorEnabled(isChecked)
        if (isChecked && !HeartRatePermissions.hasRequiredPermissions(getApplication())) {
            requestHeartRatePermissions.value = Unit
        }
    }

    fun onRideSessionRecordingEnabledClicked(isChecked: Boolean) {
        configurationRepository.setRideSessionRecordingEnabled(isChecked)
    }

    fun onHudShowPowerClicked(isChecked: Boolean) {
        configurationRepository.setHudFieldVisible(ConfigurationRepository.Preferences.HudShowPower, isChecked)
    }

    fun onHudShowSpeedClicked(isChecked: Boolean) {
        configurationRepository.setHudFieldVisible(ConfigurationRepository.Preferences.HudShowSpeed, isChecked)
    }

    fun onHudShowDistanceClicked(isChecked: Boolean) {
        configurationRepository.setHudFieldVisible(ConfigurationRepository.Preferences.HudShowDistance, isChecked)
    }

    fun onHudShowTimeClicked(isChecked: Boolean) {
        configurationRepository.setHudFieldVisible(ConfigurationRepository.Preferences.HudShowTime, isChecked)
    }

    fun onHudShowResistanceClicked(isChecked: Boolean) {
        configurationRepository.setHudFieldVisible(ConfigurationRepository.Preferences.HudShowResistance, isChecked)
    }

    fun onHudShowHeartRateClicked(isChecked: Boolean) {
        configurationRepository.setHudFieldVisible(ConfigurationRepository.Preferences.HudShowHeartRate, isChecked)
    }

    fun onHudShowCaloriesClicked(isChecked: Boolean) {
        configurationRepository.setHudFieldVisible(ConfigurationRepository.Preferences.HudShowCalories, isChecked)
    }

    fun onManualResistanceGuidanceEnabledClicked(isEnabled: Boolean) {
        configurationRepository.setManualResistanceGuidanceEnabled(isEnabled)
        if (!isEnabled) dashboardGuidanceEngine.reset()
    }

    fun onManualResistanceToleranceSelected(toleranceId: String) {
        configurationRepository.setManualResistanceTolerance(toleranceId)
    }

    fun onManualResistanceWarningSecondsSelected(seconds: Int) {
        configurationRepository.setManualResistanceWarningSeconds(seconds)
    }

    fun onResetHudClicked() {
        configurationRepository.resetHudSettings()
    }

    fun onStartServiceClicked() {
        Timber.i("Starting service")
        ContextCompat.startForegroundService(
            getApplication(),
            Intent(getApplication(), OverlayService::class.java)
        )
        finishActivity.value = Unit
    }

    fun onStopServiceClicked() {
        Timber.i("Stopping service")
        getApplication<Application>().stopService(Intent(getApplication(), OverlayService::class.java))
        infoPopup.value = "Overlay stopped"
    }

    fun onGrantPermissionClicked() {
        requestOverlayPermission.value = Unit
    }

    fun onRestartClicked() {
        requestRestart.value = Unit
    }

    fun onClickedRelease(release: Release) {
        val browserIntent = Intent(Intent.ACTION_VIEW, release.url)
        getApplication<Application>().startActivity(browserIntent)
    }

    fun onResume() {
        updatePermissionState()
        refreshRideSessions()
        refreshRoutes()
        viewModelScope.launch(Dispatchers.IO) {
            releaseChecker.getLatestRelease()
                .onSuccess { release ->
                    latestRelease.value = release
                }
                .onFailure {
                    Timber.e(it, "failed to fetch release info")
                }
        }
    }

    fun onOverlayPermissionRequestCompleted(wasGranted: Boolean) {
        updatePermissionState()
        val prompt = if (wasGranted) {
            "Permission granted, click 'Start Overlay' to get started"
        } else {
            "Without this permission the app cannot function"
        }
        infoPopup.postValue(prompt)
    }

    fun onHeartRatePermissionsRequestCompleted(wasGranted: Boolean) {
        val prompt = if (wasGranted) {
            "Heart rate permission granted. Start HeartCast, then start the overlay."
        } else {
            configurationRepository.setHeartRateMonitorEnabled(false)
            "Heart rate is off until Bluetooth permission is granted."
        }
        infoPopup.postValue(prompt)
    }

    fun refreshRideSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            val summaries = rideSessionStore.listSessionSummaries()
            withContext(Dispatchers.Main) {
                rideSessionSummaries.value = summaries
            }
        }
    }

    fun onRideSessionClicked(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = rideSessionStore.loadSession(id)
            withContext(Dispatchers.Main) {
                selectedRideSession.value = record
            }
        }
    }

    fun onBackFromRideSession() {
        selectedRideSession.value = null
    }

    fun onImportGpxClicked() {
        requestGpxImport.value = Unit
    }

    fun onAddRouteClicked() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                routeUploadServer.start(viewModelScope)
            }.onSuccess { url ->
                withContext(Dispatchers.Main) {
                    routeUploadPortalState.value = RouteUploadPortalState(
                        isRunning = true,
                        url = url,
                        message = "Scan the QR code from a device on the same Wi-Fi."
                    )
                }
            }.onFailure {
                Timber.e(it, "failed to start route upload portal")
                withContext(Dispatchers.Main) {
                    routeUploadPortalState.value = RouteUploadPortalState(
                        isRunning = false,
                        message = "Could not start route upload portal"
                    )
                }
            }
        }
    }

    fun onCloseRouteUploadClicked() {
        routeUploadServer.stop()
        routeUploadPortalState.value = RouteUploadPortalState()
    }

    fun importGpxRoute(fileName: String, gpxText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                routeStore.saveGpx(fileName, gpxText)
            }
                .onSuccess { route ->
                    val routes = routeStore.listRoutes()
                    withContext(Dispatchers.Main) {
                        importedRoutes.value = routes
                        infoPopup.value = "Imported ${route.name}"
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "failed to import GPX route")
                    withContext(Dispatchers.Main) {
                        infoPopup.value = "Could not import GPX route"
                    }
                }
        }
    }

    fun importGpxRoutesFromDropFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            routeImportDir.mkdirs()
            val files = routeImportDir
                .listFiles { file ->
                    file.isFile && file.extension.equals("gpx", ignoreCase = true)
                }
                ?.sortedBy { it.name }
                ?: emptyList()

            if (files.isEmpty()) {
                withContext(Dispatchers.Main) {
                    infoPopup.value = "Put GPX files in ${routeImportDir.absolutePath}, then tap Import GPX."
                }
                return@launch
            }

            val imported = files.mapNotNull { file ->
                runCatching {
                    routeStore.saveGpx(file.nameWithoutExtension, file.readText())
                }.onFailure {
                    Timber.e(it, "failed to import GPX route from ${file.absolutePath}")
                }.getOrNull()
            }

            val routes = routeStore.listRoutes()
            withContext(Dispatchers.Main) {
                importedRoutes.value = routes
                infoPopup.value = if (imported.isEmpty()) {
                    "Could not import GPX files"
                } else {
                    "Imported ${imported.size} GPX route${if (imported.size == 1) "" else "s"}"
                }
            }
        }
    }

    fun refreshRoutes() {
        viewModelScope.launch(Dispatchers.IO) {
            val routes = routeStore.listRoutes()
            withContext(Dispatchers.Main) {
                importedRoutes.value = routes
                selectedRoute.value = selectedRoute.value?.let { selected ->
                    routes.firstOrNull { it.id == selected.id }
                }
                restoreActiveRouteState(routes)
            }
        }
    }

    fun onRouteClicked(id: String) {
        selectedRoute.value = importedRoutes.value.firstOrNull { it.id == id }
    }

    fun onBackFromRoute() {
        selectedRoute.value = null
    }

    fun onStartRouteClicked(route: ImportedRoute) {
        val savedPosition = if (configurationRepository.activeRouteId.value == route.id) {
            configurationRepository.activeRoutePositionMeters.value
        } else {
            0.0
        }
        routeRideRuntime.start(route, savedPosition)
        configurationRepository.setActiveRouteId(route.id)
        configurationRepository.setActiveRoutePositionMeters(savedPosition)
        routeRideState.value = routeRideRuntime.state
        // Reset guidance baseline so it is re-captured from current resistance for this route
        // context rather than carrying over from a previous route or stale state.
        resetDashboardRouteResistanceAutomation()
        infoPopup.value = if (savedPosition > 0.0) {
            "Resumed ${route.name}"
        } else {
            "Started ${route.name}"
        }
    }

    fun onRestartRouteClicked(route: ImportedRoute) {
        routeRideRuntime.start(route)
        configurationRepository.setActiveRouteId(route.id)
        configurationRepository.setActiveRoutePositionMeters(0.0)
        routeRideState.value = routeRideRuntime.state
        resetDashboardRouteResistanceAutomation()
        infoPopup.value = "Started ${route.name}"
    }

    fun onResetRouteClicked() {
        routeRideRuntime.reset()
        configurationRepository.setActiveRouteId(null)
        routeRideState.value = routeRideRuntime.state
        resetDashboardRouteResistanceAutomation()
        infoPopup.value = "Route cleared"
    }

    fun onDeleteRouteClicked(route: ImportedRoute) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = routeStore.deleteRoute(route.id)
            val routes = routeStore.listRoutes()
            withContext(Dispatchers.Main) {
                if (configurationRepository.activeRouteId.value == route.id) {
                    routeRideRuntime.reset()
                    routeRideState.value = routeRideRuntime.state
                    configurationRepository.setActiveRouteId(null)
                    resetDashboardRouteResistanceAutomation()
                }
                selectedRoute.value = null
                importedRoutes.value = routes
                infoPopup.value = if (deleted) {
                    "Deleted ${route.name}"
                } else {
                    "Could not delete route"
                }
            }
        }
    }

    private fun restoreActiveRouteState(routes: List<ImportedRoute>) {
        val activeRoute = configurationRepository.activeRouteId.value?.let { id ->
            routes.firstOrNull { it.id == id }
        } ?: return
        routeRideRuntime.start(
            activeRoute,
            configurationRepository.activeRoutePositionMeters.value
        )
        routeRideState.value = routeRideRuntime.state
    }

    private fun setupLiveRideDashboard() {
        viewModelScope.launch(Dispatchers.IO) {
            dashboardSensorInterface.power.collect { value ->
                liveRideDashboardState.value = liveRideDashboardState.value.copy(powerWatts = value)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            dashboardSensorInterface.cadence.collect { value ->
                liveRideDashboardState.value = liveRideDashboardState.value.copy(cadenceRpm = value)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            dashboardSensorInterface.resistance.collect { value ->
                dashboardResistanceTelemetrySeen = true
                liveRideDashboardState.value = liveRideDashboardState.value.copy(resistance = value.toInt())
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            configurationRepository.heartRateMonitorEnabled.collectLatest { isEnabled ->
                if (!isEnabled) {
                    liveRideDashboardState.value = liveRideDashboardState.value.copy(heartRateBpm = null)
                    return@collectLatest
                }
                val nativeMonitor = if (IsRunningOnPeloton) {
                    WorkoutServicesHeartRateMonitor(getApplication()).also { it.start() }
                } else null
                val bleMonitor = BluetoothHeartRateMonitor(getApplication()).also { it.start() }
                try {
                    val hrFlow = if (nativeMonitor != null) {
                        combine(nativeMonitor.heartRateBpm, bleMonitor.heartRateBpm) { native, ble ->
                            native ?: ble
                        }
                    } else {
                        bleMonitor.heartRateBpm
                    }
                    hrFlow.collect { bpm ->
                        liveRideDashboardState.value = liveRideDashboardState.value.copy(heartRateBpm = bpm)
                    }
                } finally {
                    nativeMonitor?.close()
                    bleMonitor.close()
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            var previousEnabled = configurationRepository.routeResistanceSimulationEnabled.value
            configurationRepository.routeResistanceSimulationEnabled.collect { enabled ->
                if (enabled && !previousEnabled) {
                    dashboardAutoResistanceController.onAutoReEnabled()
                    Log.i(RouteResistanceLogTag, "Dashboard auto resistance re-enabled: controller reset")
                }
                previousEnabled = enabled
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                val current = liveRideDashboardState.value
                val isActive = current.powerWatts > 0f || current.cadenceRpm > 0f
                val speedMph = calculateSpeedFromPelotonV1Power(current.powerWatts)
                val nextDistance = if (isActive) {
                    current.distanceMiles + speedMph / 3600f
                } else {
                    current.distanceMiles
                }
                val nextElapsed = if (isActive) current.elapsedSeconds + 1L else current.elapsedSeconds
                val nextWork = if (isActive) current.workKilojoules + current.powerWatts / 1000f else current.workKilojoules
                val (routeHudState, routeProgress) = dashboardRouteHudState(nextDistance)
                maybeApplyDashboardRouteResistance(
                    routeHudState = routeHudState,
                    isRideActive = isActive,
                    currentResistance = current.resistance
                )
                // Guard: do not capture baseline until real resistance telemetry has arrived.
                // current.resistance defaults to 0 before any telemetry; locking in 0 would
                // produce bogus guidance targets for the entire ride.
                val suppressGuidanceForBikePlusAuto =
                    IsBikePlus &&
                            configurationRepository.bikePlusResistanceControlEnabled.value &&
                            configurationRepository.routeResistanceSimulationEnabled.value
                val guidanceState = if (routeHudState != null && dashboardResistanceTelemetrySeen) {
                    val baseline = dashboardGuidanceBaseline
                        ?: current.resistance.also { dashboardGuidanceBaseline = it }
                    val preset = RouteResistancePreset.fromId(
                        configurationRepository.routeResistancePreset.value
                    )
                    dashboardGuidanceEngine.evaluate(
                        currentResistance = current.resistance,
                        guidanceBaseline = baseline,
                        smoothedGradePercent = routeHudState.gradePercent,
                        upcomingPoints = routeProgress?.upcomingPoints ?: emptyList(),
                        currentPositionMeters = routeProgress?.positionMeters ?: 0.0,
                        speedMph = speedMph,
                        toleranceMode = ManualResistanceTolerance.fromId(
                            configurationRepository.manualResistanceTolerance.value
                        ),
                        warningSeconds = configurationRepository.manualResistanceWarningSeconds.value,
                        enabled = configurationRepository.manualResistanceGuidanceEnabled.value,
                        isBikePlus = suppressGuidanceForBikePlusAuto,
                        timestampMs = android.os.SystemClock.elapsedRealtime(),
                        preset = preset
                    )
                } else {
                    null
                }
                liveRideDashboardState.value = current.copy(
                    speedMph = speedMph,
                    distanceMiles = nextDistance,
                    elapsedSeconds = nextElapsed,
                    workKilojoules = nextWork,
                    routeHudState = routeHudState,
                    visualResistanceCue = null,
                    guidanceState = guidanceState
                )
            }
        }
    }

    private fun maybeApplyDashboardRouteResistance(
        routeHudState: RouteHudState?,
        isRideActive: Boolean,
        currentResistance: Int
    ) {
        val routeId = configurationRepository.activeRouteId.value
        if (routeHudState == null || routeId == null) {
            resetDashboardRouteResistanceAutomation()
            return
        }
        if (!IsBikePlus || OverlayService.isRunning.value) {
            return
        }
        if (dashboardRouteResistanceRouteId != routeId) {
            dashboardRouteResistanceRouteId = routeId
            dashboardRouteResistanceBaseline = null
            dashboardLastRouteResistanceRequest = null
            dashboardRouteResistanceWriteAtMs = 0L
            dashboardAutoResistanceController.reset()
        }
        if (!isRideActive) {
            Log.i(RouteResistanceLogTag, "Dashboard route resistance skipped: ride inactive")
            return
        }
        if (!configurationRepository.routeResistanceSimulationEnabled.value) {
            Log.i(RouteResistanceLogTag, "Dashboard route resistance skipped: simulation disabled")
            return
        }
        if (!dashboardResistanceTelemetrySeen) {
            Log.i(RouteResistanceLogTag, "Dashboard route resistance skipped: no resistance telemetry")
            return
        }
        val baseline = dashboardRouteResistanceBaseline ?: currentResistance.also {
            dashboardRouteResistanceBaseline = it
            dashboardLastRouteResistanceRequest = it
        }
        val preset = RouteResistancePreset.fromId(configurationRepository.routeResistancePreset.value)
        val targetResistance = GradeResistanceMapper(preset).targetResistance(
            baselineResistance = baseline,
            gradePercent = routeHudState.gradePercent,
            previousRequestedResistance = dashboardLastRouteResistanceRequest
        )
        val now = SystemClock.elapsedRealtime()

        // Detect physical knob override: current resistance diverged from what auto last wrote.
        val lastRequest = dashboardLastRouteResistanceRequest
        if (lastRequest != null && !dashboardRouteResistanceWriteInFlight.get() &&
            abs(currentResistance - lastRequest) > 1
        ) {
            val shouldDisableAuto = dashboardAutoResistanceController.recordManualOverride(now)
            val mapper = GradeResistanceMapper(preset)
            dashboardRouteResistanceBaseline = mapper.baselineForTargetResistance(
                targetResistance = currentResistance,
                gradePercent = routeHudState.gradePercent
            )
            dashboardLastRouteResistanceRequest = currentResistance
            Log.i(
                RouteResistanceLogTag,
                "Dashboard manual override detected: resistance=$currentResistance strikeCount=${dashboardAutoResistanceController.strikeCount}"
            )
            if (shouldDisableAuto) {
                Log.i(RouteResistanceLogTag, "Dashboard auto resistance disabled: too many manual overrides")
                configurationRepository.setRouteResistanceSimulationEnabled(false)
                return
            }
        }

        if (dashboardAutoResistanceController.isSuspended(now)) {
            Log.i(RouteResistanceLogTag, "Dashboard route resistance suspended: manual override active")
            return
        }

        if (targetResistance == dashboardLastRouteResistanceRequest || dashboardRouteResistanceWriteInFlight.get()) {
            dashboardAutoResistanceController.clearPending()
            Log.i(
                RouteResistanceLogTag,
                "Dashboard route resistance skipped: no target change or write in flight baseline=$baseline current=$currentResistance grade=${routeHudState.gradePercent} lastRequested=$dashboardLastRouteResistanceRequest target=$targetResistance inFlight=${dashboardRouteResistanceWriteInFlight.get()}"
            )
            return
        }

        val committedTarget = dashboardAutoResistanceController.stabilizeTarget(targetResistance, now)
        if (committedTarget == null) {
            Log.i(
                RouteResistanceLogTag,
                "Dashboard route resistance deferred: target=$targetResistance pending dwell baseline=$baseline grade=${routeHudState.gradePercent}"
            )
            return
        }

        if (dashboardRouteResistanceWriteAtMs != 0L &&
            now - dashboardRouteResistanceWriteAtMs < preset.minWriteIntervalMs
        ) {
            Log.i(
                RouteResistanceLogTag,
                "Dashboard route resistance skipped: rate limited elapsedMs=${now - dashboardRouteResistanceWriteAtMs} minMs=${preset.minWriteIntervalMs} target=$committedTarget"
            )
            return
        }

        if (!dashboardRouteResistanceWriteInFlight.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bikePlusService = dashboardBikePlusService
                    ?: (dashboardSensorInterface as? PelotonBikePlusSensorInterface)
                        ?.getBikePlusService(configurationRepository)
                        ?.also { dashboardBikePlusService = it }
                if (bikePlusService == null) {
                    Log.i(RouteResistanceLogTag, "Dashboard route resistance skipped: control unavailable")
                    return@launch
                }
                Log.i(
                    RouteResistanceLogTag,
                    "Dashboard route resistance target: preset=${preset.id} baseline=$baseline grade=${routeHudState.gradePercent} target=$committedTarget"
                )
                bikePlusService
                    .setResistance(committedTarget)
                    .onSuccess {
                        dashboardLastRouteResistanceRequest = it
                        dashboardRouteResistanceWriteAtMs = SystemClock.elapsedRealtime()
                        Log.i(RouteResistanceLogTag, "Dashboard route resistance sent: resistance=$it")
                    }
                    .onFailure {
                        Log.e(RouteResistanceLogTag, "Dashboard route resistance write failed", it)
                    }
            } finally {
                dashboardRouteResistanceWriteInFlight.set(false)
            }
        }
    }

    private fun resetDashboardRouteResistanceAutomation() {
        dashboardRouteResistanceRouteId = null
        dashboardRouteResistanceBaseline = null
        dashboardLastRouteResistanceRequest = null
        dashboardRouteResistanceWriteAtMs = 0L
        dashboardRouteResistanceWriteInFlight.set(false)
        dashboardGuidanceBaseline = null
        dashboardGuidanceEngine.reset()
        dashboardAutoResistanceController.reset()
    }

    private fun dashboardRouteHudState(distanceMiles: Float): Pair<RouteHudState?, RouteProgress?> {
        val activeRoute = configurationRepository.activeRouteId.value?.let { routeStore.loadRoute(it) }
            ?: return Pair(null, null)
        val startPositionMeters = configurationRepository.activeRoutePositionMeters.value
            .coerceIn(0.0, activeRoute.metadata.distanceMeters)
        val progress = RouteRideRuntime()
            .start(activeRoute, startPositionMeters + (distanceMiles.toDouble() * 1609.344))
        val preset = RouteResistancePreset.fromId(configurationRepository.routeResistancePreset.value)
        val grade = RouteGradeSmoother(preset.lookAheadMeters).gradePercent(activeRoute, progress.positionMeters)
        val hudState = RouteHudState(
            routeName = activeRoute.name,
            progressPercent = progress.progressPercent,
            positionMeters = progress.positionMeters,
            remainingMiles = progress.remainingMeters / 1609.344,
            gradePercent = grade,
            rawGradePercent = progress.gradePercent,
            latitude = progress.latitude,
            longitude = progress.longitude,
            elevationMeters = progress.elevationMeters,
            points = activeRoute.points,
            isComplete = progress.isComplete,
            visualResistanceCue = null
        )
        return Pair(hudState, progress)
    }

    override fun onCleared() {
        routeUploadServer.stop()
        (dashboardSensorInterface as? PelotonBikePlusSensorInterface)?.stop()
        (dashboardSensorInterface as? PelotonBikeSensorInterfaceV1New)?.stop()
        super.onCleared()
    }
}

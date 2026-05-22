package com.spop.poverlay

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import com.spop.poverlay.route.RouteGradeSmoother
import com.spop.poverlay.route.RouteHudState
import com.spop.poverlay.route.RouteResistancePreset
import com.spop.poverlay.route.RouteRideRuntime
import com.spop.poverlay.route.RouteRideState
import com.spop.poverlay.route.RouteStore
import com.spop.poverlay.route.RouteUploadPortalState
import com.spop.poverlay.route.RouteUploadServer
import com.spop.poverlay.sensor.hr.HeartRatePermissions
import com.spop.poverlay.sensor.interfaces.DummySensorInterface
import com.spop.poverlay.sensor.interfaces.PelotonBikePlusSensorInterface
import com.spop.poverlay.sensor.interfaces.PelotonBikeSensorInterfaceV1New
import com.spop.poverlay.sensor.interfaces.SensorInterface
import com.spop.poverlay.util.IsBikePlus
import com.spop.poverlay.util.IsRunningOnPeloton
import com.spop.poverlay.util.calculateSpeedFromPelotonV1Power
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

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
        if (isChecked) {
            val activeRoute = configurationRepository.activeRouteId.value?.let { id ->
                importedRoutes.value.firstOrNull { it.id == id }
            }
            if (activeRoute != null && ensureOverlayServiceRunningForRouteResistance()) {
                infoPopup.value = "Route resistance ready for ${activeRoute.name}"
            }
        }
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
        val startedRouteAutomation = ensureOverlayServiceRunningForRouteResistance()
        infoPopup.value = if (savedPosition > 0.0) {
            "Resumed ${route.name}${routeAutomationSuffix(startedRouteAutomation)}"
        } else {
            "Started ${route.name}${routeAutomationSuffix(startedRouteAutomation)}"
        }
    }

    fun onRestartRouteClicked(route: ImportedRoute) {
        routeRideRuntime.start(route)
        configurationRepository.setActiveRouteId(route.id)
        configurationRepository.setActiveRoutePositionMeters(0.0)
        routeRideState.value = routeRideRuntime.state
        val startedRouteAutomation = ensureOverlayServiceRunningForRouteResistance()
        infoPopup.value = "Started ${route.name}${routeAutomationSuffix(startedRouteAutomation)}"
    }

    fun onResetRouteClicked() {
        routeRideRuntime.reset()
        configurationRepository.setActiveRouteId(null)
        routeRideState.value = routeRideRuntime.state
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

    private fun ensureOverlayServiceRunningForRouteResistance(): Boolean {
        if (!IsBikePlus || !configurationRepository.routeResistanceSimulationEnabled.value) {
            return false
        }
        if (OverlayService.isRunning.value) {
            return false
        }
        Timber.i("Starting overlay service for route resistance")
        ContextCompat.startForegroundService(
            getApplication(),
            Intent(getApplication(), OverlayService::class.java)
        )
        return true
    }

    private fun routeAutomationSuffix(startedRouteAutomation: Boolean): String =
        if (startedRouteAutomation) " · route resistance ready" else ""

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
                liveRideDashboardState.value = liveRideDashboardState.value.copy(resistance = value.toInt())
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
                val routeHudState = dashboardRouteHudState(nextDistance)
                liveRideDashboardState.value = current.copy(
                    speedMph = speedMph,
                    distanceMiles = nextDistance,
                    elapsedSeconds = nextElapsed,
                    workKilojoules = nextWork,
                    routeHudState = routeHudState,
                    visualResistanceCue = if (IsBikePlus) {
                        null
                    } else routeHudState?.let {
                        visualResistanceCue(
                            currentResistance = current.resistance,
                            gradePercent = it.gradePercent
                        )
                    }
                )
            }
        }
    }

    private fun dashboardRouteHudState(distanceMiles: Float): RouteHudState? {
        val activeRoute = configurationRepository.activeRouteId.value?.let { routeStore.loadRoute(it) }
            ?: return null
        val startPositionMeters = configurationRepository.activeRoutePositionMeters.value
            .coerceIn(0.0, activeRoute.metadata.distanceMeters)
        val progress = RouteRideRuntime()
            .start(activeRoute, startPositionMeters + (distanceMiles.toDouble() * 1609.344))
        val preset = RouteResistancePreset.fromId(configurationRepository.routeResistancePreset.value)
        val grade = RouteGradeSmoother(preset.lookAheadMeters).gradePercent(activeRoute, progress.positionMeters)
        return RouteHudState(
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
            visualResistanceCue = if (IsBikePlus) {
                null
            } else {
                visualResistanceCue(
                    currentResistance = liveRideDashboardState.value.resistance,
                    gradePercent = grade
                )
            }
        )
    }

    private fun visualResistanceCue(
        currentResistance: Int,
        gradePercent: Double
    ): String {
        val preset = RouteResistancePreset.fromId(configurationRepository.routeResistancePreset.value)
        val target = GradeResistanceMapper(preset).targetResistance(
            baselineResistance = currentResistance,
            gradePercent = gradePercent,
            previousRequestedResistance = currentResistance
        )
        val delta = target - currentResistance
        return when {
            delta > 0 -> "+$delta to $target"
            delta < 0 -> "$delta to $target"
            else -> "Hold $target"
        }
    }

    override fun onCleared() {
        routeUploadServer.stop()
        (dashboardSensorInterface as? PelotonBikePlusSensorInterface)?.stop()
        (dashboardSensorInterface as? PelotonBikeSensorInterfaceV1New)?.stop()
        super.onCleared()
    }
}

package com.spop.poverlay.overlay

import android.app.Application
import android.content.Intent
import android.text.format.DateUtils
import android.util.Log
import android.os.SystemClock
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spop.poverlay.BuildConfig
import com.spop.poverlay.ConfigurationRepository
import com.spop.poverlay.MainActivity
import com.spop.poverlay.sensor.DeadSensorDetector
import com.spop.poverlay.sensor.interfaces.SensorInterface
import com.spop.poverlay.sensor.v2.BikePlusService
import com.spop.poverlay.route.ImportedRoute
import com.spop.poverlay.route.GradeResistanceMapper
import com.spop.poverlay.route.ManualResistanceGuidanceEngine
import com.spop.poverlay.route.ManualResistanceTolerance
import com.spop.poverlay.route.RouteHudState
import com.spop.poverlay.route.RouteResistancePreset
import com.spop.poverlay.route.RouteRideRuntime
import com.spop.poverlay.route.RouteStore
import com.spop.poverlay.route.RouteGradeSmoother
import com.spop.poverlay.util.IsBikePlus
import com.spop.poverlay.util.smoothSensorValue
import com.spop.poverlay.util.tickerFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val MphToKph = 1.60934
private const val MetersPerMile = 1609.344
private const val ResistanceLogTag = "GrupettoResistance"
private const val RouteResistanceLogTag = "SwitchbackRouteResistance"
private const val MIN_ROUTE_PROGRESS_SAVE_INTERVAL_MS = 5_000L
private const val MANUAL_ROUTE_OVERRIDE_SUSPEND_MS = 30_000L

class OverlaySensorViewModel(
    application: Application,
    private val sensorInterface: SensorInterface,
    private val deadSensorDetector: DeadSensorDetector,
    private val configurationRepository: ConfigurationRepository,
    private val bikePlusServiceProvider: (suspend () -> BikePlusService?)? = null,
    private val routeStore: RouteStore? = null,
    heartRateBpm: StateFlow<Int?>? = null
) : AndroidViewModel(application) {

    companion object {
        // The sensor does not necessarily return new value this quickly
        val GraphUpdatePeriod = 200.milliseconds

        // Max number of points before data starts to shift
        const val GraphMaxDataPoints = 300

    }


    //TODO: Move this logic to dialog view model
    private val mutableIsMinimized = MutableStateFlow(configurationRepository.hudCollapsed.value)
    val isMinimized = mutableIsMinimized.asStateFlow()

    private val mutableErrorMessage = MutableStateFlow<String?>(null)
    val errorMessage = mutableErrorMessage.asStateFlow()
    private val latestResistance = MutableStateFlow<Int?>(null)
    private val latestPower = MutableStateFlow(0f)
    private val latestCadence = MutableStateFlow(0f)
    private val latestSpeedMph = MutableStateFlow(0f)
    private val mutableRideElapsedSeconds = MutableStateFlow(0L)
    private val mutableRideDistanceMiles = MutableStateFlow(0f)
    private val mutableRideWorkKilojoules = MutableStateFlow(0f)
    private val mutableRouteHudState = MutableStateFlow<RouteHudState?>(null)
    private val heartRateFlow = heartRateBpm ?: MutableStateFlow(null)
    private var lastRequestedResistance: Int? = null
    private var bikePlusService: BikePlusService? = null
    private var activeRoute: ImportedRoute? = null
    private var routeStartedAtDistanceMiles = 0f
    private val routeRideRuntime = RouteRideRuntime()
    private var routeResistanceBaseline: Int? = null
    private var lastRouteResistanceRequest: Int? = null
    private var lastRouteResistanceWriteAtMs = 0L
    private var routeResistanceWriteInFlight = false
    private var routeAutomationSuspendedUntilMs = 0L
    private var latestRouteGradePercent = 0.0
    private var savedRouteStartPositionMeters = 0.0
    private var lastRouteProgressSaveAtMs = 0L
    private var lastRideActiveState: Boolean? = null
    private var lastNoActiveRouteLogAtMs = 0L
    private var lastRouteProgressLogAtMs = 0L
    private val routeGuidanceEngine = ManualResistanceGuidanceEngine()


    fun onDismissErrorPressed() {
        mutableErrorMessage.tryEmit(null)
    }

    fun onOverlayPressed() {
        val isCollapsed = !mutableIsMinimized.value
        mutableIsMinimized.value = isCollapsed
        configurationRepository.setHudCollapsed(isCollapsed)
    }

    fun onOverlayDoubleTap() {
        getApplication<Application>().apply {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun onDeadSensor() {
        mutableErrorMessage
            .tryEmit(
                "The sensors seem to have fallen asleep." +
                        " You may need to restart your Peloton by removing the" +
                        " power adapter momentarily to restore them."
            )
    }

    private var useMph = MutableStateFlow(true)

    val powerValue = sensorInterface.power
        .map { "%.0f".format(it) }
    val rpmValue = sensorInterface.cadence
        .map { "%.0f".format(it) }

    val resistanceValue = sensorInterface.resistance
        .onEach { latestResistance.value = it.roundToInt() }
        .map { "%.0f".format(it) }
    val resistanceControlEnabled = configurationRepository.bikePlusResistanceControlEnabled
        .map {
            it &&
                    BuildConfig.ENABLE_BIKE_PLUS_RESISTANCE_CONTROL &&
                    IsBikePlus &&
                    bikePlusServiceProvider != null
        }

    val speedValue = combine(
        sensorInterface.speed, useMph
    ) { speed, isMph ->
        val value = if (isMph) {
            speed
        } else {
            speed * MphToKph
        }
        "%.1f".format(value)
    }
    val speedLabel = useMph.map {
        if (it) {
            "mph"
        } else {
            "kph"
        }
    }
    val rideElapsedValue = mutableRideElapsedSeconds
        .map { DateUtils.formatElapsedTime(it) }
    val rideDistanceValue = mutableRideDistanceMiles
        .map { "%.2f".format(it) }
    val rideCaloriesValue = mutableRideWorkKilojoules
        .map { it.toInt().toString() }
    val heartRateValue = heartRateFlow
        .map { it?.toString() ?: "-" }
    val routeHudState = mutableRouteHudState.asStateFlow()
    val hudShowPower = configurationRepository.hudShowPower
    val hudShowSpeed = configurationRepository.hudShowSpeed
    val hudShowDistance = configurationRepository.hudShowDistance
    val hudShowTime = configurationRepository.hudShowTime
    val hudShowResistance = configurationRepository.hudShowResistance
    val hudShowHeartRate = configurationRepository.hudShowHeartRate
    val hudShowCalories = configurationRepository.hudShowCalories

    fun onClickedSpeed() {
        viewModelScope.launch {
            useMph.emit(!useMph.value)
        }
    }

    fun onResistanceStep(delta: Int) {
        Log.i(ResistanceLogTag, "Resistance step tapped: delta=$delta")
        viewModelScope.launch(Dispatchers.IO) {
            val currentResistance = latestResistance.value
            if (currentResistance == null) {
                Log.i(ResistanceLogTag, "Resistance step skipped: no telemetry")
                Timber.i("Bike+ resistance write skipped: no resistance telemetry yet")
                return@launch
            }

            val bikePlusService = bikePlusService
                ?: bikePlusServiceProvider?.invoke()?.also {
                    this@OverlaySensorViewModel.bikePlusService = it
                }

            if (bikePlusService == null) {
                Log.i(ResistanceLogTag, "Resistance step skipped: control unavailable")
                Timber.i("Bike+ resistance write skipped: control unavailable")
                return@launch
            }

            val requestedResistance = (lastRequestedResistance ?: currentResistance) + delta
            Log.i(
                ResistanceLogTag,
                "Resistance step sending: current=$currentResistance lastRequested=$lastRequestedResistance requested=$requestedResistance"
            )
            bikePlusService
                .setResistance(requestedResistance)
                .onSuccess {
                    Log.i(ResistanceLogTag, "Resistance step accepted: resistance=$it")
                    lastRequestedResistance = it
                    suspendRouteResistanceAutomation(it)
                }
                .onFailure {
                    Log.e(ResistanceLogTag, "Resistance step failed", it)
                    Timber.e(it, "Bike+ resistance step failed")
                }
        }
    }

    val powerGraph = mutableStateListOf<Float>()


    private fun setupPowerGraphData() {
        viewModelScope.launch(Dispatchers.IO) {
            //Sensor value is read every tick and added to graph
            combine(
                sensorInterface.power.smoothSensorValue(),
                tickerFlow(GraphUpdatePeriod)
            ) { sensorValue, _ -> sensorValue }.collect { value ->
                withContext(Dispatchers.Main) {
                    powerGraph.add(value)
                    if (powerGraph.size > GraphMaxDataPoints) {
                        powerGraph.removeFirst()
                    }
                }
            }
        }
    }

    private fun setupLiveHudRideMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            sensorInterface.power.collect { latestPower.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            sensorInterface.cadence.collect { latestCadence.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            sensorInterface.speed.collect { latestSpeedMph.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            tickerFlow(period = 1.seconds).collect {
                val power = latestPower.value
                val cadence = latestCadence.value
                val isRideActive = power > 0f || cadence > 0f
                if (lastRideActiveState != isRideActive) {
                    lastRideActiveState = isRideActive
                    Log.i(
                        RouteResistanceLogTag,
                        "Ride active changed: active=$isRideActive power=$power cadence=$cadence activeRoute=${activeRoute?.name}"
                    )
                }
                if (isRideActive) {
                    mutableRideElapsedSeconds.value += 1L
                    mutableRideDistanceMiles.value += latestSpeedMph.value / 3600f
                    mutableRideWorkKilojoules.value += power / 1000f
                    updateActiveRouteProgress(isRideActive = true)
                }
            }
        }
    }

    private fun setupRouteProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            configurationRepository.activeRouteId.collect { routeId ->
                Log.i(
                    RouteResistanceLogTag,
                    "Active route id observed: id=$routeId routeStoreAvailable=${routeStore != null}"
                )
                val route = routeId?.let { routeStore?.loadRoute(it) }
                activeRoute = route
                if (route == null) {
                    Log.i(RouteResistanceLogTag, "Active route cleared or missing: id=$routeId")
                    routeRideRuntime.reset()
                    mutableRouteHudState.value = null
                    routeResistanceBaseline = null
                    lastRouteResistanceRequest = null
                    lastRouteResistanceWriteAtMs = 0L
                    routeResistanceWriteInFlight = false
                    routeAutomationSuspendedUntilMs = 0L
                    latestRouteGradePercent = 0.0
                    savedRouteStartPositionMeters = 0.0
                    lastRouteProgressSaveAtMs = 0L
                    routeGuidanceEngine.reset()
                } else {
                    savedRouteStartPositionMeters = configurationRepository.activeRoutePositionMeters.value
                        .coerceIn(0.0, route.metadata.distanceMeters)
                    val savedRouteStartDistanceMiles = (savedRouteStartPositionMeters / MetersPerMile).toFloat()
                    if (savedRouteStartDistanceMiles > mutableRideDistanceMiles.value) {
                        mutableRideDistanceMiles.value = savedRouteStartDistanceMiles
                    }
                    routeStartedAtDistanceMiles = mutableRideDistanceMiles.value
                    routeRideRuntime.start(route, savedRouteStartPositionMeters)
                    routeResistanceBaseline = latestResistance.value
                    lastRouteResistanceRequest = routeResistanceBaseline
                    lastRouteResistanceWriteAtMs = 0L
                    routeAutomationSuspendedUntilMs = 0L
                    latestRouteGradePercent = 0.0
                    Log.i(
                        RouteResistanceLogTag,
                        "Active route loaded: id=${route.id} name=${route.name} savedPositionMeters=$savedRouteStartPositionMeters distanceMeters=${route.metadata.distanceMeters} baseline=$routeResistanceBaseline simulation=${configurationRepository.routeResistanceSimulationEnabled.value}"
                    )
                    updateActiveRouteProgress(isRideActive = false)
                }
            }
        }
    }

    private fun updateActiveRouteProgress(isRideActive: Boolean) {
        val route = activeRoute
        if (route == null) {
            val now = SystemClock.elapsedRealtime()
            if (lastNoActiveRouteLogAtMs == 0L || now - lastNoActiveRouteLogAtMs >= 10_000L) {
                lastNoActiveRouteLogAtMs = now
                Log.i(
                    RouteResistanceLogTag,
                    "Route progress skipped: no active route rideActive=$isRideActive"
                )
            }
            return
        }
        val routeDistanceMiles = (mutableRideDistanceMiles.value - routeStartedAtDistanceMiles).coerceAtLeast(0f)
        val progress = routeRideRuntime.updateDistanceMeters(
            savedRouteStartPositionMeters + (routeDistanceMiles.toDouble() * MetersPerMile)
        )
        if (progress == null) {
            Log.i(RouteResistanceLogTag, "Route progress skipped: runtime returned no progress route=${route.name}")
            return
        }
        val preset = RouteResistancePreset.fromId(configurationRepository.routeResistancePreset.value)
        val smoothedGrade = RouteGradeSmoother(preset.lookAheadMeters).gradePercent(route, progress.positionMeters)
        latestRouteGradePercent = smoothedGrade
        val now = SystemClock.elapsedRealtime()
        if (lastRouteProgressLogAtMs == 0L || now - lastRouteProgressLogAtMs >= 5_000L) {
            lastRouteProgressLogAtMs = now
            Log.i(
                RouteResistanceLogTag,
                "Route progress: route=${route.name} rideActive=$isRideActive positionMeters=${progress.positionMeters} rawGrade=${progress.gradePercent} smoothedGrade=$smoothedGrade simulation=${configurationRepository.routeResistanceSimulationEnabled.value}"
            )
        }
        val currentResistance = latestResistance.value
        val suppressGuidanceForBikePlusAuto =
            IsBikePlus &&
                    configurationRepository.bikePlusResistanceControlEnabled.value &&
                    configurationRepository.routeResistanceSimulationEnabled.value
        val guidanceState = if (currentResistance != null) {
            val baseline = routeResistanceBaseline ?: currentResistance
            val preset = RouteResistancePreset.fromId(
                configurationRepository.routeResistancePreset.value
            )
            routeGuidanceEngine.evaluate(
                currentResistance = currentResistance,
                guidanceBaseline = baseline,
                smoothedGradePercent = smoothedGrade,
                upcomingPoints = progress.upcomingPoints,
                currentPositionMeters = progress.positionMeters,
                speedMph = latestSpeedMph.value,
                toleranceMode = ManualResistanceTolerance.fromId(
                    configurationRepository.manualResistanceTolerance.value
                ),
                warningSeconds = configurationRepository.manualResistanceWarningSeconds.value,
                enabled = configurationRepository.manualResistanceGuidanceEnabled.value,
                isBikePlus = suppressGuidanceForBikePlusAuto,
                timestampMs = SystemClock.elapsedRealtime(),
                preset = preset
            )
        } else {
            null
        }
        mutableRouteHudState.value = RouteHudState(
            routeName = route.name,
            progressPercent = progress.progressPercent,
            positionMeters = progress.positionMeters,
            remainingMiles = progress.remainingMeters / MetersPerMile,
            gradePercent = smoothedGrade,
            rawGradePercent = progress.gradePercent,
            latitude = progress.latitude,
            longitude = progress.longitude,
            elevationMeters = progress.elevationMeters,
            points = route.points,
            isComplete = progress.isComplete,
            visualResistanceCue = null,
            guidanceState = guidanceState
        )
        maybeSaveRouteProgress(progress.positionMeters)
        maybeApplyRouteResistance(smoothedGrade, isRideActive)
    }

    private fun maybeSaveRouteProgress(positionMeters: Double) {
        val now = SystemClock.elapsedRealtime()
        if (lastRouteProgressSaveAtMs != 0L &&
            now - lastRouteProgressSaveAtMs < MIN_ROUTE_PROGRESS_SAVE_INTERVAL_MS
        ) {
            return
        }
        lastRouteProgressSaveAtMs = now
        configurationRepository.setActiveRoutePositionMeters(positionMeters)
    }

    private fun maybeApplyRouteResistance(
        gradePercent: Double,
        isRideActive: Boolean
    ) {
        if (!isRideActive) {
            Log.i(RouteResistanceLogTag, "Route resistance skipped: ride inactive")
            return
        }
        if (!configurationRepository.routeResistanceSimulationEnabled.value) {
            Log.i(RouteResistanceLogTag, "Route resistance skipped: simulation disabled")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now < routeAutomationSuspendedUntilMs) {
            Log.i(
                RouteResistanceLogTag,
                "Route resistance skipped: manual override suspended remainingMs=${routeAutomationSuspendedUntilMs - now}"
            )
            return
        }
        val currentResistance = latestResistance.value
        if (currentResistance == null) {
            Log.i(RouteResistanceLogTag, "Route resistance skipped: no resistance telemetry")
            return
        }
        val baseline = routeResistanceBaseline ?: currentResistance.also {
            routeResistanceBaseline = it
            lastRouteResistanceRequest = it
        }
        val preset = RouteResistancePreset.fromId(configurationRepository.routeResistancePreset.value)
        val targetResistance = GradeResistanceMapper(preset).targetResistance(
            baselineResistance = baseline,
            gradePercent = gradePercent,
            previousRequestedResistance = lastRouteResistanceRequest
        )
        if (targetResistance == lastRouteResistanceRequest || routeResistanceWriteInFlight) {
            Log.i(
                RouteResistanceLogTag,
                "Route resistance skipped: no target change or write in flight baseline=$baseline current=$currentResistance grade=$gradePercent lastRequested=$lastRouteResistanceRequest target=$targetResistance inFlight=$routeResistanceWriteInFlight"
            )
            return
        }
        if (lastRouteResistanceWriteAtMs != 0L &&
            now - lastRouteResistanceWriteAtMs < preset.minWriteIntervalMs
        ) {
            Log.i(
                RouteResistanceLogTag,
                "Route resistance skipped: rate limited elapsedMs=${now - lastRouteResistanceWriteAtMs} minMs=${preset.minWriteIntervalMs} target=$targetResistance"
            )
            return
        }

        routeResistanceWriteInFlight = true
        viewModelScope.launch(Dispatchers.IO) {
            val bikePlusService = bikePlusService
                ?: bikePlusServiceProvider?.invoke()?.also {
                    this@OverlaySensorViewModel.bikePlusService = it
                }
            if (bikePlusService == null) {
                Log.i(RouteResistanceLogTag, "Route resistance skipped: control unavailable")
                routeResistanceWriteInFlight = false
                return@launch
            }
            Log.i(
                RouteResistanceLogTag,
                "Route resistance target: preset=${preset.id} baseline=$baseline grade=$gradePercent target=$targetResistance"
            )
            bikePlusService
                .setResistance(targetResistance)
                .onSuccess {
                    lastRouteResistanceRequest = it
                    lastRouteResistanceWriteAtMs = SystemClock.elapsedRealtime()
                    Log.i(RouteResistanceLogTag, "Route resistance sent: resistance=$it")
                }
                .onFailure {
                    Log.e(RouteResistanceLogTag, "Route resistance write failed", it)
                }
            routeResistanceWriteInFlight = false
        }
    }

    private fun suspendRouteResistanceAutomation(manualResistance: Int) {
        if (activeRoute == null || !configurationRepository.routeResistanceSimulationEnabled.value) {
            return
        }
        val preset = RouteResistancePreset.fromId(configurationRepository.routeResistancePreset.value)
        val mapper = GradeResistanceMapper(preset)
        val now = SystemClock.elapsedRealtime()
        routeAutomationSuspendedUntilMs = now + MANUAL_ROUTE_OVERRIDE_SUSPEND_MS
        lastRouteResistanceRequest = manualResistance
        routeResistanceBaseline = mapper.baselineForTargetResistance(
            targetResistance = manualResistance,
            gradePercent = latestRouteGradePercent
        )
        lastRouteResistanceWriteAtMs = now
        Log.i(
            RouteResistanceLogTag,
            "Route resistance automation suspended: manual=$manualResistance baseline=$routeResistanceBaseline grade=$latestRouteGradePercent resumeInMs=$MANUAL_ROUTE_OVERRIDE_SUSPEND_MS"
        )
    }

    // Happens last to ensure initialization order is correct
    init {
        setupPowerGraphData()
        setupLiveHudRideMetrics()
        setupRouteProgress()
        viewModelScope.launch(Dispatchers.IO) {
            configurationRepository.hudCollapsed.collect {
                mutableIsMinimized.value = it
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            deadSensorDetector.deadSensorDetected.collect {
                onDeadSensor()
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            errorMessage.collect {
                // Leave minimized state if we're showing an error message
                if (it != null && mutableIsMinimized.value) {
                    mutableIsMinimized.value = false
                    configurationRepository.setHudCollapsed(false)
                }
            }
        }
    }
}


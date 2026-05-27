package com.spop.poverlay

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spop.poverlay.releases.Release
import com.spop.poverlay.ride.RideSessionRecord
import com.spop.poverlay.ride.RideSessionSummary
import com.spop.poverlay.ride.RideTotals
import com.spop.poverlay.ride.RideTotalsPeriod
import com.spop.poverlay.ride.availableRideTotalsPeriods
import com.spop.poverlay.ride.rideTotalsForPeriod
import com.spop.poverlay.route.ImportedRoute
import com.spop.poverlay.route.ManualResistanceGuidanceState
import com.spop.poverlay.route.ManualResistanceTolerance
import com.spop.poverlay.route.ResistanceDirection
import com.spop.poverlay.route.RouteProgress
import com.spop.poverlay.route.RouteUploadPortalState
import com.spop.poverlay.route.RouteUploadQrCode
import com.spop.poverlay.route.RouteResistancePreset
import com.spop.poverlay.route.RouteRideState
import com.spop.poverlay.ui.theme.ErrorColor
import com.spop.poverlay.ui.theme.LatoFontFamily
import com.spop.poverlay.util.IsBikePlus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ConfigurationPage(
    viewModel: ConfigurationViewModel
) {
    val showPermissionInfo by remember { viewModel.showPermissionInfo }
    val latestRelease by remember { viewModel.latestRelease }
    val rideSessionSummaries by remember { viewModel.rideSessionSummaries }
    val selectedRideSession by remember { viewModel.selectedRideSession }
    val importedRoutes by remember { viewModel.importedRoutes }
    val selectedRoute by remember { viewModel.selectedRoute }
    val routeRideState by remember { viewModel.routeRideState }
    val routeUploadPortalState by remember { viewModel.routeUploadPortalState }
    val liveRideDashboardState by remember { viewModel.liveRideDashboardState }
    val activeRouteId by viewModel.activeRouteId.collectAsStateWithLifecycle(initialValue = null)
    val activeRoutePositionMeters by viewModel.activeRoutePositionMeters.collectAsStateWithLifecycle(initialValue = 0.0)
    val overlayRunning by viewModel.overlayRunning.collectAsStateWithLifecycle(initialValue = false)

    if (showPermissionInfo) {
        PermissionPage(viewModel::onGrantPermissionClicked)
        return
    }

    val timerShownWhenMinimized by viewModel.showTimerWhenMinimized
        .collectAsStateWithLifecycle(initialValue = true)
    val bikePlusResistanceControlEnabled by viewModel.bikePlusResistanceControlEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val bikePlusResistanceControlOverlayVisible by viewModel.bikePlusResistanceControlOverlayVisible
        .collectAsStateWithLifecycle(initialValue = false)
    val routeResistanceSimulationEnabled by viewModel.routeResistanceSimulationEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val routeResistancePreset by viewModel.routeResistancePreset
        .collectAsStateWithLifecycle(initialValue = RouteResistancePreset.Default.id)
    val heartRateMonitorEnabled by viewModel.heartRateMonitorEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val rideSessionRecordingEnabled by viewModel.rideSessionRecordingEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val hudShowPower by viewModel.hudShowPower.collectAsStateWithLifecycle(initialValue = true)
    val hudShowSpeed by viewModel.hudShowSpeed.collectAsStateWithLifecycle(initialValue = true)
    val hudShowDistance by viewModel.hudShowDistance.collectAsStateWithLifecycle(initialValue = true)
    val hudShowTime by viewModel.hudShowTime.collectAsStateWithLifecycle(initialValue = true)
    val hudShowResistance by viewModel.hudShowResistance.collectAsStateWithLifecycle(initialValue = true)
    val hudShowHeartRate by viewModel.hudShowHeartRate.collectAsStateWithLifecycle(initialValue = true)
    val hudShowCalories by viewModel.hudShowCalories.collectAsStateWithLifecycle(initialValue = true)
    val manualResistanceGuidanceEnabled by viewModel.manualResistanceGuidanceEnabled
        .collectAsStateWithLifecycle(initialValue = true)
    val manualResistanceTolerance by viewModel.manualResistanceTolerance
        .collectAsStateWithLifecycle(initialValue = "normal")
    val manualResistanceWarningSeconds by viewModel.manualResistanceWarningSeconds
        .collectAsStateWithLifecycle(initialValue = 10)

    SwitchbackShell(
        timerShownWhenMinimized = timerShownWhenMinimized,
        bikePlusResistanceControlEnabled = bikePlusResistanceControlEnabled,
        bikePlusResistanceControlOverlayVisible = bikePlusResistanceControlOverlayVisible,
        routeResistanceSimulationEnabled = routeResistanceSimulationEnabled,
        routeResistancePreset = routeResistancePreset,
        heartRateMonitorEnabled = heartRateMonitorEnabled,
        rideSessionRecordingEnabled = rideSessionRecordingEnabled,
        hudShowPower = hudShowPower,
        hudShowSpeed = hudShowSpeed,
        hudShowDistance = hudShowDistance,
        hudShowTime = hudShowTime,
        hudShowResistance = hudShowResistance,
        hudShowHeartRate = hudShowHeartRate,
        hudShowCalories = hudShowCalories,
        rideSessionSummaries = rideSessionSummaries,
        selectedRideSession = selectedRideSession,
        liveRideDashboardState = liveRideDashboardState,
        importedRoutes = importedRoutes,
        selectedRoute = selectedRoute,
        routeRideState = routeRideState,
        routeUploadPortalState = routeUploadPortalState,
        activeRouteId = activeRouteId,
        activeRoutePositionMeters = activeRoutePositionMeters,
        overlayRunning = overlayRunning,
        onTimerShownWhenMinimizedToggled = viewModel::onShowTimerWhenMinimizedClicked,
        onBikePlusResistanceControlToggled = viewModel::onBikePlusResistanceControlClicked,
        onBikePlusResistanceControlOverlayVisibleToggled = viewModel::onBikePlusResistanceControlOverlayVisibleClicked,
        onRouteResistanceSimulationToggled = viewModel::onRouteResistanceSimulationEnabledClicked,
        onRouteResistancePresetSelected = viewModel::onRouteResistancePresetClicked,
        onHeartRateMonitorEnabledToggled = viewModel::onHeartRateMonitorEnabledClicked,
        onRideSessionRecordingEnabledToggled = viewModel::onRideSessionRecordingEnabledClicked,
        onHudShowPowerToggled = viewModel::onHudShowPowerClicked,
        onHudShowSpeedToggled = viewModel::onHudShowSpeedClicked,
        onHudShowDistanceToggled = viewModel::onHudShowDistanceClicked,
        onHudShowTimeToggled = viewModel::onHudShowTimeClicked,
        onHudShowResistanceToggled = viewModel::onHudShowResistanceClicked,
        onHudShowHeartRateToggled = viewModel::onHudShowHeartRateClicked,
        onHudShowCaloriesToggled = viewModel::onHudShowCaloriesClicked,
        onResetHud = viewModel::onResetHudClicked,
        manualResistanceGuidanceEnabled = manualResistanceGuidanceEnabled,
        manualResistanceTolerance = manualResistanceTolerance,
        manualResistanceWarningSeconds = manualResistanceWarningSeconds,
        onManualResistanceGuidanceEnabledToggled = viewModel::onManualResistanceGuidanceEnabledClicked,
        onManualResistanceToleranceSelected = viewModel::onManualResistanceToleranceSelected,
        onManualResistanceWarningSecondsSelected = viewModel::onManualResistanceWarningSecondsSelected,
        onClickedStartOverlay = viewModel::onStartServiceClicked,
        onClickedStopOverlay = viewModel::onStopServiceClicked,
        onClickedRestartApp = viewModel::onRestartClicked,
        onClickedRelease = viewModel::onClickedRelease,
        onRefreshRideSessions = viewModel::refreshRideSessions,
        onRideSessionClicked = viewModel::onRideSessionClicked,
        onBackFromRideSession = viewModel::onBackFromRideSession,
        onRefreshRoutes = viewModel::refreshRoutes,
        onImportGpx = viewModel::onImportGpxClicked,
        onAddRoute = viewModel::onAddRouteClicked,
        onCloseRouteUpload = viewModel::onCloseRouteUploadClicked,
        onRouteClicked = viewModel::onRouteClicked,
        onBackFromRoute = viewModel::onBackFromRoute,
        onStartRoute = viewModel::onStartRouteClicked,
        onRestartRoute = viewModel::onRestartRouteClicked,
        onResetRoute = viewModel::onResetRouteClicked,
        onDeleteRoute = viewModel::onDeleteRouteClicked,
        latestRelease = latestRelease
    )
}

@Composable
private fun SwitchbackShell(
    timerShownWhenMinimized: Boolean,
    bikePlusResistanceControlEnabled: Boolean,
    bikePlusResistanceControlOverlayVisible: Boolean,
    routeResistanceSimulationEnabled: Boolean,
    routeResistancePreset: String,
    heartRateMonitorEnabled: Boolean,
    rideSessionRecordingEnabled: Boolean,
    hudShowPower: Boolean,
    hudShowSpeed: Boolean,
    hudShowDistance: Boolean,
    hudShowTime: Boolean,
    hudShowResistance: Boolean,
    hudShowHeartRate: Boolean,
    hudShowCalories: Boolean,
    rideSessionSummaries: List<RideSessionSummary>,
    selectedRideSession: RideSessionRecord?,
    liveRideDashboardState: LiveRideDashboardState,
    importedRoutes: List<ImportedRoute>,
    selectedRoute: ImportedRoute?,
    routeRideState: RouteRideState,
    routeUploadPortalState: RouteUploadPortalState,
    activeRouteId: String?,
    activeRoutePositionMeters: Double,
    overlayRunning: Boolean,
    onTimerShownWhenMinimizedToggled: (Boolean) -> Unit,
    onBikePlusResistanceControlToggled: (Boolean) -> Unit,
    onBikePlusResistanceControlOverlayVisibleToggled: (Boolean) -> Unit,
    onRouteResistanceSimulationToggled: (Boolean) -> Unit,
    onRouteResistancePresetSelected: (String) -> Unit,
    onHeartRateMonitorEnabledToggled: (Boolean) -> Unit,
    onRideSessionRecordingEnabledToggled: (Boolean) -> Unit,
    onHudShowPowerToggled: (Boolean) -> Unit,
    onHudShowSpeedToggled: (Boolean) -> Unit,
    onHudShowDistanceToggled: (Boolean) -> Unit,
    onHudShowTimeToggled: (Boolean) -> Unit,
    onHudShowResistanceToggled: (Boolean) -> Unit,
    onHudShowHeartRateToggled: (Boolean) -> Unit,
    onHudShowCaloriesToggled: (Boolean) -> Unit,
    onResetHud: () -> Unit,
    manualResistanceGuidanceEnabled: Boolean,
    manualResistanceTolerance: String,
    manualResistanceWarningSeconds: Int,
    onManualResistanceGuidanceEnabledToggled: (Boolean) -> Unit,
    onManualResistanceToleranceSelected: (String) -> Unit,
    onManualResistanceWarningSecondsSelected: (Int) -> Unit,
    onClickedStartOverlay: () -> Unit,
    onClickedStopOverlay: () -> Unit,
    onClickedRestartApp: () -> Unit,
    onClickedRelease: (Release) -> Unit,
    onRefreshRideSessions: () -> Unit,
    onRideSessionClicked: (String) -> Unit,
    onBackFromRideSession: () -> Unit,
    onRefreshRoutes: () -> Unit,
    onImportGpx: () -> Unit,
    onAddRoute: () -> Unit,
    onCloseRouteUpload: () -> Unit,
    onRouteClicked: (String) -> Unit,
    onBackFromRoute: () -> Unit,
    onStartRoute: (ImportedRoute) -> Unit,
    onRestartRoute: (ImportedRoute) -> Unit,
    onResetRoute: () -> Unit,
    onDeleteRoute: (ImportedRoute) -> Unit,
    latestRelease: Release?
) {
    var activeTab by remember { mutableStateOf(AppTab.Ride) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B))
            .padding(24.dp)
    ) {
        TopBar(
            activeTab = activeTab,
            overlayRunning = overlayRunning,
            bikePlusEnabled = bikePlusResistanceControlEnabled,
            heartRateMonitorEnabled = heartRateMonitorEnabled
        )
        Spacer(modifier = Modifier.height(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                AppTab.Ride -> LiveDashboardPage(
                    state = liveRideDashboardState,
                    bikePlusControlsEnabled = bikePlusResistanceControlEnabled,
                    onAddRoute = onAddRoute
                )
                AppTab.Routes -> RoutesPage(
                    routes = importedRoutes,
                    selectedRoute = selectedRoute,
                    routeRideState = routeRideState,
                    routeUploadPortalState = routeUploadPortalState,
                    routeResistancePreset = routeResistancePreset,
                    activeRouteId = activeRouteId,
                    activeRoutePositionMeters = activeRoutePositionMeters,
                    onRefreshRoutes = onRefreshRoutes,
                    onImportGpx = onImportGpx,
                    onAddRoute = onAddRoute,
                    onCloseRouteUpload = onCloseRouteUpload,
                    onRouteResistancePresetSelected = onRouteResistancePresetSelected,
                    onRouteClicked = onRouteClicked,
                    onBackFromRoute = onBackFromRoute,
                    onStartRoute = onStartRoute,
                    onRestartRoute = onRestartRoute,
                    onResetRoute = onResetRoute,
                    onDeleteRoute = onDeleteRoute
                )
                AppTab.History -> RideHistoryPage(
                    rideSessionSummaries = rideSessionSummaries,
                    selectedRideSession = selectedRideSession,
                    onRefreshRideSessions = onRefreshRideSessions,
                    onRideSessionClicked = onRideSessionClicked,
                    onBackFromRideSession = onBackFromRideSession
                )
                AppTab.HUD -> ControlPage(
                    timerShownWhenMinimized = timerShownWhenMinimized,
                    bikePlusResistanceControlEnabled = bikePlusResistanceControlEnabled,
                    bikePlusResistanceControlOverlayVisible = bikePlusResistanceControlOverlayVisible,
                    routeResistanceSimulationEnabled = routeResistanceSimulationEnabled,
                    heartRateMonitorEnabled = heartRateMonitorEnabled,
                    rideSessionRecordingEnabled = rideSessionRecordingEnabled,
                    hudShowPower = hudShowPower,
                    hudShowSpeed = hudShowSpeed,
                    hudShowDistance = hudShowDistance,
                    hudShowTime = hudShowTime,
                    hudShowResistance = hudShowResistance,
                    hudShowHeartRate = hudShowHeartRate,
                    hudShowCalories = hudShowCalories,
                    onTimerShownWhenMinimizedToggled = onTimerShownWhenMinimizedToggled,
                    onBikePlusResistanceControlToggled = onBikePlusResistanceControlToggled,
                    onBikePlusResistanceControlOverlayVisibleToggled = onBikePlusResistanceControlOverlayVisibleToggled,
                    onRouteResistanceSimulationToggled = onRouteResistanceSimulationToggled,
                    onHeartRateMonitorEnabledToggled = onHeartRateMonitorEnabledToggled,
                    onRideSessionRecordingEnabledToggled = onRideSessionRecordingEnabledToggled,
                    onHudShowPowerToggled = onHudShowPowerToggled,
                    onHudShowSpeedToggled = onHudShowSpeedToggled,
                    onHudShowDistanceToggled = onHudShowDistanceToggled,
                    onHudShowTimeToggled = onHudShowTimeToggled,
                    onHudShowResistanceToggled = onHudShowResistanceToggled,
                    onHudShowHeartRateToggled = onHudShowHeartRateToggled,
                    onHudShowCaloriesToggled = onHudShowCaloriesToggled,
                    onResetHud = onResetHud,
                    onClickedStartOverlay = onClickedStartOverlay,
                    onClickedStopOverlay = onClickedStopOverlay,
                    overlayRunning = overlayRunning,
                    onClickedRestartApp = onClickedRestartApp,
                    onClickedRelease = onClickedRelease,
                    latestRelease = latestRelease
                )
                AppTab.Settings -> SettingsPage(
                    timerShownWhenMinimized = timerShownWhenMinimized,
                    bikePlusResistanceControlEnabled = bikePlusResistanceControlEnabled,
                    routeResistanceSimulationEnabled = routeResistanceSimulationEnabled,
                    heartRateMonitorEnabled = heartRateMonitorEnabled,
                    rideSessionRecordingEnabled = rideSessionRecordingEnabled,
                    onTimerShownWhenMinimizedToggled = onTimerShownWhenMinimizedToggled,
                    onBikePlusResistanceControlToggled = onBikePlusResistanceControlToggled,
                    onRouteResistanceSimulationToggled = onRouteResistanceSimulationToggled,
                    onHeartRateMonitorEnabledToggled = onHeartRateMonitorEnabledToggled,
                    onRideSessionRecordingEnabledToggled = onRideSessionRecordingEnabledToggled,
                    manualResistanceGuidanceEnabled = manualResistanceGuidanceEnabled,
                    manualResistanceTolerance = manualResistanceTolerance,
                    manualResistanceWarningSeconds = manualResistanceWarningSeconds,
                    onManualResistanceGuidanceEnabledToggled = onManualResistanceGuidanceEnabledToggled,
                    onManualResistanceToleranceSelected = onManualResistanceToleranceSelected,
                    onManualResistanceWarningSecondsSelected = onManualResistanceWarningSecondsSelected,
                    onClickedRestartApp = onClickedRestartApp,
                    onClickedRelease = onClickedRelease,
                    latestRelease = latestRelease
                )
            }
        }

        BottomNav(activeTab) { activeTab = it }
    }
}

private enum class AppTab(val label: String) {
    Ride("Ride"),
    HUD("HUD"),
    Routes("Routes"),
    History("History"),
    Settings("Settings")
}

@Composable
private fun TopBar(
    activeTab: AppTab,
    overlayRunning: Boolean,
    bikePlusEnabled: Boolean,
    heartRateMonitorEnabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Switchback",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF34D399)
                )
                Text(
                    text = activeTab.label,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip(if (bikePlusEnabled) "Bike+ Controls" else "Bike+ Off")
                StatusChip(if (heartRateMonitorEnabled) "HR Enabled" else "HR Off")
                StatusChip(if (overlayRunning) "Overlay On" else "Overlay Off")
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(
        color = Color(0xFF111827),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BottomNav(
    activeTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTab.values().forEach { tab ->
            val isActive = activeTab == tab
            Surface(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .height(48.dp)
                    .clickable { onTabSelected(tab) },
                color = if (isActive) Color(0xFF10B981) else Color(0xFF18181B),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = tab.label,
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 13.dp),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.Black else Color(0xFFE4E4E7)
                )
            }
        }
    }
}

@Composable
private fun HomePage(
    importedRoutes: List<ImportedRoute>,
    rideSessionSummaries: List<RideSessionSummary>,
    routeRideState: RouteRideState,
    activeRouteId: String?,
    activeRoutePositionMeters: Double,
    overlayRunning: Boolean,
    heartRateMonitorEnabled: Boolean,
    rideSessionRecordingEnabled: Boolean,
    onStartRoute: (ImportedRoute) -> Unit,
    onRestartRoute: (ImportedRoute) -> Unit,
    onBrowseRoutes: () -> Unit,
    onOpenHudDesigner: () -> Unit,
    onAddRoute: () -> Unit
) {
    val activeRoute = when (routeRideState) {
        is RouteRideState.Active -> routeRideState.route
        is RouteRideState.Completed -> routeRideState.route
        RouteRideState.Idle -> importedRoutes.firstOrNull { it.id == activeRouteId }
    }
    val activeProgress = when (routeRideState) {
        is RouteRideState.Active -> routeRideState.progress
        is RouteRideState.Completed -> routeRideState.progress
        else -> null
    }
    val heroRoute = activeRoute ?: importedRoutes.firstOrNull()
    val heroProgressMeters = activeProgress?.positionMeters
        ?: if (heroRoute?.id == activeRouteId) activeRoutePositionMeters else 0.0
    val heroDistanceMeters = heroRoute?.metadata?.distanceMeters ?: 0.0
    val heroProgressPercent = if (heroDistanceMeters > 0.0) {
        (heroProgressMeters / heroDistanceMeters * 100.0).coerceIn(0.0, 100.0)
    } else {
        0.0
    }
    val heroRemainingMeters = (heroDistanceMeters - heroProgressMeters).coerceAtLeast(0.0)
    val heroCanResume = heroRoute != null && heroProgressMeters > 0.0 && heroProgressPercent < 99.9
    val recentRoutes = importedRoutes
        .filter { it.id != heroRoute?.id }
        .take(3)
    val latestRide = rideSessionSummaries.maxByOrNull { it.startedAtMs }
    val weekStartMs = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
    val weeklyMiles = rideSessionSummaries
        .filter { it.startedAtMs >= weekStartMs }
        .sumOf { it.distanceMiles.toDouble() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1.45f),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            HomeRideHeroCard(
                route = heroRoute,
                progressPercent = heroProgressPercent,
                remainingMeters = heroRemainingMeters,
                canResume = heroCanResume,
                onResumeRide = { heroRoute?.let(onStartRoute) },
                onRestartRoute = { heroRoute?.let(onRestartRoute) },
                onChangeRoute = onBrowseRoutes,
                onAddRoute = onAddRoute
            )
            RecentRoutesSection(
                routes = recentRoutes,
                rideSessionSummaries = rideSessionSummaries,
                onRouteClicked = onStartRoute,
                onBrowseRoutes = onBrowseRoutes
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SystemReadyCard(
                bikeReady = IsBikePlus,
                heartRateReady = heartRateMonitorEnabled,
                overlayReady = overlayRunning,
                recordingReady = rideSessionRecordingEnabled
            )
            QuickActionsCard(
                onBrowseRoutes = onBrowseRoutes,
                onImportGpx = onAddRoute,
                onOpenHudDesigner = onOpenHudDesigner
            )
            HomeRideSummaryCard(
                latestRide = latestRide,
                weeklyMiles = weeklyMiles
            )
        }
    }
}

@Composable
private fun HomeRideHeroCard(
    route: ImportedRoute?,
    progressPercent: Double,
    remainingMeters: Double,
    canResume: Boolean,
    onResumeRide: () -> Unit,
    onRestartRoute: () -> Unit,
    onChangeRoute: () -> Unit,
    onAddRoute: () -> Unit
) {
    val metadata = route?.metadata
    val difficulty = metadata?.let {
        routeDifficultyLabel(it.maxGradePercent, it.averageClimbingGradePercent)
    } ?: "No route"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF101815),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(
                text = if (canResume) "Continue your journey" else "Ready to ride",
                color = Color(0xFF34D399),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = route?.name ?: "Choose a route to start",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                color = Color(0xFF0B0F0E),
                shape = MaterialTheme.shapes.large
            ) {
                if (route != null) {
                    RouteMapPreview(
                        route = route,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No active route",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Browse or import a GPX route to set up your next ride.",
                            color = Color(0xFFA1A1AA),
                            fontSize = 15.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStat("Progress", "${oneDecimal(progressPercent)}%", Modifier.weight(1f))
                SummaryStat("Remaining", formatMetersAsMiles(remainingMeters), Modifier.weight(1f))
                SummaryStat("Difficulty", difficulty, Modifier.weight(1f))
                SummaryStat("ETA", estimateRouteTime(remainingMeters), Modifier.weight(1f))
                SummaryStat("Climb", metadata?.let { formatMetersAsFeet(it.totalClimbMeters) } ?: "--", Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                onClick = if (route != null) onResumeRide else onAddRoute,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = when {
                        route == null -> "Add Route"
                        canResume -> "Resume Ride"
                        else -> "Start Ride"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = route != null,
                    onClick = onRestartRoute,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                ) {
                    Text("Restart Route")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onChangeRoute,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                ) {
                    Text("Change Route")
                }
            }
        }
    }
}

@Composable
private fun RecentRoutesSection(
    routes: List<ImportedRoute>,
    rideSessionSummaries: List<RideSessionSummary>,
    onRouteClicked: (ImportedRoute) -> Unit,
    onBrowseRoutes: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Routes",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onBrowseRoutes,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                ) {
                    Text("Browse Routes")
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (routes.isEmpty()) {
                Text(
                    text = "Saved routes will appear here once imported.",
                    color = Color(0xFFA1A1AA),
                    fontSize = 15.sp
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    routes.forEach { route ->
                        RecentRouteCard(
                            route = route,
                            lastRidden = lastRiddenLabel(route, rideSessionSummaries),
                            modifier = Modifier.weight(1f),
                            onClick = { onRouteClicked(route) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRouteCard(
    route: ImportedRoute,
    lastRidden: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val difficulty = routeDifficultyLabel(route.metadata.maxGradePercent, route.metadata.averageClimbingGradePercent)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Color(0xFF09090B),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = route.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$difficulty • ${formatMetersAsMiles(route.metadata.distanceMeters)}",
                color = Color(0xFF34D399),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            ElevationProfile(
                route = route,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = lastRidden,
                color = Color(0xFFA1A1AA),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SystemReadyCard(
    bikeReady: Boolean,
    heartRateReady: Boolean,
    overlayReady: Boolean,
    recordingReady: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = "System Ready",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReadinessPill("Bike Connected", bikeReady, Modifier.weight(1f))
                ReadinessPill("HR Enabled", heartRateReady, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReadinessPill("Overlay Ready", overlayReady, Modifier.weight(1f))
                ReadinessPill("Recording Enabled", recordingReady, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReadinessPill(
    label: String,
    isReady: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = if (isReady) Color(0xFF064E3B) else Color(0xFF3F1D1D),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = if (isReady) Color(0xFFA7F3D0) else Color(0xFFFCA5A5),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuickActionsCard(
    onBrowseRoutes: () -> Unit,
    onImportGpx: () -> Unit,
    onOpenHudDesigner: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Quick Actions",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            HomeActionButton(
                title = "Browse Routes",
                subtitle = "Open the route library and choose a ride.",
                onClick = onBrowseRoutes
            )
            HomeActionButton(
                title = "Import GPX",
                subtitle = "Add routes from Ride with GPS, Strava, Komoot, or Garmin Connect.",
                onClick = onImportGpx
            )
            HomeActionButton(
                title = "Overlay Designer",
                subtitle = "Customize HUD layouts, widgets, ride metrics, and stream overlays.",
                onClick = onOpenHudDesigner
            )
        }
    }
}

@Composable
private fun HomeActionButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color(0xFFA1A1AA),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun HomeRideSummaryCard(
    latestRide: RideSessionSummary?,
    weeklyMiles: Double
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = "Ride Summary",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStat(
                    "Last Distance",
                    latestRide?.let { "${oneDecimal(it.distanceMiles)} mi" } ?: "--",
                    Modifier.weight(1f)
                )
                SummaryStat(
                    "Last Duration",
                    latestRide?.let { formatDuration(it.durationMs) } ?: "--",
                    Modifier.weight(1f)
                )
                SummaryStat(
                    "Weekly Mileage",
                    "${oneDecimal(weeklyMiles)} mi",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(
    timerShownWhenMinimized: Boolean,
    bikePlusResistanceControlEnabled: Boolean,
    routeResistanceSimulationEnabled: Boolean,
    heartRateMonitorEnabled: Boolean,
    rideSessionRecordingEnabled: Boolean,
    onTimerShownWhenMinimizedToggled: (Boolean) -> Unit,
    onBikePlusResistanceControlToggled: (Boolean) -> Unit,
    onRouteResistanceSimulationToggled: (Boolean) -> Unit,
    onHeartRateMonitorEnabledToggled: (Boolean) -> Unit,
    onRideSessionRecordingEnabledToggled: (Boolean) -> Unit,
    manualResistanceGuidanceEnabled: Boolean,
    manualResistanceTolerance: String,
    manualResistanceWarningSeconds: Int,
    onManualResistanceGuidanceEnabledToggled: (Boolean) -> Unit,
    onManualResistanceToleranceSelected: (String) -> Unit,
    onManualResistanceWarningSecondsSelected: (Int) -> Unit,
    onClickedRestartApp: () -> Unit,
    onClickedRelease: (Release) -> Unit,
    latestRelease: Release?
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Devices ────────────────────────────────────────────────────────
        item { SettingsSectionTitle("Devices") }
        item {
            SettingsToggle(
                title = "HeartCast heart rate",
                subtitle = "Standard Bluetooth heart rate broadcast",
                checked = heartRateMonitorEnabled,
                onCheckedChange = onHeartRateMonitorEnabledToggled
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF18181B),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Bike+ controls",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Surface(
                        color = if (bikePlusResistanceControlEnabled) Color(0xFF064E3B) else Color(0xFF27272A),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (bikePlusResistanceControlEnabled) "Enabled" else "Off",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (bikePlusResistanceControlEnabled) Color(0xFF6EE7B7) else Color(0xFF71717A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ── Bike+ Controls ─────────────────────────────────────────────────
        item { SettingsSectionTitle("Bike+ Controls") }
        item {
            SettingsToggle(
                title = "Enable experimental Bike+ resistance controls",
                subtitle = "Experimental. Disabled by default.",
                checked = bikePlusResistanceControlEnabled,
                onCheckedChange = onBikePlusResistanceControlToggled
            )
        }
        if (bikePlusResistanceControlEnabled) {
            item {
                SettingsToggle(
                    title = "Simulate route grade with resistance",
                    subtitle = "Adjusts Bike+ resistance from active route grade. Choose preset from Routes.",
                    checked = routeResistanceSimulationEnabled,
                    onCheckedChange = onRouteResistanceSimulationToggled
                )
            }
        }

        // ── Manual Resistance Guidance ──────────────────────────────────────
        if (!IsBikePlus) {
            item { SettingsSectionTitle("Manual Resistance Guidance") }
            item {
                SettingsToggle(
                    title = "Enable resistance guidance",
                    subtitle = "Shows advisory targets on Ride page and HUD based on active route grade",
                    checked = manualResistanceGuidanceEnabled,
                    onCheckedChange = onManualResistanceGuidanceEnabledToggled
                )
            }
            if (manualResistanceGuidanceEnabled) {
                item {
                    GuidanceTolerancePicker(
                        selectedId = manualResistanceTolerance,
                        onSelected = onManualResistanceToleranceSelected
                    )
                }
                item {
                    GuidanceWarningSetting(
                        selectedSeconds = manualResistanceWarningSeconds,
                        onSelected = onManualResistanceWarningSecondsSelected
                    )
                }
            }
        }

        // ── Ride Recording ─────────────────────────────────────────────────
        item { SettingsSectionTitle("Ride Recording") }
        item {
            SettingsToggle(
                title = "Enable ride session recording",
                subtitle = "Saves telemetry samples to app-private storage",
                checked = rideSessionRecordingEnabled,
                onCheckedChange = onRideSessionRecordingEnabledToggled
            )
        }

        // ── App / System ───────────────────────────────────────────────────
        item { SettingsSectionTitle("App / System") }
        item {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                onClick = onClickedRestartApp,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)
            ) {
                Text(
                    text = "Restart Switchback",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF18181B),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Device info",
                        color = Color(0xFF71717A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = "Device: ${Build.DEVICE}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "SDK: ${Build.VERSION.RELEASE}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ── Safety / Disclaimer ────────────────────────────────────────────
        item { SettingsSectionTitle("Safety / Disclaimer") }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF18181B),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Not endorsed with, associated with, or supported by Peloton Interactive, Inc.",
                        color = Color(0xFFA1A1AA),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Experimental Bike+ resistance controls interact with physical hardware. Use at your own risk.",
                        color = Color(0xFFA1A1AA),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlPage(
    timerShownWhenMinimized: Boolean,
    bikePlusResistanceControlEnabled: Boolean,
    bikePlusResistanceControlOverlayVisible: Boolean,
    routeResistanceSimulationEnabled: Boolean,
    heartRateMonitorEnabled: Boolean,
    rideSessionRecordingEnabled: Boolean,
    hudShowPower: Boolean,
    hudShowSpeed: Boolean,
    hudShowDistance: Boolean,
    hudShowTime: Boolean,
    hudShowResistance: Boolean,
    hudShowHeartRate: Boolean,
    hudShowCalories: Boolean,
    onTimerShownWhenMinimizedToggled: (Boolean) -> Unit,
    onBikePlusResistanceControlToggled: (Boolean) -> Unit,
    onBikePlusResistanceControlOverlayVisibleToggled: (Boolean) -> Unit,
    onRouteResistanceSimulationToggled: (Boolean) -> Unit,
    onHeartRateMonitorEnabledToggled: (Boolean) -> Unit,
    onRideSessionRecordingEnabledToggled: (Boolean) -> Unit,
    onHudShowPowerToggled: (Boolean) -> Unit,
    onHudShowSpeedToggled: (Boolean) -> Unit,
    onHudShowDistanceToggled: (Boolean) -> Unit,
    onHudShowTimeToggled: (Boolean) -> Unit,
    onHudShowResistanceToggled: (Boolean) -> Unit,
    onHudShowHeartRateToggled: (Boolean) -> Unit,
    onHudShowCaloriesToggled: (Boolean) -> Unit,
    onResetHud: () -> Unit,
    onClickedStartOverlay: () -> Unit,
    onClickedStopOverlay: () -> Unit,
    overlayRunning: Boolean,
    onClickedRestartApp: () -> Unit,
    onClickedRelease: (Release) -> Unit,
    latestRelease: Release?
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Overlay status header ─────────────────────────────────────────
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF18181B),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Overlay Designer",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (overlayRunning) "Overlay active" else "Overlay stopped",
                                fontSize = 15.sp,
                                color = if (overlayRunning) Color(0xFF34D399) else Color(0xFF71717A)
                            )
                        }
                        Button(
                            modifier = Modifier.height(56.dp),
                            onClick = if (overlayRunning) onClickedStopOverlay else onClickedStartOverlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (overlayRunning) ErrorColor else Color(0xFF10B981)
                            )
                        ) {
                            Text(
                                text = if (overlayRunning) "Stop Overlay" else "Start Overlay",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (overlayRunning) Color.White else Color.Black
                            )
                        }
                    }
                }
            }
        }

        // ── HUD live preview ──────────────────────────────────────────────
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF18181B),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "HUD Preview",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF71717A),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF09090B),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        val activeFields: List<Pair<String, String>> = listOfNotNull(
                            ("Power" to "--- W").takeIf { hudShowPower },
                            ("Speed" to "-- mph").takeIf { hudShowSpeed },
                            ("Dist" to "-.-- mi").takeIf { hudShowDistance },
                            ("Time" to "--:--").takeIf { hudShowTime },
                            ("Resist" to "--").takeIf { hudShowResistance },
                            ("HR" to "--- bpm").takeIf { hudShowHeartRate },
                            ("Cal" to "---").takeIf { hudShowCalories }
                        )

                        if (activeFields.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No fields enabled",
                                    color = Color(0xFF52525B),
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                activeFields.chunked(4).forEach { rowFields ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowFields.forEach { (label, mockValue) ->
                                            Surface(
                                                modifier = Modifier.weight(1f),
                                                color = Color(0xFF18181B),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(10.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = mockValue,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = label,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF71717A)
                                                    )
                                                }
                                            }
                                        }
                                        repeat(4 - rowFields.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── HUD Modules ───────────────────────────────────────────────────
        item { SettingsSectionTitle("HUD Modules") }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF18181B),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HudModuleTile("Power", hudShowPower, onHudShowPowerToggled, Modifier.weight(1f))
                        HudModuleTile("Speed", hudShowSpeed, onHudShowSpeedToggled, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HudModuleTile("Distance", hudShowDistance, onHudShowDistanceToggled, Modifier.weight(1f))
                        HudModuleTile("Time", hudShowTime, onHudShowTimeToggled, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HudModuleTile("Resistance", hudShowResistance, onHudShowResistanceToggled, Modifier.weight(1f))
                        HudModuleTile("Heart Rate", hudShowHeartRate, onHudShowHeartRateToggled, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HudModuleTile("Calories", hudShowCalories, onHudShowCaloriesToggled, Modifier.weight(1f))
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ── Overlay Behavior ──────────────────────────────────────────────
        item { SettingsSectionTitle("Overlay Behavior") }
        item {
            SettingsToggle(
                title = "Show timer when minimized",
                checked = timerShownWhenMinimized,
                onCheckedChange = onTimerShownWhenMinimizedToggled
            )
        }
        if (bikePlusResistanceControlEnabled) {
            item {
                SettingsToggle(
                    title = "Show Bike+ resistance overlay",
                    checked = bikePlusResistanceControlOverlayVisible,
                    onCheckedChange = onBikePlusResistanceControlOverlayVisibleToggled
                )
            }
        }
        item {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                onClick = onResetHud,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
            ) {
                Text(
                    text = "Reset HUD",
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        }

        // ── Bike+ Controls (experimental, guarded, default-off) ───────────
        item { SettingsSectionTitle("Bike+ Controls") }
        item {
            SettingsToggle(
                title = "Enable experimental Bike+ resistance controls",
                subtitle = "Experimental. Disabled by default.",
                checked = bikePlusResistanceControlEnabled,
                onCheckedChange = onBikePlusResistanceControlToggled
            )
        }
        if (bikePlusResistanceControlEnabled) {
            item {
                SettingsToggle(
                    title = "Simulate route grade with resistance",
                    subtitle = "Experimental: changes Bike+ resistance from active route grade. Choose preset from the route page.",
                    checked = routeResistanceSimulationEnabled,
                    onCheckedChange = onRouteResistanceSimulationToggled
                )
            }
        }
    }
}

@Composable
private fun HudModuleTile(
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(64.dp)
            .clickable { onToggle(!enabled) },
        color = if (enabled) Color(0xFF064E3B) else Color(0xFF27272A),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color(0xFF6EE7B7) else Color(0xFFA1A1AA)
            )
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    subtitle: String,
    options: List<RouteResistancePreset>,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp
            )
            Text(
                text = subtitle,
                color = Color(0xFFA1A1AA),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { preset ->
                    val selected = preset.id == selectedId
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onSelected(preset.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Color(0xFF10B981) else Color(0xFF27272A),
                            contentColor = if (selected) Color.Black else Color.White
                        )
                    ) {
                        Text(
                            text = preset.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFF34D399),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = Color(0xFFA1A1AA),
                        fontSize = 13.sp
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun GuidanceTolerancePicker(
    selectedId: String,
    onSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = "Target Tolerance",
                color = Color.White,
                fontSize = 18.sp
            )
            Text(
                text = "How close your resistance must be to the route target",
                color = Color(0xFFA1A1AA),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ManualResistanceTolerance.values().forEach { tolerance ->
                    val isSelected = tolerance.id == selectedId
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelected(tolerance.id) },
                        color = if (isSelected) Color(0xFF064E3B) else Color(0xFF27272A),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = tolerance.label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            color = if (isSelected) Color(0xFF34D399) else Color(0xFFA1A1AA),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidanceWarningSetting(
    selectedSeconds: Int,
    onSelected: (Int) -> Unit
) {
    val options = listOf(0 to "Off", 5 to "5s", 10 to "10s", 15 to "15s")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = "Advance Warning",
                color = Color.White,
                fontSize = 18.sp
            )
            Text(
                text = "How many seconds ahead to warn about an upcoming grade change",
                color = Color(0xFFA1A1AA),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (seconds, label) ->
                    val isSelected = seconds == selectedSeconds
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelected(seconds) },
                        color = if (isSelected) Color(0xFF064E3B) else Color(0xFF27272A),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            color = if (isSelected) Color(0xFF34D399) else Color(0xFFA1A1AA),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseStatus(
    latestRelease: Release?,
    onClickedRelease: (Release) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (latestRelease == null) {
                Text(text = "Couldn't check for updates", color = Color(0xFFA1A1AA))
            } else {
                val formattedDate = DateUtils.getRelativeTimeSpanString(latestRelease.createdAt.time)
                val releaseText = if (latestRelease.isCurrentlyInstalled) {
                    buildAnnotatedString {
                        append("Switchback is up to date: ${latestRelease.tagName} - $formattedDate - ${latestRelease.friendlyName}")
                    }
                } else {
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append("New version released $formattedDate: ${latestRelease.friendlyName}.")
                        }
                    }
                }
                ClickableText(
                    text = releaseText,
                    style = LocalTextStyle.current.copy(
                        fontSize = 16.sp,
                        color = Color.White
                    )
                ) {
                    onClickedRelease(latestRelease)
                }
            }
        }
    }
}

@Composable
private fun RideHistoryPage(
    rideSessionSummaries: List<RideSessionSummary>,
    selectedRideSession: RideSessionRecord?,
    onRefreshRideSessions: () -> Unit,
    onRideSessionClicked: (String) -> Unit,
    onBackFromRideSession: () -> Unit
) {
    var selectedTotalsPeriod by remember { mutableStateOf(RideTotalsPeriod.AllTime) }

    if (selectedRideSession != null) {
        RideDetailPage(
            record = selectedRideSession,
            onBack = onBackFromRideSession
        )
        return
    }

    val availablePeriods = availableRideTotalsPeriods(rideSessionSummaries)
    val activeTotalsPeriod = if (availablePeriods.contains(selectedTotalsPeriod)) {
        selectedTotalsPeriod
    } else {
        availablePeriods.firstOrNull() ?: RideTotalsPeriod.AllTime
    }
    val activeTotals = rideTotalsForPeriod(rideSessionSummaries, activeTotalsPeriod)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Rides",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onRefreshRideSessions,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                ) {
                    Text("Refresh")
                }
            }
        }

        if (rideSessionSummaries.isEmpty()) {
            item {
                EmptyRideHistoryCard()
            }
        } else {
            item {
                LifetimeTotalsCard(
                    totals = activeTotals,
                    availablePeriods = availablePeriods,
                    selectedPeriod = activeTotalsPeriod,
                    onPeriodSelected = { selectedTotalsPeriod = it }
                )
            }

            items(rideSessionSummaries) { summary ->
                RideSummaryCard(
                    summary = summary,
                    onClick = { onRideSessionClicked(summary.id) }
                )
            }
        }
    }
}

@Composable
private fun LiveDashboardPage(
    state: LiveRideDashboardState,
    bikePlusControlsEnabled: Boolean,
    onAddRoute: () -> Unit
) {
    val routeHudState = state.routeHudState
    val completedMiles = routeHudState?.let { it.positionMeters / 1609.344 }
    val totalMiles = routeHudState?.let { (it.positionMeters / 1609.344) + it.remainingMiles }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier.weight(2f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    RideRouteHeroPanel(
                        routeHudState = routeHudState,
                        completedMiles = completedMiles,
                        totalMiles = totalMiles,
                        bikePlusControlsEnabled = bikePlusControlsEnabled,
                        onAddRoute = onAddRoute
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF18181B),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Live Stats",
                                        color = Color.White,
                                        fontSize = 21.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Real-time telemetry",
                                        color = Color(0xFF71717A),
                                        fontSize = 14.sp
                                    )
                                }
                                Surface(
                                    color = if (state.powerWatts > 0f || state.cadenceRpm > 0f) {
                                        Color(0xFF064E3B)
                                    } else {
                                        Color(0xFF27272A)
                                    },
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text(
                                        text = if (state.powerWatts > 0f || state.cadenceRpm > 0f) "LIVE" else "IDLE",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        color = if (state.powerWatts > 0f || state.cadenceRpm > 0f) Color(0xFFA7F3D0) else Color(0xFFA1A1AA),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RideStatTile("Power", state.powerWatts.roundToInt().toString(), "W", Modifier.weight(1f))
                                RideStatTile("Cadence", state.cadenceRpm.roundToInt().toString(), "RPM", Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RideStatTile("Heart Rate", state.heartRateBpm?.toString() ?: "--", "BPM", Modifier.weight(1f))
                                RideStatTile("Speed", oneDecimal(state.speedMph), "MPH", Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RideStatTile("Resistance", state.resistance.toString(), "%", Modifier.weight(1f))
                                RideStatTile("Calories", state.workKilojoules.toInt().toString(), "KCAL", Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RideStatTile("Distance", oneDecimal(state.distanceMiles), "MI", Modifier.weight(1f))
                                RideStatTile("Duration", DateUtils.formatElapsedTime(state.elapsedSeconds), "", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        // Guidance card — only shown when there's an active route and guidance state is present
        state.guidanceState?.let { guidance ->
            item {
                RideGuidanceCard(guidance)
            }
        }
    }
}

@Composable
private fun RideGuidanceCard(guidance: ManualResistanceGuidanceState) {
    val (bgColor, accentColor, title, body) = when (guidance) {
        is ManualResistanceGuidanceState.AdjustmentNeeded -> GuidanceCardContent(
            bgColor = Color(0xFF052E16),
            accentColor = Color(0xFF34D399),
            title = "Adjust Resistance",
            body = buildGuidanceAdjustText(
                guidance.currentResistance, guidance.targetCenter,
                guidance.targetMin, guidance.targetMax, guidance.delta, guidance.direction
            )
        )
        is ManualResistanceGuidanceState.Upcoming -> GuidanceCardContent(
            bgColor = Color(0xFF1C1400),
            accentColor = Color(0xFFFBBF24),
            title = "Upcoming Change  ·  ${guidance.etaSeconds}s",
            body = buildGuidanceAdjustText(
                guidance.currentResistance, guidance.targetCenter,
                guidance.targetMin, guidance.targetMax, guidance.delta, guidance.direction
            )
        )
        is ManualResistanceGuidanceState.InRange -> GuidanceCardContent(
            bgColor = Color(0xFF0A2018),
            accentColor = Color(0xFF6EE7B7),
            title = "On Target",
            body = "In range  ${guidance.targetMin}–${guidance.targetMax}  ·  Now ${guidance.currentResistance}"
        )
        is ManualResistanceGuidanceState.Stale -> GuidanceCardContent(
            bgColor = Color(0xFF18181B),
            accentColor = Color(0xFF71717A),
            title = "Guidance Stale",
            body = buildGuidanceAdjustText(
                guidance.currentResistance, guidance.targetCenter,
                guidance.targetMin, guidance.targetMax, guidance.delta, guidance.direction
            )
        )
        ManualResistanceGuidanceState.Neutral -> return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bgColor,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private data class GuidanceCardContent(
    val bgColor: Color,
    val accentColor: Color,
    val title: String,
    val body: String
)

private fun buildGuidanceAdjustText(
    current: Int,
    target: Int,
    min: Int,
    max: Int,
    delta: Int,
    direction: ResistanceDirection
): String {
    val arrow = when (direction) {
        ResistanceDirection.Up -> "▲"
        ResistanceDirection.Down -> "▼"
        ResistanceDirection.Hold -> "●"
    }
    val sign = if (delta >= 0) "+$delta" else "$delta"
    return "$arrow $sign  →  $target  (range $min–$max)  ·  Now $current"
}

@Composable
private fun RideRouteHeroPanel(
    routeHudState: com.spop.poverlay.route.RouteHudState?,
    completedMiles: Double?,
    totalMiles: Double?,
    bikePlusControlsEnabled: Boolean,
    onAddRoute: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current Route",
                        color = Color(0xFF71717A),
                        fontSize = 14.sp
                    )
                    Text(
                        text = routeHudState?.routeName ?: "No Active Route",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = if (routeHudState != null && completedMiles != null && totalMiles != null) {
                            "${oneDecimal(completedMiles)} / ${oneDecimal(totalMiles)} mi completed"
                        } else {
                            "Choose a route to unlock map, grade, and elevation"
                        },
                        color = Color(0xFFA1A1AA),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Surface(
                    color = Color(0xFF09090B),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "Current Grade",
                            color = Color(0xFF71717A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = routeHudState?.let { formatPercent(it.gradePercent) } ?: "--",
                            color = Color(0xFF34D399),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1.15f)
                        .height(285.dp),
                    color = Color(0xFF09090B),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (routeHudState != null && routeHudState.points.size >= 2) {
                            DashboardRouteMap(
                                routeHudState = routeHudState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DashboardStatCard(
                                    label = "Remaining",
                                    value = "${oneDecimal(routeHudState.remainingMiles)} mi",
                                    modifier = Modifier.weight(1f)
                                )
                                DashboardStatCard(
                                    label = "Progress",
                                    value = formatPercent(routeHudState.progressPercent),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No route loaded",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Select or import a GPX route to see the live course map.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Button(
                                    onClick = onAddRoute,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    modifier = Modifier.padding(top = 18.dp)
                                ) {
                                    Text("Select / Add Route", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(0.85f)
                        .height(285.dp),
                    color = Color(0xFF09090B),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Elevation",
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (bikePlusControlsEnabled) "Auto ready" else "Auto off",
                                    color = if (bikePlusControlsEnabled) Color(0xFFA7F3D0) else Color(0xFFA1A1AA),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (routeHudState != null && routeHudState.points.size >= 2) {
                            DashboardElevationProfile(
                                routeHudState = routeHudState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DashboardStatCard(
                                    label = "Elevation",
                                    value = formatElevation(routeHudState.elevationMeters),
                                    modifier = Modifier.weight(1f)
                                )
                                DashboardStatCard(
                                    label = "Grade",
                                    value = formatPercent(routeHudState.gradePercent),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Elevation appears when a route is active",
                                    color = Color(0xFFA1A1AA),
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RideStatTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF09090B),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                color = Color(0xFF71717A),
                fontSize = 11.sp
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = unit,
                        color = Color(0xFF71717A),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF111827),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = label, color = Color(0xFF94A3B8), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashboardStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF111827),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = label, color = Color(0xFF94A3B8), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashboardRouteMap(
    routeHudState: com.spop.poverlay.route.RouteHudState,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val routePoints = routeHudState.points
        if (routePoints.size < 2) {
            return@Canvas
        }
        val minLat = routePoints.minOf { it.latitude }
        val maxLat = routePoints.maxOf { it.latitude }
        val minLon = routePoints.minOf { it.longitude }
        val maxLon = routePoints.maxOf { it.longitude }
        val latRange = max(maxLat - minLat, 0.000001)
        val lonRange = max(maxLon - minLon, 0.000001)
        val padding = 16f
        val width = max(size.width - padding * 2f, 1f)
        val height = max(size.height - padding * 2f, 1f)

        fun pointOffset(latitude: Double, longitude: Double): Offset =
            Offset(
                x = padding + (((longitude - minLon) / lonRange).toFloat() * width),
                y = padding + height - (((latitude - minLat) / latRange).toFloat() * height)
            )

        val fullPath = Path()
        routePoints.forEachIndexed { index, point ->
            val offset = pointOffset(point.latitude, point.longitude)
            if (index == 0) fullPath.moveTo(offset.x, offset.y) else fullPath.lineTo(offset.x, offset.y)
        }

        val progressPath = Path()
        routePoints
            .filter { it.distanceFromStartMeters <= routeHudState.positionMeters }
            .forEachIndexed { index, point ->
                val offset = pointOffset(point.latitude, point.longitude)
                if (index == 0) progressPath.moveTo(offset.x, offset.y) else progressPath.lineTo(offset.x, offset.y)
            }
        val currentOffset = pointOffset(routeHudState.latitude, routeHudState.longitude)
        if (routeHudState.positionMeters > 0.0) {
            progressPath.lineTo(currentOffset.x, currentOffset.y)
        }

        drawPath(fullPath, Color.White.copy(alpha = 0.24f), style = Stroke(width = 7f))
        drawPath(progressPath, Color(0xFF34D399), style = Stroke(width = 7f))
        drawCircle(Color.White, radius = 9f, center = currentOffset)
    }
}

@Composable
private fun DashboardElevationProfile(
    routeHudState: com.spop.poverlay.route.RouteHudState,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val points = routeHudState.points.filter { it.elevationMeters != null }
        if (points.size < 2) {
            return@Canvas
        }
        val minElevation = points.minOf { it.elevationMeters ?: 0.0 }
        val maxElevation = points.maxOf { it.elevationMeters ?: 0.0 }
        val elevationRange = max(maxElevation - minElevation, 1.0)
        val totalDistance = routeHudState.points.lastOrNull()?.distanceFromStartMeters?.coerceAtLeast(1.0) ?: 1.0
        val padding = 16f
        val width = max(size.width - padding * 2f, 1f)
        val height = max(size.height - padding * 2f, 1f)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = padding + ((point.distanceFromStartMeters / totalDistance).toFloat() * width)
            val y = padding + height - ((((point.elevationMeters ?: minElevation) - minElevation) / elevationRange).toFloat() * height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val progressX = padding + ((routeHudState.positionMeters / totalDistance).coerceIn(0.0, 1.0).toFloat() * width)
        drawPath(path, Color(0xFF34D399), style = Stroke(width = 5f))
        drawLine(Color.White, Offset(progressX, padding), Offset(progressX, padding + height), strokeWidth = 3f)
    }
}

@Composable
private fun RouteMapPreview(
    route: ImportedRoute,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val points = route.points
        if (points.size < 2) return@Canvas
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latRange = max(maxLat - minLat, 0.000001)
        val lonRange = max(maxLon - minLon, 0.000001)
        val padding = 16f
        val w = max(size.width - padding * 2f, 1f)
        val h = max(size.height - padding * 2f, 1f)

        fun toOffset(lat: Double, lon: Double) = Offset(
            x = padding + (((lon - minLon) / lonRange).toFloat() * w),
            y = padding + h - (((lat - minLat) / latRange).toFloat() * h)
        )

        val path = Path()
        points.forEachIndexed { i, p ->
            val o = toOffset(p.latitude, p.longitude)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(path, Color(0xFF34D399), style = Stroke(width = 7f))
        points.firstOrNull()?.let {
            drawCircle(Color.White, radius = 10f, center = toOffset(it.latitude, it.longitude))
        }
        points.lastOrNull()?.let {
            drawCircle(Color(0xFF10B981), radius = 10f, center = toOffset(it.latitude, it.longitude))
        }
    }
}

@Composable
private fun RoutesPage(
    routes: List<ImportedRoute>,
    selectedRoute: ImportedRoute?,
    routeRideState: RouteRideState,
    routeUploadPortalState: RouteUploadPortalState,
    routeResistancePreset: String,
    activeRouteId: String?,
    activeRoutePositionMeters: Double,
    onRefreshRoutes: () -> Unit,
    onImportGpx: () -> Unit,
    onAddRoute: () -> Unit,
    onCloseRouteUpload: () -> Unit,
    onRouteResistancePresetSelected: (String) -> Unit,
    onRouteClicked: (String) -> Unit,
    onBackFromRoute: () -> Unit,
    onStartRoute: (ImportedRoute) -> Unit,
    onRestartRoute: (ImportedRoute) -> Unit,
    onResetRoute: () -> Unit,
    onDeleteRoute: (ImportedRoute) -> Unit
) {
    if (selectedRoute != null) {
        RouteDetailPage(
            route = selectedRoute,
            routeRideState = routeRideState,
            savedRoutePositionMeters = if (activeRouteId == selectedRoute.id) activeRoutePositionMeters else 0.0,
            routeResistancePreset = routeResistancePreset,
            onBack = onBackFromRoute,
            onStartRoute = { onStartRoute(selectedRoute) },
            onRestartRoute = { onRestartRoute(selectedRoute) },
            onResetRoute = onResetRoute,
            onRouteResistancePresetSelected = onRouteResistancePresetSelected,
            onDeleteRoute = { onDeleteRoute(selectedRoute) }
        )
        return
    }

    if (routeUploadPortalState.isRunning) {
        AddRoutePage(
            routeUploadPortalState = routeUploadPortalState,
            onBack = onCloseRouteUpload,
            onImportDropFolder = onImportGpx
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Saved Routes",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${routes.size} routes",
                        color = Color(0xFFA1A1AA),
                        fontSize = 14.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onRefreshRoutes,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                    ) {
                        Text("Refresh")
                    }
                    Button(
                        onClick = onAddRoute,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text(
                            text = "Add Route",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            ActiveRouteCard(
                routeRideState = routeRideState,
                onResumeRoute = onStartRoute,
                onRestartRoute = onRestartRoute,
                onResetRoute = onResetRoute
            )
        }

        if (routes.isEmpty()) {
            item {
                EmptyRoutesCard()
            }
        } else {
            items(routes) { route ->
                RouteSummaryCard(
                    route = route,
                    selectedPresetId = routeResistancePreset,
                    onClick = { onRouteClicked(route.id) }
                )
            }
        }
    }
}

@Composable
private fun AddRoutePage(
    routeUploadPortalState: RouteUploadPortalState,
    onBack: () -> Unit,
    onImportDropFolder: () -> Unit
) {
    val url = routeUploadPortalState.url
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Add Route",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Upload a GPX from your phone or computer",
                    color = Color(0xFFA1A1AA),
                    fontSize = 14.sp
                )
            }
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
            ) {
                Text("Back")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier
                    .weight(1.05f)
                    .height(430.dp),
                color = Color(0xFF18181B),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (url == null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = routeUploadPortalState.message ?: "Starting upload portal...",
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        }
                    } else {
                        Surface(
                            color = Color.White,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Image(
                                bitmap = RouteUploadQrCode.bitmap(url).asImageBitmap(),
                                contentDescription = "Route upload QR code",
                                modifier = Modifier
                                    .padding(12.dp)
                                    .requiredSize(250.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Scan this QR code from a device on the same Wi-Fi.",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = url,
                            color = Color(0xFF34D399),
                            fontSize = 14.sp
                        )
                        routeUploadPortalState.message?.let {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = it,
                                color = Color(0xFFA1A1AA),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .height(430.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = Color(0xFF18181B),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Finding GPX routes",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Trusted sources include Ride with GPS, Strava exports, Komoot, and Garmin Connect. Export the route as GPX, then upload it here.",
                            color = Color(0xFFA1A1AA),
                            fontSize = 14.sp
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = Color(0xFF18181B),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Fallback",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "If the phone cannot reach the bike, push GPX files to the route_imports folder and import them here.",
                            color = Color(0xFFA1A1AA),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onImportDropFolder,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                        ) {
                            Text("Import Drop Folder")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRoutesCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "No saved routes yet",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tap Add Route to scan a QR code and upload GPX files from your phone or computer.",
                color = Color(0xFFA1A1AA),
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun RouteSummaryCard(
    route: ImportedRoute,
    selectedPresetId: String,
    onClick: () -> Unit
) {
    val metadata = route.metadata
    val suggestedPreset = suggestedRoutePreset(metadata.maxGradePercent)
    val selectedPreset = RouteResistancePreset.fromId(selectedPresetId)
    val difficulty = routeDifficultyLabel(metadata.maxGradePercent, metadata.averageClimbingGradePercent)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = route.name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = route.id,
                        color = Color(0xFF71717A),
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "${route.points.size} track pts",
                    color = Color(0xFF34D399),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStat("Distance", formatMetersAsMiles(metadata.distanceMeters), Modifier.weight(1f))
                SummaryStat("Climb", formatMetersAsFeet(metadata.totalClimbMeters), Modifier.weight(1f))
                SummaryStat("Max Sust.", formatPercent(metadata.maxGradePercent), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStat("Difficulty", difficulty, Modifier.weight(1f))
                SummaryStat("Suggested", suggestedPreset.label, Modifier.weight(1f))
                SummaryStat("Selected", selectedPreset.label, Modifier.weight(1f))
            }
            if (isExtremeRouteGrade(metadata.maxGradePercent)) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Extreme grade detected. Bike-safe resistance caps should be used.",
                    color = Color(0xFFFBBF24),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ActiveRouteCard(
    routeRideState: RouteRideState,
    onResumeRoute: (ImportedRoute) -> Unit,
    onRestartRoute: (ImportedRoute) -> Unit,
    onResetRoute: () -> Unit
) {
    when (routeRideState) {
        RouteRideState.Idle -> return
        is RouteRideState.Active -> ActiveRouteContent(
            title = "Active Route",
            route = routeRideState.route,
            progress = routeRideState.progress,
            onResumeRoute = { onResumeRoute(routeRideState.route) },
            onRestartRoute = { onRestartRoute(routeRideState.route) },
            onResetRoute = onResetRoute
        )
        is RouteRideState.Completed -> ActiveRouteContent(
            title = "Completed Route",
            route = routeRideState.route,
            progress = routeRideState.progress,
            onResumeRoute = null,
            onRestartRoute = { onRestartRoute(routeRideState.route) },
            onResetRoute = onResetRoute
        )
    }
}

@Composable
private fun ActiveRouteContent(
    title: String,
    route: ImportedRoute,
    progress: RouteProgress,
    onResumeRoute: (() -> Unit)?,
    onRestartRoute: (() -> Unit)?,
    onResetRoute: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF064E3B),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color(0xFFA7F3D0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = route.name,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onResumeRoute != null) {
                        Button(
                            onClick = onResumeRoute,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Resume", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (onRestartRoute != null) {
                        Button(
                            onClick = onRestartRoute,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Restart")
                        }
                    }
                    Button(
                        onClick = onResetRoute,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                    ) {
                        Text("Clear")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStat("Progress", formatPercent(progress.progressPercent), Modifier.weight(1f))
                SummaryStat("Remaining", formatMetersAsMiles(progress.remainingMeters), Modifier.weight(1f))
                SummaryStat("Grade", formatPercent(progress.gradePercent), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RouteDetailPage(
    route: ImportedRoute,
    routeRideState: RouteRideState,
    savedRoutePositionMeters: Double,
    routeResistancePreset: String,
    onBack: () -> Unit,
    onStartRoute: () -> Unit,
    onRestartRoute: () -> Unit,
    onResetRoute: () -> Unit,
    onRouteResistancePresetSelected: (String) -> Unit,
    onDeleteRoute: () -> Unit
) {
    val metadata = route.metadata
    val suggestedPreset = suggestedRoutePreset(metadata.maxGradePercent)
    val difficulty = routeDifficultyLabel(metadata.maxGradePercent, metadata.averageClimbingGradePercent)
    val activeProgress = when (routeRideState) {
        is RouteRideState.Active -> routeRideState.progress.takeIf { routeRideState.route.id == route.id }
        is RouteRideState.Completed -> routeRideState.progress.takeIf { routeRideState.route.id == route.id }
        RouteRideState.Idle -> null
    }
    val canResume = savedRoutePositionMeters > 0.0 && activeProgress == null
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                ) {
                    Text("Back")
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = route.name,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = route.id,
                        color = Color(0xFF71717A),
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = onDeleteRoute,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D))
                ) {
                    Text("Delete")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.height(56.dp),
                    onClick = onStartRoute,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = if (canResume) "Resume Route" else "Start Route",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (savedRoutePositionMeters > 0.0 || activeProgress != null) {
                    Button(
                        modifier = Modifier.height(56.dp),
                        onClick = onRestartRoute,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Restart", fontSize = 18.sp)
                    }
                }
            }
        }

        if (activeProgress != null) {
            item {
                ActiveRouteContent(
                    title = if (activeProgress.isComplete) "Completed Route" else "Active Route",
                    route = route,
                    progress = activeProgress,
                    onResumeRoute = null,
                    onRestartRoute = null,
                    onResetRoute = onResetRoute
                )
            }
        }

        if (route.points.size >= 2) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF18181B),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Route Preview",
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        RouteMapPreview(
                            route = route,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        ElevationProfile(
                            route = route,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryStat("Start Elev", formatElevation(route.points.firstOrNull()?.elevationMeters), Modifier.weight(1f))
                            SummaryStat("End Elev", formatElevation(route.points.lastOrNull()?.elevationMeters), Modifier.weight(1f))
                            SummaryStat("Range", formatElevationRange(route), Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF18181B),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Route Metrics",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryStat("Distance", formatMetersAsMiles(metadata.distanceMeters), Modifier.weight(1f))
                        SummaryStat("Climb", formatMetersAsFeet(metadata.totalClimbMeters), Modifier.weight(1f))
                        SummaryStat("Track Pts", route.points.size.toString(), Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryStat("Max Sust.", formatPercent(metadata.maxGradePercent), Modifier.weight(1f))
                        SummaryStat("Avg Climb", formatPercent(metadata.averageClimbingGradePercent), Modifier.weight(1f))
                        SummaryStat("Distance km", formatMetersAsKilometers(metadata.distanceMeters), Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryStat("Difficulty", difficulty, Modifier.weight(1f))
                        SummaryStat("Suggested", suggestedPreset.label, Modifier.weight(1f))
                        SummaryStat("Selected", RouteResistancePreset.fromId(routeResistancePreset).label, Modifier.weight(1f))
                    }
                    if (isExtremeRouteGrade(metadata.maxGradePercent)) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Extreme grade detected. This may be a hiking/trail route. Bike-safe grade caps should be used for resistance simulation.",
                            color = Color(0xFFFBBF24),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            SettingsChoiceRow(
                title = "Route resistance preset",
                subtitle = "Choose how aggressively this route should control Bike+ resistance.",
                options = RouteResistancePreset.values().toList(),
                selectedId = routeResistancePreset,
                onSelected = onRouteResistancePresetSelected
            )
        }
    }
}

@Composable
private fun ElevationProfile(
    route: ImportedRoute,
    modifier: Modifier = Modifier
) {
    val points = route.points.filter { it.elevationMeters != null }
    Canvas(modifier = modifier) {
        if (points.size < 2) {
            return@Canvas
        }

        val minElevation = points.minOf { it.elevationMeters ?: 0.0 }
        val maxElevation = points.maxOf { it.elevationMeters ?: 0.0 }
        val elevationRange = max(maxElevation - minElevation, 1.0)
        val routeDistance = max(route.metadata.distanceMeters, 1.0)
        val horizontalPadding = 8f
        val verticalPadding = 12f
        val chartWidth = max(size.width - horizontalPadding * 2, 1f)
        val chartHeight = max(size.height - verticalPadding * 2, 1f)

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = horizontalPadding + ((point.distanceFromStartMeters / routeDistance) * chartWidth).toFloat()
            val y = verticalPadding + ((maxElevation - (point.elevationMeters ?: minElevation)) / elevationRange * chartHeight).toFloat()
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawLine(
            color = Color(0xFF3F3F46),
            start = Offset(horizontalPadding, size.height - verticalPadding),
            end = Offset(size.width - horizontalPadding, size.height - verticalPadding),
            strokeWidth = 2f
        )
        drawPath(
            path = path,
            color = Color(0xFF34D399),
            style = Stroke(width = 5f)
        )
    }
}

@Composable
private fun LifetimeTotalsCard(
    totals: RideTotals,
    availablePeriods: List<RideTotalsPeriod>,
    selectedPeriod: RideTotalsPeriod,
    onPeriodSelected: (RideTotalsPeriod) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Totals",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = selectedPeriod.label,
                    color = Color(0xFF34D399),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availablePeriods.forEach { period ->
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onPeriodSelected(period) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (period == selectedPeriod) Color(0xFF10B981) else Color(0xFF27272A),
                            contentColor = if (period == selectedPeriod) Color.Black else Color.White
                        )
                    ) {
                        Text(
                            text = period.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStat("Rides", "${totals.rideCount}", Modifier.weight(1f))
                SummaryStat("Distance", "${oneDecimal(totals.totalDistanceMiles)} mi", Modifier.weight(1f))
                SummaryStat("Time", formatDuration(totals.totalDurationMs), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStat("Calories", "${totals.totalCalories}", Modifier.weight(1f))
                SummaryStat("Work", "${totals.totalWorkKilojoules.roundToInt()} kJ", Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStat("Avg Power", "${totals.averagePowerWatts.roundToInt()} W", Modifier.weight(1f))
                SummaryStat("Avg Cadence", "${totals.averageCadenceRpm.roundToInt()} rpm", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmptyRideHistoryCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "No saved rides yet",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Enable ride session recording, start the overlay, and pedal until the first 30 samples are saved.",
                color = Color(0xFFA1A1AA),
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun RideSummaryCard(
    summary: RideSessionSummary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color(0xFF18181B),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.name ?: "Ride ${summary.id.take(8)}",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatRideDate(summary.startedAtMs),
                        color = Color(0xFF71717A),
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "${summary.sampleCount} samples",
                    color = Color(0xFF34D399),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStat("Distance", "${oneDecimal(summary.distanceMiles)} mi", Modifier.weight(1f))
                SummaryStat("Duration", formatDuration(summary.durationMs), Modifier.weight(1f))
                SummaryStat("Avg Power", "${summary.averagePowerWatts.roundToInt()} W", Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStat("Max Power", "${summary.maxPowerWatts.roundToInt()} W", Modifier.weight(1f))
                SummaryStat("Avg Cadence", "${summary.averageCadenceRpm.roundToInt()} rpm", Modifier.weight(1f))
                SummaryStat("Calories", "${summary.estimatedCalories}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RideDetailPage(
    record: RideSessionRecord,
    onBack: () -> Unit
) {
    val summary = record.summary
    val samples = record.samples
    val firstSample = samples.firstOrNull()
    val lastSample = samples.lastOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                ) {
                    Text("Back")
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = record.name ?: "Ride ${record.id.take(8)}",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatRideDate(record.startedAtMs),
                        color = Color(0xFF71717A),
                        fontSize = 13.sp
                    )
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF18181B),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Session Summary",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryStat("Distance", "${oneDecimal(summary.distanceMiles)} mi", Modifier.weight(1f))
                        SummaryStat("Duration", formatDuration(summary.durationMs), Modifier.weight(1f))
                        SummaryStat("Samples", summary.sampleCount.toString(), Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryStat("Avg Power", "${summary.averagePowerWatts.roundToInt()} W", Modifier.weight(1f))
                        SummaryStat("Max Power", "${summary.maxPowerWatts.roundToInt()} W", Modifier.weight(1f))
                        SummaryStat("Avg Cadence", "${summary.averageCadenceRpm.roundToInt()} rpm", Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryStat("Calories", "${summary.estimatedCalories}", Modifier.weight(1f))
                        SummaryStat("Work", "${summary.totalWorkKilojoules.roundToInt()} kJ", Modifier.weight(1f))
                        SummaryStat("Status", if (summary.completedAtMs == null) "Saved" else "Done", Modifier.weight(1f))
                    }
                    if (summary.averageHeartRateBpm != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryStat("Avg HR", formatHeartRate(summary.averageHeartRateBpm), Modifier.weight(1f))
                            SummaryStat("Max HR", formatHeartRate(summary.maxHeartRateBpm), Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            val hrValues = samples.mapNotNull { it.heartRateBpm }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF18181B),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Ride Range",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryStat(
                            "Power",
                            "${samples.minOfOrNull { it.powerWatts }?.roundToInt() ?: 0}-${samples.maxOfOrNull { it.powerWatts }?.roundToInt() ?: 0} W",
                            Modifier.weight(1f)
                        )
                        SummaryStat(
                            "Cadence",
                            "${samples.minOfOrNull { it.cadenceRpm }?.roundToInt() ?: 0}-${samples.maxOfOrNull { it.cadenceRpm }?.roundToInt() ?: 0} rpm",
                            Modifier.weight(1f)
                        )
                        SummaryStat(
                            "Resistance",
                            "${samples.minOfOrNull { it.resistance } ?: 0}-${samples.maxOfOrNull { it.resistance } ?: 0}",
                            Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryStat("Start Res", "${firstSample?.resistance ?: 0}", Modifier.weight(1f))
                        SummaryStat("End Res", "${lastSample?.resistance ?: 0}", Modifier.weight(1f))
                        SummaryStat("Top Speed", "${oneDecimal(samples.maxOfOrNull { it.speedMph } ?: 0f)} mph", Modifier.weight(1f))
                    }
                    if (hrValues.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryStat(
                                "HR Range",
                                formatHeartRateRange(hrValues),
                                Modifier.weight(1f)
                            )
                            SummaryStat("Start HR", formatHeartRate(firstSample?.heartRateBpm), Modifier.weight(1f))
                            SummaryStat("End HR", formatHeartRate(lastSample?.heartRateBpm), Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF09090B),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, color = Color(0xFF71717A), fontSize = 12.sp)
            Text(text = value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PermissionPage(onClickedGrantPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B))
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Switchback Needs Permission To Draw Over Other Apps",
            color = Color.White,
            fontSize = 40.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "It uses this permission to draw an overlay with your bike's sensor data",
            color = Color(0xFFA1A1AA),
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onClickedGrantPermission) {
            Text(text = "Grant Permission")
        }
    }
}

private fun formatRideDate(timestampMs: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(timestampMs))

private fun lastRiddenLabel(
    route: ImportedRoute,
    rideSessionSummaries: List<RideSessionSummary>
): String {
    val lastRide = rideSessionSummaries
        .filter { it.name == route.name }
        .maxByOrNull { it.startedAtMs }
    return lastRide?.let { "Last ridden ${formatRideDate(it.startedAtMs)}" } ?: "Not ridden yet"
}

private fun estimateRouteTime(remainingMeters: Double): String {
    if (remainingMeters <= 0.0) {
        return "--"
    }
    val assumedMetersPerHour = 12.0 * 1609.344
    val durationMs = (remainingMeters / assumedMetersPerHour * 60.0 * 60.0 * 1000.0).roundToInt().toLong()
    return formatDuration(durationMs)
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    return if (hours > 0L) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

private fun oneDecimal(value: Float): String =
    String.format(Locale.US, "%.1f", value)

private fun oneDecimal(value: Double): String =
    String.format(Locale.US, "%.1f", value)

private fun formatMetersAsMiles(meters: Double): String =
    "${oneDecimal(meters / 1609.344)} mi"

private fun formatMetersAsKilometers(meters: Double): String =
    "${oneDecimal(meters / 1000.0)} km"

private fun formatMetersAsFeet(meters: Double): String =
    "${(meters * 3.28084).roundToInt()} ft"

private fun formatElevation(elevationMeters: Double?): String =
    elevationMeters?.let { formatMetersAsFeet(it) } ?: "--"

private fun formatElevationRange(route: ImportedRoute): String {
    val elevations = route.points.mapNotNull { it.elevationMeters }
    if (elevations.isEmpty()) {
        return "--"
    }
    val minElevation = elevations.fold(Double.POSITIVE_INFINITY) { current, value -> min(current, value) }
    val maxElevation = elevations.fold(Double.NEGATIVE_INFINITY) { current, value -> max(current, value) }
    return "${formatMetersAsFeet(minElevation)}-${formatMetersAsFeet(maxElevation)}"
}

private fun formatPercent(value: Double): String =
    "${oneDecimal(value)}%"

private fun suggestedRoutePreset(maxGradePercent: Double): RouteResistancePreset =
    when {
        maxGradePercent < 8.0 -> RouteResistancePreset.Gentle
        maxGradePercent < 16.0 -> RouteResistancePreset.Standard
        else -> RouteResistancePreset.Strong
    }

private fun routeDifficultyLabel(
    maxGradePercent: Double,
    averageClimbingGradePercent: Double
): String =
    when {
        maxGradePercent >= 25.0 -> "Extreme"
        maxGradePercent >= 16.0 || averageClimbingGradePercent >= 7.0 -> "Hard"
        maxGradePercent >= 8.0 || averageClimbingGradePercent >= 4.0 -> "Moderate"
        else -> "Easy"
    }

private fun isExtremeRouteGrade(maxGradePercent: Double): Boolean =
    maxGradePercent >= 25.0

private fun formatHeartRate(heartRateBpm: Int?): String =
    heartRateBpm?.let { "$it bpm" } ?: "--"

private fun formatHeartRateRange(values: List<Int>): String =
    if (values.isEmpty()) {
        "--"
    } else {
        "${values.minOrNull()}-${values.maxOrNull()} bpm"
    }

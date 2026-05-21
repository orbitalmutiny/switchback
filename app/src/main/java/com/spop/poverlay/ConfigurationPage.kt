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
import com.spop.poverlay.route.RouteProgress
import com.spop.poverlay.route.RouteUploadPortalState
import com.spop.poverlay.route.RouteUploadQrCode
import com.spop.poverlay.route.RouteResistancePreset
import com.spop.poverlay.route.RouteRideState
import com.spop.poverlay.ui.theme.ErrorColor
import com.spop.poverlay.ui.theme.LatoFontFamily
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
    var activeTab by remember { mutableStateOf(AppTab.Home) }

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
                AppTab.Home -> HomePage(
                    importedRoutes = importedRoutes,
                    routeRideState = routeRideState,
                    activeRouteId = activeRouteId,
                    liveRideDashboardState = liveRideDashboardState,
                    onStartRoute = onStartRoute,
                    onAddRoute = onAddRoute,
                    onImportGpx = onImportGpx
                )
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
    Home("Home"),
    Ride("Ride"),
    Routes("Routes"),
    History("History"),
    HUD("HUD"),
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
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppTab.values().forEach { tab ->
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                onClick = { onTabSelected(tab) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeTab == tab) Color(0xFF10B981) else Color(0xFF18181B),
                    contentColor = if (activeTab == tab) Color.Black else Color.White
                )
            ) {
                Text(
                    text = tab.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HomePage(
    importedRoutes: List<ImportedRoute>,
    routeRideState: RouteRideState,
    activeRouteId: String?,
    liveRideDashboardState: LiveRideDashboardState,
    onStartRoute: (ImportedRoute) -> Unit,
    onAddRoute: () -> Unit,
    onImportGpx: () -> Unit
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
    val lastRoute = importedRoutes.firstOrNull()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF18181B),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = activeRoute?.name ?: "No active route",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeRoute?.metadata?.distanceMeters?.let { formatMetersAsMiles(it) }
                                ?: "Add a route to get started",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = activeProgress?.let { "${formatPercent(it.progressPercent)} complete" }
                                ?: "Ready for your next ride",
                            color = Color(0xFF34D399),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    activeRoute?.let(onStartRoute)
                                },
                                enabled = activeRoute != null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeRoute != null) Color(0xFF10B981) else Color(0xFF27272A)
                                )
                            ) {
                                Text(
                                    text = if (activeProgress != null && !activeProgress.isComplete) "Resume route" else "Start route",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeRoute != null) Color.Black else Color.White
                                )
                            }
                            Button(
                                onClick = onAddRoute,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                            ) {
                                Text(text = "Add Route", fontSize = 16.sp, color = Color.White)
                            }
                            Button(
                                onClick = onImportGpx,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                            ) {
                                Text(text = "Import GPX", fontSize = 16.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF18181B),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Live Summary",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryStat("Power", "${liveRideDashboardState.powerWatts.roundToInt()} W", Modifier.weight(1f))
                            SummaryStat("Cadence", "${liveRideDashboardState.cadenceRpm.roundToInt()} rpm", Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryStat("Speed", "${oneDecimal(liveRideDashboardState.speedMph)} mph", Modifier.weight(1f))
                            SummaryStat("Resistance", liveRideDashboardState.resistance.toString(), Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryStat("Distance", "${oneDecimal(liveRideDashboardState.distanceMiles)} mi", Modifier.weight(1f))
                            SummaryStat("Time", DateUtils.formatElapsedTime(liveRideDashboardState.elapsedSeconds), Modifier.weight(1f))
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF18181B),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Saved Route Preview",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        if (lastRoute != null) {
                            Text(text = lastRoute.name, color = Color(0xFF34D399), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(text = formatMetersAsMiles(lastRoute.metadata.distanceMeters), color = Color(0xFFA1A1AA), fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SummaryStat("Climb", formatMetersAsFeet(lastRoute.metadata.totalClimbMeters), Modifier.weight(1f))
                                SummaryStat("Max Grade", formatPercent(lastRoute.metadata.maxGradePercent), Modifier.weight(1f))
                                SummaryStat("Avg Climb", formatPercent(lastRoute.metadata.averageClimbingGradePercent), Modifier.weight(1f))
                            }
                        } else {
                            Text(
                                text = "No saved routes yet.",
                                color = Color(0xFFA1A1AA),
                                fontSize = 16.sp
                            )
                        }
                    }
                }
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
    onClickedRestartApp: () -> Unit,
    onClickedRelease: (Release) -> Unit,
    latestRelease: Release?
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            SettingsToggle(
                title = "Show timer when minimized",
                checked = timerShownWhenMinimized,
                onCheckedChange = onTimerShownWhenMinimizedToggled
            )
        }
        item {
            SettingsToggle(
                title = "Enable experimental Bike+ resistance controls",
                checked = bikePlusResistanceControlEnabled,
                onCheckedChange = onBikePlusResistanceControlToggled
            )
        }
        if (bikePlusResistanceControlEnabled) {
            item {
                SettingsToggle(
                    title = "Simulate route grade with resistance",
                    subtitle = "Experimental: changes Bike+ resistance from active route grade.",
                    checked = routeResistanceSimulationEnabled,
                    onCheckedChange = onRouteResistanceSimulationToggled
                )
            }
        }
        item {
            SettingsToggle(
                title = "Enable HeartCast heart rate",
                subtitle = "Uses the standard Bluetooth heart rate broadcast from HeartCast",
                checked = heartRateMonitorEnabled,
                onCheckedChange = onHeartRateMonitorEnabledToggled
            )
        }
        item {
            SettingsToggle(
                title = "Enable ride session recording",
                checked = rideSessionRecordingEnabled,
                onCheckedChange = onRideSessionRecordingEnabledToggled
            )
        }
        item {
            ReleaseStatus(latestRelease, onClickedRelease)
        }
        item {
            Button(
                onClick = onClickedRestartApp,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)
            ) {
                Text(
                    text = "Restart Switchback",
                    fontSize = 18.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color.White
                )
            }
        }
        item {
            Text(
                "Device: ${Build.DEVICE}\tSDK: ${Build.VERSION.RELEASE}\tOS: ${Build.FINGERPRINT}",
                color = Color.White.copy(alpha = .5f)
            )
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
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF18181B),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        text = "Switchback overlay",
                        fontSize = 28.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Not endorsed with, associated with, or supported by Peloton",
                        fontSize = 16.sp,
                        color = Color(0xFFA1A1AA),
                        fontStyle = FontStyle.Italic
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        onClick = if (overlayRunning) onClickedStopOverlay else onClickedStartOverlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (overlayRunning) ErrorColor else Color(0xFF10B981)
                        )
                    ) {
                        Text(
                            text = if (overlayRunning) "Stop Overlay" else "Start Overlay",
                            fontSize = 22.sp,
                            fontFamily = LatoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (overlayRunning) Color.White else Color.Black
                        )
                    }
                }
            }
        }

        item {
            SettingsToggle(
                title = "Show timer when minimized",
                checked = timerShownWhenMinimized,
                onCheckedChange = onTimerShownWhenMinimizedToggled
            )
        }
        item {
            SettingsToggle(
                title = "Enable experimental Bike+ resistance controls",
                checked = bikePlusResistanceControlEnabled,
                onCheckedChange = onBikePlusResistanceControlToggled
            )
        }
        if (bikePlusResistanceControlEnabled) {
            item {
                SettingsToggle(
                    title = "Show Bike+ resistance control overlay",
                    checked = bikePlusResistanceControlOverlayVisible,
                    onCheckedChange = onBikePlusResistanceControlOverlayVisibleToggled
                )
            }
            item {
                SettingsToggle(
                    title = "Simulate route grade with resistance",
                    subtitle = "Experimental: changes Bike+ resistance from active route grade. Choose preset from the route page.",
                    checked = routeResistanceSimulationEnabled,
                    onCheckedChange = onRouteResistanceSimulationToggled
                )
            }
        }
        item {
            SettingsToggle(
                title = "Enable HeartCast heart rate",
                subtitle = "Uses the standard Bluetooth heart rate broadcast from HeartCast",
                checked = heartRateMonitorEnabled,
                onCheckedChange = onHeartRateMonitorEnabledToggled
            )
        }
        item {
            SettingsToggle(
                title = "Enable ride session recording",
                checked = rideSessionRecordingEnabled,
                onCheckedChange = onRideSessionRecordingEnabledToggled
            )
        }
        item {
            SettingsSectionTitle("Stats HUD fields")
        }
        item {
            SettingsToggle("Power", hudShowPower, onHudShowPowerToggled)
        }
        item {
            SettingsToggle("Speed", hudShowSpeed, onHudShowSpeedToggled)
        }
        item {
            SettingsToggle("Distance", hudShowDistance, onHudShowDistanceToggled)
        }
        item {
            SettingsToggle("Time", hudShowTime, onHudShowTimeToggled)
        }
        item {
            SettingsToggle("Resistance", hudShowResistance, onHudShowResistanceToggled)
        }
        item {
            SettingsToggle("Heart Rate", hudShowHeartRate, onHudShowHeartRateToggled)
        }
        item {
            SettingsToggle("Calories", hudShowCalories, onHudShowCaloriesToggled)
        }
        item {
            Button(
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
        item {
            ReleaseStatus(latestRelease, onClickedRelease)
        }
        item {
            Button(
                onClick = onClickedRestartApp,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorColor),
            ) {
                Text(
                    text = "Restart Switchback",
                    fontSize = 18.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color.White
                )
            }
        }
        item {
            Text(
                "Device: ${Build.DEVICE}\t" +
                        "SDK: ${Build.VERSION.RELEASE}\t" +
                        "OS Version: ${Build.FINGERPRINT}\t",
                color = Color.White.copy(alpha = .5f)
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
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0F172A),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Live Ride",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.routeHudState?.routeName ?: "No active route",
                        color = Color(0xFF34D399),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DashboardMetricCard(
                            label = "Power",
                            value = "${state.powerWatts.roundToInt()} W",
                            modifier = Modifier.weight(1f)
                        )
                        DashboardMetricCard(
                            label = "Speed",
                            value = "${oneDecimal(state.speedMph)} mph",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DashboardStatCard(
                            label = "Cadence",
                            value = "${state.cadenceRpm.roundToInt()} rpm",
                            modifier = Modifier.weight(1f)
                        )
                        DashboardStatCard(
                            label = "Resistance",
                            value = state.resistance.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DashboardStatCard(
                            label = "Distance",
                            value = "${oneDecimal(state.distanceMiles)} mi",
                            modifier = Modifier.weight(1f)
                        )
                        DashboardStatCard(
                            label = "Time",
                            value = DateUtils.formatElapsedTime(state.elapsedSeconds),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DashboardStatCard(
                            label = "Calories / Work",
                            value = "${state.workKilojoules.toInt()} kJ",
                            modifier = Modifier.weight(1f)
                        )
                        if (state.routeHudState != null) {
                            DashboardStatCard(
                                label = if (bikePlusControlsEnabled) "Bike+ Control" else "Cue",
                                value = if (bikePlusControlsEnabled) "Auto control" else state.routeHudState.visualResistanceCue ?: "--",
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        state.routeHudState?.let { routeHudState ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0F172A),
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
                                    text = routeHudState.routeName,
                                    color = Color(0xFF34D399),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Active route",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                text = formatPercent(routeHudState.progressPercent),
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            DashboardStatCard(
                                label = "Remaining",
                                value = "${oneDecimal(routeHudState.remainingMiles)} mi",
                                modifier = Modifier.weight(1f)
                            )
                            DashboardStatCard(
                                label = "Current grade",
                                value = formatPercent(routeHudState.gradePercent),
                                modifier = Modifier.weight(1f)
                            )
                            DashboardStatCard(
                                label = "Elevation",
                                value = formatElevation(routeHudState.elevationMeters),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (routeHudState.points.size >= 2) {
                            DashboardRouteMap(
                                routeHudState = routeHudState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            DashboardElevationProfile(
                                routeHudState = routeHudState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            )
                        }
                    }
                }
            }
        } ?: item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0F172A),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "No active route",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose a route to see progress, remaining distance, grade, and map preview.",
                        color = Color(0xFF94A3B8),
                        fontSize = 16.sp
                    )
                    Button(
                        onClick = onAddRoute,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF064E3B))
                    ) {
                        Text(text = "Select / Add Route")
                    }
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
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF18181B),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (url == null) {
                        Text(
                            text = routeUploadPortalState.message ?: "Starting upload portal...",
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    } else {
                        Surface(
                            color = Color.White,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Image(
                                bitmap = RouteUploadQrCode.bitmap(url).asImageBitmap(),
                                contentDescription = "Route upload QR code",
                                modifier = Modifier
                                    .padding(14.dp)
                                    .requiredSize(260.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Scan this QR code from a device on the same Wi-Fi.",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = url,
                            color = Color(0xFF34D399),
                            fontSize = 16.sp
                        )
                        routeUploadPortalState.message?.let {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = it,
                                color = Color(0xFFA1A1AA),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
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
                        text = "Fallback",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "If the phone cannot reach the bike, you can still push GPX files to the route_imports folder and import them here.",
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

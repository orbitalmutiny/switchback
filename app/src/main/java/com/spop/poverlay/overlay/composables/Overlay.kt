package com.spop.poverlay.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spop.poverlay.overlay.composables.OverlayMainContent
import com.spop.poverlay.overlay.composables.OverlayMinimizedContent
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

const val VisibilityChangeDurationMs = 150
val OverlayCornerRadius = 25.dp
val StatCardWidth = 105.dp
val PowerChartFullWidth = 200.dp
val PowerChartShrunkWidth = 120.dp
val BackgroundColorDefault = Color(20, 20, 20)

// Shown when a sensor hasn't reported a value yet
const val SensorValuePlaceholderText = "-"

@Composable
fun Overlay(
    sensorViewModel: OverlaySensorViewModel,
    timerViewModel: OverlayTimerViewModel,
    height: Dp,
    locationState: State<OverlayLocation>,
    horizontalDragCallback: (Float) -> Float,
    verticalDragCallback: (Float) -> Float,
    offsetCallback: (Float, Float) -> Unit,
    onLayout: (IntSize) -> Unit,
    onTimerLayout: (IntSize) -> Unit
) {
    val power by sensorViewModel.powerValue.collectAsStateWithLifecycle(initialValue = SensorValuePlaceholderText)

    val rpm by sensorViewModel.rpmValue.collectAsStateWithLifecycle(initialValue = SensorValuePlaceholderText)
    val resistance by sensorViewModel.resistanceValue.collectAsStateWithLifecycle(initialValue = SensorValuePlaceholderText)
    val speed by sensorViewModel.speedValue.collectAsStateWithLifecycle(initialValue = SensorValuePlaceholderText)
    val speedLabel by sensorViewModel.speedLabel.collectAsStateWithLifecycle(initialValue = "")
    val timerLabel by sensorViewModel.rideElapsedValue.collectAsStateWithLifecycle(initialValue = "00:00")
    val distance by sensorViewModel.rideDistanceValue.collectAsStateWithLifecycle(initialValue = "0.00")
    val calories by sensorViewModel.rideCaloriesValue.collectAsStateWithLifecycle(initialValue = "0")
    val heartRate by sensorViewModel.heartRateValue.collectAsStateWithLifecycle(initialValue = SensorValuePlaceholderText)
    val routeHudState by sensorViewModel.routeHudState.collectAsStateWithLifecycle(initialValue = null)
    val showPower by sensorViewModel.hudShowPower.collectAsStateWithLifecycle(initialValue = true)
    val showSpeed by sensorViewModel.hudShowSpeed.collectAsStateWithLifecycle(initialValue = true)
    val showDistance by sensorViewModel.hudShowDistance.collectAsStateWithLifecycle(initialValue = true)
    val showTime by sensorViewModel.hudShowTime.collectAsStateWithLifecycle(initialValue = true)
    val showResistance by sensorViewModel.hudShowResistance.collectAsStateWithLifecycle(initialValue = true)
    val showHeartRate by sensorViewModel.hudShowHeartRate.collectAsStateWithLifecycle(initialValue = true)
    val showCalories by sensorViewModel.hudShowCalories.collectAsStateWithLifecycle(initialValue = true)
    val errorMessage by sensorViewModel.errorMessage.collectAsStateWithLifecycle(initialValue = null)

    val minimized by sensorViewModel.isMinimized.collectAsStateWithLifecycle(initialValue = false)
    val location by remember { locationState }
    val size = remember { mutableStateOf(IntSize.Zero) }


    val mainContentHeight = with(LocalDensity.current) {
        height.roundToPx()
    }

    val timerAlpha by animateFloatAsState(
        if (minimized) .5f else 1f,
        animationSpec = TweenSpec(VisibilityChangeDurationMs, 0, LinearEasing)
    )

    val visibilityOffset by animateIntOffsetAsState(
        if (minimized) {
            when (location) {
                // When the main content is hidden, move it off screen completely
                OverlayLocation.Top -> IntOffset(0, -mainContentHeight)
                OverlayLocation.Bottom -> IntOffset(0, mainContentHeight)
                OverlayLocation.Left -> IntOffset(-size.value.width, 0)
                OverlayLocation.Right -> IntOffset(size.value.width, 0)
            }
        } else {
            IntOffset.Zero
        },
        animationSpec = TweenSpec(VisibilityChangeDurationMs, 0, LinearEasing),
        finishedListener = {
        }
    )

    offsetCallback(visibilityOffset.y.toFloat(), size.value.height.toFloat())

    var horizontalDragOffset by remember { mutableStateOf(0f) }
    var verticalDragOffset by remember { mutableStateOf(0f) }

    val backgroundShape = when (location) {
        OverlayLocation.Top -> RoundedCornerShape(
            bottomStart = OverlayCornerRadius, bottomEnd = OverlayCornerRadius
        )
        OverlayLocation.Bottom -> RoundedCornerShape(
            topStart = OverlayCornerRadius, topEnd = OverlayCornerRadius
        )
        OverlayLocation.Left -> RoundedCornerShape(
            topEnd = OverlayCornerRadius, bottomEnd = OverlayCornerRadius
        )
        OverlayLocation.Right -> RoundedCornerShape(
            topStart = OverlayCornerRadius, bottomStart = OverlayCornerRadius
        )
    }
    val timer = @Composable {
        val showTimerWhenMinimizedFlow = remember {
            timerViewModel.showTimerWhenMinimized.onEach {
                Timber.i("Show Timer: $it")
            }
        }
        val showTimerWhenMinimized by showTimerWhenMinimizedFlow
            .collectAsStateWithLifecycle(initialValue = true)

        OverlayMinimizedContent(
            isMinimized = minimized,
            timerPaused = false,
            showTimerWhenMinimized = showTimerWhenMinimized,
            location = location,
            powerLabel = power,
            contentAlpha = timerAlpha,
            timerLabel = timerLabel,
            cadenceLabel = rpm,
            speedLabel = speed,
            distanceLabel = distance,
            resistanceLabel = resistance,
            heartRateLabel = heartRate,
            caloriesLabel = calories,
            showPower = showPower,
            showSpeed = showSpeed,
            showDistance = showDistance,
            showResistance = showResistance,
            showHeartRate = showHeartRate,
            showCalories = showCalories,
            isHorizontal = location.isHorizontal,
            onTap = { sensorViewModel.onOverlayPressed() },
            onLongPress = { sensorViewModel.onOverlayDoubleTap() },
            onLayout = onTimerLayout
        )
    }
    val mainContent = @Composable {
        Box(modifier = Modifier
            .then(if (location.isHorizontal) Modifier.requiredHeight(height) else Modifier.wrapContentHeight())
            .wrapContentWidth(unbounded = true)
            .onSizeChanged {
                if (it.width != size.value.width || it.height != size.value.height) {
                    size.value = it
                    onLayout(size.value)
                }
            }
            .background(
                color = BackgroundColorDefault,
                shape = backgroundShape,
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { sensorViewModel.onOverlayPressed() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(onDrag = { change, offset ->
                    change.consume()
                    horizontalDragOffset += offset.x
                    horizontalDragOffset = horizontalDragCallback(horizontalDragOffset)

                    verticalDragOffset += offset.y
                    verticalDragOffset = verticalDragCallback(verticalDragOffset)
                }, onDragEnd = {
                    verticalDragOffset = 0f
                })
            }) {
            OverlayMainContent(
                modifier = Modifier
                    .wrapContentWidth(unbounded = true)
                    .padding(horizontal = 9.dp)
                    .padding(bottom = 5.dp),
                isHorizontal = location.isHorizontal,
                power = power,
                speed = speed,
                speedLabel = speedLabel,
                distance = distance,
                timer = timerLabel,
                resistance = resistance,
                heartRate = heartRate,
                calories = calories,
                showPower = showPower,
                showSpeed = showSpeed,
                showDistance = showDistance,
                showTime = showTime,
                showResistance = showResistance,
                showHeartRate = showHeartRate,
                showCalories = showCalories,
                routeHudState = routeHudState,
                onSpeedClicked = { sensorViewModel.onClickedSpeed() },
            )
        }
    }


    Box(
        modifier = Modifier
            .wrapContentSize(unbounded = true)
    ) {
        errorMessage?.let {
            Snackbar(
                action = {
                    Button(onClick = { sensorViewModel.onDismissErrorPressed() }) {
                        Text("Dismiss")
                    }
                },
                containerColor = Color.White,
                modifier = Modifier
                    .padding(8.dp)
                    .zIndex(1f)
            ) {
                Text(it, color = Color.Black)
            }
            return@Box
        }
        Column(
            modifier = Modifier
                .wrapContentSize()
                .offset { visibilityOffset },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            when (location) {
                OverlayLocation.Top -> {
                    mainContent()

                    if (minimized) {
                        timer()
                    }
                }
                OverlayLocation.Bottom -> {
                    if (minimized) {
                        timer()
                    }
                    mainContent()

                }
                OverlayLocation.Left,
                OverlayLocation.Right -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (location == OverlayLocation.Right) {
                            timer()
                        }
                        mainContent()
                        if (location == OverlayLocation.Left) {
                            timer()
                        }
                    }
                }
            }
        }
    }

}

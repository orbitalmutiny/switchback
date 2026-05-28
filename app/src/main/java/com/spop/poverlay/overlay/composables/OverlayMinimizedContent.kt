package com.spop.poverlay.overlay.composables

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spop.poverlay.R
import com.spop.poverlay.overlay.BackgroundColorDefault
import com.spop.poverlay.overlay.OverlayLocation
import com.spop.poverlay.route.ManualResistanceGuidanceState
import com.spop.poverlay.route.ResistanceDirection


@Composable
fun OverlayMinimizedContent(
    isMinimized: Boolean,
    showTimerWhenMinimized: Boolean,
    location: OverlayLocation,
    powerLabel: String,
    cadenceLabel: String,
    speedLabel: String,
    distanceLabel: String,
    resistanceLabel: String,
    guidanceState: ManualResistanceGuidanceState?,
    heartRateLabel: String,
    caloriesLabel: String,
    showPower: Boolean,
    showSpeed: Boolean,
    showDistance: Boolean,
    showResistance: Boolean,
    showHeartRate: Boolean,
    showCalories: Boolean,
    isHorizontal: Boolean,
    contentAlpha: Float,
    timerLabel: String,
    timerPaused: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onLayout: (IntSize) -> Unit
) {
    val backgroundShape = if (isMinimized) {
        RoundedCornerShape(8.dp)
    } else {
        when (location) {
            OverlayLocation.Top -> RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
            OverlayLocation.Bottom -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            OverlayLocation.Left -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
            OverlayLocation.Right -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
        }
    }
    val expandedVerticalPadding = if (isMinimized) {
        1.dp
    } else {
        0.dp
    }
    val size = remember { mutableStateOf(IntSize.Zero) }

    val contentModifier = Modifier
        .alpha(contentAlpha)
        .wrapContentSize().onSizeChanged {
            if (it.width != size.value.width || it.height != size.value.height) {
                size.value = it
                onLayout(size.value)
            }
        }
        .padding(vertical = expandedVerticalPadding)
        .background(
            color = BackgroundColorDefault,
            shape = backgroundShape,
        )
        .padding(horizontal = 10.dp)
        .padding(top = 1.dp)
        .animateContentSize()
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    onTap()
                },
                onLongPress = {
                    onLongPress()
                }
            )
        }

    val content = @Composable {
        val infiniteTransition = rememberInfiniteTransition()
        if (!isMinimized || showTimerWhenMinimized || timerPaused) {

            val timerAlpha = if (timerPaused) {
                infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                ).value
            } else {
                1f
            }

            OverlayTimerField(
                modifier = Modifier
                    .width(80.dp)
                    .alpha(timerAlpha),
                timerLabel = timerLabel,
                iconDrawable = R.drawable.ic_timer
            )
        }

        if (isMinimized) {
            if (showPower) {
                Spacer(modifier = if (isHorizontal) Modifier.width(4.dp) else Modifier.height(4.dp))
                OverlayTimerField(
                    modifier = Modifier.width(58.dp),
                    timerLabel = powerLabel,
                    iconDrawable = R.drawable.ic_power
                )
            }
            Spacer(modifier = if (isHorizontal) Modifier.width(4.dp) else Modifier.height(4.dp))
            OverlayTimerField(
                modifier = Modifier.width(58.dp),
                timerLabel = cadenceLabel,
                iconDrawable = R.drawable.ic_cadence
            )
            if (showSpeed) {
                Spacer(modifier = if (isHorizontal) Modifier.width(4.dp) else Modifier.height(4.dp))
                OverlayTimerField(
                    modifier = Modifier.width(58.dp),
                    timerLabel = speedLabel,
                    iconDrawable = R.drawable.ic_speed
                )
            }
            if (showDistance) {
                Spacer(modifier = if (isHorizontal) Modifier.width(4.dp) else Modifier.height(4.dp))
                OverlayTimerField(
                    modifier = Modifier.width(58.dp),
                    timerLabel = distanceLabel,
                    iconDrawable = R.drawable.ic_distance
                )
            }
            if (showResistance) {
                Spacer(modifier = if (isHorizontal) Modifier.width(4.dp) else Modifier.height(4.dp))
                MiniResistanceField(
                    modifier = Modifier.width(94.dp),
                    resistanceLabel = resistanceLabel,
                    guidanceState = guidanceState
                )
            }
            if (showHeartRate) {
                Spacer(modifier = if (isHorizontal) Modifier.width(4.dp) else Modifier.height(4.dp))
                OverlayTimerField(
                    modifier = Modifier.width(58.dp),
                    timerLabel = heartRateLabel,
                    iconDrawable = R.drawable.ic_heart_rate
                )
            }
            if (showCalories) {
                Spacer(modifier = if (isHorizontal) Modifier.width(4.dp) else Modifier.height(4.dp))
                OverlayTimerField(
                    modifier = Modifier.width(58.dp),
                    timerLabel = caloriesLabel,
                    iconDrawable = R.drawable.ic_calories
                )
            }
        }
    }

    if (isHorizontal) {
        Row(
            modifier = contentModifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    } else {
        Column(
            modifier = contentModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun MiniResistanceField(
    modifier: Modifier,
    resistanceLabel: String,
    guidanceState: ManualResistanceGuidanceState?
) {
    val (indicator, color) = miniGuidanceIndicator(guidanceState)
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                modifier = Modifier
                    .requiredHeight(20.dp)
                    .requiredWidth(16.dp)
                    .padding(vertical = 4.dp),
                painter = painterResource(id = R.drawable.ic_resistance),
                contentDescription = null,
            )
            Text(
                text = resistanceLabel,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = indicator,
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun miniGuidanceIndicator(guidanceState: ManualResistanceGuidanceState?): Pair<String, Color> =
    when (guidanceState) {
        is ManualResistanceGuidanceState.AdjustmentNeeded -> if (guidanceState.direction == ResistanceDirection.Up) {
            "\u2191" to Color(0xFFFACC15)
        } else {
            "\u2193" to Color(0xFFF97316)
        }
        is ManualResistanceGuidanceState.Upcoming -> if (guidanceState.direction == ResistanceDirection.Up) {
            "\u2197" to Color(0xFF3B82F6)
        } else {
            "\u2198" to Color(0xFF3B82F6)
        }
        is ManualResistanceGuidanceState.InRange -> "\u2713" to Color(0xFF22C55E)
        is ManualResistanceGuidanceState.Stale -> "\u2022" to Color.White
        else -> "\u2022" to Color.White.copy(alpha = 0.5f)
    }

@Composable
private fun OverlayTimerField(
    modifier: Modifier,
    timerLabel: String,
    iconDrawable: Int,
) {
    Row(
        modifier = modifier
            .wrapContentHeight(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Image(
            modifier = Modifier
                .requiredHeight(20.dp)
                .requiredWidth(16.dp)
                .align(Alignment.CenterVertically)
                .padding(vertical = 4.dp),
            painter = painterResource(id = iconDrawable),
            contentDescription = null,
        )
        Text(
            timerLabel,
            color = Color.White,
            fontSize = if (timerLabel.length > 6) 17.sp else 19.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
        )
    }
}

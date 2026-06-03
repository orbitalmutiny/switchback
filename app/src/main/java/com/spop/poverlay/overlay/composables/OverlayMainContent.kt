package com.spop.poverlay.overlay.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spop.poverlay.route.ManualResistanceGuidanceState
import com.spop.poverlay.route.ResistanceDirection
import com.spop.poverlay.route.RouteHudState
import kotlin.math.abs

@Composable
fun OverlayMainContent(
    modifier: Modifier,
    isHorizontal: Boolean,
    power: String,
    speed: String,
    speedLabel: String,
    distance: String,
    timer: String,
    resistance: String,
    heartRate: String,
    calories: String,
    showPower: Boolean,
    showSpeed: Boolean,
    showDistance: Boolean,
    showTime: Boolean,
    showResistance: Boolean,
    showHeartRate: Boolean,
    showCalories: Boolean,
    routeHudState: RouteHudState?,
    onSpeedClicked: () -> Unit
) {
    val stats = listOfNotNull(
        HudStat("Time", timer, "").takeIf { showTime },
        HudStat("Power", power, "watts").takeIf { showPower },
        HudStat("Speed", speed, speedLabel, onSpeedClicked).takeIf { showSpeed },
        HudStat("Distance", distance, "mi").takeIf { showDistance },
        HudStat("Resistance", resistance, "").takeIf { showResistance },
        HudStat("Heart Rate", heartRate, "bpm").takeIf { showHeartRate },
        HudStat("Calories", calories, "cal").takeIf { showCalories }
    )

    if (isHorizontal) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Surface(
                modifier = Modifier,
                color = Color(0xFF111318).copy(alpha = 0.92f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    stats.forEachIndexed { index, stat ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .requiredWidth(1.dp)
                                    .requiredHeight(64.dp)
                                    .background(Color.White.copy(alpha = 0.12f))
                            )
                        }
                        if (stat.label == "Resistance") {
                            GuidedResistanceTile(
                                currentResistance = stat.value,
                                guidance = routeHudState?.guidanceState ?: ManualResistanceGuidanceState.Neutral,
                                modifier = Modifier
                                    .requiredWidth(120.dp)
                                    .padding(horizontal = 4.dp)
                            )
                        } else {
                            FullHudStatCell(
                                stat = stat,
                                modifier = Modifier
                                    .requiredWidth(106.dp)
                                    .then(if (stat.onClick == null) Modifier else Modifier.clickable { stat.onClick.invoke() })
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier.width(188.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            stats.forEach {
                SideHudStatRow(
                    stat = it,
                    modifier = if (it.onClick == null) Modifier else Modifier.clickable { it.onClick.invoke() }
                )
            }
            routeHudState?.let {
                SideRouteHudSummary(it)
            }
        }
    }
}

private data class HudStat(
    val label: String,
    val value: String,
    val unit: String,
    val onClick: (() -> Unit)? = null
)

@Composable
private fun FullHudStatCell(
    stat: HudStat,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stat.label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 10.sp
        )
        Text(
            text = stat.value,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        Text(
            text = stat.unit,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GuidedResistanceTile(
    currentResistance: String,
    guidance: ManualResistanceGuidanceState,
    modifier: Modifier = Modifier
) {
    val style = guidanceStyle(guidance)
    val isUpcoming = guidance is ManualResistanceGuidanceState.Upcoming
    // Fixed height regardless of subtitle presence — prevents the outer Row from resizing
    // when guidance state transitions, which was causing the visible layout shift / bottom gap.
    Column(
        modifier = modifier
            .height(96.dp)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
            Text("Resistance", color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp)
            Text(
                text = "${style.icon} $currentResistance",
                color = style.color,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp
            )
            // Always reserve the subtitle row — transparent text when inactive so the tile
            // height and content centering are identical across all guidance states.
            Box(modifier = Modifier.height(2.dp))
            Text(
                text = style.subtitle ?: "",
                color = if (style.subtitle != null) style.color else Color.Transparent,
                fontSize = if (isUpcoming) 11.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = if (isUpcoming) 12.sp else 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
    }
}

private data class GuidanceStyle(
    val color: Color,
    val icon: String,
    val subtitle: String?
)

private fun guidanceStyle(guidance: ManualResistanceGuidanceState): GuidanceStyle =
    when (guidance) {
        is ManualResistanceGuidanceState.AdjustmentNeeded -> GuidanceStyle(
            color = if (guidance.delta >= 0) Color(0xFFFACC15) else Color(0xFFF97316),
            icon = if (guidance.delta >= 0) "\u2191" else "\u2193",
            subtitle = "TARGET ${guidance.targetCenter}"
        )
        is ManualResistanceGuidanceState.Upcoming -> GuidanceStyle(
            color = Color(0xFF3B82F6),
            icon = if (guidance.delta >= 0) "\u2197" else "\u2198",
            subtitle = "NEXT ${guidance.targetCenter} in ${guidance.etaSeconds}s"
        )
        is ManualResistanceGuidanceState.InRange -> GuidanceStyle(
            color = Color(0xFF22C55E),
            icon = "\u2022",
            subtitle = null
        )
        is ManualResistanceGuidanceState.Stale -> GuidanceStyle(
            color = Color.White,
            icon = "\u2022",
            subtitle = "TARGET ${guidance.targetCenter}"
        )
        ManualResistanceGuidanceState.Neutral -> GuidanceStyle(
            color = Color.White,
            icon = "\u2022",
            subtitle = null
        )
    }

@Composable
private fun SideHudStatRow(
    stat: HudStat,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = stat.unit,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Light
            )
        }
        Text(
            text = stat.value,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SideRouteHudSummary(routeHudState: RouteHudState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = routeHudState.routeName,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "${formatPercent(routeHudState.progressPercent)} | ${formatMiles(routeHudState.remainingMiles)} mi left",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp
        )
        routeHudState.guidanceState?.let { guidance ->
            CompactGuidanceLine(guidance)
        }
    }
}

@Composable
private fun CompactGuidanceLine(guidance: ManualResistanceGuidanceState) {
    val (color, text) = when (guidance) {
        is ManualResistanceGuidanceState.AdjustmentNeeded -> Pair(
            if (guidance.delta >= 0) Color(0xFFFACC15) else Color(0xFFF97316),
            "${guidanceArrow(guidance.direction)}${abs(guidance.delta)} -> ${guidance.targetCenter} (now ${guidance.currentResistance})"
        )
        is ManualResistanceGuidanceState.Upcoming -> Pair(
            Color(0xFF3B82F6),
            "${guidanceUpcomingArrow(guidance.direction)}${abs(guidance.delta)}! to ${guidance.targetCenter}"
        )
        is ManualResistanceGuidanceState.InRange -> Pair(
            Color(0xFF6EE7B7),
            "ON ${guidance.targetMin}-${guidance.targetMax}"
        )
        is ManualResistanceGuidanceState.Stale -> Pair(
            Color.White,
            "~ ${guidance.targetCenter}  (now ${guidance.currentResistance})"
        )
        ManualResistanceGuidanceState.Neutral -> return
    }
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
}

private fun guidanceArrow(direction: ResistanceDirection): String = when (direction) {
    ResistanceDirection.Up -> "\u2191"
    ResistanceDirection.Down -> "\u2193"
    ResistanceDirection.Hold -> "\u2022"
}

private fun guidanceUpcomingArrow(direction: ResistanceDirection): String = when (direction) {
    ResistanceDirection.Up -> "\u2197"
    ResistanceDirection.Down -> "\u2198"
    ResistanceDirection.Hold -> "\u2022"
}

private fun formatPercent(value: Double): String =
    "${value.toInt().coerceIn(0, 100)}%"

private fun formatMiles(value: Double): String =
    "%.1f".format(value.coerceAtLeast(0.0))

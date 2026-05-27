package com.spop.poverlay.overlay.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spop.poverlay.overlay.StatCard
import com.spop.poverlay.overlay.StatCardWidth
import com.spop.poverlay.route.ManualResistanceGuidanceState
import com.spop.poverlay.route.ResistanceDirection
import com.spop.poverlay.route.RouteHudState

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
    val compact = !isHorizontal
    val statCardModifier = Modifier.requiredWidth(if (compact) 82.dp else StatCardWidth)
    val stats = listOfNotNull(
        HudStat("Power", power, "watts").takeIf { showPower },
        HudStat("Speed", speed, speedLabel, onSpeedClicked).takeIf { showSpeed },
        HudStat("Distance", distance, "mi").takeIf { showDistance },
        HudStat("Time", timer, "").takeIf { showTime },
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
            routeHudState?.let {
                RouteHudStrip(it, Modifier.fillMaxWidth())
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                stats.forEach {
                    StatCard(
                        it.label,
                        it.value,
                        it.unit,
                        statCardModifier.then(
                            if (it.onClick == null) Modifier else Modifier.clickable { it.onClick.invoke() }
                        )
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier.width(176.dp),
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
private fun RouteHudStrip(
    routeHudState: RouteHudState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .requiredHeight(24.dp)
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatRouteHudSummary(routeHudState),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
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

private fun formatRouteHudSummary(routeHudState: RouteHudState): String {
    val base = "${routeHudState.routeName}  ${formatPercent(routeHudState.progressPercent)}  " +
            "${formatMiles(routeHudState.remainingMiles)} mi left  grade ${formatGrade(routeHudState.gradePercent)}"
    val guidanceSuffix = when (val g = routeHudState.guidanceState) {
        is ManualResistanceGuidanceState.AdjustmentNeeded ->
            "  ${guidanceArrow(g.direction)}${if (g.delta >= 0) "+${g.delta}" else "${g.delta}"}→${g.targetCenter}"
        is ManualResistanceGuidanceState.Upcoming ->
            "  ${guidanceArrow(g.direction)}${g.targetCenter} in ${g.etaSeconds}s"
        is ManualResistanceGuidanceState.InRange ->
            "  ✓ ${g.targetMin}–${g.targetMax}"
        is ManualResistanceGuidanceState.Stale ->
            "  ~${g.targetCenter}"
        else -> ""
    }
    return base + guidanceSuffix
}

@Composable
private fun CompactGuidanceLine(guidance: ManualResistanceGuidanceState) {
    val (color, text) = when (guidance) {
        is ManualResistanceGuidanceState.AdjustmentNeeded -> Pair(
            Color(0xFF34D399),
            "${guidanceArrow(guidance.direction)} ${if (guidance.delta >= 0) "+${guidance.delta}" else "${guidance.delta}"} → ${guidance.targetCenter}  (now ${guidance.currentResistance})"
        )
        is ManualResistanceGuidanceState.Upcoming -> Pair(
            Color(0xFFFBBF24),
            "${guidanceArrow(guidance.direction)} ${guidance.targetCenter} in ${guidance.etaSeconds}s"
        )
        is ManualResistanceGuidanceState.InRange -> Pair(
            Color(0xFF6EE7B7),
            "✓ ${guidance.targetMin}–${guidance.targetMax}"
        )
        is ManualResistanceGuidanceState.Stale -> Pair(
            Color(0xFF71717A),
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
    ResistanceDirection.Up -> "▲"
    ResistanceDirection.Down -> "▼"
    ResistanceDirection.Hold -> "●"
}

private fun formatPercent(value: Double): String =
    "${value.toInt().coerceIn(0, 100)}%"

private fun formatMiles(value: Double): String =
    "%.1f".format(value.coerceAtLeast(0.0))

private fun formatGrade(value: Double): String =
    "${"%.1f".format(value)}%"

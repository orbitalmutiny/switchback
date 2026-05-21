package com.spop.poverlay.overlay.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spop.poverlay.route.RouteHudState
import com.spop.poverlay.route.RoutePoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private enum class RouteOverlayMode {
    Map,
    Elevation
}

@Composable
fun RouteMapOverlay(
    routeHudState: RouteHudState,
    modifier: Modifier = Modifier,
    useMph: Boolean = true,
    onCollapsedChanged: (Boolean) -> Unit = {},
    onMoveBy: (Float) -> Unit = {}
) {
    var collapsed by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(RouteOverlayMode.Map) }

    fun setCollapsed(value: Boolean) {
        collapsed = value
        onCollapsedChanged(value)
    }

    if (collapsed) {
        Box(
            modifier = modifier
                .width(44.dp)
                .height(92.dp)
                .background(Color(18, 18, 18).copy(alpha = 0.94f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { setCollapsed(false) }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onMoveBy(dragAmount.y)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ">",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        return
    }

    var swipeOffset by remember { mutableStateOf(0f) }
    Column(
        modifier = modifier
            .width(276.dp)
            .height(190.dp)
            .background(Color(18, 18, 18).copy(alpha = 0.94f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
            .padding(12.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { setCollapsed(true) }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { swipeOffset = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        swipeOffset += dragAmount
                    },
                    onDragEnd = {
                        if (abs(swipeOffset) > 40f) {
                            mode = if (mode == RouteOverlayMode.Map) {
                                RouteOverlayMode.Elevation
                            } else {
                                RouteOverlayMode.Map
                            }
                        }
                        swipeOffset = 0f
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onMoveBy(dragAmount.y)
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Header(routeHudState, mode)

        if (mode == RouteOverlayMode.Map) {
            RoutePathPreview(routeHudState, Modifier.fillMaxWidth().height(84.dp))
        } else {
            ElevationPreview(routeHudState, Modifier.fillMaxWidth().height(84.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MapMetric("Left", formatDistance(routeHudState.remainingMiles, useMph))
            MapMetric("Grade", formatGrade(routeHudState.gradePercent))
            MapMetric("Elev", formatElevation(routeHudState.elevationMeters, useMph))
        }
    }
}

@Composable
private fun Header(
    routeHudState: RouteHudState,
    mode: RouteOverlayMode
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = routeHudState.routeName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = "${formatPercent(routeHudState.progressPercent)} complete",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp
            )
        }
        Text(
            text = if (mode == RouteOverlayMode.Map) "Map" else "Elev",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RoutePathPreview(
    routeHudState: RouteHudState,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val routePoints = routeHudState.points
        if (routePoints.size < 2) {
            return@Canvas
        }

        val padding = 7f
        val projected = projectGeoPoints(
            points = routePoints,
            width = size.width - padding * 2f,
            height = size.height - padding * 2f,
            offset = Offset(padding, padding)
        )
        drawPath(
            path = projected.toPath(),
            color = Color.White.copy(alpha = 0.22f),
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )

        val currentPoint = projectGeoPoint(routeHudState, routePoints, size.width - padding * 2f, size.height - padding * 2f, Offset(padding, padding))
        val progressPoints = routePoints
            .zip(projected)
            .takeWhile { (point, _) -> point.distanceFromStartMeters <= routeHudState.positionMeters }
            .map { it.second }
            .toMutableList()
        if (progressPoints.isEmpty()) {
            progressPoints.add(projected.first())
        }
        progressPoints.add(currentPoint)

        drawPath(
            path = progressPoints.toPath(),
            color = Color(80, 220, 160),
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )
        drawCircle(
            color = Color.White,
            radius = 6f,
            center = currentPoint
        )
    }
}

@Composable
private fun ElevationPreview(
    routeHudState: RouteHudState,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val points = routeHudState.points.filter { it.elevationMeters != null }
        if (points.size < 2) {
            return@Canvas
        }

        val padding = 7f
        val totalDistance = routeHudState.points.lastOrNull()?.distanceFromStartMeters?.coerceAtLeast(1.0) ?: 1.0
        val minElevation = points.minOf { it.elevationMeters ?: 0.0 }
        val maxElevation = points.maxOf { it.elevationMeters ?: 0.0 }
        val elevationRange = max(maxElevation - minElevation, 1.0)
        val width = size.width - padding * 2f
        val height = size.height - padding * 2f
        val offsets = points.map {
            Offset(
                x = padding + ((it.distanceFromStartMeters / totalDistance).toFloat() * width),
                y = padding + height - ((((it.elevationMeters ?: minElevation) - minElevation) / elevationRange).toFloat() * height)
            )
        }
        drawPath(
            path = offsets.toPath(),
            color = Color.White.copy(alpha = 0.25f),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
        val progressX = padding + ((routeHudState.positionMeters / totalDistance).coerceIn(0.0, 1.0).toFloat() * width)
        drawLine(
            color = Color(80, 220, 160),
            start = Offset(progressX, padding),
            end = Offset(progressX, padding + height),
            strokeWidth = 2f
        )
        val currentY = routeHudState.elevationMeters?.let {
            padding + height - (((it - minElevation) / elevationRange).toFloat() * height)
        } ?: (padding + height)
        drawCircle(
            color = Color.White,
            radius = 6f,
            center = Offset(progressX, currentY)
        )
    }
}

private fun projectGeoPoints(
    points: List<RoutePoint>,
    width: Float,
    height: Float,
    offset: Offset
): List<Offset> {
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }
    val latRange = max(maxLat - minLat, 0.000001)
    val lonRange = max(maxLon - minLon, 0.000001)

    return points.map {
        Offset(
            x = offset.x + (((it.longitude - minLon) / lonRange).toFloat() * width),
            y = offset.y + height - (((it.latitude - minLat) / latRange).toFloat() * height)
        )
    }
}

private fun projectGeoPoint(
    routeHudState: RouteHudState,
    points: List<RoutePoint>,
    width: Float,
    height: Float,
    offset: Offset
): Offset {
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }
    val latRange = max(maxLat - minLat, 0.000001)
    val lonRange = max(maxLon - minLon, 0.000001)
    return Offset(
        x = offset.x + (((routeHudState.longitude - minLon) / lonRange).toFloat() * width),
        y = offset.y + height - (((routeHudState.latitude - minLat) / latRange).toFloat() * height)
    )
}

private fun List<Offset>.toPath(): Path =
    Path().also { path ->
        forEachIndexed { index, offset ->
            if (index == 0) {
                path.moveTo(offset.x, offset.y)
            } else {
                path.lineTo(offset.x, offset.y)
            }
        }
    }

@Composable
private fun MapMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatPercent(value: Double): String =
    "${value.roundToInt().coerceIn(0, 100)}%"

private fun formatDistance(valueMiles: Double, useMph: Boolean): String =
    if (useMph) {
        "%.1f mi".format(valueMiles.coerceAtLeast(0.0))
    } else {
        "%.1f km".format((valueMiles * 1.609344).coerceAtLeast(0.0))
    }

private fun formatGrade(value: Double): String =
    "${"%.1f".format(value)}%"

private fun formatElevation(value: Double?, useMph: Boolean): String =
    value?.let {
        if (useMph) {
            "${(it * 3.28084).roundToInt()}ft"
        } else {
            "${it.roundToInt()}m"
        }
    } ?: "-"

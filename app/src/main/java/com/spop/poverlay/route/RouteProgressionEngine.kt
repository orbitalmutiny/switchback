package com.spop.poverlay.route

import kotlin.math.max
import kotlin.math.min

data class RouteProgress(
    val positionMeters: Double,
    val remainingMeters: Double,
    val progressPercent: Double,
    val segmentIndex: Int,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val gradePercent: Double,
    val upcomingPoints: List<RoutePoint>,
    val isComplete: Boolean
)

class RouteProgressionEngine {
    fun progressAt(
        route: ImportedRoute,
        distanceMeters: Double,
        windowAheadMeters: Double = 500.0
    ): RouteProgress {
        require(route.points.size >= 2) { "Route must contain at least two points" }

        val routeDistance = route.metadata.distanceMeters.coerceAtLeast(0.0)
        val position = distanceMeters.coerceIn(0.0, routeDistance)
        val segmentIndex = route.segmentIndexAt(position)
        val start = route.points[segmentIndex]
        val end = route.points[segmentIndex + 1]
        val segmentDistance = (end.distanceFromStartMeters - start.distanceFromStartMeters).coerceAtLeast(0.0)
        val segmentProgress = if (segmentDistance > 0.0) {
            ((position - start.distanceFromStartMeters) / segmentDistance).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val elevation = interpolateNullable(start.elevationMeters, end.elevationMeters, segmentProgress)
        val grade = gradePercent(start, end)
        val progressPercent = if (routeDistance > 0.0) {
            (position / routeDistance) * 100.0
        } else {
            0.0
        }

        return RouteProgress(
            positionMeters = position,
            remainingMeters = (routeDistance - position).coerceAtLeast(0.0),
            progressPercent = progressPercent.coerceIn(0.0, 100.0),
            segmentIndex = segmentIndex,
            latitude = interpolate(start.latitude, end.latitude, segmentProgress),
            longitude = interpolate(start.longitude, end.longitude, segmentProgress),
            elevationMeters = elevation,
            gradePercent = grade,
            upcomingPoints = route.windowFrom(position, windowAheadMeters),
            isComplete = position >= routeDistance
        )
    }

    private fun ImportedRoute.segmentIndexAt(positionMeters: Double): Int {
        val lastSegmentIndex = points.lastIndex - 1
        return points
            .zipWithNext()
            .indexOfFirst { (start, end) ->
                positionMeters >= start.distanceFromStartMeters &&
                        positionMeters <= end.distanceFromStartMeters
            }
            .takeIf { it >= 0 }
            ?: lastSegmentIndex
    }

    private fun ImportedRoute.windowFrom(
        positionMeters: Double,
        windowAheadMeters: Double
    ): List<RoutePoint> {
        val windowEnd = min(metadata.distanceMeters, positionMeters + windowAheadMeters.coerceAtLeast(0.0))
        return points.filter {
            it.distanceFromStartMeters >= positionMeters &&
                    it.distanceFromStartMeters <= windowEnd
        }
    }

    private fun gradePercent(start: RoutePoint, end: RoutePoint): Double {
        val startElevation = start.elevationMeters ?: return 0.0
        val endElevation = end.elevationMeters ?: return 0.0
        val distance = end.distanceFromStartMeters - start.distanceFromStartMeters
        if (distance <= 0.0) {
            return 0.0
        }
        return ((endElevation - startElevation) / distance) * 100.0
    }

    private fun interpolate(start: Double, end: Double, progress: Double): Double =
        start + (end - start) * progress.coerceIn(0.0, 1.0)

    private fun interpolateNullable(start: Double?, end: Double?, progress: Double): Double? {
        if (start == null || end == null) {
            return start ?: end
        }
        return interpolate(start, end, progress)
    }
}


package com.spop.poverlay.route

class RouteGradeSmoother(
    private val lookAheadMeters: Double = RouteResistancePreset.Default.lookAheadMeters
) {
    fun gradePercent(
        route: ImportedRoute,
        positionMeters: Double
    ): Double {
        val points = route.points
        if (points.size < 2) {
            return 0.0
        }

        val start = points.lastOrNull { it.distanceFromStartMeters <= positionMeters }
            ?: points.first()
        val endDistance = (positionMeters + lookAheadMeters)
            .coerceAtMost(route.metadata.distanceMeters)
        val end = points.firstOrNull { it.distanceFromStartMeters >= endDistance }
            ?: points.last()

        val startElevation = start.elevationMeters ?: return 0.0
        val endElevation = end.elevationMeters ?: return 0.0
        val distance = (end.distanceFromStartMeters - start.distanceFromStartMeters)
            .coerceAtLeast(1.0)
        return ((endElevation - startElevation) / distance) * 100.0
    }
}

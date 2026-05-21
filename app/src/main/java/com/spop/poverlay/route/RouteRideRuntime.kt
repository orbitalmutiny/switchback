package com.spop.poverlay.route

private const val MetersPerMile = 1609.344

sealed class RouteRideState {
    object Idle : RouteRideState()
    data class Active(
        val route: ImportedRoute,
        val progress: RouteProgress
    ) : RouteRideState()
    data class Completed(
        val route: ImportedRoute,
        val progress: RouteProgress
    ) : RouteRideState()
}

class RouteRideRuntime(
    private val progressionEngine: RouteProgressionEngine = RouteProgressionEngine()
) {
    var state: RouteRideState = RouteRideState.Idle
        private set

    val activeRoute: ImportedRoute?
        get() = when (val currentState = state) {
            is RouteRideState.Active -> currentState.route
            is RouteRideState.Completed -> currentState.route
            RouteRideState.Idle -> null
        }

    fun start(route: ImportedRoute): RouteProgress {
        return start(route, positionMeters = 0.0)
    }

    fun start(
        route: ImportedRoute,
        positionMeters: Double
    ): RouteProgress {
        val progress = progressionEngine.progressAt(route, distanceMeters = positionMeters)
        state = if (progress.isComplete) {
            RouteRideState.Completed(route, progress)
        } else {
            RouteRideState.Active(route, progress)
        }
        return progress
    }

    fun updateDistanceMeters(distanceMeters: Double): RouteProgress? {
        val route = activeRoute ?: return null
        val progress = progressionEngine.progressAt(route, distanceMeters)
        state = if (progress.isComplete) {
            RouteRideState.Completed(route, progress)
        } else {
            RouteRideState.Active(route, progress)
        }
        return progress
    }

    fun updateDistanceMiles(distanceMiles: Float): RouteProgress? =
        updateDistanceMeters(distanceMiles.toDouble() * MetersPerMile)

    fun reset() {
        state = RouteRideState.Idle
    }
}

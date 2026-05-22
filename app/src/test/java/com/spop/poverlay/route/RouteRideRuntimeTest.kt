package com.spop.poverlay.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRideRuntimeTest {
    @Test
    fun startsRouteAtBeginning() {
        val runtime = RouteRideRuntime()

        val progress = runtime.start(route())

        assertEquals(0.0, progress.positionMeters, 0.0001)
        assertTrue(runtime.state is RouteRideState.Active)
    }

    @Test
    fun startsRouteAtSavedPosition() {
        val runtime = RouteRideRuntime()

        val progress = runtime.start(route(), positionMeters = 50.0)

        assertEquals(50.0, progress.positionMeters, 0.0001)
        assertEquals(25.0, progress.progressPercent, 0.0001)
        assertTrue(runtime.state is RouteRideState.Active)
    }

    @Test
    fun ignoresDistanceUpdatesWhileIdle() {
        val runtime = RouteRideRuntime()

        assertNull(runtime.updateDistanceMeters(50.0))
        assertTrue(runtime.state is RouteRideState.Idle)
    }

    @Test
    fun updatesProgressFromDistanceMeters() {
        val runtime = RouteRideRuntime()
        runtime.start(route())

        val progress = runtime.updateDistanceMeters(50.0)

        assertEquals(50.0, progress?.positionMeters ?: 0.0, 0.0001)
        assertEquals(25.0, progress?.progressPercent ?: 0.0, 0.0001)
        assertTrue(runtime.state is RouteRideState.Active)
    }

    @Test
    fun completesWhenDistanceReachesRouteEnd() {
        val runtime = RouteRideRuntime()
        runtime.start(route())

        val progress = runtime.updateDistanceMeters(250.0)

        assertEquals(200.0, progress?.positionMeters ?: 0.0, 0.0001)
        assertTrue(progress?.isComplete == true)
        assertTrue(runtime.state is RouteRideState.Completed)
    }

    @Test
    fun updatesProgressFromDistanceMiles() {
        val runtime = RouteRideRuntime()
        runtime.start(route(distanceMeters = 1609.344))

        val progress = runtime.updateDistanceMiles(0.5f)

        assertEquals(804.672, progress?.positionMeters ?: 0.0, 0.01)
        assertEquals(50.0, progress?.progressPercent ?: 0.0, 0.01)
    }

    @Test
    fun resetClearsRoute() {
        val runtime = RouteRideRuntime()
        runtime.start(route())

        runtime.reset()

        assertTrue(runtime.state is RouteRideState.Idle)
        assertNull(runtime.activeRoute)
    }

    private fun route(distanceMeters: Double = 200.0): ImportedRoute {
        val points = listOf(
            RoutePoint(40.0, -105.0, 100.0, 0.0),
            RoutePoint(40.001, -105.0, 110.0, distanceMeters / 2.0),
            RoutePoint(40.002, -105.0, 100.0, distanceMeters)
        )
        return ImportedRoute(
            id = "test-route",
            name = "Test Route",
            points = points,
            metadata = RouteMetadata(
                distanceMeters = distanceMeters,
                totalClimbMeters = 10.0,
                maxGradePercent = 10.0,
                averageClimbingGradePercent = 5.0
            )
        )
    }
}

package com.spop.poverlay.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProgressionEngineTest {
    private val engine = RouteProgressionEngine()

    @Test
    fun clampsBeforeRouteStart() {
        val progress = engine.progressAt(route(), distanceMeters = -50.0)

        assertEquals(0.0, progress.positionMeters, 0.0001)
        assertEquals(0.0, progress.progressPercent, 0.0001)
        assertEquals(0, progress.segmentIndex)
        assertFalse(progress.isComplete)
    }

    @Test
    fun interpolatesPositionAndElevationWithinSegment() {
        val progress = engine.progressAt(route(), distanceMeters = 50.0)

        assertEquals(50.0, progress.positionMeters, 0.0001)
        assertEquals(25.0, progress.progressPercent, 0.0001)
        assertEquals(0, progress.segmentIndex)
        assertEquals(40.0005, progress.latitude, 0.0001)
        assertEquals(105.0, progress.elevationMeters ?: 0.0, 0.0001)
        assertEquals(10.0, progress.gradePercent, 0.0001)
        assertEquals(150.0, progress.remainingMeters, 0.0001)
    }

    @Test
    fun handlesDescentGrade() {
        val progress = engine.progressAt(route(), distanceMeters = 150.0)

        assertEquals(1, progress.segmentIndex)
        assertEquals(105.0, progress.elevationMeters ?: 0.0, 0.0001)
        assertEquals(-10.0, progress.gradePercent, 0.0001)
    }

    @Test
    fun clampsAfterRouteEnd() {
        val progress = engine.progressAt(route(), distanceMeters = 300.0)

        assertEquals(200.0, progress.positionMeters, 0.0001)
        assertEquals(0.0, progress.remainingMeters, 0.0001)
        assertEquals(100.0, progress.progressPercent, 0.0001)
        assertTrue(progress.isComplete)
    }

    @Test
    fun missingElevationProducesZeroGrade() {
        val progress = engine.progressAt(route(withElevation = false), distanceMeters = 50.0)

        assertEquals(0.0, progress.gradePercent, 0.0001)
        assertEquals(null, progress.elevationMeters)
    }

    @Test
    fun returnsUpcomingWindow() {
        val progress = engine.progressAt(route(), distanceMeters = 50.0, windowAheadMeters = 75.0)

        assertEquals(1, progress.upcomingPoints.size)
        assertEquals(100.0, progress.upcomingPoints.first().distanceFromStartMeters, 0.0001)
    }

    private fun route(withElevation: Boolean = true): ImportedRoute {
        val points = listOf(
            RoutePoint(40.0, -105.0, if (withElevation) 100.0 else null, 0.0),
            RoutePoint(40.001, -105.0, if (withElevation) 110.0 else null, 100.0),
            RoutePoint(40.002, -105.0, if (withElevation) 100.0 else null, 200.0)
        )
        return ImportedRoute(
            id = "test-route",
            name = "Test Route",
            points = points,
            metadata = RouteMetadata(
                distanceMeters = 200.0,
                totalClimbMeters = if (withElevation) 10.0 else 0.0,
                maxGradePercent = if (withElevation) 10.0 else 0.0,
                averageClimbingGradePercent = if (withElevation) 5.0 else 0.0
            )
        )
    }
}


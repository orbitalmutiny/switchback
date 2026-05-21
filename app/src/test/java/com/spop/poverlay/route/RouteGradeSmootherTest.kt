package com.spop.poverlay.route

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteGradeSmootherTest {
    @Test
    fun averagesGradeOverLookAheadWindow() {
        val route = ImportedRoute(
            id = "test",
            name = "Test",
            points = listOf(
                RoutePoint(0.0, 0.0, 100.0, 0.0),
                RoutePoint(0.0, 0.0, 120.0, 10.0),
                RoutePoint(0.0, 0.0, 110.0, 80.0)
            ),
            metadata = RouteMetadata(
                distanceMeters = 80.0,
                totalClimbMeters = 20.0,
                maxGradePercent = 200.0,
                averageClimbingGradePercent = 25.0
            )
        )

        assertEquals(12.5, RouteGradeSmoother(80.0).gradePercent(route, 0.0), 0.0001)
        assertEquals(12.5, RouteGradeSmoother().gradePercent(route, 0.0), 0.0001)
    }
}

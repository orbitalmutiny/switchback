package com.spop.poverlay.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxRouteParserTest {
    @Test
    fun parsesTrackPointsAndMetadata() {
        val route = GpxRouteParser().parse(
            id = "test-route",
            fallbackName = "Fallback",
            inputStream = sampleGpx().byteInputStream()
        )

        assertEquals("Sample Climb", route.name)
        assertEquals(3, route.points.size)
        assertEquals(40.0, route.points.first().latitude, 0.0001)
        assertEquals(-105.0, route.points.first().longitude, 0.0001)
        assertEquals(100.0, route.points.first().elevationMeters ?: 0.0, 0.0001)
        assertTrue(route.metadata.distanceMeters > 100.0)
        assertEquals(30.0, route.metadata.totalClimbMeters, 0.0001)
        assertTrue(route.metadata.maxGradePercent > 0.0)
        assertTrue(route.metadata.averageClimbingGradePercent > 0.0)
    }

    @Test
    fun fallsBackToRoutePoints() {
        val route = GpxRouteParser().parse(
            id = "route-points",
            fallbackName = "Route Points",
            inputStream = """
                <gpx>
                    <rte>
                        <rtept lat="40.0" lon="-105.0"><ele>100</ele></rtept>
                        <rtept lat="40.001" lon="-105.0"><ele>110</ele></rtept>
                    </rte>
                </gpx>
            """.trimIndent().byteInputStream()
        )

        assertEquals("Route Points", route.name)
        assertEquals(2, route.points.size)
        assertEquals(10.0, route.metadata.totalClimbMeters, 0.0001)
    }

    @Test
    fun parsesNamespacedRideWithGpsTrack() {
        val route = GpxRouteParser().parse(
            id = "ride-with-gps",
            fallbackName = "Ride With GPS",
            inputStream = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="http://ridewithgps.com/">
                    <metadata>
                        <name>Spring Out '26 - Main Ride featuring Hominy Creek Greenway</name>
                    </metadata>
                    <trk>
                        <name>Spring Out '26 - Main Ride featuring Hominy Creek Greenway</name>
                        <trkseg>
                            <trkpt lat="35.57734" lon="-82.56566"><ele>602.9</ele></trkpt>
                            <trkpt lat="35.5774" lon="-82.56549"><ele>603.1</ele></trkpt>
                            <trkpt lat="35.57741" lon="-82.56542"><ele>603.8</ele></trkpt>
                        </trkseg>
                    </trk>
                </gpx>
            """.trimIndent().byteInputStream()
        )

        assertEquals("Spring Out '26 - Main Ride featuring Hominy Creek Greenway", route.name)
        assertEquals(3, route.points.size)
        assertTrue(route.metadata.distanceMeters > 0.0)
    }

    @Test
    fun maxGradeIgnoresTinyElevationSpikes() {
        val route = GpxRouteParser().parse(
            id = "spiky",
            fallbackName = "Spiky",
            inputStream = """
                <gpx>
                    <trk>
                        <name>Spiky</name>
                        <trkseg>
                            <trkpt lat="40.00000" lon="-105.00000"><ele>100</ele></trkpt>
                            <trkpt lat="40.00001" lon="-105.00000"><ele>110</ele></trkpt>
                            <trkpt lat="40.00050" lon="-105.00000"><ele>112</ele></trkpt>
                            <trkpt lat="40.00100" lon="-105.00000"><ele>114</ele></trkpt>
                        </trkseg>
                    </trk>
                </gpx>
            """.trimIndent().byteInputStream()
        )

        assertTrue(route.metadata.maxGradePercent < 15.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRoutesWithFewerThanTwoPoints() {
        GpxRouteParser().parse(
            id = "bad",
            fallbackName = "Bad",
            inputStream = """
                <gpx>
                    <trk><trkseg><trkpt lat="40.0" lon="-105.0" /></trkseg></trk>
                </gpx>
            """.trimIndent().byteInputStream()
        )
    }

    private fun sampleGpx() = """
        <gpx version="1.1" creator="Switchback">
            <trk>
                <name>Sample Climb</name>
                <trkseg>
                    <trkpt lat="40.0" lon="-105.0"><ele>100</ele></trkpt>
                    <trkpt lat="40.001" lon="-105.0"><ele>115</ele></trkpt>
                    <trkpt lat="40.002" lon="-105.0"><ele>130</ele></trkpt>
                </trkseg>
            </trk>
        </gpx>
    """.trimIndent()
}

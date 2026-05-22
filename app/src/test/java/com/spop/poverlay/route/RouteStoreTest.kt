package com.spop.poverlay.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RouteStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun savesAndLoadsGpxRoutes() {
        val store = RouteStore(temporaryFolder.newFolder("routes"))

        val saved = store.saveGpx("Morning Climb", sampleGpx("Morning Climb"))
        val loaded = store.loadRoute(saved.id)

        assertEquals("morning-climb", saved.id)
        assertNotNull(loaded)
        assertEquals("Morning Climb", loaded?.name)
        assertEquals(2, loaded?.points?.size)
    }

    @Test
    fun keepsDuplicateNamesAsSeparateRoutes() {
        val store = RouteStore(temporaryFolder.newFolder("routes"))

        val first = store.saveGpx("Loop", sampleGpx("Loop"))
        val second = store.saveGpx("Loop", sampleGpx("Loop"))

        assertEquals("loop", first.id)
        assertEquals("loop-2", second.id)
        assertEquals(2, store.listRoutes().size)
    }

    @Test
    fun deletesRoutes() {
        val store = RouteStore(temporaryFolder.newFolder("routes"))
        val saved = store.saveGpx("Loop", sampleGpx("Loop"))

        assertEquals(true, store.deleteRoute(saved.id))
        assertEquals(null, store.loadRoute(saved.id))
        assertEquals(0, store.listRoutes().size)
    }

    private fun sampleGpx(name: String) = """
        <gpx>
            <trk>
                <name>$name</name>
                <trkseg>
                    <trkpt lat="40.0" lon="-105.0"><ele>100</ele></trkpt>
                    <trkpt lat="40.001" lon="-105.0"><ele>110</ele></trkpt>
                </trkseg>
            </trk>
        </gpx>
    """.trimIndent()
}

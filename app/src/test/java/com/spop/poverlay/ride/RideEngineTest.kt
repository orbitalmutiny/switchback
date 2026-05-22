package com.spop.poverlay.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideEngineTest {
    @Test
    fun startsFromIdle() {
        val rideEngine = RideEngine()

        assertEquals(RideState.Idle, rideEngine.state.value)
    }

    @Test
    fun startFromPreparingMovesToActive() {
        val rideEngine = RideEngine(clockMs = { 1000L })

        rideEngine.prepare()
        rideEngine.start()

        assertEquals(RideState.Active(startedAtMs = 1000L), rideEngine.state.value)
    }

    @Test
    fun pauseAndResumePreservesOriginalStartTime() {
        var now = 1000L
        val rideEngine = RideEngine(clockMs = { now })

        rideEngine.start()
        now = 2000L
        rideEngine.pause()
        now = 3000L
        rideEngine.start()

        assertEquals(RideState.Active(startedAtMs = 1000L), rideEngine.state.value)
    }

    @Test
    fun completeFromPausedPreservesOriginalStartTime() {
        var now = 1000L
        val rideEngine = RideEngine(clockMs = { now })

        rideEngine.start()
        now = 2000L
        rideEngine.pause()
        now = 4000L
        rideEngine.complete()

        assertEquals(
            RideState.Completed(startedAtMs = 1000L, completedAtMs = 4000L),
            rideEngine.state.value
        )
    }

    @Test
    fun failMovesToError() {
        val rideEngine = RideEngine()

        rideEngine.fail("sensor unavailable")

        assertTrue(rideEngine.state.value is RideState.Error)
    }
}

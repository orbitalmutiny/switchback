package com.spop.poverlay.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSessionManagerTest {
    @Test
    fun ignoresTelemetryWhileIdle() {
        val manager = RideSessionManager()

        val recorded = manager.onTelemetrySample(sample(timestampMs = 1000L))

        assertFalse(recorded)
        assertTrue(manager.samples.isEmpty())
    }

    @Test
    fun recordsTelemetryWhileActive() {
        val manager = RideSessionManager()
        val sample = sample(timestampMs = 1000L)

        manager.start()
        val recorded = manager.onTelemetrySample(sample)

        assertTrue(recorded)
        assertEquals(listOf(sample), manager.samples)
    }

    @Test
    fun ignoresTelemetryWhilePaused() {
        val manager = RideSessionManager()

        manager.start()
        manager.pause()
        val recorded = manager.onTelemetrySample(sample(timestampMs = 1000L))

        assertFalse(recorded)
        assertTrue(manager.samples.isEmpty())
    }

    @Test
    fun resetClearsSamplesAndReturnsToIdle() {
        val manager = RideSessionManager()

        manager.start()
        manager.onTelemetrySample(sample(timestampMs = 1000L))
        manager.reset()

        assertEquals(RideState.Idle, manager.state.value)
        assertTrue(manager.samples.isEmpty())
    }

    @Test
    fun completeStopsRecordingAdditionalSamples() {
        val manager = RideSessionManager()
        val first = sample(timestampMs = 1000L)

        manager.start()
        assertTrue(manager.onTelemetrySample(first))
        manager.complete()

        assertFalse(manager.onTelemetrySample(sample(timestampMs = 2000L)))
        assertEquals(listOf(first), manager.samples)
    }

    private fun sample(timestampMs: Long) = RideTelemetrySample(
        timestampMs = timestampMs,
        powerWatts = 125f,
        cadenceRpm = 85f,
        resistance = 42,
        speedMph = 19f,
        distanceMiles = 2f
    )
}

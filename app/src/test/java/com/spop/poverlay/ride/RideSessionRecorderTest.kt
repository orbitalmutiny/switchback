package com.spop.poverlay.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSessionRecorderTest {
    @Test
    fun recordsFirstSampleWhenRideIsActive() {
        val recorder = RideSessionRecorder()
        val sample = sample(timestampMs = 1000L)

        val recorded = recorder.recordIfDue(
            rideState = RideState.Active(startedAtMs = 0L),
            sample = sample
        )

        assertTrue(recorded)
        assertEquals(listOf(sample), recorder.samples)
    }

    @Test
    fun doesNotRecordWhenRideIsNotActive() {
        val recorder = RideSessionRecorder()

        val recorded = recorder.recordIfDue(
            rideState = RideState.Paused(startedAtMs = 0L, pausedAtMs = 1000L),
            sample = sample(timestampMs = 1000L)
        )

        assertFalse(recorded)
        assertTrue(recorder.samples.isEmpty())
    }

    @Test
    fun enforcesSampleInterval() {
        val recorder = RideSessionRecorder(sampleIntervalMs = 1000L)
        val rideState = RideState.Active(startedAtMs = 0L)
        val first = sample(timestampMs = 1000L)
        val tooSoon = sample(timestampMs = 1500L)
        val due = sample(timestampMs = 2000L)

        assertTrue(recorder.recordIfDue(rideState, first))
        assertFalse(recorder.recordIfDue(rideState, tooSoon))
        assertTrue(recorder.recordIfDue(rideState, due))

        assertEquals(listOf(first, due), recorder.samples)
    }

    @Test
    fun clearRemovesSamplesAndResetsInterval() {
        val recorder = RideSessionRecorder(sampleIntervalMs = 1000L)
        val rideState = RideState.Active(startedAtMs = 0L)

        assertTrue(recorder.recordIfDue(rideState, sample(timestampMs = 1000L)))
        recorder.clear()
        assertTrue(recorder.recordIfDue(rideState, sample(timestampMs = 1200L)))

        assertEquals(1, recorder.samples.size)
        assertEquals(1200L, recorder.samples.first().timestampMs)
    }

    private fun sample(timestampMs: Long) = RideTelemetrySample(
        timestampMs = timestampMs,
        powerWatts = 100f,
        cadenceRpm = 80f,
        resistance = 40,
        speedMph = 18f,
        distanceMiles = 1.5f
    )
}

package com.spop.poverlay.ride

import com.spop.poverlay.sensor.v2.BikeTelemetrySnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideAutoStartDetectorTest {
    @Test
    fun doesNotStartOnIdleTelemetry() {
        val detector = RideAutoStartDetector()

        assertFalse(detector.shouldStart(snapshot(cadenceRpm = 0f, powerWatts = 0f)))
        assertFalse(detector.shouldStart(snapshot(cadenceRpm = 0f, powerWatts = 0f)))
    }

    @Test
    fun startsAfterTwoConsecutiveCadenceSamples() {
        val detector = RideAutoStartDetector()

        assertFalse(detector.shouldStart(snapshot(cadenceRpm = 30f, powerWatts = 0f)))
        assertTrue(detector.shouldStart(snapshot(cadenceRpm = 31f, powerWatts = 0f)))
    }

    @Test
    fun startsAfterTwoConsecutivePowerSamples() {
        val detector = RideAutoStartDetector()

        assertFalse(detector.shouldStart(snapshot(cadenceRpm = 0f, powerWatts = 50f)))
        assertTrue(detector.shouldStart(snapshot(cadenceRpm = 0f, powerWatts = 55f)))
    }

    @Test
    fun idleSampleResetsActiveCount() {
        val detector = RideAutoStartDetector()

        assertFalse(detector.shouldStart(snapshot(cadenceRpm = 30f, powerWatts = 0f)))
        assertFalse(detector.shouldStart(snapshot(cadenceRpm = 0f, powerWatts = 0f)))
        assertFalse(detector.shouldStart(snapshot(cadenceRpm = 30f, powerWatts = 0f)))
    }

    @Test
    fun resetClearsActiveCount() {
        val detector = RideAutoStartDetector()

        assertFalse(detector.shouldStart(snapshot(cadenceRpm = 30f, powerWatts = 0f)))
        detector.reset()

        assertFalse(detector.shouldStart(snapshot(cadenceRpm = 31f, powerWatts = 0f)))
    }

    private fun snapshot(
        cadenceRpm: Float,
        powerWatts: Float
    ) = BikeTelemetrySnapshot(
        timestampMs = 1000L,
        cadenceRpm = cadenceRpm,
        powerWatts = powerWatts,
        currentResistance = 40,
        targetResistance = 40,
        powerZoneAutoFollowEnabled = false,
        powerZoneAutoFollowStatus = 0,
        powerZoneAutoFollowTargetResistance = 0f
    )
}

package com.spop.poverlay.ride

import org.junit.Assert.assertEquals
import org.junit.Test

class RideSessionRecordTest {
    @Test
    fun summaryAggregatesSamples() {
        val record = RideSessionRecord(
            id = "ride-1",
            name = "Route Ride",
            startedAtMs = 1000L,
            completedAtMs = 4000L,
            samples = listOf(
                sample(timestampMs = 1000L, powerWatts = 100f, cadenceRpm = 80f, distanceMiles = 0f, heartRateBpm = 140),
                sample(timestampMs = 2000L, powerWatts = 200f, cadenceRpm = 90f, distanceMiles = 0.2f, heartRateBpm = 150),
                sample(timestampMs = 3000L, powerWatts = 300f, cadenceRpm = 100f, distanceMiles = 0.4f, heartRateBpm = 160)
            )
        )

        val summary = record.summary

        assertEquals("ride-1", summary.id)
        assertEquals("Route Ride", summary.name)
        assertEquals(3000L, summary.durationMs)
        assertEquals(3, summary.sampleCount)
        assertEquals(200f, summary.averagePowerWatts)
        assertEquals(300f, summary.maxPowerWatts)
        assertEquals(90f, summary.averageCadenceRpm)
        assertEquals(0.4f, summary.distanceMiles)
        assertEquals(150, summary.averageHeartRateBpm)
        assertEquals(160, summary.maxHeartRateBpm)
        assertEquals(0.3f, summary.totalWorkKilojoules)
        assertEquals(0, summary.estimatedCalories)
    }

    @Test
    fun summaryDurationUsesSampleTimeSpanWhenStartTimeIsWallClock() {
        val record = RideSessionRecord(
            id = "ride-2",
            startedAtMs = 1_700_000_000_000L,
            completedAtMs = null,
            samples = listOf(
                sample(timestampMs = 10_000L, powerWatts = 100f, cadenceRpm = 80f, distanceMiles = 0f),
                sample(timestampMs = 70_000L, powerWatts = 100f, cadenceRpm = 80f, distanceMiles = 0.3f)
            )
        )

        assertEquals(60_000L, record.summary.durationMs)
    }

    private fun sample(
        timestampMs: Long,
        powerWatts: Float,
        cadenceRpm: Float,
        distanceMiles: Float,
        heartRateBpm: Int? = null
    ) = RideTelemetrySample(
        timestampMs = timestampMs,
        powerWatts = powerWatts,
        cadenceRpm = cadenceRpm,
        resistance = 40,
        speedMph = 18f,
        distanceMiles = distanceMiles,
        heartRateBpm = heartRateBpm
    )
}

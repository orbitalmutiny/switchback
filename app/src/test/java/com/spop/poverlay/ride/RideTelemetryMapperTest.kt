package com.spop.poverlay.ride

import com.spop.poverlay.sensor.v2.BikeTelemetrySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class RideTelemetryMapperTest {
    @Test
    fun mapsBikeTelemetryToRideSample() {
        val mapper = RideTelemetryMapper()
        val snapshot = snapshot(
            timestampMs = 1000L,
            powerWatts = 150f,
            cadenceRpm = 90f,
            targetResistance = 45
        )

        val sample = mapper.toRideTelemetrySample(snapshot, speedMph = 18f, heartRateBpm = 142)

        assertEquals(1000L, sample.timestampMs)
        assertEquals(150f, sample.powerWatts)
        assertEquals(90f, sample.cadenceRpm)
        assertEquals(45, sample.resistance)
        assertEquals(18f, sample.speedMph)
        assertEquals(0f, sample.distanceMiles)
        assertEquals(142, sample.heartRateBpm)
    }

    @Test
    fun accumulatesDistanceFromSpeedAndElapsedTime() {
        val mapper = RideTelemetryMapper()

        mapper.toRideTelemetrySample(snapshot(timestampMs = 0L), speedMph = 18f)
        val sample = mapper.toRideTelemetrySample(
            snapshot(timestampMs = 10 * 60 * 1000L),
            speedMph = 18f
        )

        assertEquals(3f, sample.distanceMiles, 0.0001f)
    }

    @Test
    fun mapsRoutePositionFromAccumulatedDistance() {
        val mapper = RideTelemetryMapper()

        mapper.toRideTelemetrySample(snapshot(timestampMs = 0L), speedMph = 18f)
        val sample = mapper.toRideTelemetrySample(
            snapshot(timestampMs = 10 * 60 * 1000L),
            speedMph = 18f,
            routePositionMetersForDistance = { distanceMiles -> distanceMiles * 1609.344f }
        )

        assertEquals(3f, sample.distanceMiles, 0.0001f)
        assertEquals(4828.032f, sample.routePositionMeters ?: 0f, 0.01f)
    }

    @Test
    fun resetClearsAccumulatedDistance() {
        val mapper = RideTelemetryMapper()

        mapper.toRideTelemetrySample(snapshot(timestampMs = 0L), speedMph = 18f)
        mapper.toRideTelemetrySample(snapshot(timestampMs = 10 * 60 * 1000L), speedMph = 18f)
        mapper.reset()
        val sample = mapper.toRideTelemetrySample(snapshot(timestampMs = 20 * 60 * 1000L), speedMph = 18f)

        assertEquals(0f, sample.distanceMiles)
    }

    private fun snapshot(
        timestampMs: Long,
        powerWatts: Float = 100f,
        cadenceRpm: Float = 80f,
        targetResistance: Int = 40
    ) = BikeTelemetrySnapshot(
        timestampMs = timestampMs,
        cadenceRpm = cadenceRpm,
        powerWatts = powerWatts,
        currentResistance = targetResistance,
        targetResistance = targetResistance,
        powerZoneAutoFollowEnabled = false,
        powerZoneAutoFollowStatus = 0,
        powerZoneAutoFollowTargetResistance = 0f
    )
}

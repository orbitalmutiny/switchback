package com.spop.poverlay.ride

import com.spop.poverlay.sensor.v2.BikeTelemetrySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSessionRuntimeTest {
    @Test
    fun idleTelemetryDoesNotAutoStartOrRecord() {
        val runtime = RideSessionRuntime()

        val recorded = runtime.onTelemetrySnapshot(
            snapshot = snapshot(timestampMs = 1000L, cadenceRpm = 0f, powerWatts = 0f),
            speedMph = 0f
        )

        assertFalse(recorded)
        assertEquals(RideState.Idle, runtime.state.value)
        assertTrue(runtime.samples.isEmpty())
    }

    @Test
    fun startsAndRecordsAfterTwoActiveTelemetrySnapshots() {
        val runtime = RideSessionRuntime()

        assertFalse(
            runtime.onTelemetrySnapshot(
                snapshot = snapshot(timestampMs = 1000L, cadenceRpm = 30f, powerWatts = 0f),
                speedMph = 10f
            )
        )
        assertTrue(
            runtime.onTelemetrySnapshot(
                snapshot = snapshot(timestampMs = 2000L, cadenceRpm = 31f, powerWatts = 0f),
                speedMph = 10f
            )
        )

        assertTrue(runtime.state.value is RideState.Active)
        assertEquals(1, runtime.samples.size)
        assertEquals(2000L, runtime.samples.first().timestampMs)
    }

    @Test
    fun activeRuntimeRecordsAtOneSecondInterval() {
        val runtime = RideSessionRuntime()

        runtime.start()
        assertTrue(runtime.onTelemetrySnapshot(snapshot(timestampMs = 1000L), speedMph = 12f))
        assertFalse(runtime.onTelemetrySnapshot(snapshot(timestampMs = 1500L), speedMph = 12f))
        assertTrue(runtime.onTelemetrySnapshot(snapshot(timestampMs = 2000L), speedMph = 12f))

        assertEquals(2, runtime.samples.size)
    }

    @Test
    fun pausedRuntimeDoesNotRecord() {
        val runtime = RideSessionRuntime()

        runtime.start()
        runtime.pause()
        val recorded = runtime.onTelemetrySnapshot(snapshot(timestampMs = 1000L), speedMph = 12f)

        assertFalse(recorded)
        assertTrue(runtime.samples.isEmpty())
    }

    @Test
    fun resetClearsSamplesDistanceAndAutoStartState() {
        val runtime = RideSessionRuntime()

        runtime.start()
        runtime.onTelemetrySnapshot(snapshot(timestampMs = 0L), speedMph = 18f)
        runtime.onTelemetrySnapshot(snapshot(timestampMs = 10 * 60 * 1000L), speedMph = 18f)
        runtime.reset()

        assertEquals(RideState.Idle, runtime.state.value)
        assertTrue(runtime.samples.isEmpty())

        assertFalse(
            runtime.onTelemetrySnapshot(
                snapshot = snapshot(timestampMs = 20 * 60 * 1000L, cadenceRpm = 30f),
                speedMph = 18f
            )
        )
    }

    @Test
    fun currentRecordContainsActiveSessionSamples() {
        val runtime = RideSessionRuntime()

        runtime.start()
        runtime.onTelemetrySnapshot(snapshot(timestampMs = 1000L), speedMph = 18f)

        val record = runtime.currentRecord()

        assertNotNull(record)
        assertTrue(record?.startedAtMs ?: 0L > 0L)
        assertEquals(1, record?.samples?.size)
        assertEquals(1000L, record?.samples?.first()?.timestampMs)
    }

    private fun snapshot(
        timestampMs: Long,
        cadenceRpm: Float = 80f,
        powerWatts: Float = 100f
    ) = BikeTelemetrySnapshot(
        timestampMs = timestampMs,
        cadenceRpm = cadenceRpm,
        powerWatts = powerWatts,
        currentResistance = 40,
        targetResistance = 40,
        powerZoneAutoFollowEnabled = false,
        powerZoneAutoFollowStatus = 0,
        powerZoneAutoFollowTargetResistance = 0f
    )
}

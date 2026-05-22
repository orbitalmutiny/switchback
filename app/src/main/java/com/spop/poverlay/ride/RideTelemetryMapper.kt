package com.spop.poverlay.ride

import com.spop.poverlay.sensor.v2.BikeTelemetrySnapshot

private const val MillisecondsPerHour = 3_600_000f

class RideDistanceAccumulator {
    private var lastTimestampMs: Long? = null
    private var distanceMiles = 0f

    fun update(snapshot: BikeTelemetrySnapshot, speedMph: Float): Float {
        val lastTimestampMs = lastTimestampMs
        if (lastTimestampMs != null) {
            val elapsedMs = (snapshot.timestampMs - lastTimestampMs).coerceAtLeast(0L)
            distanceMiles += speedMph * (elapsedMs / MillisecondsPerHour)
        }
        this.lastTimestampMs = snapshot.timestampMs
        return distanceMiles
    }

    fun reset() {
        lastTimestampMs = null
        distanceMiles = 0f
    }
}

class RideTelemetryMapper(
    private val distanceAccumulator: RideDistanceAccumulator = RideDistanceAccumulator()
) {
    fun toRideTelemetrySample(
        snapshot: BikeTelemetrySnapshot,
        speedMph: Float,
        heartRateBpm: Int? = null,
        routePositionMetersForDistance: ((Float) -> Float?)? = null
    ): RideTelemetrySample {
        val distanceMiles = distanceAccumulator.update(snapshot, speedMph)
        return RideTelemetrySample(
            timestampMs = snapshot.timestampMs,
            powerWatts = snapshot.powerWatts,
            cadenceRpm = snapshot.cadenceRpm,
            resistance = snapshot.targetResistance,
            speedMph = speedMph,
            distanceMiles = distanceMiles,
            heartRateBpm = heartRateBpm,
            routePositionMeters = routePositionMetersForDistance?.invoke(distanceMiles)
        )
    }

    fun reset() {
        distanceAccumulator.reset()
    }
}

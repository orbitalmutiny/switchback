package com.spop.poverlay.ride

data class RideTelemetrySample(
    val timestampMs: Long,
    val powerWatts: Float,
    val cadenceRpm: Float,
    val resistance: Int,
    val speedMph: Float,
    val distanceMiles: Float,
    val heartRateBpm: Int? = null,
    val routePositionMeters: Float? = null
)

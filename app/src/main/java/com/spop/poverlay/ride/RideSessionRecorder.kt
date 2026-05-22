package com.spop.poverlay.ride

class RideSessionRecorder(
    private val sampleIntervalMs: Long = 1000L
) {
    private val mutableSamples = mutableListOf<RideTelemetrySample>()
    val samples: List<RideTelemetrySample>
        get() = mutableSamples.toList()

    private var lastRecordedAtMs: Long? = null

    fun recordIfDue(
        rideState: RideState,
        sample: RideTelemetrySample
    ): Boolean {
        if (rideState !is RideState.Active) {
            return false
        }

        val lastRecordedAtMs = lastRecordedAtMs
        if (lastRecordedAtMs != null &&
            sample.timestampMs - lastRecordedAtMs < sampleIntervalMs
        ) {
            return false
        }

        mutableSamples.add(sample)
        this.lastRecordedAtMs = sample.timestampMs
        return true
    }

    fun clear() {
        mutableSamples.clear()
        lastRecordedAtMs = null
    }
}

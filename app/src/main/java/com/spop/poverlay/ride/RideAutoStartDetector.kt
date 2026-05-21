package com.spop.poverlay.ride

import com.spop.poverlay.sensor.v2.BikeTelemetrySnapshot

class RideAutoStartDetector(
    private val activeSampleThreshold: Int = 2
) {
    private var consecutiveActiveSamples = 0

    fun shouldStart(snapshot: BikeTelemetrySnapshot): Boolean {
        if (snapshot.isActiveRideSignal()) {
            consecutiveActiveSamples += 1
        } else {
            consecutiveActiveSamples = 0
        }

        return consecutiveActiveSamples >= activeSampleThreshold
    }

    fun reset() {
        consecutiveActiveSamples = 0
    }

    private fun BikeTelemetrySnapshot.isActiveRideSignal() =
        cadenceRpm > 0f || powerWatts > 0f
}

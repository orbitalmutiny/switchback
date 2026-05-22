package com.spop.poverlay.ride

import kotlinx.coroutines.flow.StateFlow

class RideSessionManager(
    private val rideEngine: RideEngine = RideEngine(),
    private val rideSessionRecorder: RideSessionRecorder = RideSessionRecorder()
) {
    val state: StateFlow<RideState>
        get() = rideEngine.state

    val samples: List<RideTelemetrySample>
        get() = rideSessionRecorder.samples

    fun prepare() {
        rideEngine.prepare()
    }

    fun start() {
        rideEngine.start()
    }

    fun pause() {
        rideEngine.pause()
    }

    fun complete() {
        rideEngine.complete()
    }

    fun fail(message: String, cause: Throwable? = null) {
        rideEngine.fail(message, cause)
    }

    fun reset() {
        rideEngine.reset()
        rideSessionRecorder.clear()
    }

    fun onTelemetrySample(sample: RideTelemetrySample): Boolean =
        rideSessionRecorder.recordIfDue(state.value, sample)
}

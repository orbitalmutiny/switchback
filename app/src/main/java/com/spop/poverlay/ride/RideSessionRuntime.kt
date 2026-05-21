package com.spop.poverlay.ride

import com.spop.poverlay.sensor.v2.BikeTelemetrySnapshot
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class RideSessionRuntime(
    private val rideSessionManager: RideSessionManager = RideSessionManager(),
    private val rideTelemetryMapper: RideTelemetryMapper = RideTelemetryMapper(),
    private val rideAutoStartDetector: RideAutoStartDetector = RideAutoStartDetector()
) {
    private var sessionId = UUID.randomUUID().toString()

    val state: StateFlow<RideState>
        get() = rideSessionManager.state

    val samples: List<RideTelemetrySample>
        get() = rideSessionManager.samples

    fun onTelemetrySnapshot(
        snapshot: BikeTelemetrySnapshot,
        speedMph: Float,
        heartRateBpm: Int? = null,
        routePositionMetersForDistance: ((Float) -> Float?)? = null
    ): Boolean {
        if (state.value == RideState.Idle &&
            rideAutoStartDetector.shouldStart(snapshot)
        ) {
            rideSessionManager.start()
        }

        val sample = rideTelemetryMapper.toRideTelemetrySample(
            snapshot = snapshot,
            speedMph = speedMph,
            heartRateBpm = heartRateBpm,
            routePositionMetersForDistance = routePositionMetersForDistance
        )
        return rideSessionManager.onTelemetrySample(sample)
    }

    fun currentRecord(name: String? = null): RideSessionRecord? {
        val currentState = state.value
        val startedAtMs = when (currentState) {
            is RideState.Active -> currentState.startedAtMs
            is RideState.Paused -> currentState.startedAtMs
            is RideState.Completed -> currentState.startedAtMs
            RideState.Idle,
            RideState.Preparing,
            is RideState.Error -> return null
        }
        return RideSessionRecord(
            id = sessionId,
            name = name,
            startedAtMs = startedAtMs,
            completedAtMs = (currentState as? RideState.Completed)?.completedAtMs,
            samples = samples
        )
    }

    fun prepare() {
        rideSessionManager.prepare()
    }

    fun start() {
        rideSessionManager.start()
    }

    fun pause() {
        rideSessionManager.pause()
    }

    fun complete() {
        rideSessionManager.complete()
    }

    fun fail(message: String, cause: Throwable? = null) {
        rideSessionManager.fail(message, cause)
    }

    fun reset() {
        rideSessionManager.reset()
        rideTelemetryMapper.reset()
        rideAutoStartDetector.reset()
        sessionId = UUID.randomUUID().toString()
    }
}

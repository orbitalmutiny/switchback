package com.spop.poverlay.ride

sealed class RideState {
    object Idle : RideState()
    object Preparing : RideState()
    data class Active(val startedAtMs: Long) : RideState()
    data class Paused(val startedAtMs: Long, val pausedAtMs: Long) : RideState()
    data class Completed(val startedAtMs: Long, val completedAtMs: Long) : RideState()
    data class Error(val message: String, val cause: Throwable? = null) : RideState()
}

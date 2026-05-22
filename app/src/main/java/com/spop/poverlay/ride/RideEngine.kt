package com.spop.poverlay.ride

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RideEngine(
    initialState: RideState = RideState.Idle,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<RideState> = mutableState.asStateFlow()

    fun prepare() {
        when (state.value) {
            RideState.Idle,
            is RideState.Completed,
            is RideState.Error -> mutableState.value = RideState.Preparing
            RideState.Preparing,
            is RideState.Active,
            is RideState.Paused -> Unit
        }
    }

    fun start() {
        when (val currentState = state.value) {
            RideState.Idle,
            RideState.Preparing -> mutableState.value = RideState.Active(clockMs())
            is RideState.Paused -> mutableState.value = RideState.Active(currentState.startedAtMs)
            is RideState.Active,
            is RideState.Completed,
            is RideState.Error -> Unit
        }
    }

    fun pause() {
        val currentState = state.value
        if (currentState is RideState.Active) {
            mutableState.value = RideState.Paused(
                startedAtMs = currentState.startedAtMs,
                pausedAtMs = clockMs()
            )
        }
    }

    fun complete() {
        when (val currentState = state.value) {
            is RideState.Active -> mutableState.value = RideState.Completed(
                startedAtMs = currentState.startedAtMs,
                completedAtMs = clockMs()
            )
            is RideState.Paused -> mutableState.value = RideState.Completed(
                startedAtMs = currentState.startedAtMs,
                completedAtMs = clockMs()
            )
            RideState.Idle,
            RideState.Preparing,
            is RideState.Completed,
            is RideState.Error -> Unit
        }
    }

    fun fail(message: String, cause: Throwable? = null) {
        mutableState.value = RideState.Error(message, cause)
    }

    fun reset() {
        mutableState.value = RideState.Idle
    }
}

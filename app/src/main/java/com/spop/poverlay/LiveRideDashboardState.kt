package com.spop.poverlay

import com.spop.poverlay.route.ManualResistanceGuidanceState
import com.spop.poverlay.route.RouteHudState

data class LiveRideDashboardState(
    val powerWatts: Float = 0f,
    val cadenceRpm: Float = 0f,
    val resistance: Int = 0,
    val speedMph: Float = 0f,
    val distanceMiles: Float = 0f,
    val elapsedSeconds: Long = 0L,
    val workKilojoules: Float = 0f,
    val heartRateBpm: Int? = null,
    val routeHudState: RouteHudState? = null,
    /** Kept for backward compatibility; set to null going forward. UI uses [guidanceState]. */
    val visualResistanceCue: String? = null,
    /** Typed manual resistance guidance state for non-Bike+ rides. Null when inactive. */
    val guidanceState: ManualResistanceGuidanceState? = null
)

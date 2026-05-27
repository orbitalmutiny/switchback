package com.spop.poverlay.route

data class RouteHudState(
    val routeName: String,
    val progressPercent: Double,
    val positionMeters: Double,
    val remainingMiles: Double,
    val gradePercent: Double,
    val rawGradePercent: Double,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val points: List<RoutePoint>,
    val isComplete: Boolean,
    /** Kept for backward compatibility; set to null going forward. UI uses [guidanceState]. */
    val visualResistanceCue: String? = null,
    /** Typed manual resistance guidance state for non-Bike+ rides. Null when inactive. */
    val guidanceState: ManualResistanceGuidanceState? = null
)

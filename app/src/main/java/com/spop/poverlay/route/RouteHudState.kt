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
    val visualResistanceCue: String? = null
)

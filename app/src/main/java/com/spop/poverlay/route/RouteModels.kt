package com.spop.poverlay.route

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val distanceFromStartMeters: Double
)

data class RouteMetadata(
    val distanceMeters: Double,
    val totalClimbMeters: Double,
    val maxGradePercent: Double,
    val averageClimbingGradePercent: Double
)

data class ImportedRoute(
    val id: String,
    val name: String,
    val points: List<RoutePoint>,
    val metadata: RouteMetadata
)


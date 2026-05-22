package com.spop.poverlay.route

enum class RouteResistancePreset(
    val id: String,
    val label: String,
    val baselineResistanceFloor: Int,
    val lookAheadMeters: Double,
    val uphillResistancePerGrade: Double,
    val downhillResistancePerGrade: Double,
    val maxStepPerWrite: Int,
    val minWriteIntervalMs: Long
) {
    Gentle(
        id = "gentle",
        label = "Gentle",
        baselineResistanceFloor = 15,
        lookAheadMeters = 220.0,
        uphillResistancePerGrade = 1.0,
        downhillResistancePerGrade = 0.5,
        maxStepPerWrite = 1,
        minWriteIntervalMs = 8_000L
    ),
    Standard(
        id = "standard",
        label = "Standard",
        baselineResistanceFloor = 20,
        lookAheadMeters = 180.0,
        uphillResistancePerGrade = 1.5,
        downhillResistancePerGrade = 0.75,
        maxStepPerWrite = 2,
        minWriteIntervalMs = 6_000L
    ),
    Strong(
        id = "strong",
        label = "Strong",
        baselineResistanceFloor = 25,
        lookAheadMeters = 140.0,
        uphillResistancePerGrade = 2.0,
        downhillResistancePerGrade = 1.0,
        maxStepPerWrite = 10,
        minWriteIntervalMs = 4_000L
    );

    companion object {
        val Default = Standard

        fun fromId(id: String?): RouteResistancePreset =
            values().firstOrNull { it.id == id } ?: Default
    }
}

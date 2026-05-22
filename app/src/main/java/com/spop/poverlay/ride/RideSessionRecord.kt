package com.spop.poverlay.ride

data class RideSessionRecord(
    val id: String,
    val name: String? = null,
    val startedAtMs: Long,
    val completedAtMs: Long?,
    val samples: List<RideTelemetrySample>
) {
    val summary: RideSessionSummary
        get() {
            val durationMs = if (completedAtMs != null) {
                completedAtMs - startedAtMs
            } else if (samples.size >= 2) {
                (samples.last().timestampMs - samples.first().timestampMs).coerceAtLeast(0L)
            } else {
                0L
            }
            return RideSessionSummary(
                id = id,
                name = name,
                startedAtMs = startedAtMs,
                completedAtMs = completedAtMs,
                durationMs = durationMs.coerceAtLeast(0L),
                sampleCount = samples.size,
                averagePowerWatts = samples.map { it.powerWatts }.averageOrZero(),
                maxPowerWatts = samples.maxOfOrNull { it.powerWatts } ?: 0f,
                averageCadenceRpm = samples.map { it.cadenceRpm }.averageOrZero(),
                distanceMiles = samples.lastOrNull()?.distanceMiles ?: 0f,
                averageHeartRateBpm = samples.mapNotNull { it.heartRateBpm }.averageIntOrNull(),
                maxHeartRateBpm = samples.mapNotNull { it.heartRateBpm }.maxOrNull(),
                totalWorkKilojoules = samples.totalWorkKilojoules(),
                estimatedCalories = samples.totalWorkKilojoules().toInt()
            )
        }
}

data class RideSessionSummary(
    val id: String,
    val name: String? = null,
    val startedAtMs: Long,
    val completedAtMs: Long?,
    val durationMs: Long,
    val sampleCount: Int,
    val averagePowerWatts: Float,
    val maxPowerWatts: Float,
    val averageCadenceRpm: Float,
    val distanceMiles: Float,
    val averageHeartRateBpm: Int?,
    val maxHeartRateBpm: Int?,
    val totalWorkKilojoules: Float,
    val estimatedCalories: Int
)

private fun List<Float>.averageOrZero(): Float =
    if (isEmpty()) {
        0f
    } else {
        average().toFloat()
    }

private fun List<Int>.averageIntOrNull(): Int? =
    if (isEmpty()) {
        null
    } else {
        average().toInt()
    }

private fun List<RideTelemetrySample>.totalWorkKilojoules(): Float {
    if (size < 2) {
        return 0f
    }

    return zipWithNext().sumOf { (previous, current) ->
        val elapsedSeconds = ((current.timestampMs - previous.timestampMs).coerceAtLeast(0L)) / 1000.0
        (previous.powerWatts * elapsedSeconds / 1000.0)
    }.toFloat()
}

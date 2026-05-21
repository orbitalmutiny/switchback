package com.spop.poverlay.ride

private const val DayMs = 24L * 60L * 60L * 1000L

data class RideTotals(
    val rideCount: Int,
    val totalDistanceMiles: Float,
    val totalDurationMs: Long,
    val totalCalories: Int,
    val totalWorkKilojoules: Float,
    val averagePowerWatts: Float,
    val averageCadenceRpm: Float
)

enum class RideTotalsPeriod(
    val label: String,
    val days: Int?
) {
    AllTime("All", null),
    Last90("90d", 90),
    Last60("60d", 60),
    Last30("30d", 30),
    Last14("14d", 14),
    Last7("7d", 7),
    Last3("3d", 3)
}

fun availableRideTotalsPeriods(
    summaries: List<RideSessionSummary>,
    nowMs: Long = System.currentTimeMillis()
): List<RideTotalsPeriod> =
    RideTotalsPeriod.values().filter { period ->
        summaries.filterForPeriod(period, nowMs).isNotEmpty()
    }

fun rideTotalsForPeriod(
    summaries: List<RideSessionSummary>,
    period: RideTotalsPeriod,
    nowMs: Long = System.currentTimeMillis()
): RideTotals =
    summaries.filterForPeriod(period, nowMs).toRideTotals()

private fun List<RideSessionSummary>.filterForPeriod(
    period: RideTotalsPeriod,
    nowMs: Long
): List<RideSessionSummary> {
    val days = period.days ?: return this
    val cutoffMs = nowMs - (days * DayMs)
    return filter { it.startedAtMs >= cutoffMs }
}

private fun List<RideSessionSummary>.toRideTotals(): RideTotals {
    if (isEmpty()) {
        return RideTotals(
            rideCount = 0,
            totalDistanceMiles = 0f,
            totalDurationMs = 0L,
            totalCalories = 0,
            totalWorkKilojoules = 0f,
            averagePowerWatts = 0f,
            averageCadenceRpm = 0f
        )
    }

    return RideTotals(
        rideCount = size,
        totalDistanceMiles = sumOf { it.distanceMiles.toDouble() }.toFloat(),
        totalDurationMs = sumOf { it.durationMs },
        totalCalories = sumOf { it.estimatedCalories },
        totalWorkKilojoules = sumOf { it.totalWorkKilojoules.toDouble() }.toFloat(),
        averagePowerWatts = map { it.averagePowerWatts }.average().toFloat(),
        averageCadenceRpm = map { it.averageCadenceRpm }.average().toFloat()
    )
}

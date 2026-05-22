package com.spop.poverlay.ride

import org.junit.Assert.assertEquals
import org.junit.Test

class RideTotalsTest {
    @Test
    fun availablePeriodsOnlyIncludeWindowsWithData() {
        val now = 100L * DayMs
        val summaries = listOf(
            summary(id = "older", startedAtMs = now - 40L * DayMs)
        )

        val periods = availableRideTotalsPeriods(summaries, now)

        assertEquals(
            listOf(
                RideTotalsPeriod.AllTime,
                RideTotalsPeriod.Last90,
                RideTotalsPeriod.Last60
            ),
            periods
        )
    }

    @Test
    fun periodTotalsUseOnlyRidesInsideWindow() {
        val now = 100L * DayMs
        val summaries = listOf(
            summary(id = "recent", startedAtMs = now - 2L * DayMs, distanceMiles = 10f, calories = 100),
            summary(id = "older", startedAtMs = now - 40L * DayMs, distanceMiles = 20f, calories = 200)
        )

        val totals = rideTotalsForPeriod(summaries, RideTotalsPeriod.Last3, now)

        assertEquals(1, totals.rideCount)
        assertEquals(10f, totals.totalDistanceMiles)
        assertEquals(100, totals.totalCalories)
    }

    private fun summary(
        id: String,
        startedAtMs: Long,
        distanceMiles: Float = 1f,
        calories: Int = 10
    ) = RideSessionSummary(
        id = id,
        startedAtMs = startedAtMs,
        completedAtMs = null,
        durationMs = 60_000L,
        sampleCount = 60,
        averagePowerWatts = 100f,
        maxPowerWatts = 200f,
        averageCadenceRpm = 80f,
        distanceMiles = distanceMiles,
        averageHeartRateBpm = null,
        maxHeartRateBpm = null,
        totalWorkKilojoules = calories.toFloat(),
        estimatedCalories = calories
    )

    private companion object {
        private const val DayMs = 24L * 60L * 60L * 1000L
    }
}

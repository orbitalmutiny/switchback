package com.spop.poverlay.route

import kotlin.math.roundToInt

private const val MinResistance = 0
private const val MaxResistance = 100

class GradeResistanceMapper(
    private val preset: RouteResistancePreset = RouteResistancePreset.Default
) {
    fun targetResistance(
        baselineResistance: Int,
        gradePercent: Double,
        previousRequestedResistance: Int? = null
    ): Int {
        val effectiveBaseline = baselineResistance
            .coerceAtLeast(preset.baselineResistanceFloor)
            .coerceIn(MinResistance, MaxResistance)
        val adjustment = if (gradePercent >= 0.0) {
            gradePercent * preset.uphillResistancePerGrade
        } else {
            gradePercent * preset.downhillResistancePerGrade
        }
        val rawTarget = (effectiveBaseline + adjustment.roundToInt())
            .coerceAtLeast(preset.baselineResistanceFloor)
            .coerceIn(MinResistance, MaxResistance)

        val previous = (previousRequestedResistance ?: effectiveBaseline)
            .coerceAtLeast(preset.baselineResistanceFloor)
        return rawTarget
            .coerceIn(previous - preset.maxStepPerWrite, previous + preset.maxStepPerWrite)
            .coerceIn(MinResistance, MaxResistance)
    }

    fun baselineForTargetResistance(
        targetResistance: Int,
        gradePercent: Double
    ): Int {
        val adjustment = if (gradePercent >= 0.0) {
            gradePercent * preset.uphillResistancePerGrade
        } else {
            gradePercent * preset.downhillResistancePerGrade
        }
        return (targetResistance - adjustment.roundToInt())
            .coerceAtLeast(preset.baselineResistanceFloor)
            .coerceIn(MinResistance, MaxResistance)
    }
}

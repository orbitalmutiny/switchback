package com.spop.poverlay.route

import kotlin.math.roundToInt

private const val MinResistance = 0
private const val MaxResistance = 100
private const val DownhillStepMultiplier = 2

class GradeResistanceMapper(
    private val preset: RouteResistancePreset = RouteResistancePreset.Default
) {
    fun targetResistance(
        baselineResistance: Int,
        gradePercent: Double,
        previousRequestedResistance: Int? = null
    ): Int {
        val isDownhill = gradePercent < 0.0
        val effectiveBaseline = if (isDownhill) {
            minOf(baselineResistance, previousRequestedResistance ?: baselineResistance)
                .coerceIn(MinResistance, MaxResistance)
        } else {
            baselineResistance
                .coerceAtLeast(preset.baselineResistanceFloor)
                .coerceIn(MinResistance, MaxResistance)
        }
        val adjustment = if (gradePercent >= 0.0) {
            gradePercent * preset.uphillResistancePerGrade
        } else {
            gradePercent * preset.downhillResistancePerGrade
        }
        val adjustedTarget = effectiveBaseline + adjustment.roundToInt()
        val rawTarget = if (isDownhill) {
            adjustedTarget
        } else {
            adjustedTarget.coerceAtLeast(preset.baselineResistanceFloor)
        }
            .coerceIn(MinResistance, MaxResistance)

        val previous = if (isDownhill) {
            (previousRequestedResistance ?: effectiveBaseline).coerceIn(MinResistance, MaxResistance)
        } else {
            (previousRequestedResistance ?: effectiveBaseline)
                .coerceAtLeast(preset.baselineResistanceFloor)
                .coerceIn(MinResistance, MaxResistance)
        }
        val maxStep = if (isDownhill) {
            preset.maxStepPerWrite * DownhillStepMultiplier
        } else {
            preset.maxStepPerWrite
        }
        val rateLimitedTarget = rawTarget
            .coerceIn(previous - maxStep, previous + maxStep)
            .coerceIn(MinResistance, MaxResistance)
        return if (isDownhill) {
            val result = rateLimitedTarget.coerceAtMost(previous)
            if (previous >= preset.baselineResistanceFloor) {
                result.coerceAtLeast(preset.baselineResistanceFloor)
            } else {
                result
            }
        } else {
            rateLimitedTarget
        }
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
        val baseline = targetResistance - adjustment.roundToInt()
        return if (gradePercent < 0.0) {
            baseline
        } else {
            baseline.coerceAtLeast(preset.baselineResistanceFloor)
        }.coerceIn(MinResistance, MaxResistance)
    }
}

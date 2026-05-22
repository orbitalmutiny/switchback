package com.spop.poverlay.route

import org.junit.Assert.assertEquals
import org.junit.Test

class GradeResistanceMapperTest {
    private val mapper = GradeResistanceMapper()

    @Test
    fun increasesResistanceForPositiveGrade() {
        assertEquals(42, mapper.targetResistance(baselineResistance = 40, gradePercent = 5.0))
    }

    @Test
    fun decreasesResistanceForNegativeGrade() {
        assertEquals(38, mapper.targetResistance(baselineResistance = 40, gradePercent = -5.0))
    }

    @Test
    fun limitsEachWriteStep() {
        assertEquals(44, mapper.targetResistance(baselineResistance = 40, gradePercent = 10.0, previousRequestedResistance = 42))
    }

    @Test
    fun strongPresetAllowsLargerSteps() {
        val strongMapper = GradeResistanceMapper(RouteResistancePreset.Strong)

        assertEquals(52, strongMapper.targetResistance(baselineResistance = 40, gradePercent = 10.0, previousRequestedResistance = 42))
    }

    @Test
    fun appliesPresetFloorForFlatAndDownhillGrades() {
        assertEquals(15, GradeResistanceMapper(RouteResistancePreset.Gentle).targetResistance(baselineResistance = 5, gradePercent = 0.0))
        assertEquals(20, GradeResistanceMapper(RouteResistancePreset.Standard).targetResistance(baselineResistance = 5, gradePercent = -5.0))
        assertEquals(25, GradeResistanceMapper(RouteResistancePreset.Strong).targetResistance(baselineResistance = 5, gradePercent = -5.0))
    }

    @Test
    fun strongPresetUsesHigherGradeTargetFromFloor() {
        assertEquals(
            35,
            GradeResistanceMapper(RouteResistancePreset.Strong)
                .targetResistance(baselineResistance = 5, gradePercent = 5.0)
        )
    }

    @Test
    fun estimatesBaselineThatKeepsManualTargetAtCurrentGrade() {
        assertEquals(
            25,
            GradeResistanceMapper(RouteResistancePreset.Strong)
                .baselineForTargetResistance(targetResistance = 35, gradePercent = 5.0)
        )
    }

    @Test
    fun baselineEstimateUsesPresetFloor() {
        assertEquals(
            25,
            GradeResistanceMapper(RouteResistancePreset.Strong)
                .baselineForTargetResistance(targetResistance = 20, gradePercent = 5.0)
        )
    }

    @Test
    fun clampsToResistanceRange() {
        assertEquals(100, mapper.targetResistance(baselineResistance = 100, gradePercent = 10.0, previousRequestedResistance = 99))
        assertEquals(20, mapper.targetResistance(baselineResistance = 0, gradePercent = -10.0, previousRequestedResistance = 1))
    }
}

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
        assertEquals(36, mapper.targetResistance(baselineResistance = 40, gradePercent = -5.0))
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
    fun appliesPresetFloorForFlatAndUphillGrades() {
        assertEquals(15, GradeResistanceMapper(RouteResistancePreset.Gentle).targetResistance(baselineResistance = 5, gradePercent = 0.0))
        assertEquals(20, GradeResistanceMapper(RouteResistancePreset.Standard).targetResistance(baselineResistance = 5, gradePercent = 0.0))
        assertEquals(35, GradeResistanceMapper(RouteResistancePreset.Strong).targetResistance(baselineResistance = 5, gradePercent = 5.0))
    }

    @Test
    fun downhillDoesNotIncreaseResistanceToPresetFloor() {
        assertEquals(3, GradeResistanceMapper(RouteResistancePreset.Gentle).targetResistance(baselineResistance = 5, gradePercent = -5.0))
        assertEquals(1, GradeResistanceMapper(RouteResistancePreset.Standard).targetResistance(baselineResistance = 5, gradePercent = -5.0))
        assertEquals(0, GradeResistanceMapper(RouteResistancePreset.Strong).targetResistance(baselineResistance = 5, gradePercent = -5.0))
        assertEquals(10, GradeResistanceMapper(RouteResistancePreset.Strong).targetResistance(baselineResistance = 30, gradePercent = -5.0, previousRequestedResistance = 10))
    }

    @Test
    fun downhillAllowsFasterDropThanUphillStepLimit() {
        assertEquals(
            36,
            GradeResistanceMapper(RouteResistancePreset.Standard)
                .targetResistance(baselineResistance = 50, gradePercent = -8.0, previousRequestedResistance = 40)
        )
        assertEquals(
            30,
            GradeResistanceMapper(RouteResistancePreset.Strong)
                .targetResistance(baselineResistance = 50, gradePercent = -8.0, previousRequestedResistance = 50)
        )
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
        assertEquals(0, mapper.targetResistance(baselineResistance = 0, gradePercent = -10.0, previousRequestedResistance = 1))
    }
}

package com.spop.poverlay.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ManualResistanceGuidanceEngine].
 *
 * Timestamps are injected as parameters so the engine behaviour is fully deterministic
 * without any dependency on SystemClock.
 */
class ManualResistanceGuidanceEngineTest {

    private lateinit var engine: ManualResistanceGuidanceEngine
    private val preset = RouteResistancePreset.Standard  // uphill: 1.5/grade, floor: 20

    // A timestamp baseline; tests increment from here to simulate time passing.
    private val t0 = 1_000_000L

    @Before
    fun setUp() {
        engine = ManualResistanceGuidanceEngine()
    }

    // ── Neutral ──────────────────────────────────────────────────────────────

    @Test
    fun neutralWhenDisabled() {
        val state = engine.evaluate(
            currentResistance = 30,
            guidanceBaseline = 30,
            smoothedGradePercent = 5.0,
            upcomingPoints = emptyList(),
            currentPositionMeters = 0.0,
            speedMph = 15f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 10,
            enabled = false,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertEquals(ManualResistanceGuidanceState.Neutral, state)
    }

    @Test
    fun neutralWhenIsBikePlus() {
        val state = engine.evaluate(
            currentResistance = 30,
            guidanceBaseline = 30,
            smoothedGradePercent = 5.0,
            upcomingPoints = emptyList(),
            currentPositionMeters = 0.0,
            speedMph = 15f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 10,
            enabled = true,
            isBikePlus = true,
            timestampMs = t0,
            preset = preset
        )
        assertEquals(ManualResistanceGuidanceState.Neutral, state)
    }

    // ── InRange ───────────────────────────────────────────────────────────────

    @Test
    fun inRangeWhenResistanceMatchesTarget() {
        // Standard preset: flat grade (0%), baseline 30, floor 20 → target = 30
        val state = engine.evaluate(
            currentResistance = 30,
            guidanceBaseline = 30,
            smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(),
            currentPositionMeters = 0.0,
            speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal,   // ±4
            warningSeconds = 0,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected InRange, got $state", state is ManualResistanceGuidanceState.InRange)
        val inRange = state as ManualResistanceGuidanceState.InRange
        assertEquals(30, inRange.targetCenter)
        assertEquals(26, inRange.targetMin)
        assertEquals(34, inRange.targetMax)
    }

    @Test
    fun inRangeAtEdgeOfTolerance() {
        // target = 30, tolerance ±4 → range 26–34; current at 26 should be InRange
        val state = engine.evaluate(
            currentResistance = 26,
            guidanceBaseline = 30,
            smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(),
            currentPositionMeters = 0.0,
            speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 0,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected InRange at lower edge, got $state", state is ManualResistanceGuidanceState.InRange)
    }

    // ── AdjustmentNeeded ──────────────────────────────────────────────────────

    @Test
    fun adjustmentNeededWhenBelowRange() {
        // baseline 30, grade 5%: target = 30 + round(5*1.5) = 30+8 = 38, range 34–42, current 30
        val state = engine.evaluate(
            currentResistance = 30,
            guidanceBaseline = 30,
            smoothedGradePercent = 5.0,
            upcomingPoints = emptyList(),
            currentPositionMeters = 0.0,
            speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 0,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected AdjustmentNeeded, got $state", state is ManualResistanceGuidanceState.AdjustmentNeeded)
        val adj = state as ManualResistanceGuidanceState.AdjustmentNeeded
        assertEquals(38, adj.targetCenter)
        assertEquals(34, adj.targetMin)
        assertEquals(42, adj.targetMax)
        assertEquals(8, adj.delta)
        assertEquals(ResistanceDirection.Up, adj.direction)
    }

    @Test
    fun adjustmentNeededWhenAboveRange() {
        // baseline 40, grade -5%: target = 40 + round(-5*2.0) = 40-10 = 30, range 26–34, current 40
        val state = engine.evaluate(
            currentResistance = 40,
            guidanceBaseline = 40,
            smoothedGradePercent = -5.0,
            upcomingPoints = emptyList(),
            currentPositionMeters = 0.0,
            speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 0,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected AdjustmentNeeded, got $state", state is ManualResistanceGuidanceState.AdjustmentNeeded)
        val adj = state as ManualResistanceGuidanceState.AdjustmentNeeded
        assertEquals(ResistanceDirection.Down, adj.direction)
        assertTrue("delta should be negative, was ${adj.delta}", adj.delta < 0)
    }

    // ── Tolerance mode coverage ────────────────────────────────────────────────

    @Test
    fun tightToleranceSmallerBand() {
        // target 30, Tight ±2 → range 28–32; current 34 should be AdjustmentNeeded
        val state = engine.evaluate(
            currentResistance = 34,
            guidanceBaseline = 30,
            smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(),
            currentPositionMeters = 0.0,
            speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Tight,
            warningSeconds = 0,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected AdjustmentNeeded with tight tolerance, got $state",
            state is ManualResistanceGuidanceState.AdjustmentNeeded)
    }

    @Test
    fun looseToleranceLargerBand() {
        // target 30, Loose ±6 → range 24–36; current 34 should be InRange
        val state = engine.evaluate(
            currentResistance = 34,
            guidanceBaseline = 30,
            smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(),
            currentPositionMeters = 0.0,
            speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Loose,
            warningSeconds = 0,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected InRange with loose tolerance, got $state",
            state is ManualResistanceGuidanceState.InRange)
    }

    // ── Hysteresis ────────────────────────────────────────────────────────────

    @Test
    fun hysteresisKeepsInRangeOneUnitBeyondBand() {
        // First tick: rider is in range (target=30, current=30)
        engine.evaluate(
            currentResistance = 30, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0, preset = preset
        )
        // Second tick: rider drifts 1 unit beyond upper edge (34+1=35); should still be InRange
        val state = engine.evaluate(
            currentResistance = 35, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0 + 1000, preset = preset
        )
        assertTrue("Expected InRange due to hysteresis, got $state",
            state is ManualResistanceGuidanceState.InRange)
    }

    @Test
    fun hysteresisTriggerAdjustmentBeyondBuffer() {
        // First tick: in range
        engine.evaluate(
            currentResistance = 30, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0, preset = preset
        )
        // Second tick: rider is 2 units beyond upper edge (34+2=36); should flip to AdjustmentNeeded
        val state = engine.evaluate(
            currentResistance = 36, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0 + 1000, preset = preset
        )
        assertTrue("Expected AdjustmentNeeded after hysteresis buffer exceeded, got $state",
            state is ManualResistanceGuidanceState.AdjustmentNeeded)
    }

    // ── Stale ─────────────────────────────────────────────────────────────────

    @Test
    fun staleAfterThirtySecondsOutOfRange() {
        // Out of range at t0
        engine.evaluate(
            currentResistance = 20, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0, preset = preset
        )
        // Still out of range at t0 + 30 001 ms → Stale
        val state = engine.evaluate(
            currentResistance = 20, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0 + 30_001L, preset = preset
        )
        assertTrue("Expected Stale after 30s, got $state", state is ManualResistanceGuidanceState.Stale)
    }

    @Test
    fun adjustmentNeededBeforeStaleThreshold() {
        engine.evaluate(
            currentResistance = 20, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0, preset = preset
        )
        val state = engine.evaluate(
            currentResistance = 20, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0 + 29_999L, preset = preset
        )
        assertTrue("Expected AdjustmentNeeded before stale threshold, got $state",
            state is ManualResistanceGuidanceState.AdjustmentNeeded)
    }

    @Test
    fun staleTimerResetsAfterComingInRange() {
        // Go out of range
        engine.evaluate(
            currentResistance = 20, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0, preset = preset
        )
        // Come back in range
        engine.evaluate(
            currentResistance = 30, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0 + 25_000L, preset = preset
        )
        // Go out of range again at t0+25s; should NOT be stale immediately
        val state = engine.evaluate(
            currentResistance = 20, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0 + 30_001L, preset = preset
        )
        assertTrue("Expected AdjustmentNeeded (stale timer reset), got $state",
            state is ManualResistanceGuidanceState.AdjustmentNeeded)
    }

    // ── Upcoming ──────────────────────────────────────────────────────────────

    private fun makePoints(vararg pairs: Pair<Double, Double?>): List<RoutePoint> =
        pairs.mapIndexed { i, (distMeters, elev) ->
            RoutePoint(
                latitude = 0.0,
                longitude = 0.0,
                elevationMeters = elev,
                distanceFromStartMeters = distMeters
            )
        }

    @Test
    fun upcomingWhenTransitionWithinWarningWindow() {
        // Current position 0m; upcoming segment at 50m rises sharply
        // At 15 mph (~6.7 m/s), 50m = ~7.5s → within 10s warning
        // baseline 30, flat → target 30; upcoming segment ~10% grade → target ~30+15=45; |45-30|=15 > 4
        val points = makePoints(
            0.0 to 10.0,   // current area (flat)
            50.0 to 15.0,  // 50m: elevation 15m — grade = (15-10)/50 * 100 = 10%
            100.0 to 20.0
        )
        val state = engine.evaluate(
            currentResistance = 30,
            guidanceBaseline = 30,
            smoothedGradePercent = 0.0,
            upcomingPoints = points,
            currentPositionMeters = 0.0,
            speedMph = 15f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 10,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected Upcoming, got $state", state is ManualResistanceGuidanceState.Upcoming)
        val upcoming = state as ManualResistanceGuidanceState.Upcoming
        assertTrue("ETA should be <= 10s, was ${upcoming.etaSeconds}", upcoming.etaSeconds <= 10)
        assertEquals(ResistanceDirection.Up, upcoming.direction)
    }

    @Test
    fun noUpcomingWhenTransitionBeyondWarningWindow() {
        // At 5 mph (~2.2 m/s), 200m = ~90s → beyond 10s warning
        val points = makePoints(
            0.0 to 10.0,
            200.0 to 15.0,  // 10% grade segment but far away
            250.0 to 17.5
        )
        val state = engine.evaluate(
            currentResistance = 30,
            guidanceBaseline = 30,
            smoothedGradePercent = 0.0,
            upcomingPoints = points,
            currentPositionMeters = 0.0,
            speedMph = 5f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 10,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected InRange (no upcoming within window), got $state",
            state is ManualResistanceGuidanceState.InRange)
    }

    @Test
    fun noUpcomingWhenWarningSuppressed() {
        val points = makePoints(
            0.0 to 10.0,
            50.0 to 15.0,
            100.0 to 20.0
        )
        val state = engine.evaluate(
            currentResistance = 30,
            guidanceBaseline = 30,
            smoothedGradePercent = 0.0,
            upcomingPoints = points,
            currentPositionMeters = 0.0,
            speedMph = 15f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 0,   // Off
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected InRange (warning off), got $state",
            state is ManualResistanceGuidanceState.InRange)
    }

    @Test
    fun adjustmentNeededTakesPriorityOverUpcoming() {
        // Rider is already OUT of range — Upcoming should not supersede AdjustmentNeeded
        val points = makePoints(
            0.0 to 10.0,
            50.0 to 15.0,
            100.0 to 20.0
        )
        val state = engine.evaluate(
            currentResistance = 20,   // well below target range 26–34
            guidanceBaseline = 30,
            smoothedGradePercent = 0.0,
            upcomingPoints = points,
            currentPositionMeters = 0.0,
            speedMph = 15f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 10,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected AdjustmentNeeded (not Upcoming), got $state",
            state is ManualResistanceGuidanceState.AdjustmentNeeded)
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    fun resetClearsStaleState() {
        engine.evaluate(
            currentResistance = 20, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0, preset = preset
        )
        engine.reset()
        // After reset, out-of-range at t0+40s should be AdjustmentNeeded, not Stale
        val state = engine.evaluate(
            currentResistance = 20, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0 + 40_000L, preset = preset
        )
        assertTrue("Expected AdjustmentNeeded after reset (stale cleared), got $state",
            state is ManualResistanceGuidanceState.AdjustmentNeeded)
    }

    // ── Coexistence with Bike+ ─────────────────────────────────────────────────

    @Test
    fun bikePlusAlwaysNeutralRegardlessOfGrade() {
        for (grade in listOf(-10.0, 0.0, 5.0, 15.0)) {
            val state = engine.evaluate(
                currentResistance = 30, guidanceBaseline = 30, smoothedGradePercent = grade,
                upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 15f,
                toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 10,
                enabled = true, isBikePlus = true, timestampMs = t0, preset = preset
            )
            assertEquals("Expected Neutral for grade=$grade on Bike+",
                ManualResistanceGuidanceState.Neutral, state)
        }
    }

    // ── Grade-to-target mapping cross-check ───────────────────────────────────

    @Test
    fun targetCenterMatchesExpectedForStandardPreset() {
        // Standard: uphill 1.5/grade, floor 20. baseline=30, grade=4% → 30 + round(4*1.5)=30+6=36
        val state = engine.evaluate(
            currentResistance = 36,
            guidanceBaseline = 30,
            smoothedGradePercent = 4.0,
            upcomingPoints = emptyList(),
            currentPositionMeters = 0.0,
            speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 0,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected InRange at exact target for 4% grade, got $state",
            state is ManualResistanceGuidanceState.InRange)
        assertEquals(36, (state as ManualResistanceGuidanceState.InRange).targetCenter)
    }

    @Test
    fun baselineFloorAppliedOnFlatForLowBaseline() {
        // Standard floor is 20. baseline 10, grade 0 → effective baseline = max(10,20) = 20 → target = 20
        val state = engine.evaluate(
            currentResistance = 20,
            guidanceBaseline = 10,   // below floor
            smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(),
            currentPositionMeters = 0.0,
            speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 0,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected InRange with floor applied, got $state",
            state is ManualResistanceGuidanceState.InRange)
        assertEquals(20, (state as ManualResistanceGuidanceState.InRange).targetCenter)
    }

    // ── Route-switch regression ───────────────────────────────────────────────

    /**
     * Regression: when a route is switched the caller resets the engine so the stale
     * timer from the prior route does not bleed into the new route's guidance.
     *
     * Sequence:
     *   1. Route A: rider is out of range for 35 s (would be Stale without reset).
     *   2. Route switch → engine.reset() called (simulating onStartRouteClicked /
     *      onRestartRouteClicked resetting dashboardGuidanceBaseline).
     *   3. Route B: first evaluation with a *different* baseline at t0+35s.
     *   Expected: AdjustmentNeeded (fresh timer), NOT Stale.
     */
    @Test
    fun resetOnRouteSwitchClearsPriorStaleTimer() {
        // Route A — go out of range at t0
        engine.evaluate(
            currentResistance = 20, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0, preset = preset
        )

        // Simulate route switch
        engine.reset()

        // Route B — new baseline (e.g., rider adjusted resistance to 25 before new route)
        // Evaluated 35 s after the original t0 — would be Stale if timer wasn't cleared
        val state = engine.evaluate(
            currentResistance = 20, guidanceBaseline = 25, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0 + 35_000L, preset = preset
        )
        assertTrue(
            "Expected AdjustmentNeeded on route B (stale timer must have been cleared by reset), got $state",
            state is ManualResistanceGuidanceState.AdjustmentNeeded
        )
    }

    // ── Hysteresis — lower bound ───────────────────────────────────────────────

    @Test
    fun hysteresisKeepsInRangeOneUnitBelowBand() {
        // First tick: in range (target=30, current=30, Normal ±4 → band 26–34)
        engine.evaluate(
            currentResistance = 30, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0, preset = preset
        )
        // Second tick: drift 1 unit below lower edge (26 - 1 = 25); still InRange via exit buffer
        val state = engine.evaluate(
            currentResistance = 25, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = emptyList(), currentPositionMeters = 0.0, speedMph = 0f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 0,
            enabled = true, isBikePlus = false, timestampMs = t0 + 1000, preset = preset
        )
        assertTrue("Expected InRange due to lower-bound hysteresis, got $state",
            state is ManualResistanceGuidanceState.InRange)
    }

    // ── Stale vs Upcoming priority ────────────────────────────────────────────

    @Test
    fun staleStateNotOverriddenByUpcoming() {
        // Rider has been out of range for 35 s — engine is in Stale territory.
        // Upcoming scan is not even reached (checkUpcoming is only called when isInRange).
        val points = makePoints(
            0.0 to 10.0,
            50.0 to 15.0,   // significant uphill within warning window at 15 mph
            100.0 to 20.0
        )
        // Seed the stale timer
        engine.evaluate(
            currentResistance = 20, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = points, currentPositionMeters = 0.0, speedMph = 15f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 10,
            enabled = true, isBikePlus = false, timestampMs = t0, preset = preset
        )
        // 35 s later — still out of range with a transition within warning window
        val state = engine.evaluate(
            currentResistance = 20, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = points, currentPositionMeters = 0.0, speedMph = 15f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 10,
            enabled = true, isBikePlus = false, timestampMs = t0 + 35_000L, preset = preset
        )
        assertTrue("Expected Stale (not Upcoming) when rider is out of range, got $state",
            state is ManualResistanceGuidanceState.Stale)
    }

    // ── checkUpcoming: null-elevation segments ────────────────────────────────

    @Test
    fun upcomingSkipsNullElevationSegments() {
        // Point[1] has null elevation — segment 0→1 should be skipped.
        // Segment 1→2 has a significant grade and is within the warning window.
        val points = makePoints(
            0.0 to 10.0,
            50.0 to null,   // null elevation — skipped by checkUpcoming
            100.0 to 20.0   // grade from null point will also be skipped since start is null
        )
        // With both legs involving a null endpoint, no upcoming transition should be found.
        val state = engine.evaluate(
            currentResistance = 30, guidanceBaseline = 30, smoothedGradePercent = 0.0,
            upcomingPoints = points, currentPositionMeters = 0.0, speedMph = 15f,
            toleranceMode = ManualResistanceTolerance.Normal, warningSeconds = 10,
            enabled = true, isBikePlus = false, timestampMs = t0, preset = preset
        )
        assertTrue("Expected InRange (null elevation segments gracefully skipped), got $state",
            state is ManualResistanceGuidanceState.InRange)
    }

    // ── Upcoming: downhill direction ──────────────────────────────────────────

    @Test
    fun upcomingDownhillDirectionIsDown() {
        // Current area is steep uphill (grade 10%) → target ~45.
        // Upcoming segment drops back to flat → target 30; delta = 30 - 30 = 0... actually
        // we need the upcoming target to differ from currentTarget by > halfBand.
        // Current: baseline=30, grade 10% → target=30+15=45 (currentTarget passed to checkUpcoming)
        // Upcoming at 50m: grade = (10-20)/50*100 = -20% → target = 30 + round(-20*2.0) = 30-40 = 0 (floor)
        // |0 - 45| = 45 > 4, at 15mph → 50/6.7 ≈ 7s <= 10s warning → Upcoming with Down direction
        val points = makePoints(
            0.0 to 20.0,    // rider is here; going to 50m
            50.0 to 10.0,   // elevation drops 10m over 50m → grade = -20%
            100.0 to 5.0
        )
        val state = engine.evaluate(
            currentResistance = 45,  // in range for current 10% grade (target=45)
            guidanceBaseline = 30,
            smoothedGradePercent = 10.0,
            upcomingPoints = points,
            currentPositionMeters = 0.0,
            speedMph = 15f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 10,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected Upcoming for downhill transition, got $state",
            state is ManualResistanceGuidanceState.Upcoming)
        val upcoming = state as ManualResistanceGuidanceState.Upcoming
        assertEquals(ResistanceDirection.Down, upcoming.direction)
    }

    // ── Upcoming: warningSeconds boundary (inclusive) ─────────────────────────

    @Test
    fun upcomingIncludedWhenEtaEqualsWarningSeconds() {
        // At 15 mph (~6.71 m/s), a segment at exactly 67m away ≈ 10s ETA.
        // warningSeconds=10 — should be included (etaSeconds <= warningSeconds).
        val points = makePoints(
            0.0 to 10.0,
            67.0 to 15.0,   // grade ≈ 7.5% → target ~30+11=41; |41-30|=11 > 4
            134.0 to 20.0
        )
        val state = engine.evaluate(
            currentResistance = 30,
            guidanceBaseline = 30,
            smoothedGradePercent = 0.0,
            upcomingPoints = points,
            currentPositionMeters = 0.0,
            speedMph = 15f,
            toleranceMode = ManualResistanceTolerance.Normal,
            warningSeconds = 10,
            enabled = true,
            isBikePlus = false,
            timestampMs = t0,
            preset = preset
        )
        assertTrue("Expected Upcoming when ETA is at the boundary, got $state",
            state is ManualResistanceGuidanceState.Upcoming)
        val upcoming = state as ManualResistanceGuidanceState.Upcoming
        assertTrue("ETA should be <= 10s, was ${upcoming.etaSeconds}", upcoming.etaSeconds <= 10)
    }
}

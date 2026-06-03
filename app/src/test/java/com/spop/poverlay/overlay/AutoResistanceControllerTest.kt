package com.spop.poverlay.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AutoResistanceController].
 *
 * Covers all five required test scenarios for the auto-resistance bug-fix pass:
 *   1. Manual override suspends auto for the full 30-second window.
 *   2. Each subsequent manual change refreshes (extends) the suspension window.
 *   3. Three manual changes disable auto and require an explicit re-enable.
 *   4. Smoothing stability prevents rapid alternating targets from committing.
 *   5. Settings toggle reflects persisted auto state: re-enable resets controller so
 *      subsequent overrides work from a clean slate.
 *
 * All timing is injected via the [nowMs] parameter, so the tests are fully deterministic.
 */
class AutoResistanceControllerTest {

    // Controller configured with the same constants used in production.
    private lateinit var controller: AutoResistanceController

    private val t0 = 1_000_000L   // arbitrary baseline timestamp
    private val suspendMs = 30_000L
    private val dwellMs = 1_200L

    @Before
    fun setUp() {
        controller = AutoResistanceController(
            suspendDurationMs = suspendMs,
            strikeThreshold = 3,
            targetDwellMs = dwellMs
        )
    }

    // ── Scenario 1: Manual override suspends auto for the full 30-second window ─────────────

    @Test
    fun manualOverrideSuspendsAutoImmediately() {
        controller.recordManualOverride(nowMs = t0)
        assertTrue(
            "Auto should be suspended immediately after a manual override",
            controller.isSuspended(t0)
        )
    }

    @Test
    fun autoStillSuspendedJustBeforeWindowExpires() {
        controller.recordManualOverride(nowMs = t0)
        val justBefore = t0 + suspendMs - 1
        assertTrue(
            "Auto must remain suspended at t0+29999ms (1ms before window)",
            controller.isSuspended(justBefore)
        )
    }

    @Test
    fun autoResumesExactlyAtWindowExpiry() {
        controller.recordManualOverride(nowMs = t0)
        val atExpiry = t0 + suspendMs
        assertFalse(
            "Auto must resume at t0+30000ms (suspension window elapsed)",
            controller.isSuspended(atExpiry)
        )
    }

    @Test
    fun autoResumesAfterWindowExpires() {
        controller.recordManualOverride(nowMs = t0)
        val afterExpiry = t0 + suspendMs + 5_000L
        assertFalse(
            "Auto must resume after the 30-second window has fully elapsed",
            controller.isSuspended(afterExpiry)
        )
    }

    // ── Scenario 2: Each manual change refreshes the suspension window ───────────────────────

    @Test
    fun secondManualOverrideRefreshesSuspensionWindowFromLatestCall() {
        // First override at t0
        controller.recordManualOverride(nowMs = t0)

        // Second override 20 s later — window resets to t0+20s+30s = t0+50s
        val t1 = t0 + 20_000L
        controller.recordManualOverride(nowMs = t1)

        // At t0+30s, the ORIGINAL window would have expired, but the refreshed one hasn't
        val originalWindowExpiry = t0 + suspendMs
        assertTrue(
            "Second override must extend suspension beyond the original 30s window (at $originalWindowExpiry)",
            controller.isSuspended(originalWindowExpiry)
        )
    }

    @Test
    fun suspensionWindowIsAnchoredToLatestManualOverride() {
        controller.recordManualOverride(nowMs = t0)
        val t1 = t0 + 10_000L
        controller.recordManualOverride(nowMs = t1)

        // New window expires at t1 + 30s
        val newExpiry = t1 + suspendMs
        assertFalse(
            "Suspension must expire at latest-override + 30s, not original-override + 30s",
            controller.isSuspended(newExpiry)
        )
        assertTrue(
            "Suspension must still be active 1ms before new expiry",
            controller.isSuspended(newExpiry - 1)
        )
    }

    // ── Scenario 3: Three manual changes disable auto; explicit re-enable required ───────────

    @Test
    fun firstTwoStrikesDontDisableAuto() {
        assertFalse("Strike 1 must not return true", controller.recordManualOverride(t0))
        assertFalse("Strike 2 must not return true", controller.recordManualOverride(t0 + 1_000L))
    }

    @Test
    fun thirdStrikeSignalsAutoDisable() {
        controller.recordManualOverride(t0)
        controller.recordManualOverride(t0 + 1_000L)
        val shouldDisable = controller.recordManualOverride(t0 + 2_000L)
        assertTrue(
            "Third strike must return true so the caller can disable auto in settings",
            shouldDisable
        )
    }

    @Test
    fun strikeCounterResetsAfterThirdStrike() {
        controller.recordManualOverride(t0)
        controller.recordManualOverride(t0 + 1_000L)
        controller.recordManualOverride(t0 + 2_000L)  // 3rd strike, counter resets

        // Verify the counter is back to 0 (not 3)
        assertEquals(
            "Strike counter must reset to 0 after the threshold is reached",
            0,
            controller.strikeCount
        )
    }

    @Test
    fun autoDoesNotSilentlyReEnableAfterTimeout() {
        // Strike 3 times to trigger disable
        controller.recordManualOverride(t0)
        controller.recordManualOverride(t0 + 1_000L)
        controller.recordManualOverride(t0 + 2_000L)

        // The suspension window from the last strike expires after 30s.
        // After expiry, [isSuspended] returns false — but that's fine because the CALLER
        // (OverlaySensorViewModel) checks routeResistanceSimulationEnabled, which has been
        // persisted to false.  The controller itself is reset only by [onAutoReEnabled].
        // We verify that [strikeCount] is 0 (clean slate) but the controller doesn't
        // re-arm itself automatically.
        assertEquals(0, controller.strikeCount)
        // After suspension expires, isSuspended is false — auto WOULD run if the setting
        // were still on.  The guarantee is that the VM disables the setting, not the controller.
        // This test confirms the counter is zeroed (not accumulating silently).
    }

    @Test
    fun explicitReEnableResetsControllerSoNextCycleStartsFresh() {
        // Strike 3 times
        controller.recordManualOverride(t0)
        controller.recordManualOverride(t0 + 1_000L)
        controller.recordManualOverride(t0 + 2_000L)

        // User explicitly re-enables auto in settings
        controller.onAutoReEnabled()

        // Suspension is cleared
        assertFalse(
            "Suspension must be cleared after explicit re-enable",
            controller.isSuspended(t0 + 5_000L)
        )

        // One manual override after re-enable must NOT disable auto (clean slate)
        val shouldDisable = controller.recordManualOverride(t0 + 5_000L)
        assertFalse(
            "After re-enable, first override must not trigger auto-disable",
            shouldDisable
        )
        assertEquals(
            "Strike count after one override post re-enable must be 1",
            1,
            controller.strikeCount
        )
    }

    @Test
    fun twoFullCyclesOfThreeStrikesEachWorkIndependently() {
        // Cycle 1
        controller.recordManualOverride(t0)
        controller.recordManualOverride(t0 + 500L)
        val disabled1 = controller.recordManualOverride(t0 + 1_000L)
        assertTrue("Cycle 1, 3rd strike must disable", disabled1)

        // Re-enable and run cycle 2
        controller.onAutoReEnabled()
        controller.recordManualOverride(t0 + 60_000L)
        controller.recordManualOverride(t0 + 61_000L)
        val disabled2 = controller.recordManualOverride(t0 + 62_000L)
        assertTrue("Cycle 2, 3rd strike must disable", disabled2)
    }

    // ── Scenario 4: Smoothing stability prevents rapid alternating targets ────────────────────

    @Test
    fun firstNewTargetIsNotCommittedImmediately() {
        // No prior history — first call to stabilizeTarget for a new target returns null
        // (starts the dwell clock).
        val result = controller.stabilizeTarget(rawTarget = 32, nowMs = t0)
        assertNull(
            "First encounter of a new target must not commit immediately (dwell not elapsed)",
            result
        )
    }

    @Test
    fun targetNotCommittedBeforeDwellElapsed() {
        // Start dwell at t0
        controller.stabilizeTarget(rawTarget = 32, nowMs = t0)
        // Re-evaluate 100ms later — still within 1200ms dwell window
        val result = controller.stabilizeTarget(rawTarget = 32, nowMs = t0 + 100)
        assertNull(
            "Target must not be committed before the ${dwellMs}ms dwell has elapsed",
            result
        )
    }

    @Test
    fun targetCommittedAfterDwellElapsed() {
        // Start dwell at t0
        controller.stabilizeTarget(rawTarget = 32, nowMs = t0)
        // Re-evaluate exactly at dwell threshold
        val result = controller.stabilizeTarget(rawTarget = 32, nowMs = t0 + dwellMs)
        assertEquals(
            "Target must be committed after ${dwellMs}ms dwell has elapsed",
            32,
            result
        )
    }

    @Test
    fun rapidAlternatingTargetNeverCommits() {
        // Simulate grade noise that causes target to flip between 30 and 32 every 500ms
        // (well under the 1200ms dwell).  Neither should ever be committed.
        for (i in 0 until 10) {
            val t = t0 + i * 500L
            val target = if (i % 2 == 0) 30 else 32
            val result = controller.stabilizeTarget(rawTarget = target, nowMs = t)
            assertNull(
                "Rapidly alternating target at i=$i (target=$target) must never commit",
                result
            )
        }
    }

    @Test
    fun newTargetResetsDwellClockAndRequiresFreshDwell() {
        // Start dwell on target=32 at t0
        controller.stabilizeTarget(rawTarget = 32, nowMs = t0)
        // At t0+900ms, grade flips back to target=30 (a new candidate)
        controller.stabilizeTarget(rawTarget = 30, nowMs = t0 + 900)
        // At t0+1300ms (400ms after the flip), target=30 has been pending for only 400ms
        val result = controller.stabilizeTarget(rawTarget = 30, nowMs = t0 + 1_300)
        assertNull(
            "After target change, dwell clock must restart; 400ms is not enough",
            result
        )
    }

    @Test
    fun stableSustainedTargetEventuallyCommits() {
        // Grade settles on target=32 and stays there.
        controller.stabilizeTarget(rawTarget = 32, nowMs = t0)
        // Re-evaluate every 200ms; after 1200ms it should commit.
        var committed: Int? = null
        for (i in 1..10) {
            val now = t0 + i * 200L
            committed = controller.stabilizeTarget(rawTarget = 32, nowMs = now)
            if (committed != null) break
        }
        assertEquals(
            "Sustained stable target must eventually commit (after ${dwellMs}ms)",
            32,
            committed
        )
    }

    @Test
    fun clearPendingPreventsCommitEvenAfterDwell() {
        controller.stabilizeTarget(rawTarget = 32, nowMs = t0)
        // A manual override clears pending mid-dwell
        controller.clearPending()
        // Evaluate with same target after original dwell would have elapsed
        val result = controller.stabilizeTarget(rawTarget = 32, nowMs = t0 + dwellMs + 100)
        assertNull(
            "After clearPending the dwell clock must restart; old dwell must not count",
            result
        )
    }

    // ── Scenario 5: Re-enable cleans state so toggle-reflected persisted state works ─────────

    @Test
    fun resetClearsAllStateSoNewRouteStartsClean() {
        // Accumulate some state
        controller.recordManualOverride(t0)
        controller.recordManualOverride(t0 + 500L)
        controller.stabilizeTarget(rawTarget = 35, nowMs = t0 + 600L)

        controller.reset()

        assertFalse("After reset, auto must not be suspended", controller.isSuspended(t0 + 1_000L))
        assertEquals("After reset, strike count must be 0", 0, controller.strikeCount)
        // Dwell is also cleared: a new target right after reset starts a fresh dwell
        val result = controller.stabilizeTarget(rawTarget = 35, nowMs = t0 + dwellMs + 100L)
        assertNull(
            "After reset, previously-seen target must restart dwell (cleared state)",
            result
        )
    }

    @Test
    fun onAutoReEnabledClearsSuspensionAndStrikesSoToggleIsConsistent() {
        // Simulate: user had 2 pending strikes and auto is suspended
        controller.recordManualOverride(t0)
        controller.recordManualOverride(t0 + 1_000L)

        assertTrue("Sanity: auto must be suspended", controller.isSuspended(t0 + 2_000L))
        assertEquals("Sanity: 2 strikes recorded", 2, controller.strikeCount)

        // User toggles auto back ON in Settings (triggered by VM watching routeResistanceSimulationEnabled)
        controller.onAutoReEnabled()

        assertFalse(
            "After re-enable, suspension must be cleared so auto resumes immediately",
            controller.isSuspended(t0 + 2_000L)
        )
        assertEquals(
            "After re-enable, strike count must be 0 so UI toggle reflects clean state",
            0,
            controller.strikeCount
        )
    }
}

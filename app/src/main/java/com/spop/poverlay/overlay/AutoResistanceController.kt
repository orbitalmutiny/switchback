package com.spop.poverlay.overlay

private const val DefaultSuspendDurationMs = 30_000L
private const val DefaultStrikeThreshold = 3
private const val DefaultTargetDwellMs = 1200L

/**
 * Pure, thread-visible state machine for the auto-resistance write gate.
 *
 * Responsibilities:
 *  1. Manual-override suspension — each [recordManualOverride] call blocks auto writes for
 *     [suspendDurationMs] from that moment, refreshing the window on repeat calls.
 *  2. Three-strikes disable — after [strikeThreshold] manual overrides while auto is active,
 *     [recordManualOverride] returns `true`; the caller must then disable auto in settings.
 *     The counter resets to 0 at that moment so subsequent re-enable is a clean slate.
 *  3. Target stability dwell — [stabilizeTarget] debounces rapid target changes. A new
 *     target must remain stable for [targetDwellMs] before it is committed for writing.
 *
 * All mutable fields are @Volatile to ensure cross-coroutine visibility on Dispatchers.IO.
 * This class is NOT thread-safe for compound CAS operations; callers may observe brief
 * inconsistencies under extreme concurrency, which is acceptable for the use case.
 */
class AutoResistanceController(
    private val suspendDurationMs: Long = DefaultSuspendDurationMs,
    val strikeThreshold: Int = DefaultStrikeThreshold,
    private val targetDwellMs: Long = DefaultTargetDwellMs
) {
    @Volatile var suspendedUntilMs: Long = 0L
        private set

    @Volatile var strikeCount: Int = 0
        private set

    @Volatile private var pendingTarget: Int? = null
    @Volatile private var pendingTargetFirstSeenMs: Long = 0L

    /**
     * Called whenever the rider manually changes resistance while auto-control is active.
     *
     * - Refreshes the 30-second suspension window from [nowMs].
     * - Clears any pending auto-target dwell (the manual value becomes the anchor).
     * - Increments the strike counter.
     *
     * @return `true` if the strike threshold was just reached. The caller should immediately
     *         disable auto-resistance in settings. The counter is reset to 0 so that after
     *         an explicit re-enable the next cycle starts fresh.
     */
    fun recordManualOverride(nowMs: Long): Boolean {
        suspendedUntilMs = nowMs + suspendDurationMs
        clearPending()
        strikeCount++
        if (strikeCount >= strikeThreshold) {
            strikeCount = 0
            return true
        }
        return false
    }

    /** Returns true while the manual-override suspension window is active. */
    fun isSuspended(nowMs: Long): Boolean = nowMs < suspendedUntilMs

    /**
     * Debounces rapid auto-target changes to prevent oscillation.
     *
     * Must be called only after confirming [rawTarget] differs from the last committed
     * resistance request. Returns the committed target once it has been stable for
     * [targetDwellMs], otherwise returns `null` (caller should skip the write).
     */
    fun stabilizeTarget(rawTarget: Int, nowMs: Long): Int? {
        return if (rawTarget == pendingTarget) {
            if (nowMs - pendingTargetFirstSeenMs >= targetDwellMs) {
                pendingTarget = null
                rawTarget
            } else {
                null
            }
        } else {
            pendingTarget = rawTarget
            pendingTargetFirstSeenMs = nowMs
            null
        }
    }

    /** Clears any in-flight pending target. Call when the target stabilizes or is overridden. */
    fun clearPending() {
        pendingTarget = null
        pendingTargetFirstSeenMs = 0L
    }

    /**
     * Called when the user explicitly re-enables auto-resistance (toggle path).
     * Resets strike count, clears suspension, and clears pending dwell.
     */
    fun onAutoReEnabled() {
        strikeCount = 0
        suspendedUntilMs = 0L
        clearPending()
    }

    /**
     * Full reset — call on route change or when the active route is cleared.
     * Preserves no state from the previous route context.
     */
    fun reset() {
        suspendedUntilMs = 0L
        strikeCount = 0
        clearPending()
    }
}

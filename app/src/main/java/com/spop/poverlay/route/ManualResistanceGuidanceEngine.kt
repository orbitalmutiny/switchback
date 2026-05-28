package com.spop.poverlay.route

import kotlin.math.abs
import kotlin.math.roundToInt

private const val StaleThresholdMs = 30_000L
private const val MetersPerMile = 1609.344

/**
 * A new raw target must remain stable for this many milliseconds before it is adopted.
 * Prevents flip-flop on small rolling-grade noise between adjacent targets.
 */
private const val PendingTargetDwellMs = 1200L

/**
 * Stateful, deterministic engine that converts live route/resistance data into a
 * [ManualResistanceGuidanceState] for non-Bike+ riders.
 *
 * Call [evaluate] once per tick from the dashboard or overlay update loop.
 * Call [reset] when the active route changes or guidance is cleared.
 *
 * Thread-safety: intended to be called from a single coroutine thread; no locking.
 */
class ManualResistanceGuidanceEngine {

    // ── Anti-flap state ────────────────────────────────────────────────────────

    /** True when the previous evaluation was InRange; gates the 1-unit exit hysteresis. */
    private var lastWasInRange = false

    /** Timestamp at which the rider first went out of range; null when in range. */
    private var outOfRangeStartMs: Long? = null

    // ── Target stability state ─────────────────────────────────────────────────

    /** The target center currently shown to the user. Null until the first evaluation. */
    private var committedTarget: Int? = null

    /** A newly computed target that has not yet dwelled long enough to be committed. */
    private var pendingTarget: Int? = null

    /** Timestamp (injected) when [pendingTarget] was first seen. */
    private var pendingTargetFirstSeenMs: Long = 0L

    // ── Public API ─────────────────────────────────────────────────────────────

    fun reset() {
        lastWasInRange = false
        outOfRangeStartMs = null
        committedTarget = null
        pendingTarget = null
        pendingTargetFirstSeenMs = 0L
    }

    /**
     * Evaluate the current guidance state.
     *
     * @param currentResistance  Live resistance reading from telemetry.
     * @param guidanceBaseline   Resistance captured at route-start (flat baseline).
     *                           Target for any grade is derived from this + grade scaling.
     * @param smoothedGradePercent  Lookahead-smoothed grade at the rider's current position.
     * @param upcomingPoints     Upcoming route points (next ~500 m window from RouteProgressionEngine).
     * @param currentPositionMeters  Rider's absolute route position in metres.
     * @param speedMph           Current ride speed; used to estimate ETA to next grade transition.
     * @param toleranceMode      Tolerance half-band setting (Tight ±2 / Normal ±4 / Loose ±6).
     * @param warningSeconds     How many seconds ahead to trigger Upcoming (0 = off).
     * @param enabled            Whether the guidance feature is toggled on by the user.
     * @param isBikePlus         Suppresses guidance entirely on Bike+ auto-control contexts.
     * @param timestampMs        Monotonic clock value (e.g. SystemClock.elapsedRealtime()) for
     *                           stale-threshold and dwell tracking; injectable for deterministic tests.
     * @param preset             RouteResistancePreset used to map grade → resistance.
     */
    fun evaluate(
        currentResistance: Int,
        guidanceBaseline: Int,
        smoothedGradePercent: Double,
        upcomingPoints: List<RoutePoint>,
        currentPositionMeters: Double,
        speedMph: Float,
        toleranceMode: ManualResistanceTolerance,
        warningSeconds: Int,
        enabled: Boolean,
        isBikePlus: Boolean,
        timestampMs: Long,
        preset: RouteResistancePreset
    ): ManualResistanceGuidanceState {
        if (!enabled || isBikePlus) {
            reset()
            return ManualResistanceGuidanceState.Neutral
        }

        val rawTarget = instantaneousTarget(guidanceBaseline, smoothedGradePercent, preset)
        val targetCenter = stabilizeTarget(rawTarget, timestampMs)

        val halfBand = toleranceMode.halfBand
        val targetMin = (targetCenter - halfBand).coerceAtLeast(0)
        val targetMax = (targetCenter + halfBand).coerceAtMost(100)
        val delta = targetCenter - currentResistance
        val direction = delta.toDirection()

        // Hysteresis: once in-range, the rider must exceed the band by >1 unit before we flip
        // back to AdjustmentNeeded. This prevents rapid oscillation at the boundary.
        val exitBuffer = if (lastWasInRange) 1 else 0
        val isInRange = currentResistance in (targetMin - exitBuffer)..(targetMax + exitBuffer)

        return if (isInRange) {
            lastWasInRange = true
            outOfRangeStartMs = null

            checkUpcoming(
                currentResistance = currentResistance,
                guidanceBaseline = guidanceBaseline,
                currentTarget = targetCenter,
                currentPositionMeters = currentPositionMeters,
                upcomingPoints = upcomingPoints,
                speedMph = speedMph,
                warningSeconds = warningSeconds,
                toleranceMode = toleranceMode,
                preset = preset
            ) ?: ManualResistanceGuidanceState.InRange(
                currentResistance = currentResistance,
                targetCenter = targetCenter,
                targetMin = targetMin,
                targetMax = targetMax
            )
        } else {
            lastWasInRange = false
            val startMs = outOfRangeStartMs ?: timestampMs.also { outOfRangeStartMs = it }
            val outOfRangeDurationMs = timestampMs - startMs

            if (outOfRangeDurationMs >= StaleThresholdMs) {
                ManualResistanceGuidanceState.Stale(
                    currentResistance = currentResistance,
                    targetCenter = targetCenter,
                    targetMin = targetMin,
                    targetMax = targetMax,
                    delta = delta,
                    direction = direction
                )
            } else {
                ManualResistanceGuidanceState.AdjustmentNeeded(
                    currentResistance = currentResistance,
                    targetCenter = targetCenter,
                    targetMin = targetMin,
                    targetMax = targetMax,
                    delta = delta,
                    direction = direction
                )
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Debounces rapid target-center changes caused by rolling grade noise.
     * A newly computed [rawTarget] must remain stable for [PendingTargetDwellMs] before it
     * replaces the [committedTarget].  The very first call commits immediately so there is no
     * start-up delay.
     */
    private fun stabilizeTarget(rawTarget: Int, timestampMs: Long): Int {
        val current = committedTarget
        if (current == null) {
            committedTarget = rawTarget
            pendingTarget = null
            return rawTarget
        }
        if (rawTarget == current) {
            pendingTarget = null
            return current
        }
        // rawTarget differs from committed — track as pending
        if (rawTarget == pendingTarget) {
            if (timestampMs - pendingTargetFirstSeenMs >= PendingTargetDwellMs) {
                committedTarget = rawTarget
                pendingTarget = null
                return rawTarget
            }
        } else {
            // New candidate — reset dwell clock
            pendingTarget = rawTarget
            pendingTargetFirstSeenMs = timestampMs
        }
        return current
    }

    /**
     * Compute the ideal (non-rate-limited) target resistance for [gradePercent] relative to
     * [baseline].
     *
     * The floor ([RouteResistancePreset.baselineResistanceFloor]) is applied in both the uphill
     * and downhill directions so that guidance never advertises a target below the preset minimum
     * — matching the floor enforced by [GradeResistanceMapper].  The ceiling is always 100.
     */
    private fun instantaneousTarget(
        baseline: Int,
        gradePercent: Double,
        preset: RouteResistancePreset
    ): Int {
        val effectiveBaseline = baseline
            .coerceAtLeast(preset.baselineResistanceFloor)
            .coerceIn(0, 100)
        val adjustment = if (gradePercent >= 0.0) {
            gradePercent * preset.uphillResistancePerGrade
        } else {
            gradePercent * preset.downhillResistancePerGrade
        }
        val raw = effectiveBaseline + adjustment.roundToInt()
        return raw.coerceAtLeast(preset.baselineResistanceFloor).coerceIn(0, 100)
    }

    /**
     * Scan the upcoming route window for the first grade segment whose target resistance would
     * differ from [currentTarget] by more than [toleranceMode]'s half-band.  If found within
     * [warningSeconds], return an [ManualResistanceGuidanceState.Upcoming] state.
     *
     * Returns null if no qualifying transition is found or if speed/warning are zero.
     */
    private fun checkUpcoming(
        currentResistance: Int,
        guidanceBaseline: Int,
        currentTarget: Int,
        currentPositionMeters: Double,
        upcomingPoints: List<RoutePoint>,
        speedMph: Float,
        warningSeconds: Int,
        toleranceMode: ManualResistanceTolerance,
        preset: RouteResistancePreset
    ): ManualResistanceGuidanceState.Upcoming? {
        if (warningSeconds <= 0 || speedMph < 0.1f || upcomingPoints.size < 2) return null

        val speedMetersPerSecond = speedMph.toDouble() * MetersPerMile / 3600.0
        val halfBand = toleranceMode.halfBand

        for ((start, end) in upcomingPoints.zipWithNext()) {
            val startElev = start.elevationMeters ?: continue
            val endElev = end.elevationMeters ?: continue
            val segmentDistance = end.distanceFromStartMeters - start.distanceFromStartMeters
            if (segmentDistance <= 0.0) continue

            val segmentGrade = ((endElev - startElev) / segmentDistance) * 100.0
            val segmentTarget = instantaneousTarget(guidanceBaseline, segmentGrade, preset)

            if (abs(segmentTarget - currentTarget) > halfBand) {
                val distanceToTransitionMeters =
                    (start.distanceFromStartMeters - currentPositionMeters).coerceAtLeast(0.0)
                val etaSeconds = (distanceToTransitionMeters / speedMetersPerSecond).toInt()

                if (etaSeconds <= warningSeconds) {
                    val upcomingDelta = segmentTarget - currentResistance
                    return ManualResistanceGuidanceState.Upcoming(
                        currentResistance = currentResistance,
                        targetCenter = segmentTarget,
                        targetMin = (segmentTarget - halfBand).coerceAtLeast(0),
                        targetMax = (segmentTarget + halfBand).coerceAtMost(100),
                        delta = upcomingDelta,
                        direction = upcomingDelta.toDirection(),
                        etaSeconds = etaSeconds
                    )
                }
                // First significant transition found but outside warning window — stop scanning.
                break
            }
        }
        return null
    }

    private fun Int.toDirection(): ResistanceDirection = when {
        this > 0 -> ResistanceDirection.Up
        this < 0 -> ResistanceDirection.Down
        else -> ResistanceDirection.Hold
    }
}

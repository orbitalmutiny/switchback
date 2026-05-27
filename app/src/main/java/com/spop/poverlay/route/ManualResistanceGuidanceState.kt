package com.spop.poverlay.route

sealed class ManualResistanceGuidanceState {

    /** No active route, feature disabled, or Bike+ auto-control context — nothing to show. */
    object Neutral : ManualResistanceGuidanceState()

    /**
     * Rider is currently in range but a grade transition is approaching within the warning window.
     * UI should prompt the rider to prepare for the upcoming change.
     */
    data class Upcoming(
        val currentResistance: Int,
        val targetCenter: Int,
        val targetMin: Int,
        val targetMax: Int,
        /** Signed delta from current resistance to upcoming target center. */
        val delta: Int,
        val direction: ResistanceDirection,
        /** Estimated seconds until the grade transition arrives. */
        val etaSeconds: Int
    ) : ManualResistanceGuidanceState()

    /**
     * Rider's resistance is outside the target band and adjustment is needed.
     */
    data class AdjustmentNeeded(
        val currentResistance: Int,
        val targetCenter: Int,
        val targetMin: Int,
        val targetMax: Int,
        /** Signed delta: positive = need to go up, negative = need to go down. */
        val delta: Int,
        val direction: ResistanceDirection
    ) : ManualResistanceGuidanceState()

    /**
     * Rider's resistance is within the target band. No action needed.
     */
    data class InRange(
        val currentResistance: Int,
        val targetCenter: Int,
        val targetMin: Int,
        val targetMax: Int
    ) : ManualResistanceGuidanceState()

    /**
     * Rider has been outside the target band for too long without adjusting.
     * UI shifts to a muted "stale" presentation to avoid nagging.
     */
    data class Stale(
        val currentResistance: Int,
        val targetCenter: Int,
        val targetMin: Int,
        val targetMax: Int,
        val delta: Int,
        val direction: ResistanceDirection
    ) : ManualResistanceGuidanceState()
}

enum class ResistanceDirection { Up, Down, Hold }

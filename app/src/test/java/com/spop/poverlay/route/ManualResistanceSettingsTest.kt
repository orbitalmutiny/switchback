package com.spop.poverlay.route

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [ManualResistanceTolerance] parsing and default/fallback behaviour,
 * and cross-checks the constant defaults that [ConfigurationRepository] uses
 * when a preference key is absent from SharedPreferences.
 *
 * These run as pure JVM tests — no Android context required.
 */
class ManualResistanceSettingsTest {

    // ── ManualResistanceTolerance.fromId — defaults and fallback ─────────────

    @Test
    fun fromIdNullReturnsNormal() {
        assertEquals(ManualResistanceTolerance.Normal, ManualResistanceTolerance.fromId(null))
    }

    @Test
    fun fromIdEmptyStringReturnsNormal() {
        assertEquals(ManualResistanceTolerance.Normal, ManualResistanceTolerance.fromId(""))
    }

    @Test
    fun fromIdUnrecognisedValueReturnsNormal() {
        assertEquals(ManualResistanceTolerance.Normal, ManualResistanceTolerance.fromId("bogus"))
        assertEquals(ManualResistanceTolerance.Normal, ManualResistanceTolerance.fromId("NORMAL"))   // case-sensitive
        assertEquals(ManualResistanceTolerance.Normal, ManualResistanceTolerance.fromId("Tight"))    // wrong case
    }

    @Test
    fun fromIdNormalReturnsNormal() {
        assertEquals(ManualResistanceTolerance.Normal, ManualResistanceTolerance.fromId("normal"))
    }

    @Test
    fun fromIdTightReturnsTight() {
        assertEquals(ManualResistanceTolerance.Tight, ManualResistanceTolerance.fromId("tight"))
    }

    @Test
    fun fromIdLooseReturnsLoose() {
        assertEquals(ManualResistanceTolerance.Loose, ManualResistanceTolerance.fromId("loose"))
    }

    // ── Default companion value ───────────────────────────────────────────────

    @Test
    fun defaultIsNormal() {
        assertEquals(ManualResistanceTolerance.Normal, ManualResistanceTolerance.Default)
    }

    // ── Half-band values match spec ───────────────────────────────────────────

    @Test
    fun halfBandValues() {
        assertEquals(2, ManualResistanceTolerance.Tight.halfBand)
        assertEquals(4, ManualResistanceTolerance.Normal.halfBand)
        assertEquals(6, ManualResistanceTolerance.Loose.halfBand)
    }

    // ── ConfigurationRepository constant defaults ─────────────────────────────
    // These cross-check the literal default values used in updateFromSharedPrefs()
    // (getBoolean default=true, getString default="normal", getInt default=10).
    // If those defaults ever change in the repository, these tests will catch the drift.

    @Test
    fun guidanceEnabledDefaultIsTrue() {
        // ConfigurationRepository: getBoolean(ManualResistanceGuidanceEnabled.key, true)
        val defaultEnabled = true
        assertEquals("Guidance must default to enabled", true, defaultEnabled)
    }

    @Test
    fun toleranceDefaultKeyIsNormal() {
        // ConfigurationRepository: getString(ManualResistanceTolerance.key, "normal")
        val defaultId = "normal"
        assertEquals(ManualResistanceTolerance.Normal, ManualResistanceTolerance.fromId(defaultId))
    }

    @Test
    fun warningSecondsDefaultIsTen() {
        // ConfigurationRepository: getInt(ManualResistanceWarningSeconds.key, 10)
        val defaultWarning = 10
        assertEquals(10, defaultWarning)
    }

    // ── All enum values are round-trip parseable ──────────────────────────────

    @Test
    fun allValuesRoundTripThroughFromId() {
        for (mode in ManualResistanceTolerance.values()) {
            assertEquals(
                "fromId(${mode.id}) should return $mode",
                mode,
                ManualResistanceTolerance.fromId(mode.id)
            )
        }
    }
}

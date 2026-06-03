package com.spop.poverlay

import android.content.SharedPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.spop.poverlay.overlay.AutoResistanceController
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for the SharedPreferences → ConfigurationRepository StateFlow chain.
 *
 * Validates three behaviours required by the auto-resistance bug-fix pass:
 *   1. setRouteResistanceSimulationEnabled(true/false) immediately updates the StateFlow.
 *   2. The persisted value survives re-instantiation of the repository (same SharedPreferences).
 *   3. The 3-strike controller → repo → StateFlow chain disables and re-enables correctly.
 *
 * All tests run on the JVM using [FakeSharedPreferences] and a no-op [FakeLifecycleOwner].
 */
class ConfigurationRepositoryIntegrationTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repo: ConfigurationRepository

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        repo = ConfigurationRepository(fakePrefs, FakeLifecycleOwner())
    }

    @After
    fun tearDown() {
        repo.close()
        ConfigurationRepository.SharedPreferenceListeners.clear()
    }

    // ── 1. StateFlow reflects write immediately ───────────────────────────────────────────────

    @Test
    fun setEnabled_true_stateFlowReadsTrue() {
        repo.setRouteResistanceSimulationEnabled(true)
        assertTrue(
            "StateFlow must read true immediately after setRouteResistanceSimulationEnabled(true)",
            repo.routeResistanceSimulationEnabled.value
        )
    }

    @Test
    fun setEnabled_false_stateFlowReadsFalse() {
        repo.setRouteResistanceSimulationEnabled(true)
        repo.setRouteResistanceSimulationEnabled(false)
        assertFalse(
            "StateFlow must read false immediately after setRouteResistanceSimulationEnabled(false)",
            repo.routeResistanceSimulationEnabled.value
        )
    }

    // ── 2. Value survives re-instantiation (simulates app restart with same SharedPreferences) ─

    @Test
    fun enabledTrue_persistsAcrossRepoReinstantiation() {
        repo.setRouteResistanceSimulationEnabled(true)
        repo.close()

        val repo2 = ConfigurationRepository(fakePrefs, FakeLifecycleOwner())
        try {
            assertTrue(
                "Enabled=true must survive re-instantiation of ConfigurationRepository",
                repo2.routeResistanceSimulationEnabled.value
            )
        } finally {
            repo2.close()
        }
    }

    @Test
    fun enabledFalse_persistsAcrossRepoReinstantiation() {
        repo.setRouteResistanceSimulationEnabled(true)
        repo.setRouteResistanceSimulationEnabled(false)
        repo.close()

        val repo2 = ConfigurationRepository(fakePrefs, FakeLifecycleOwner())
        try {
            assertFalse(
                "Enabled=false must survive re-instantiation of ConfigurationRepository",
                repo2.routeResistanceSimulationEnabled.value
            )
        } finally {
            repo2.close()
        }
    }

    // ── 3. AutoResistanceController 3-strike → repo → StateFlow chain ────────────────────────

    @Test
    fun thirdStrike_callsDisable_stateFlowReadsFalse() {
        repo.setRouteResistanceSimulationEnabled(true)
        assertTrue("Sanity: auto is enabled", repo.routeResistanceSimulationEnabled.value)

        val controller = AutoResistanceController(strikeThreshold = 3)
        val t0 = 1_000_000L

        assertFalse("Strike 1 must not signal disable", controller.recordManualOverride(t0))
        assertFalse("Strike 2 must not signal disable", controller.recordManualOverride(t0 + 1_000L))
        val shouldDisable = controller.recordManualOverride(t0 + 2_000L)
        assertTrue("Strike 3 must signal disable", shouldDisable)

        // Simulate the ViewModel calling the repo when controller signals disable.
        if (shouldDisable) repo.setRouteResistanceSimulationEnabled(false)

        assertFalse(
            "StateFlow must read false after 3-strike disable + repo.setRouteResistanceSimulationEnabled(false)",
            repo.routeResistanceSimulationEnabled.value
        )
    }

    @Test
    fun afterDisable_reEnable_stateFlowReadsTrue() {
        repo.setRouteResistanceSimulationEnabled(true)
        repo.setRouteResistanceSimulationEnabled(false)
        assertFalse("Sanity: disabled", repo.routeResistanceSimulationEnabled.value)

        repo.setRouteResistanceSimulationEnabled(true)
        assertTrue(
            "StateFlow must read true after re-enabling from Settings",
            repo.routeResistanceSimulationEnabled.value
        )
    }

    @Test
    fun afterDisable_reEnableAndDisableAgain_stateFlowReadsFalse() {
        // Full two-cycle sequence: disable → re-enable → disable again.
        val controller = AutoResistanceController(strikeThreshold = 3)
        val t0 = 1_000_000L

        repo.setRouteResistanceSimulationEnabled(true)

        // Cycle 1: 3 strikes → disable
        controller.recordManualOverride(t0)
        controller.recordManualOverride(t0 + 500L)
        val disabled1 = controller.recordManualOverride(t0 + 1_000L)
        if (disabled1) repo.setRouteResistanceSimulationEnabled(false)
        assertFalse("Cycle 1: auto must be disabled", repo.routeResistanceSimulationEnabled.value)

        // User re-enables in Settings
        repo.setRouteResistanceSimulationEnabled(true)
        controller.onAutoReEnabled()
        assertTrue("After re-enable: auto must be on", repo.routeResistanceSimulationEnabled.value)

        // Cycle 2: 3 more strikes → disable again
        controller.recordManualOverride(t0 + 60_000L)
        controller.recordManualOverride(t0 + 61_000L)
        val disabled2 = controller.recordManualOverride(t0 + 62_000L)
        if (disabled2) repo.setRouteResistanceSimulationEnabled(false)
        assertFalse("Cycle 2: auto must be disabled again", repo.routeResistanceSimulationEnabled.value)
    }
}

// ── Test doubles ──────────────────────────────────────────────────────────────────────────────

private class FakeLifecycleOwner : LifecycleOwner {
    override fun getLifecycle(): Lifecycle = NoOpLifecycle
}

private object NoOpLifecycle : Lifecycle() {
    override fun addObserver(observer: LifecycleObserver) {}
    override fun removeObserver(observer: LifecycleObserver) {}
    override fun getCurrentState(): State = State.STARTED
}

class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, Any?> = data.toMap()
    override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners.remove(listener)
    }

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
            apply { pending[key] = values }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun remove(key: String): SharedPreferences.Editor =
            apply { removals.add(key) }
        override fun clear(): SharedPreferences.Editor =
            apply { clearAll = true }

        override fun commit(): Boolean {
            flush()
            return true
        }

        override fun apply() {
            flush()
        }

        private fun flush() {
            if (clearAll) data.clear()
            removals.forEach { data.remove(it) }
            data.putAll(pending)
            val snapshot = listeners.toList()
            snapshot.forEach { it.onSharedPreferenceChanged(this@FakeSharedPreferences, null) }
        }
    }
}

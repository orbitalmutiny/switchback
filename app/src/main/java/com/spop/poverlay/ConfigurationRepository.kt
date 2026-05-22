package com.spop.poverlay

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow

class ConfigurationRepository(context: Context, lifecycleOwner: LifecycleOwner) : AutoCloseable {

    enum class Preferences(val key: String) {
        ShowTimerWhenMinimized("showTimerWhenMinimized"),
        BikePlusResistanceControlEnabled("bikePlusResistanceControlEnabled"),
        BikePlusResistanceControlOverlayVisible("bikePlusResistanceControlOverlayVisible"),
        RouteResistanceSimulationEnabled("routeResistanceSimulationEnabled"),
        RouteResistancePreset("routeResistancePreset"),
        HeartRateMonitorEnabled("heartRateMonitorEnabled"),
        RideSessionRecordingEnabled("rideSessionRecordingEnabled"),
        ActiveRouteId("activeRouteId"),
        ActiveRoutePositionMeters("activeRoutePositionMeters"),
        HudDockLocation("hudDockLocation"),
        HudCollapsed("hudCollapsed"),
        HudShowPower("hudShowPower"),
        HudShowSpeed("hudShowSpeed"),
        HudShowDistance("hudShowDistance"),
        HudShowTime("hudShowTime"),
        HudShowResistance("hudShowResistance"),
        HudShowHeartRate("hudShowHeartRate"),
        HudShowCalories("hudShowCalories")
    }

    companion object {
        const val SharedPrefsName = "configuration"
        // This workaround is required since SharedPreferences
        // only stores weak references to objects
        val SharedPreferenceListeners =
            mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    }

    private val mutableShowTimerWhenMinimized = MutableStateFlow(true)
    private val mutableBikePlusResistanceControlEnabled = MutableStateFlow(false)
    private val mutableBikePlusResistanceControlOverlayVisible = MutableStateFlow(false)
    private val mutableRouteResistanceSimulationEnabled = MutableStateFlow(false)
    private val mutableRouteResistancePreset = MutableStateFlow("standard")
    private val mutableHeartRateMonitorEnabled = MutableStateFlow(false)
    private val mutableRideSessionRecordingEnabled = MutableStateFlow(false)
    private val mutableActiveRouteId = MutableStateFlow<String?>(null)
    private val mutableActiveRoutePositionMeters = MutableStateFlow(0.0)
    private val mutableHudDockLocation = MutableStateFlow("bottom")
    private val mutableHudCollapsed = MutableStateFlow(false)
    private val mutableHudShowPower = MutableStateFlow(true)
    private val mutableHudShowSpeed = MutableStateFlow(true)
    private val mutableHudShowDistance = MutableStateFlow(true)
    private val mutableHudShowTime = MutableStateFlow(true)
    private val mutableHudShowResistance = MutableStateFlow(true)
    private val mutableHudShowHeartRate = MutableStateFlow(true)
    private val mutableHudShowCalories = MutableStateFlow(true)

    val showTimerWhenMinimized = mutableShowTimerWhenMinimized
    val bikePlusResistanceControlEnabled = mutableBikePlusResistanceControlEnabled
    val bikePlusResistanceControlOverlayVisible = mutableBikePlusResistanceControlOverlayVisible
    val routeResistanceSimulationEnabled = mutableRouteResistanceSimulationEnabled
    val routeResistancePreset = mutableRouteResistancePreset
    val heartRateMonitorEnabled = mutableHeartRateMonitorEnabled
    val rideSessionRecordingEnabled = mutableRideSessionRecordingEnabled
    val activeRouteId = mutableActiveRouteId
    val activeRoutePositionMeters = mutableActiveRoutePositionMeters
    val hudDockLocation = mutableHudDockLocation
    val hudCollapsed = mutableHudCollapsed
    val hudShowPower = mutableHudShowPower
    val hudShowSpeed = mutableHudShowSpeed
    val hudShowDistance = mutableHudShowDistance
    val hudShowTime = mutableHudShowTime
    val hudShowResistance = mutableHudShowResistance
    val hudShowHeartRate = mutableHudShowHeartRate
    val hudShowCalories = mutableHudShowCalories

    private val sharedPreferences: SharedPreferences

    // Must be kept as reference, unowned lambda would be garbage collected
    private fun createSharedPreferencesListener() =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updateFromSharedPrefs()
        }

    private val listener : SharedPreferences.OnSharedPreferenceChangeListener

    init {
        sharedPreferences = context.getSharedPreferences(SharedPrefsName, Context.MODE_PRIVATE)
        updateFromSharedPrefs()

        listener = createSharedPreferencesListener()
        SharedPreferenceListeners.add(listener)
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver{
            override fun onStop(owner: LifecycleOwner) {
                close()
            }
        })
    }

    fun setShowTimerWhenMinimized(isShown: Boolean) {
        sharedPreferences.edit {
            putBoolean(Preferences.ShowTimerWhenMinimized.key, isShown)
        }
    }

    fun setBikePlusResistanceControlEnabled(isEnabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(Preferences.BikePlusResistanceControlEnabled.key, isEnabled)
            if (!isEnabled) {
                putBoolean(Preferences.BikePlusResistanceControlOverlayVisible.key, false)
                putBoolean(Preferences.RouteResistanceSimulationEnabled.key, false)
            }
        }
    }

    fun setBikePlusResistanceControlOverlayVisible(isVisible: Boolean) {
        sharedPreferences.edit {
            putBoolean(Preferences.BikePlusResistanceControlOverlayVisible.key, isVisible)
        }
    }

    fun setRouteResistanceSimulationEnabled(isEnabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(Preferences.RouteResistanceSimulationEnabled.key, isEnabled)
        }
    }

    fun setRouteResistancePreset(presetId: String) {
        sharedPreferences.edit {
            putString(Preferences.RouteResistancePreset.key, presetId)
        }
    }

    fun setHeartRateMonitorEnabled(isEnabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(Preferences.HeartRateMonitorEnabled.key, isEnabled)
        }
    }

    fun setRideSessionRecordingEnabled(isEnabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(Preferences.RideSessionRecordingEnabled.key, isEnabled)
        }
    }

    fun setActiveRouteId(routeId: String?) {
        sharedPreferences.edit {
            if (routeId == null) {
                remove(Preferences.ActiveRouteId.key)
                remove(Preferences.ActiveRoutePositionMeters.key)
            } else {
                putString(Preferences.ActiveRouteId.key, routeId)
            }
        }
    }

    fun setActiveRoutePositionMeters(positionMeters: Double) {
        sharedPreferences.edit {
            putFloat(Preferences.ActiveRoutePositionMeters.key, positionMeters.toFloat().coerceAtLeast(0f))
        }
    }

    fun setHudDockLocation(location: String) {
        sharedPreferences.edit {
            putString(Preferences.HudDockLocation.key, location)
        }
    }

    fun setHudCollapsed(isCollapsed: Boolean) {
        sharedPreferences.edit {
            putBoolean(Preferences.HudCollapsed.key, isCollapsed)
        }
    }

    fun setHudFieldVisible(preference: Preferences, isVisible: Boolean) {
        sharedPreferences.edit {
            putBoolean(preference.key, isVisible)
        }
    }

    fun resetHudSettings() {
        sharedPreferences.edit {
            putString(Preferences.HudDockLocation.key, "bottom")
            putBoolean(Preferences.HudCollapsed.key, false)
            putBoolean(Preferences.HudShowPower.key, true)
            putBoolean(Preferences.HudShowSpeed.key, true)
            putBoolean(Preferences.HudShowDistance.key, true)
            putBoolean(Preferences.HudShowTime.key, true)
            putBoolean(Preferences.HudShowResistance.key, true)
            putBoolean(Preferences.HudShowHeartRate.key, true)
            putBoolean(Preferences.HudShowCalories.key, true)
        }
    }

    private fun updateFromSharedPrefs() {
        mutableShowTimerWhenMinimized.value =
            sharedPreferences
                .getBoolean(Preferences.ShowTimerWhenMinimized.key, true)
        mutableBikePlusResistanceControlEnabled.value =
            sharedPreferences
                .getBoolean(Preferences.BikePlusResistanceControlEnabled.key, false)
        mutableBikePlusResistanceControlOverlayVisible.value =
            sharedPreferences
                .getBoolean(Preferences.BikePlusResistanceControlOverlayVisible.key, false)
        mutableRouteResistanceSimulationEnabled.value =
            sharedPreferences
                .getBoolean(Preferences.RouteResistanceSimulationEnabled.key, false)
        mutableRouteResistancePreset.value =
            sharedPreferences
                .getString(Preferences.RouteResistancePreset.key, "standard") ?: "standard"
        mutableHeartRateMonitorEnabled.value =
            sharedPreferences
                .getBoolean(Preferences.HeartRateMonitorEnabled.key, false)
        mutableRideSessionRecordingEnabled.value =
            sharedPreferences
                .getBoolean(Preferences.RideSessionRecordingEnabled.key, false)
        mutableActiveRouteId.value =
            sharedPreferences
                .getString(Preferences.ActiveRouteId.key, null)
        mutableActiveRoutePositionMeters.value =
            sharedPreferences
                .getFloat(Preferences.ActiveRoutePositionMeters.key, 0f)
                .toDouble()
        mutableHudDockLocation.value =
            sharedPreferences
                .getString(Preferences.HudDockLocation.key, "bottom") ?: "bottom"
        mutableHudCollapsed.value =
            sharedPreferences
                .getBoolean(Preferences.HudCollapsed.key, false)
        mutableHudShowPower.value =
            sharedPreferences
                .getBoolean(Preferences.HudShowPower.key, true)
        mutableHudShowSpeed.value =
            sharedPreferences
                .getBoolean(Preferences.HudShowSpeed.key, true)
        mutableHudShowDistance.value =
            sharedPreferences
                .getBoolean(Preferences.HudShowDistance.key, true)
        mutableHudShowTime.value =
            sharedPreferences
                .getBoolean(Preferences.HudShowTime.key, true)
        mutableHudShowResistance.value =
            sharedPreferences
                .getBoolean(Preferences.HudShowResistance.key, true)
        mutableHudShowHeartRate.value =
            sharedPreferences
                .getBoolean(Preferences.HudShowHeartRate.key, true)
        mutableHudShowCalories.value =
            sharedPreferences
                .getBoolean(Preferences.HudShowCalories.key, true)

    }

    override fun close() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        SharedPreferenceListeners.remove(listener)
    }
}

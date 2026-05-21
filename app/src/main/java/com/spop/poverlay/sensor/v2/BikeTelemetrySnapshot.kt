package com.spop.poverlay.sensor.v2

import android.os.SystemClock
import com.spop.poverlay.sensor.BikeData

data class BikeTelemetrySnapshot(
    val timestampMs: Long,
    val cadenceRpm: Float,
    val powerWatts: Float,
    val currentResistance: Int,
    val targetResistance: Int,
    val powerZoneAutoFollowEnabled: Boolean,
    val powerZoneAutoFollowStatus: Int,
    val powerZoneAutoFollowTargetResistance: Float
)

fun BikeData.toTelemetrySnapshot(
    timestampMs: Long = SystemClock.elapsedRealtime()
) = BikeTelemetrySnapshot(
    timestampMs = timestampMs,
    cadenceRpm = rpm.toFloat(),
    powerWatts = power.toFloat() / 100.0f,
    currentResistance = currentResistance,
    targetResistance = targetResistance,
    powerZoneAutoFollowEnabled = powerZoneAutoFollowEnabled != 0,
    powerZoneAutoFollowStatus = powerZoneAutoFollowStatus,
    powerZoneAutoFollowTargetResistance = powerZoneAutoFollowTargetResistance
)

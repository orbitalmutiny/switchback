package com.spop.poverlay.sensor.v2

import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.spop.poverlay.BuildConfig
import com.spop.poverlay.ConfigurationRepository
import com.spop.poverlay.util.IsBikePlus
import kotlinx.coroutines.flow.first
import timber.log.Timber

private const val MIN_RESISTANCE = 0
private const val MAX_RESISTANCE = 100
private const val MIN_WRITE_INTERVAL_MS = 500L
private const val ResistanceLogTag = "GrupettoResistance"

fun setResistance(binder: IBinder, resistance: Int) {
    val safeResistance = resistance.coerceIn(MIN_RESISTANCE, MAX_RESISTANCE)
    Log.i(ResistanceLogTag, "Binder setResistance start: requested=$resistance clamped=$safeResistance")
    BikePlusBinderClient(binder).setResistance(safeResistance)
    Log.i(ResistanceLogTag, "Binder setResistance transacted: resistance=$safeResistance")
}

private fun setResistance(binderClient: BikePlusBinderClient, resistance: Int) {
    val safeResistance = resistance.coerceIn(MIN_RESISTANCE, MAX_RESISTANCE)
    Log.i(ResistanceLogTag, "Binder setResistance start: requested=$resistance clamped=$safeResistance")
    binderClient.setResistance(safeResistance)
    Log.i(ResistanceLogTag, "Binder setResistance transacted: resistance=$safeResistance")
}

class BikePlusResistanceControl(
    private val bikePlusBinderClient: BikePlusBinderClient,
    private val configurationRepository: ConfigurationRepository
) {
    private var lastWriteAtMs = 0L

    constructor(
        binder: IBinder,
        configurationRepository: ConfigurationRepository
    ) : this(BikePlusBinderClient(binder), configurationRepository)

    suspend fun setResistance(resistance: Int): Result<Int> {
        val safeResistance = resistance.coerceIn(MIN_RESISTANCE, MAX_RESISTANCE)
        Log.i(ResistanceLogTag, "Resistance write requested: requested=$resistance clamped=$safeResistance")
        Timber.i("Bike+ resistance write requested: requested=$resistance clamped=$safeResistance")

        if (!BuildConfig.ENABLE_BIKE_PLUS_RESISTANCE_CONTROL) {
            Log.i(ResistanceLogTag, "Resistance write blocked: build flag disabled")
            return Result.failure(IllegalStateException("Bike+ resistance control is disabled for this build"))
        }

        if (!IsBikePlus) {
            Log.i(ResistanceLogTag, "Resistance write blocked: not Bike+")
            return Result.failure(IllegalStateException("Bike+ resistance control is only available on Bike+"))
        }

        if (!configurationRepository.bikePlusResistanceControlEnabled.first()) {
            Log.i(ResistanceLogTag, "Resistance write blocked: setting disabled")
            return Result.failure(IllegalStateException("Bike+ resistance control setting is disabled"))
        }

        val now = SystemClock.elapsedRealtime()
        val elapsedSinceLastWrite = now - lastWriteAtMs
        if (lastWriteAtMs != 0L && elapsedSinceLastWrite < MIN_WRITE_INTERVAL_MS) {
            Log.i(ResistanceLogTag, "Resistance write blocked: rate limited elapsedMs=$elapsedSinceLastWrite")
            return Result.failure(
                IllegalStateException("Bike+ resistance write rate limited")
            )
        }

        return try {
            setResistance(bikePlusBinderClient, safeResistance)
            lastWriteAtMs = now
            Log.i(ResistanceLogTag, "Resistance write sent: resistance=$safeResistance")
            Timber.i("Bike+ resistance write sent: resistance=$safeResistance")
            Result.success(safeResistance)
        } catch (e: Exception) {
            Log.e(ResistanceLogTag, "Resistance write failed", e)
            Timber.e(e, "Bike+ resistance write failed")
            Result.failure(e)
        }
    }
}

package com.spop.poverlay.sensor.v2

import android.os.IBinder
import com.spop.poverlay.ConfigurationRepository

class BikePlusService(
    binder: IBinder,
    configurationRepository: ConfigurationRepository
) {
    private val binderClient = BikePlusBinderClient(binder)
    private val resistanceControl = BikePlusResistanceControl(
        binderClient,
        configurationRepository
    )

    fun readTelemetrySnapshot(): BikeTelemetrySnapshot =
        binderClient.readTelemetrySnapshot()

    suspend fun setResistance(resistance: Int): Result<Int> =
        resistanceControl.setResistance(resistance)
}

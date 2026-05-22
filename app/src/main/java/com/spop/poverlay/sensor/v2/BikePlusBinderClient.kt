package com.spop.poverlay.sensor.v2

import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import com.spop.poverlay.sensor.BikeData

private const val TRANSACTION_SET_RESISTANCE = 7
private const val TRANSACTION_GET_BIKE_DATA = 14

class BikePlusBinderClient(private val binder: IBinder) {
    fun readBikeData(): BikeData {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_ACTION)
            synchronized(binder) {
                binder.transact(TRANSACTION_GET_BIKE_DATA, data, reply, 0)
            }
            reply.readException()
            reply.readInt()
            return BikeData.CREATOR.createFromParcel(reply)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun readTelemetrySnapshot(): BikeTelemetrySnapshot =
        readBikeData().toTelemetrySnapshot()

    fun setResistance(resistance: Int) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_ACTION)
            data.writeInt(resistance)
            val transacted = synchronized(binder) {
                binder.transact(TRANSACTION_SET_RESISTANCE, data, null, IBinder.FLAG_ONEWAY)
            }
            if (!transacted) {
                throw RemoteException("Bike+ resistance Binder transaction was not accepted")
            }
        } finally {
            data.recycle()
        }
    }
}

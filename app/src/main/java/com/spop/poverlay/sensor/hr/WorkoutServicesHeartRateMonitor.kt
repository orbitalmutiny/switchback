package com.spop.poverlay.sensor.hr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val LogTag = "SwitchbackNativeHR"

// ── IHeartRate (data) ──────────────────────────────────────────────────────────
private const val HrServiceAction = "com.onepeloton.workoutservices.heartrate.IHeartRate"
private const val HrServicePackage = "com.onepeloton.workoutservices.app"
private const val HrServiceClass =
    "com.onepeloton.workoutservices.services.heartrate.HeartRateService"
private const val HrServiceDescriptor = "com.onepeloton.workoutservices.heartrate.IHeartRate"
private const val HrCallbackDescriptor =
    "com.onepeloton.workoutservices.heartrate.IHeartRateCallback"

// ── IHeartRateConnection (activation / device management) ─────────────────────
// Action uses the singular form; the confirmed binder descriptor (from prior logs) is plural.
private const val HrConnectionServiceAction =
    "com.onepeloton.workoutservices.heartrateconnection.IHeartRateConnection"
private const val HrConnectionServiceClass =
    "com.onepeloton.workoutservices.services.heartrateconnection.HeartRateConnectionService"
// Descriptor as reported by binder.interfaceDescriptor — "connections" is plural.
private const val HrConnectionDescriptor =
    "com.onepeloton.workoutservices.heartrateconnections.IHeartRateConnection"
private const val HrConnectionCallbackDescriptor =
    "com.onepeloton.workoutservices.heartrateconnections.IHeartRateConnectionCallback"

// ── AIDL transaction codes ─────────────────────────────────────────────────────
// Both services follow the same AIDL convention: first declared method = code 1.
private const val TRANSACTION_REGISTER_CALLBACK = IBinder.FIRST_CALL_TRANSACTION      // 1
private const val TRANSACTION_UNREGISTER_CALLBACK = IBinder.FIRST_CALL_TRANSACTION + 1 // 2

// ── Bundle keys confirmed from device schema ───────────────────────────────────
private const val KEY_CALLBACK_TYPE = "hrm_tracking_callback_type_name"
private const val KEY_HR_DATA = "heartRateData"
private const val KEY_CALCULATED_HR = "calculatedHeartRate"
private const val KEY_HEARTBEAT_COUNT = "heartbeatCount"

// Active state is tried first. Inactive is the observed fallback state.
private val STATE_MAP_KEYS = listOf(
    "heart_rate_device_tracking_state_active_map",
    "heart_rate_device_tracking_state_inactive_map",
)

// Throttle "inactive only" log spam: log on first occurrence, then every 30 callbacks.
private const val INACTIVE_LOG_INTERVAL = 30

class WorkoutServicesHeartRateMonitor(private val context: Context) {
    private val _heartRateBpm = MutableStateFlow<Int?>(null)
    val heartRateBpm: StateFlow<Int?> = _heartRateBpm

    private var hrBinder: IBinder? = null
    private var hrConnectionBinder: IBinder? = null
    private var serviceConnection: ServiceConnection? = null
    private var connectionServiceConnection: ServiceConnection? = null

    @Volatile private var schemaLogged = false
    @Volatile private var inactiveOnlyCount = 0

    // ── IHeartRate callback stub ───────────────────────────────────────────────

    private val hrCallbackStub = object : Binder() {
        override fun getInterfaceDescriptor() = HrCallbackDescriptor

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val posStart = data.dataPosition()
            return when (code) {
                IBinder.FIRST_CALL_TRANSACTION -> {
                    try {
                        try {
                            data.enforceInterface(HrCallbackDescriptor)
                        } catch (e: SecurityException) {
                            Log.w(LogTag, "HR enforceInterface failed ($e) — resetting position")
                            data.setDataPosition(posStart)
                        }

                        val presence = data.readInt()
                        if (presence == 0) return true

                        val bundle = data.readBundle(
                            WorkoutServicesHeartRateMonitor::class.java.classLoader
                        ) ?: return true

                        if (!schemaLogged) {
                            schemaLogged = true
                            logBundleSchema("HR", bundle)
                        }

                        extractBpm(bundle)
                        true
                    } catch (e: Exception) {
                        Log.e(LogTag, "HR callback code=1 error", e)
                        false
                    }
                }
                else -> {
                    try { super.onTransact(code, data, reply, flags) }
                    catch (e: Exception) { false }
                }
            }
        }
    }

    // ── IHeartRateConnection callback stub ─────────────────────────────────────
    // We don't know the connection callback schema yet, so we log everything we receive.

    private val connectionCallbackStub = object : Binder() {
        override fun getInterfaceDescriptor() = HrConnectionCallbackDescriptor

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val posStart = data.dataPosition()
            Log.i(LogTag, "ConnSvc callback: code=$code dataSize=${data.dataSize()} flags=$flags")

            // Try the same bundle-reading pattern that worked for IHeartRate.
            if (code == IBinder.FIRST_CALL_TRANSACTION) {
                try {
                    try {
                        data.enforceInterface(HrConnectionCallbackDescriptor)
                        Log.i(LogTag, "ConnSvc callback code=1: enforceInterface OK")
                    } catch (e: SecurityException) {
                        Log.w(LogTag, "ConnSvc callback code=1: enforceInterface failed — resetting")
                        data.setDataPosition(posStart)
                    }
                    val presence = data.readInt()
                    Log.i(LogTag, "ConnSvc callback code=1: presence=$presence pos=${data.dataPosition()}")
                    if (presence != 0) {
                        val bundle = data.readBundle(
                            WorkoutServicesHeartRateMonitor::class.java.classLoader
                        )
                        if (bundle != null) {
                            logBundleSchema("ConnSvc", bundle)
                        } else {
                            // Presence was set but readBundle returned null — log raw ints.
                            data.setDataPosition(posStart)
                            val remaining = data.dataSize() - data.dataPosition()
                            Log.i(LogTag, "ConnSvc callback code=1: no bundle, $remaining raw bytes")
                            repeat(minOf(remaining / 4, 8)) {
                                Log.i(LogTag, "  raw[${it * 4}] = ${data.readInt()}")
                            }
                        }
                    }
                    return true
                } catch (e: Exception) {
                    Log.e(LogTag, "ConnSvc callback code=1 error", e)
                }
            } else {
                // Log unknown codes with raw leading ints for diagnostic value.
                val remaining = data.dataSize() - data.dataPosition()
                Log.i(LogTag, "ConnSvc callback code=$code: $remaining bytes")
                repeat(minOf(remaining / 4, 4)) {
                    Log.i(LogTag, "  raw[${it * 4}] = ${data.readInt()}")
                }
            }

            return try { super.onTransact(code, data, reply, flags) }
            catch (e: Exception) { false }
        }
    }

    // ── BPM extraction ─────────────────────────────────────────────────────────

    private fun extractBpm(bundle: android.os.Bundle) {
        val callbackType = bundle.getString(KEY_CALLBACK_TYPE) ?: "<unknown>"

        val stateMapKey = STATE_MAP_KEYS.firstOrNull { bundle.containsKey(it) }
        if (stateMapKey == null) {
            Log.w(LogTag, "HR type=$callbackType: no state map key in bundle")
            return
        }

        val isInactiveOnly = stateMapKey == "heart_rate_device_tracking_state_inactive_map"
        if (isInactiveOnly) {
            inactiveOnlyCount++
            if (inactiveOnlyCount == 1 || inactiveOnlyCount % INACTIVE_LOG_INTERVAL == 0) {
                Log.i(
                    LogTag,
                    "HR active-state map absent — only inactive map present" +
                    " (count=$inactiveOnlyCount type=$callbackType)"
                )
            }
        } else {
            if (inactiveOnlyCount > 0) {
                Log.i(LogTag, "HR active-state map now present after $inactiveOnlyCount inactive callbacks")
                inactiveOnlyCount = 0
            }
        }

        @Suppress("UNCHECKED_CAST")
        val stateMap = bundle.get(stateMapKey) as? Map<*, *> ?: run {
            Log.w(LogTag, "HR type=$callbackType map=$stateMapKey: not a Map")
            return
        }

        @Suppress("UNCHECKED_CAST")
        val hrData = stateMap[KEY_HR_DATA] as? Map<*, *> ?: run {
            Log.w(LogTag, "HR type=$callbackType map=$stateMapKey: no $KEY_HR_DATA")
            return
        }

        val calculated = (hrData[KEY_CALCULATED_HR] as? Number)?.toDouble() ?: 0.0
        val heartbeatCount = (hrData[KEY_HEARTBEAT_COUNT] as? Number)?.toInt()
        val newBpm = if (calculated > 0.0) calculated.toInt() else null

        if (newBpm != _heartRateBpm.value) {
            if (newBpm != null) {
                Log.i(
                    LogTag,
                    "HR native bpm: $newBpm" +
                    " (calculated=$calculated heartbeatCount=$heartbeatCount" +
                    " type=$callbackType map=$stateMapKey)"
                )
            } else {
                Log.i(
                    LogTag,
                    "HR bpm→null (calculated=$calculated heartbeatCount=$heartbeatCount" +
                    " type=$callbackType map=$stateMapKey) — BLE fallback active"
                )
            }
            _heartRateBpm.value = newBpm
        }
    }

    // ── Logging helpers ────────────────────────────────────────────────────────

    private fun logBundleSchema(label: String, bundle: android.os.Bundle) {
        val keys = try { bundle.keySet() } catch (e: Exception) {
            Log.e(LogTag, "$label bundle keySet() threw: $e"); return
        }
        Log.i(LogTag, "$label bundle schema (${keys.size} keys): $keys")
        for (key in keys) {
            try {
                val v = bundle.get(key)
                Log.i(LogTag, "  [$key] = $v  (${v?.javaClass?.simpleName ?: "null"})")
            } catch (e: Exception) {
                Log.w(LogTag, "  [$key] = <unreadable: ${e.javaClass.simpleName}>")
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    fun start() {
        if (serviceConnection != null) return
        bindHrService()
        bindHrConnectionService()
    }

    private fun bindHrService() {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null) { Log.w(LogTag, "HeartRateService: null binder"); return }
                Log.i(LogTag, "HeartRateService connected: descriptor=${binder.interfaceDescriptor}")
                hrBinder = binder
                transactRegister(
                    binder, HrServiceDescriptor, hrCallbackStub, "HeartRateService"
                )
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                Log.i(LogTag, "HeartRateService disconnected"); hrBinder = null
            }
            override fun onBindingDied(name: ComponentName?) {
                Log.w(LogTag, "HeartRateService binding died")
                serviceConnection = null; hrBinder = null
            }
            override fun onNullBinding(name: ComponentName?) {
                Log.w(LogTag, "HeartRateService null binding")
            }
        }
        val intent = Intent(HrServiceAction).apply {
            setPackage(HrServicePackage)
            setClassName(HrServicePackage, HrServiceClass)
        }
        val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        Log.i(LogTag, "bindService HeartRateService: bound=$bound")
        if (bound) serviceConnection = connection
    }

    private fun bindHrConnectionService() {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null) { Log.w(LogTag, "HeartRateConnectionService: null binder"); return }
                Log.i(
                    LogTag,
                    "HeartRateConnectionService connected: descriptor=${binder.interfaceDescriptor}"
                )
                hrConnectionBinder = binder
                // Apply the same AIDL convention we proved on IHeartRate: code 1 + callback stub.
                // The connection service may need a subscription/activation call to trigger
                // IHeartRate moving from inactive→active state.
                transactRegister(
                    binder, HrConnectionDescriptor, connectionCallbackStub, "HeartRateConnectionService"
                )
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                Log.i(LogTag, "HeartRateConnectionService disconnected"); hrConnectionBinder = null
            }
            override fun onBindingDied(name: ComponentName?) {
                Log.w(LogTag, "HeartRateConnectionService binding died")
                connectionServiceConnection = null; hrConnectionBinder = null
            }
            override fun onNullBinding(name: ComponentName?) {
                Log.w(LogTag, "HeartRateConnectionService null binding")
            }
        }
        val intent = Intent(HrConnectionServiceAction).apply {
            setPackage(HrServicePackage)
            setClassName(HrServicePackage, HrConnectionServiceClass)
        }
        val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        Log.i(LogTag, "bindService HeartRateConnectionService: bound=$bound")
        if (bound) connectionServiceConnection = connection
    }

    // Shared transact helper used by both services — same AIDL pattern in both cases.
    private fun transactRegister(
        binder: IBinder,
        descriptor: String,
        callbackStub: IBinder,
        label: String
    ) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptor)
            data.writeStrongBinder(callbackStub)
            val success = binder.transact(TRANSACTION_REGISTER_CALLBACK, data, reply, 0)
            Log.i(LogTag, "$label registerCallback transact returned=$success")
            if (success) {
                try {
                    reply.readException()
                    Log.i(LogTag, "$label registerCallback: no exception in reply")
                } catch (e: Exception) {
                    Log.w(LogTag, "$label registerCallback reply exception: $e")
                }
            }
        } catch (e: Exception) {
            Log.e(LogTag, "$label registerCallback transact threw", e)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun transactUnregister(binder: IBinder, descriptor: String, label: String) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptor)
            // Send the same stub so the service can match and remove it.
            val stub = if (descriptor == HrServiceDescriptor) hrCallbackStub else connectionCallbackStub
            data.writeStrongBinder(stub)
            binder.transact(TRANSACTION_UNREGISTER_CALLBACK, data, reply, 0)
            Log.i(LogTag, "$label unregisterCallback sent")
        } catch (e: Exception) {
            Log.w(LogTag, "$label unregisterCallback failed: $e")
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun stop() {
        hrBinder?.let { transactUnregister(it, HrServiceDescriptor, "HeartRateService") }
        hrConnectionBinder?.let {
            transactUnregister(it, HrConnectionDescriptor, "HeartRateConnectionService")
        }
        serviceConnection?.let { context.unbindService(it) }
        serviceConnection = null
        hrBinder = null
        connectionServiceConnection?.let { context.unbindService(it) }
        connectionServiceConnection = null
        hrConnectionBinder = null
        _heartRateBpm.value = null
        schemaLogged = false
        inactiveOnlyCount = 0
    }

    fun close() = stop()
}

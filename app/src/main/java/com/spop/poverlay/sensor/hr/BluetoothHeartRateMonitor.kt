package com.spop.poverlay.sensor.hr

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.UUID

class BluetoothHeartRateMonitor(
    private val context: Context
) : AutoCloseable {
    companion object {
        private const val LogTag = "SwitchbackHeartRate"

        private val HeartRateServiceUuid: UUID =
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HeartRateMeasurementUuid: UUID =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val ClientCharacteristicConfigUuid: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val ConnectTimeoutMs = 20_000L
    }

    private enum class ConnectMode {
        DirectLe,
        DirectDefault,
        AutoLe
    }

    private val mutableHeartRateBpm = MutableStateFlow<Int?>(null)
    val heartRateBpm: StateFlow<Int?> = mutableHeartRateBpm.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanning = false
    private var connecting = false
    private var enabled = false
    private var pendingDevice: BluetoothDevice? = null
    private var connectMode: ConnectMode? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!scanning || connecting || bluetoothGatt != null) {
                return
            }
            connecting = true
            pendingDevice = result.device
            val serviceUuids = result.scanRecord
                ?.serviceUuids
                ?.joinToString { it.uuid.toString() }
                ?: ""
            val connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result.isConnectable.toString()
            } else {
                "unknown"
            }
            Log.i(
                LogTag,
                "Heart rate monitor found: address=${result.device.address} name=${result.device.name} connectable=$connectable services=[$serviceUuids]"
            )
            Timber.i("Heart rate monitor found: ${result.device.address}")
            stopScan()
            bluetoothGatt?.close()
            val device = result.device
            mainHandler.postDelayed(
                {
                    if (!enabled || !connecting || bluetoothGatt != null) {
                        return@postDelayed
                    }
                    connectToDevice(device, ConnectMode.DirectLe)
                },
                900L
            )
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            Log.w(LogTag, "Heart rate scan failed: errorCode=$errorCode")
            Timber.w("Heart rate scan failed: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(LogTag, "Heart rate GATT status error: status=$status newState=$newState")
                Timber.w("Heart rate GATT status error: $status")
                closeGatt(gatt)
                if (enabled) {
                    val retryDevice = pendingDevice
                    val nextMode = nextConnectMode(connectMode)
                    if (nextMode != null && retryDevice != null) {
                        connecting = true
                        mainHandler.postDelayed(
                            {
                                if (enabled && bluetoothGatt == null) {
                                    connectToDevice(retryDevice, nextMode)
                                }
                            },
                            1500L
                        )
                    } else {
                        start()
                    }
                }
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connecting = false
                    Log.i(LogTag, "Heart rate monitor connected")
                    Timber.i("Heart rate monitor connected")
                    refreshDeviceCache(gatt)
                    mainHandler.postDelayed(
                        {
                            if (enabled && bluetoothGatt == gatt) {
                                Log.i(LogTag, "Heart rate service discovery starting")
                                gatt.discoverServices()
                            }
                        },
                        600L
                    )
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connecting = false
                    Log.i(LogTag, "Heart rate monitor disconnected")
                    Timber.i("Heart rate monitor disconnected")
                    mutableHeartRateBpm.value = null
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    if (enabled) {
                        start()
                    }
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(LogTag, "Heart rate service discovery failed: status=$status")
                Timber.w("Heart rate service discovery failed: $status")
                return
            }
            val characteristic = gatt
                .getService(HeartRateServiceUuid)
                ?.getCharacteristic(HeartRateMeasurementUuid)
            if (characteristic == null) {
                val services = gatt.services.joinToString { it.uuid.toString() }
                Log.w(LogTag, "Heart rate service was not present on connected device. services=[$services]")
                Timber.w("Heart rate service was not present on connected device")
                closeGatt(gatt)
                if (enabled) {
                    start()
                }
                return
            }
            subscribe(gatt, characteristic)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleHeartRateMeasurement(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleHeartRateMeasurement(characteristic.uuid, value)
        }

        private fun handleHeartRateMeasurement(uuid: UUID, value: ByteArray?) {
            if (uuid == HeartRateMeasurementUuid) {
                val bpm = parseHeartRateMeasurement(value)
                Log.i(LogTag, "Heart rate measurement received: bpm=$bpm")
                mutableHeartRateBpm.value = bpm
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        enabled = true
        if (!HeartRatePermissions.hasRequiredPermissions(context)) {
            Log.w(LogTag, "Heart rate scan skipped: missing Bluetooth permissions")
            Timber.w("Heart rate scan skipped: missing Bluetooth permissions")
            return
        }
        if (scanning || bluetoothGatt != null) {
            Log.i(LogTag, "Heart rate scan skipped: already active scanning=$scanning connecting=$connecting connected=${bluetoothGatt != null}")
            return
        }
        val bluetoothAdapter = bluetoothAdapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(LogTag, "Heart rate scan skipped: Bluetooth unavailable or disabled")
            Timber.w("Heart rate scan skipped: Bluetooth unavailable or disabled")
            return
        }
        val scanner = scanner
        if (scanner == null) {
            Log.w(LogTag, "Heart rate scan skipped: BLE scanner unavailable")
            Timber.w("Heart rate scan skipped: BLE scanner unavailable")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HeartRateServiceUuid))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanning = true
        Log.i(LogTag, "Heart rate scan started")
        Timber.i("Heart rate scan started")
        scanner.startScan(filters, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        enabled = false
        stopScan()
        disconnect()
        mutableHeartRateBpm.value = null
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) {
            return
        }
        scanning = false
        scanner?.stopScan(scanCallback)
        Log.i(LogTag, "Heart rate scan stopped")
        Timber.i("Heart rate scan stopped")
    }

    @SuppressLint("MissingPermission")
    private fun disconnect() {
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            closeGatt(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt) {
        if (bluetoothGatt == gatt) {
            bluetoothGatt = null
        }
        connecting = false
        gatt.close()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice, mode: ConnectMode) {
        connectMode = mode
        Log.i(
            LogTag,
            "Heart rate monitor connecting: address=${device.address} mode=$mode"
        )
        bluetoothGatt = connectGatt(device, mode)
        val expectedGatt = bluetoothGatt
        mainHandler.postDelayed(
            {
                if (enabled && connecting && bluetoothGatt == expectedGatt && expectedGatt != null) {
                    Log.w(LogTag, "Heart rate connection timed out: mode=$mode")
                    closeGatt(expectedGatt)
                    val retryDevice = pendingDevice
                    val nextMode = nextConnectMode(mode)
                    if (retryDevice != null && nextMode != null) {
                        connecting = true
                        connectToDevice(retryDevice, nextMode)
                    } else {
                        start()
                    }
                }
            },
            ConnectTimeoutMs
        )
    }

    @SuppressLint("MissingPermission")
    private fun connectGatt(device: BluetoothDevice, mode: ConnectMode): BluetoothGatt =
        when (mode) {
            ConnectMode.DirectLe ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(context, false, gattCallback)
                }
            ConnectMode.DirectDefault ->
                device.connectGatt(context, false, gattCallback)
            ConnectMode.AutoLe ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(context, true, gattCallback)
                }
        }

    private fun nextConnectMode(mode: ConnectMode?): ConnectMode? =
        when (mode) {
            ConnectMode.DirectLe -> ConnectMode.DirectDefault
            ConnectMode.DirectDefault -> ConnectMode.AutoLe
            ConnectMode.AutoLe,
            null -> null
        }

    private fun refreshDeviceCache(gatt: BluetoothGatt) {
        try {
            val refresh = gatt.javaClass.getMethod("refresh")
            val refreshed = refresh.invoke(gatt) as? Boolean ?: false
            Log.i(LogTag, "Heart rate GATT cache refresh requested: refreshed=$refreshed")
        } catch (e: Exception) {
            Log.i(LogTag, "Heart rate GATT cache refresh unavailable")
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribe(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        gatt.setCharacteristicNotification(characteristic, true)
        characteristic
            .getDescriptor(ClientCharacteristicConfigUuid)
            ?.let { descriptor ->
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        Log.i(LogTag, "Heart rate notifications enabled")
        Timber.i("Heart rate notifications enabled")
    }

    private fun parseHeartRateMeasurement(bytes: ByteArray?): Int? {
        if (bytes == null || bytes.size < 2) {
            return null
        }
        val isUint16 = bytes[0].toInt() and 0x01 == 0x01
        return if (isUint16) {
            if (bytes.size < 3) {
                null
            } else {
                (bytes[1].toInt() and 0xff) or ((bytes[2].toInt() and 0xff) shl 8)
            }
        } else {
            bytes[1].toInt() and 0xff
        }
    }

    override fun close() {
        stop()
    }
}

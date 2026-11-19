package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.service.NavManeuverType
import com.example.service.RelayStateBus
import com.example.service.RelayedNavManeuver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.sin

enum class BtConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class BtLinkMode(val label: String) {
    CLASSIC_OBD_SPP("OBD-II ELM327 (SPP)"),
    BLE_GATT("CB350RS BLE / Cluster")
}

data class ScannedBtDevice(
    val name: String,
    val address: String,
    val rssi: Int? = null,
    val bonded: Boolean = false,
    val looksLikeBikeOrObd: Boolean = false,
    val transportLabel: String = "Classic"
)

data class LiveObdTelemetry(
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val fuelLevelPercent: Int? = null,
    val engineTempC: Int? = null,
    val batteryVoltage: Double? = null,
    val throttlePercent: Int? = null,
    val engineLoadPercent: Int? = null,
    val storedDtcs: List<String> = emptyList(),
    val lastUpdatedMs: Long? = null,
    val ecuProtocol: String = "",
    val recentLog: List<String> = emptyList()
)

data class BluetoothUiState(
    val hardwareSupported: Boolean = true,
    val adapterEnabled: Boolean = false,
    val permissionsGranted: Boolean = false,
    val safetySimulationActive: Boolean = false,
    val connectionState: BtConnectionState = BtConnectionState.DISCONNECTED,
    val linkMode: BtLinkMode = BtLinkMode.CLASSIC_OBD_SPP,
    val connectedDevice: ScannedBtDevice? = null,
    val bondedDevices: List<ScannedBtDevice> = emptyList(),
    val discoveredDevices: List<ScannedBtDevice> = emptyList(),
    val statusMessage: String = "Ready to discover & link Honda CB350RS OBD-II",
    val telemetry: LiveObdTelemetry = LiveObdTelemetry()
)

/**
 * Handles Bluetooth discovery (Classic BluetoothAdapter discovery + BLE scanning) and
 * connection (RFCOMM SPP ELM327 OBD-II + BLE GATT) for the Honda CB350RS.
 */
class BluetoothManager(context: Context) {

    companion object {
        private const val TAG = "MotoLinkBluetoothMgr"
        // Standard Classic Serial Port Profile UUID for ELM327 OBD-II adapters on Honda Euro5 6-pin port
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        // Standard BLE Battery Service & Nordic UART Service UUIDs for dash/cluster telemetry
        private val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
        private val NUS_TX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private const val DISCOVERY_TIMEOUT_MS = 12_000L
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val systemBluetoothManager: SystemBluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? SystemBluetoothManager

    val bluetoothAdapter: BluetoothAdapter?
        get() = systemBluetoothManager?.adapter

    private val _state = MutableStateFlow(BluetoothUiState())
    val state: StateFlow<BluetoothUiState> = _state.asStateFlow()

    private var activeGatt: BluetoothGatt? = null
    private var writableGattChar: BluetoothGattCharacteristic? = null
    private var activeSocket: BluetoothSocket? = null
    private var sppInput: InputStream? = null
    private var sppOutput: OutputStream? = null
    private var pollingJob: Job? = null
    private var simulationJob: Job? = null
    private var discoveryTimeoutJob: Job? = null
    private var bleScanCallback: ScanCallback? = null
    private var discoveryReceiverRegistered = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device ?: return
                    val rssiShort = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    val rssi = if (rssiShort != Short.MIN_VALUE) rssiShort.toInt() else null
                    val rawName = try {
                        device.name
                    } catch (_: SecurityException) {
                        null
                    }
                    val displayName = rawName?.takeIf { it.isNotBlank() }
                        ?: "Bluetooth OBD/Device (${device.address.takeLast(5)})"
                    val isBonded = try {
                        device.bondState == BluetoothDevice.BOND_BONDED
                    } catch (_: SecurityException) {
                        false
                    }
                    recordDiscoveredDevice(
                        ScannedBtDevice(
                            name = displayName,
                            address = device.address,
                            rssi = rssi,
                            bonded = isBonded,
                            looksLikeBikeOrObd = isLikelyBikeOrObd(displayName),
                            transportLabel = "Classic"
                        )
                    )
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _state.update { current ->
                        current.copy(
                            connectionState = if (current.connectionState == BtConnectionState.CONNECTED) {
                                BtConnectionState.CONNECTED
                            } else {
                                BtConnectionState.SCANNING
                            },
                            statusMessage = "Discovering CB350RS & OBD-II Bluetooth devices…"
                        )
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _state.update { current ->
                        if (current.connectionState == BtConnectionState.SCANNING && bleScanCallback == null) {
                            current.copy(
                                connectionState = BtConnectionState.DISCONNECTED,
                                statusMessage = "Discovery complete • ${current.discoveredDevices.size} device(s) found"
                            )
                        } else {
                            current
                        }
                    }
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    refreshAdapterState()
                }
            }
        }
    }

    init {
        registerDiscoveryReceiver()
        refreshAdapterState()
    }

    private fun registerDiscoveryReceiver() {
        if (discoveryReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appContext.registerReceiver(discoveryReceiver, filter)
            }
            discoveryReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Bluetooth discovery receiver", e)
        }
    }

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(appContext, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshAdapterState() {
        val adapter = bluetoothAdapter
        val supported = adapter != null
        val enabled = adapter?.isEnabled == true
        val perms = hasBluetoothPermissions()

        val bonded = if (supported && enabled && perms) {
            try {
                adapter?.bondedDevices.orEmpty().map { dev ->
                    val name = dev.name?.takeIf { it.isNotBlank() } ?: "Bluetooth Device"
                    ScannedBtDevice(
                        name = name,
                        address = dev.address,
                        bonded = true,
                        looksLikeBikeOrObd = isLikelyBikeOrObd(name),
                        transportLabel = "Paired"
                    )
                }.sortedByDescending { it.looksLikeBikeOrObd }
            } catch (e: SecurityException) {
                Log.w(TAG, "Missing permission reading bonded devices", e)
                emptyList()
            }
        } else {
            emptyList()
        }

        _state.update {
            it.copy(
                hardwareSupported = supported,
                adapterEnabled = enabled,
                permissionsGranted = perms,
                bondedDevices = bonded,
                statusMessage = when {
                    it.safetySimulationActive -> "Safety Simulation Mode • Virtual CB350RS ECU Linked"
                    !supported -> "No Bluetooth radio on this device — use Safety Simulation Mode for bench testing"
                    !perms -> "Grant Bluetooth permissions to discover & link CB350RS OBD-II"
                    !enabled -> "Enable Bluetooth in Android Settings to connect"
                    it.connectionState == BtConnectionState.CONNECTED -> it.statusMessage
                    else -> "Bluetooth ready • ${bonded.size} paired device(s)"
                }
            )
        }
    }

    fun setLinkMode(mode: BtLinkMode) {
        _state.update { it.copy(linkMode = mode) }
    }

    /**
     * Starts both Classic BluetoothAdapter discovery (for ELM327 OBD-II SPP dongles)
     * and BLE scanning (for BLE 4.0/5.0 CB350RS OBD & dash modules).
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        refreshAdapterState()
        val adapter = bluetoothAdapter ?: return
        if (!hasBluetoothPermissions() || !adapter.isEnabled) return

        stopDiscovery()

        _state.update {
            it.copy(
                connectionState = if (it.connectionState == BtConnectionState.CONNECTED) {
                    BtConnectionState.CONNECTED
                } else {
                    BtConnectionState.SCANNING
                },
                discoveredDevices = emptyList(),
                statusMessage = "Discovering Honda CB350RS & OBD-II adapters…"
            )
        }

        // 1. Classic Bluetooth discovery via BluetoothAdapter.startDiscovery()
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            val started = adapter.startDiscovery()
            appendLog("BluetoothAdapter.startDiscovery() -> $started")
        } catch (e: Exception) {
            Log.w(TAG, "Classic Bluetooth discovery failed", e)
        }

        // 2. Concurrent BLE scan for dual-mode / BLE OBD-II & CB350RS modules
        startBleScanInternal(adapter)

        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = scope.launch {
            delay(DISCOVERY_TIMEOUT_MS)
            stopDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScanInternal(adapter: BluetoothAdapter) {
        val scanner = adapter.bluetoothLeScanner ?: return
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = try {
                    result.scanRecord?.deviceName ?: device.name
                } catch (_: SecurityException) {
                    null
                }?.takeIf { it.isNotBlank() } ?: "BLE Device (${device.address.takeLast(5)})"

                val isBonded = try {
                    device.bondState == BluetoothDevice.BOND_BONDED
                } catch (_: SecurityException) {
                    false
                }

                recordDiscoveredDevice(
                    ScannedBtDevice(
                        name = name,
                        address = device.address,
                        rssi = result.rssi,
                        bonded = isBonded,
                        looksLikeBikeOrObd = isLikelyBikeOrObd(name),
                        transportLabel = "BLE"
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE scan failed with errorCode=$errorCode")
            }
        }

        bleScanCallback = callback
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, callback)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start BLE scan", e)
            bleScanCallback = null
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = null

        val adapter = bluetoothAdapter
        if (adapter != null && hasBluetoothPermissions()) {
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling Classic discovery", e)
            }

            val cb = bleScanCallback
            bleScanCallback = null
            if (cb != null && adapter.isEnabled) {
                try {
                    adapter.bluetoothLeScanner?.stopScan(cb)
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping BLE scan", e)
                }
            }
        } else {
            bleScanCallback = null
        }

        _state.update {
            if (it.connectionState == BtConnectionState.SCANNING) {
                it.copy(
                    connectionState = BtConnectionState.DISCONNECTED,
                    statusMessage = "Discovery complete • ${it.discoveredDevices.size} device(s) found"
                )
            } else {
                it
            }
        }
    }

    private fun recordDiscoveredDevice(entry: ScannedBtDevice) {
        _state.update { current ->
            val existing = current.discoveredDevices.firstOrNull { it.address == entry.address }
            val mergedEntry = if (existing != null) {
                entry.copy(
                    name = if (entry.name.startsWith("BLE Device") || entry.name.startsWith("Bluetooth OBD/Device")) {
                        existing.name
                    } else {
                        entry.name
                    },
                    rssi = entry.rssi ?: existing.rssi,
                    bonded = entry.bonded || existing.bonded,
                    looksLikeBikeOrObd = entry.looksLikeBikeOrObd || existing.looksLikeBikeOrObd
                )
            } else {
                entry
            }

            val merged = (current.discoveredDevices.filterNot { it.address == entry.address } + mergedEntry)
                .sortedWith(
                    compareByDescending<ScannedBtDevice> { it.looksLikeBikeOrObd }
                        .thenByDescending { it.rssi ?: -120 }
                )
                .take(30)
            current.copy(discoveredDevices = merged)
        }
    }

    /**
     * Connects to a discovered or paired Honda CB350RS OBD-II adapter or BLE module
     * via BluetoothAdapter.getRemoteDevice().
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: ScannedBtDevice, mode: BtLinkMode = _state.value.linkMode) {
        if (!hasBluetoothPermissions()) {
            _state.update { it.copy(statusMessage = "Bluetooth permission required to connect") }
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _state.update { it.copy(statusMessage = "Enable Bluetooth before connecting") }
            return
        }

        stopDiscovery()
        disconnect()

        _state.update {
            it.copy(
                linkMode = mode,
                connectionState = BtConnectionState.CONNECTING,
                statusMessage = "Connecting to ${device.name} (${mode.label})…"
            )
        }

        val remoteDevice = try {
            adapter.getRemoteDevice(device.address)
        } catch (e: IllegalArgumentException) {
            _state.update {
                it.copy(
                    connectionState = BtConnectionState.ERROR,
                    statusMessage = "Invalid Bluetooth address: ${device.address}"
                )
            }
            return
        }

        when (mode) {
            BtLinkMode.CLASSIC_OBD_SPP -> connectClassicObdSpp(remoteDevice, device)
            BtLinkMode.BLE_GATT -> connectBleGatt(remoteDevice, device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectClassicObdSpp(remote: BluetoothDevice, deviceInfo: ScannedBtDevice) {
        scope.launch {
            try {
                // Always cancel discovery before socket.connect() so RFCOMM handshake is fast & reliable
                if (bluetoothAdapter?.isDiscovering == true) {
                    bluetoothAdapter?.cancelDiscovery()
                }

                val socket = openObdRfcommSocket(remote)
                activeSocket = socket
                sppInput = socket.inputStream
                sppOutput = socket.outputStream

                _state.update {
                    it.copy(
                        connectionState = BtConnectionState.CONNECTED,
                        connectedDevice = deviceInfo,
                        statusMessage = "OBD-II Connected • ${deviceInfo.name}"
                    )
                }
                appendLog("RFCOMM SPP connected to ${deviceInfo.name} (${deviceInfo.address})")

                // Initialize ELM327 for Honda CB350RS BS6 Euro5 (ISO 15765-4 CAN / K-Line auto-detect)
                val resetResp = sendElmCommand("ATZ")
                appendLog("ATZ -> ${resetResp.ifBlank { "OK" }}")
                sendElmCommand("ATE0") // Echo off
                sendElmCommand("ATL0") // Linefeeds off
                sendElmCommand("ATS0") // Spaces off for compact hex parsing
                sendElmCommand("ATH0") // Headers off
                sendElmCommand("ATSP0") // Auto-detect OBD-II protocol
                val protocol = sendElmCommand("ATDP").ifBlank { "ISO 15765-4 CAN (Auto)" }
                _state.update {
                    it.copy(telemetry = it.telemetry.copy(ecuProtocol = protocol))
                }
                appendLog("ECU Protocol: $protocol")
                startObdPollingLoop()
            } catch (e: IOException) {
                Log.w(TAG, "OBD-II SPP connection failed", e)
                closeSppQuietly()
                _state.update {
                    it.copy(
                        connectionState = BtConnectionState.ERROR,
                        connectedDevice = null,
                        statusMessage = "Failed to connect to ${deviceInfo.name}: ${e.localizedMessage ?: "OBD adapter unreachable"}"
                    )
                }
            } catch (e: SecurityException) {
                _state.update {
                    it.copy(
                        connectionState = BtConnectionState.ERROR,
                        statusMessage = "Bluetooth connect permission denied"
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openObdRfcommSocket(remote: BluetoothDevice): BluetoothSocket {
        return try {
            val secure = remote.createRfcommSocketToServiceRecord(SPP_UUID)
            secure.connect()
            secure
        } catch (secureError: IOException) {
            Log.w(TAG, "Secure RFCOMM failed, retrying insecure SPP for ELM327 compatibility", secureError)
            val insecure = remote.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            insecure.connect()
            insecure
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectBleGatt(remote: BluetoothDevice, deviceInfo: ScannedBtDevice) {
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    appendLog("GATT link error (status $status)")
                    disconnectHardwareOnly()
                    _state.update {
                        it.copy(
                            connectionState = BtConnectionState.ERROR,
                            statusMessage = "BLE link failed (status $status). Try Classic OBD-II SPP mode."
                        )
                    }
                    return
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        appendLog("Connected BLE GATT to ${deviceInfo.name}")
                        _state.update {
                            it.copy(
                                connectionState = BtConnectionState.CONNECTED,
                                connectedDevice = deviceInfo,
                                statusMessage = "BLE Connected • ${deviceInfo.name}"
                            )
                        }
                        try {
                            gatt.discoverServices()
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Missing permission discovering services", e)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        appendLog("BLE GATT disconnected")
                        _state.update {
                            it.copy(
                                connectionState = BtConnectionState.DISCONNECTED,
                                connectedDevice = null,
                                statusMessage = "Disconnected from ${deviceInfo.name}"
                            )
                        }
                        writableGattChar = null
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) return
                var foundWritable: BluetoothGattCharacteristic? = null
                gatt.services.forEach { service ->
                    service.characteristics.forEach { ch ->
                        val props = ch.properties
                        if (ch.uuid == NUS_TX_CHAR_UUID ||
                            (foundWritable == null &&
                                (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                                    props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0))
                        ) {
                            foundWritable = ch
                        }
                        if (ch.uuid == BATTERY_LEVEL_CHAR_UUID &&
                            props and BluetoothGattCharacteristic.PROPERTY_READ != 0
                        ) {
                            try {
                                gatt.readCharacteristic(ch)
                            } catch (_: SecurityException) {
                            }
                        }
                    }
                }
                writableGattChar = foundWritable
                appendLog(
                    "Discovered ${gatt.services.size} GATT service(s)" +
                        if (foundWritable != null) " • Dash relay TX ready" else ""
                )
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                    val level = characteristic.value?.firstOrNull()?.toInt()?.and(0xFF) ?: return
                    appendLog("BLE Battery Service: $level%")
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                    val level = value.firstOrNull()?.toInt()?.and(0xFF) ?: return
                    appendLog("BLE Battery Service: $level%")
                }
            }
        }

        try {
            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                remote.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                remote.connectGatt(appContext, false, gattCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "BLE connectGatt failed", e)
            _state.update {
                it.copy(
                    connectionState = BtConnectionState.ERROR,
                    statusMessage = "BLE connect failed: ${e.localizedMessage}"
                )
            }
        }
    }

    /**
     * Explicit opt-in Safety Simulation Mode for off-road / emulator bench testing without
     * risking testing handlebar controls or OBD diagnostics while riding a live motorcycle.
     */
    fun enableSafetySimulationMode() {
        stopDiscovery()
        disconnectHardwareOnly()
        val virtualDevice = ScannedBtDevice(
            name = "CB350RS-SIM (Bench)",
            address = "SIM:CB:35:0R:S0:01",
            rssi = -48,
            bonded = true,
            looksLikeBikeOrObd = true,
            transportLabel = "SIM"
        )
        _state.update {
            it.copy(
                safetySimulationActive = true,
                connectionState = BtConnectionState.CONNECTED,
                connectedDevice = virtualDevice,
                statusMessage = "Safety Simulation Mode • Virtual CB350RS ECU Linked",
                telemetry = LiveObdTelemetry(
                    rpm = 1350,
                    speedKmh = 0,
                    fuelLevelPercent = 76,
                    engineTempC = 82,
                    batteryVoltage = 13.8,
                    throttlePercent = 12,
                    engineLoadPercent = 19,
                    storedDtcs = emptyList(),
                    lastUpdatedMs = System.currentTimeMillis(),
                    ecuProtocol = "SIM ISO 15765-4 CAN (Bench Mode)",
                    recentLog = listOf("Safety Simulation Mode started (off-road bench test)")
                )
            )
        }

        simulationJob?.cancel()
        simulationJob = scope.launch {
            var tick = 0
            while (isActive && _state.value.safetySimulationActive) {
                delay(1500L)
                tick++
                val wave = sin(tick * 0.45)
                val rpm = (2800 + (wave * 1500)).toInt().coerceIn(1250, 6100)
                val speed = (48 + (wave * 26)).toInt().coerceIn(0, 102)
                val fuel = (76 - ((tick / 6) % 58)).coerceIn(18, 100)
                val temp = (84 + (tick % 6)).coerceAtMost(96)
                val volts = 13.7 + ((tick % 3) * 0.1)
                val throttle = (22 + (wave * 18)).toInt().coerceIn(8, 72)
                val load = (28 + (wave * 18)).toInt().coerceIn(14, 72)

                _state.update { current ->
                    current.copy(
                        telemetry = current.telemetry.copy(
                            rpm = rpm,
                            speedKmh = speed,
                            fuelLevelPercent = fuel,
                            engineTempC = temp,
                            batteryVoltage = volts,
                            throttlePercent = throttle,
                            engineLoadPercent = load,
                            lastUpdatedMs = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    fun disableSafetySimulationMode() {
        simulationJob?.cancel()
        simulationJob = null
        RelayStateBus.updateNavManeuver(null)
        _state.update {
            it.copy(
                safetySimulationActive = false,
                connectionState = BtConnectionState.DISCONNECTED,
                connectedDevice = null,
                telemetry = LiveObdTelemetry()
            )
        }
        refreshAdapterState()
    }

    fun triggerSimulatedNavRelay(destination: String) {
        if (!_state.value.safetySimulationActive) return
        val maneuver = RelayedNavManeuver(
            maneuver = NavManeuverType.RIGHT,
            primaryInstruction = "Turn right in 350 m toward ${destination.ifBlank { "Honda BigWing" }}",
            secondaryDetails = "Safety Simulation Mode • 4.2 km remaining",
            sourcePackage = "sim.motolink.bench",
            timestampMs = System.currentTimeMillis()
        )
        RelayStateBus.updateNavManeuver(maneuver)
        RelayStateBus.recordRelayedPacket("SIM TX -> NAV|RIGHT|${maneuver.primaryInstruction.take(28)}")
    }

    private fun startObdPollingLoop() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && activeSocket?.isConnected == true) {
                pollObdMetricsInternal()
                delay(2200L)
            }
        }
    }

    fun pollObdNow(): Boolean {
        if (_state.value.safetySimulationActive) {
            val tel = _state.value.telemetry
            appendLog(
                "SIM Mode 01 Poll -> RPM ${tel.rpm ?: "--"}, ${tel.speedKmh ?: "--"} km/h, Fuel ${tel.fuelLevelPercent ?: "--"}%, Batt ${String.format("%.1fV", tel.batteryVoltage ?: 13.8)}"
            )
            return true
        }
        scope.launch {
            if (activeSocket?.isConnected == true) {
                pollObdMetricsInternal()
                appendLog("Live ECU PID poll refreshed")
            } else {
                appendLog("Poll skipped: connect an ELM327 OBD-II Bluetooth adapter first")
            }
        }
        return activeSocket?.isConnected == true
    }

    fun scanDtcFaultCodes(): Boolean {
        if (_state.value.safetySimulationActive) {
            val sampleCodes = listOf("P0562")
            appendLog("SIM Mode 03 Scan -> 1 bench test DTC (P0562)")
            _state.update {
                it.copy(
                    telemetry = it.telemetry.copy(
                        storedDtcs = sampleCodes,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )
            }
            return true
        }
        scope.launch {
            if (activeSocket?.isConnected != true) {
                appendLog("DTC scan requires active ELM327 OBD-II Bluetooth connection")
                return@launch
            }
            val raw = sendElmCommand("03")
            val parsed = parseDtcResponse(raw)
            appendLog("Mode 03 DTC scan -> ${if (parsed.isEmpty()) "0 faults" else parsed.joinToString()}")
            _state.update {
                it.copy(
                    telemetry = it.telemetry.copy(
                        storedDtcs = parsed,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )
            }
        }
        return activeSocket?.isConnected == true
    }

    fun clearDtcFaultCodes(): Boolean {
        if (_state.value.safetySimulationActive) {
            appendLog("SIM Mode 04 Clear -> Stored bench DTCs cleared")
            _state.update {
                it.copy(
                    telemetry = it.telemetry.copy(
                        storedDtcs = emptyList(),
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )
            }
            return true
        }
        scope.launch {
            if (activeSocket?.isConnected != true) {
                appendLog("Clear DTC requires active ELM327 OBD-II connection")
                return@launch
            }
            val resp = sendElmCommand("04")
            appendLog("Mode 04 Clear DTC -> ${resp.ifBlank { "sent" }}")
            _state.update {
                it.copy(
                    telemetry = it.telemetry.copy(
                        storedDtcs = emptyList(),
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )
            }
        }
        return activeSocket?.isConnected == true
    }

    @SuppressLint("MissingPermission")
    fun relayNavigationToBike(maneuver: RelayedNavManeuver) {
        val compact = "NAV|${maneuver.maneuver.bleCode}|${maneuver.primaryInstruction.take(28)}"
        if (_state.value.safetySimulationActive) {
            RelayStateBus.recordRelayedPacket("SIM TX -> $compact")
            return
        }
        val bytes = (compact + "\r\n").toByteArray(Charsets.UTF_8)
        val delivered = writeRawPacket(bytes)
        RelayStateBus.recordRelayedPacket(
            if (delivered) "Sent to ${_state.value.connectedDevice?.name}: $compact"
            else "Queued (connect BT to transmit): $compact"
        )
    }

    @SuppressLint("MissingPermission")
    fun relayMediaActionToBike(actionLabel: String) {
        val compact = "MEDIA|$actionLabel"
        if (_state.value.safetySimulationActive) {
            appendLog("SIM TX -> $compact")
            RelayStateBus.recordRelayedPacket("SIM TX -> $compact")
            return
        }
        val bytes = (compact + "\r\n").toByteArray(Charsets.UTF_8)
        val delivered = writeRawPacket(bytes)
        if (delivered) {
            RelayStateBus.recordRelayedPacket("Sent to ${_state.value.connectedDevice?.name}: $compact")
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeRawPacket(bytes: ByteArray): Boolean {
        val gatt = activeGatt
        val ch = writableGattChar
        if (gatt != null && ch != null && hasBluetoothPermissions()) {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        ch,
                        bytes,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                } else {
                    @Suppress("DEPRECATION")
                    ch.value = bytes
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(ch)
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "BLE write failed", e)
                false
            }
        }
        val out = sppOutput
        if (out != null && activeSocket?.isConnected == true) {
            return try {
                out.write(bytes)
                out.flush()
                true
            } catch (_: IOException) {
                false
            }
        }
        return false
    }

    private suspend fun pollObdMetricsInternal() {
        val voltRaw = sendElmCommand("ATRV")
        val rpmRaw = sendElmCommand("010C")
        val spdRaw = sendElmCommand("010D")
        val fuelRaw = sendElmCommand("012F")
        val tmpRaw = sendElmCommand("0105")
        val thrRaw = sendElmCommand("0111")
        val loadRaw = sendElmCommand("0104")

        val voltage = parseVoltage(voltRaw)
        val rpm = parseRpm(rpmRaw)
        val speed = parseSingleBytePid(spdRaw, "410D")
        val fuel = parseSingleBytePid(fuelRaw, "412F")?.let { ((it * 100) / 255).coerceIn(0, 100) }
        val tempC = parseSingleBytePid(tmpRaw, "4105")?.minus(40)
        val throttle = parseSingleBytePid(thrRaw, "4111")?.let { (it * 100) / 255 }
        val load = parseSingleBytePid(loadRaw, "4104")?.let { (it * 100) / 255 }

        _state.update { current ->
            val prev = current.telemetry
            current.copy(
                telemetry = prev.copy(
                    batteryVoltage = voltage ?: prev.batteryVoltage,
                    rpm = rpm ?: prev.rpm,
                    speedKmh = speed ?: prev.speedKmh,
                    fuelLevelPercent = fuel ?: prev.fuelLevelPercent,
                    engineTempC = tempC ?: prev.engineTempC,
                    throttlePercent = throttle ?: prev.throttlePercent,
                    engineLoadPercent = load ?: prev.engineLoadPercent,
                    lastUpdatedMs = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun sendElmCommand(cmd: String): String = withContext(Dispatchers.IO) {
        val out = sppOutput ?: return@withContext ""
        val input = sppInput ?: return@withContext ""
        try {
            out.write("$cmd\r".toByteArray(Charsets.US_ASCII))
            out.flush()

            val sb = StringBuilder()
            val deadline = System.currentTimeMillis() + 1200L
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {
                    val b = input.read()
                    if (b == -1) break
                    val c = b.toChar()
                    if (c == '>') break
                    sb.append(c)
                } else {
                    delay(20L)
                }
            }
            sb.toString().replace("\r", " ").replace("\n", " ").trim()
        } catch (e: IOException) {
            Log.w(TAG, "ELM command $cmd failed", e)
            ""
        }
    }

    private fun parseVoltage(raw: String): Double? {
        val match = Regex("""(\d{1,2}\.\d{1,2})""").find(raw) ?: return null
        return match.groupValues[1].toDoubleOrNull()?.takeIf { it in 5.0..20.0 }
    }

    private fun parseRpm(raw: String): Int? {
        val hex = raw.uppercase().replace(Regex("[^0-9A-F]"), "")
        val idx = hex.indexOf("410C")
        if (idx == -1 || idx + 8 > hex.length) return null
        val a = hex.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return null
        val b = hex.substring(idx + 6, idx + 8).toIntOrNull(16) ?: return null
        return ((a * 256) + b) / 4
    }

    private fun parseSingleBytePid(raw: String, header: String): Int? {
        val hex = raw.uppercase().replace(Regex("[^0-9A-F]"), "")
        val idx = hex.indexOf(header)
        if (idx == -1 || idx + 6 > hex.length) return null
        return hex.substring(idx + 4, idx + 6).toIntOrNull(16)
    }

    private fun parseDtcResponse(raw: String): List<String> {
        val hex = raw.uppercase().replace(Regex("[^0-9A-F]"), "")
        val idx = hex.indexOf("43")
        if (idx == -1) return emptyList()
        val payload = hex.substring(idx + 2)
        val codes = mutableListOf<String>()
        var pos = 0
        while (pos + 4 <= payload.length && codes.size < 20) {
            val chunk = payload.substring(pos, pos + 4)
            pos += 4
            if (chunk == "0000") continue
            val firstNibble = chunk[0].digitToIntOrNull(16) ?: continue
            val prefix = when ((firstNibble shr 2) and 0x03) {
                0 -> 'P'
                1 -> 'C'
                2 -> 'B'
                else -> 'U'
            }
            val secondDigit = firstNibble and 0x03
            codes.add("$prefix$secondDigit${chunk.substring(1)}")
        }
        return codes.distinct()
    }

    @SuppressLint("MissingPermission")
    private fun disconnectHardwareOnly() {
        pollingJob?.cancel()
        pollingJob = null
        closeSppQuietly()
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: Exception) {
        }
        activeGatt = null
        writableGattChar = null
    }

    fun disconnect() {
        simulationJob?.cancel()
        simulationJob = null
        disconnectHardwareOnly()
        _state.update {
            it.copy(
                safetySimulationActive = false,
                connectionState = BtConnectionState.DISCONNECTED,
                connectedDevice = null,
                statusMessage = "Disconnected • Ready to link CB350RS",
                telemetry = LiveObdTelemetry()
            )
        }
    }

    private fun closeSppQuietly() {
        try { sppInput?.close() } catch (_: Exception) {}
        try { sppOutput?.close() } catch (_: Exception) {}
        try { activeSocket?.close() } catch (_: Exception) {}
        sppInput = null
        sppOutput = null
        activeSocket = null
    }

    private fun appendLog(line: String) {
        _state.update { current ->
            val updated = (listOf(line) + current.telemetry.recentLog).take(12)
            current.copy(telemetry = current.telemetry.copy(recentLog = updated))
        }
    }

    private fun isLikelyBikeOrObd(name: String): Boolean {
        val lower = name.lowercase()
        return listOf("cb350", "honda", "roadsync", "obd", "elm327", "vlink", "veepeak", "sena", "cardo", "intercom", "bike", "moto")
            .any { lower.contains(it) }
    }

    fun release() {
        stopDiscovery()
        disconnect()
        if (discoveryReceiverRegistered) {
            try {
                appContext.unregisterReceiver(discoveryReceiver)
            } catch (_: Exception) {
            }
            discoveryReceiverRegistered = false
        }
    }
}

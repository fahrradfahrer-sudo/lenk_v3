package com.witt3d.autosteer.hardware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.tinkerforge.BrickIMUV2
import com.tinkerforge.IPConnection
import com.witt3d.autosteer.data.models.ImuConfig
import com.witt3d.autosteer.data.models.TelemetryData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImuHandler {
    private val TAG = "ImuHandler"
    private val ACTION_USB_PERMISSION = "com.witt3d.autosteer.USB_PERMISSION"

    private var ipcon: IPConnection? = null
    private var imu: BrickIMUV2? = null

    private val _config = MutableStateFlow(ImuConfig())
    val config: StateFlow<ImuConfig> = _config.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private var simulationJob: Job? = null
    private var usbDirectJob: Job? = null
    private var retryJob: Job? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var appContext: Context? = null
    private var isUsbReceiverRegistered = false

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                Log.d(TAG, "USB Device Attached broadcast received")
                connect(context)
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                Log.d(TAG, "USB Device Detached broadcast received")
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (device != null && (device.vendorId == 5840 || device.vendorId == 0x16D0)) {
                    disconnect()
                }
            }
        }
    }

    private fun registerUsbReceiver(context: Context) {
        if (isUsbReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(usbReceiver, filter)
            }
            isUsbReceiverRegistered = true
            Log.d(TAG, "Registered USB Attached/Detached BroadcastReceiver")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register USB BroadcastReceiver: ${e.message}")
        }
    }

    // Live Telemetry state
    private var currentHeading = 0.0
    private var currentRoll = 0.0
    private var currentPitch = 0.0
    private var currentAccelX = 0.0
    private var currentAccelY = 0.0
    private var currentAccelZ = 9.81

    // Simulated Telemetry state
    private var simHeading = 0.0
    private var simRoll = 0.0
    private var simPitch = 0.0
    private var simAccelX = 0.0
    private var simAccelY = 0.0
    private var simAccelZ = 9.81

    private fun getCustomUsbProber(): UsbSerialProber {
        val probeTable = UsbSerialProber.getDefaultProbeTable()
        val vendorIds = intArrayOf(5840, 0x16D0)
        val productIds = intArrayOf(
            0x063D, 0x063F, 0x0640, 0x0659, 0x0650, 0x0651, 0x0652, 0x0653, 0x0654, 0x0655, 0x0656, 0x0657, 0x0658, 0x0660
        )
        for (vid in vendorIds) {
            for (pid in productIds) {
                probeTable.addProduct(vid, pid, CdcAcmSerialDriver::class.java)
            }
        }
        return UsbSerialProber(probeTable)
    }

    /**
     * Scans for attached USB devices and requests USB permission if needed.
     */
    fun scanUsbDevices(context: Context): List<UsbDevice> {
        this.appContext = context.applicationContext
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        val deviceList = usbManager.deviceList
        val tinkerforgeDevices = mutableListOf<UsbDevice>()

        for ((_, device) in deviceList) {
            val isTinkerforge = device.vendorId == 5840 || device.vendorId == 0x16D0
            if (isTinkerforge) {
                tinkerforgeDevices.add(device)
            }
        }

        val firstDevice = tinkerforgeDevices.firstOrNull()
        if (firstDevice != null) {
            val hasPerm = usbManager.hasPermission(firstDevice)
            val devName = "${firstDevice.manufacturerName ?: "Tinkerforge"} (${firstDevice.productName ?: "IMU Brick 2.0"})"
            _config.value = _config.value.copy(
                usbDeviceDetected = true,
                usbDeviceName = if (hasPerm) "$devName (Granted)" else "$devName (Permission Required)"
            )
            Log.d(TAG, "Detected USB IMU Device: $devName, Has Permission: $hasPerm")

            if (!hasPerm) {
                requestUsbPermission(context, usbManager, firstDevice)
            }
        } else {
            _config.value = _config.value.copy(
                usbDeviceDetected = false,
                usbDeviceName = null
            )
        }

        return tinkerforgeDevices
    }

    private fun requestUsbPermission(context: Context, usbManager: UsbManager, device: UsbDevice) {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val intent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(context.packageName)
            }
            val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags)

            val filter = IntentFilter(ACTION_USB_PERMISSION)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (ACTION_USB_PERMISSION == intent?.action) {
                        synchronized(this) {
                            c?.unregisterReceiver(this)
                            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            Log.d(TAG, "USB Permission callback result: $granted")
                            if (granted) {
                                scanUsbDevices(context)
                                connect(context)
                            } else {
                                _config.value = _config.value.copy(
                                    errorMessage = "USB permission was denied by user."
                                )
                            }
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            usbManager.requestPermission(device, permissionIntent)
            Log.d(TAG, "Requested USB Permission for device: ${device.deviceName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting USB permission", e)
        }
    }

    fun updateConfig(newConfig: ImuConfig) {
        val oldConfig = _config.value
        _config.value = newConfig.copy(errorMessage = null)

        if (oldConfig.isSimulationMode != newConfig.isSimulationMode ||
            oldConfig.host != newConfig.host ||
            oldConfig.port != newConfig.port ||
            oldConfig.uid != newConfig.uid) {
            disconnect()
            connect()
        }
    }

    /**
     * Tries connecting directly to the Tinkerforge IMU Brick 2.0 using native Android USB Host Bulk Transfers.
     */
    private suspend fun tryConnectUsbDirect(context: Context): Boolean = withContext(Dispatchers.IO) {
        stopUsbDirect()

        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return@withContext false
        val device = usbManager.deviceList.values.firstOrNull { it.vendorId == 5840 || it.vendorId == 0x16D0 }
            ?: return@withContext false

        if (!usbManager.hasPermission(device)) {
            Log.w(TAG, "USB permission missing for device: ${device.deviceName}")
            requestUsbPermission(context, usbManager, device)
            return@withContext false
        }

        val connection = usbManager.openDevice(device) ?: run {
            Log.w(TAG, "Could not open UsbDeviceConnection")
            return@withContext false
        }

        val iface = device.getInterface(0)
        if (!connection.claimInterface(iface, true)) {
            Log.w(TAG, "Could not claim USB Interface 0")
            connection.close()
            return@withContext false
        }

        usbConnection = connection
        usbInterface = iface

        var endpointIn: UsbEndpoint? = null
        var endpointOut: UsbEndpoint? = null

        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) {
                    endpointIn = ep
                } else if (ep.direction == UsbConstants.USB_DIR_OUT) {
                    endpointOut = ep
                }
            }
        }

        if (endpointIn == null || endpointOut == null) {
            Log.e(TAG, "Bulk endpoints not found on USB device")
            stopUsbDirect()
            return@withContext false
        }

        Log.d(TAG, "Direct Native USB Bulk Transfer connection established to IMU Brick v2")

        // Build 'Set Orientation Period' packet (Function ID 22, Period = 50ms)
        val periodPacket = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0) // UID = 0 (broadcast)
            put(12.toByte()) // Length = 12
            put(22.toByte()) // Function ID 22 = SET_ORIENTATION_PERIOD
            put(0.toByte())  // Sequence / Options
            put(0.toByte())  // Error
            putInt(50)       // Period = 50ms (20Hz)
        }.array()

        // Build 'Set Linear Acceleration Period' packet (Function ID 24, Period = 50ms)
        val accelPeriodPacket = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0)
            put(12.toByte())
            put(24.toByte()) // Function ID 24 = SET_LINEAR_ACCELERATION_PERIOD
            put(0.toByte())
            put(0.toByte())
            putInt(50)
        }.array()

        try {
            connection.bulkTransfer(endpointOut, periodPacket, periodPacket.size, 1000)
            connection.bulkTransfer(endpointOut, accelPeriodPacket, accelPeriodPacket.size, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending USB IMU configuration packets", e)
        }

        // Start background USB Bulk Reader Loop
        usbDirectJob?.cancel()
        usbDirectJob = scope.launch(Dispatchers.IO) {
            val readBuffer = ByteArray(256)
            val packetBuffer = ByteArray(2048)
            var packetBufLen = 0

            while (isActive) {
                try {
                    val read = connection.bulkTransfer(endpointIn, readBuffer, readBuffer.size, 200)
                    if (read > 0) {
                        System.arraycopy(readBuffer, 0, packetBuffer, packetBufLen, read)
                        packetBufLen += read

                        // Parse full Tinkerforge packets
                        while (packetBufLen >= 8) {
                            val packetLen = packetBuffer[4].toInt() and 0xFF
                            if (packetLen < 8 || packetLen > 256) {
                                packetBufLen = 0
                                break
                            }

                            if (packetBufLen >= packetLen) {
                                val functionId = packetBuffer[5].toInt() and 0xFF
                                parseTinkerforgeUsbPacket(packetBuffer, packetLen, functionId)

                                System.arraycopy(packetBuffer, packetLen, packetBuffer, 0, packetBufLen - packetLen)
                                packetBufLen -= packetLen
                            } else {
                                break
                            }
                        }
                    } else {
                        // Request periodic orientation update if callbacks are idle
                        val getOrientationPacket = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
                            putInt(0)
                            put(8.toByte())
                            put(5.toByte()) // Function ID 5 = GET_ORIENTATION
                            put(0.toByte())
                            put(0.toByte())
                        }.array()
                        connection.bulkTransfer(endpointOut, getOrientationPacket, getOrientationPacket.size, 200)
                    }
                } catch (e: Exception) {
                    if (isActive) Log.e(TAG, "Error reading native USB IMU bulk stream", e)
                    delay(100)
                }
            }
        }

        return@withContext true
    }

    private fun parseTinkerforgeUsbPacket(data: ByteArray, length: Int, functionId: Int) {
        if ((functionId == 38 || functionId == 5) && length >= 14) { // CALLBACK_ORIENTATION = 38 or GET_ORIENTATION = 5
            val bb = ByteBuffer.wrap(data, 8, 6).order(ByteOrder.LITTLE_ENDIAN)
            val heading = bb.short / 16.0
            val roll = bb.short / 16.0
            val pitch = bb.short / 16.0

            currentHeading = heading
            currentRoll = roll
            currentPitch = pitch

            emitTelemetry(
                TelemetryData(
                    headingDegrees = currentHeading,
                    inclinationDegrees = currentRoll,
                    pitchDegrees = currentPitch,
                    accelX = currentAccelX,
                    accelY = currentAccelY,
                    accelZ = currentAccelZ
                )
            )
        } else if ((functionId == 36 || functionId == 6) && length >= 14) { // CALLBACK_LINEAR_ACCELERATION = 36
            val bb = ByteBuffer.wrap(data, 8, 6).order(ByteOrder.LITTLE_ENDIAN)
            currentAccelX = bb.short / 100.0
            currentAccelY = bb.short / 100.0
            currentAccelZ = bb.short / 100.0
        }
    }

    private fun emitTelemetry(data: TelemetryData) {
        _telemetry.value = data
    }

    fun connect(context: Context? = null) {
        if (context != null) {
            appContext = context.applicationContext
            registerUsbReceiver(context.applicationContext)
        }

        retryJob?.cancel()
        retryJob = scope.launch(Dispatchers.IO) {
            var attempt = 0
            val maxAttempts = 5

            while (isActive && !_config.value.isConnected && attempt < maxAttempts) {
                attempt++
                val ctx = context ?: appContext
                if (ctx != null) {
                    scanUsbDevices(ctx)
                }

                val currentConfig = _config.value
                if (currentConfig.isSimulationMode) {
                    startSimulation()
                    _config.value = currentConfig.copy(isConnected = true, errorMessage = null)
                    Log.d(TAG, "Started Simulated IMU Mode")
                    return@launch
                }

                stopSimulation()

                // Step 1: If host is localhost/127.0.0.1 and context is available, attempt native Direct USB Bulk Transfer connection
                if ((currentConfig.host == "127.0.0.1" || currentConfig.host == "localhost") && ctx != null) {
                    val usbDirectSuccess = tryConnectUsbDirect(ctx)
                    if (usbDirectSuccess) {
                        _config.value = currentConfig.copy(isConnected = true, errorMessage = null)
                        Log.d(TAG, "Successfully connected to IMU Brick v2 via Native USB Bulk Transfer")
                        return@launch
                    }
                }

                // Step 2: Fall back to TCP IPConnection (for remote host PC/brickd)
                try {
                    safelyDisconnectIpcon()

                    val connection = IPConnection()
                    ipcon = connection
                    connection.connect(currentConfig.host, currentConfig.port)

                    connection.addEnumerateListener { uid, _, _, _, _, deviceIdentifier, _ ->
                        if (deviceIdentifier == BrickIMUV2.DEVICE_IDENTIFIER) {
                            Log.d(TAG, "Discovered IMU Brick v2 with UID: $uid")
                            if (_config.value.uid.isEmpty() || _config.value.uid == uid) {
                                setupImuDevice(connection, uid)
                            }
                        }
                    }

                    if (currentConfig.uid.isNotEmpty()) {
                        setupImuDevice(connection, currentConfig.uid)
                    } else {
                        connection.enumerate()
                    }

                    _config.value = _config.value.copy(isConnected = true, errorMessage = null)
                    Log.d(TAG, "Connected to Tinkerforge IPConnection at ${currentConfig.host}:${currentConfig.port}")
                    return@launch
                } catch (e: Exception) {
                    val host = currentConfig.host
                    val port = currentConfig.port
                    val errorMsg = if (host == "127.0.0.1" || host == "localhost") {
                        if (_config.value.usbDeviceDetected) {
                            "USB IMU Brick v2 detected (${_config.value.usbDeviceName}).\n" +
                            "• Tap 'Scan USB' to grant USB permission if prompt appeared.\n" +
                            "• To connect to remote hardware, enter remote brickd IP address or toggle Simulated Mode."
                        } else {
                            "No local brickd server found on localhost:$port.\n" +
                            "• Connect IMU Brick via USB or enter remote IP address / Simulated Mode."
                        }
                    } else {
                        "Could not connect to $host:$port - ${e.localizedMessage ?: e.message}"
                    }
                    Log.e(TAG, "Error connecting to Tinkerforge IMU Brick v2 (Attempt $attempt/$maxAttempts)", e)
                    _config.value = _config.value.copy(isConnected = false, errorMessage = errorMsg)
                }

                if (attempt < maxAttempts) {
                    delay(2000L)
                }
            }
        }
    }

    private fun setupImuDevice(connection: IPConnection, uid: String) {
        try {
            val brick = BrickIMUV2(uid, connection)
            imu = brick
            _config.value = _config.value.copy(uid = uid, errorMessage = null)

            brick.addOrientationListener { heading, roll, pitch ->
                currentHeading = heading / 16.0
                currentRoll = roll / 16.0
                currentPitch = pitch / 16.0

                emitTelemetry(
                    TelemetryData(
                        headingDegrees = currentHeading,
                        inclinationDegrees = currentRoll,
                        pitchDegrees = currentPitch,
                        accelX = currentAccelX,
                        accelY = currentAccelY,
                        accelZ = currentAccelZ
                    )
                )
            }

            brick.addLinearAccelerationListener { x, y, z ->
                currentAccelX = x / 100.0
                currentAccelY = y / 100.0
                currentAccelZ = z / 100.0
            }

            try {
                brick.setOrientationPeriod(50)
                brick.setLinearAccelerationPeriod(50)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set callback periods: ${e.message}")
            }

            Log.d(TAG, "IMU Brick v2 initialized with UID: $uid")
        } catch (e: Exception) {
            val errorMsg = "Failed to setup BrickIMUV2 ($uid): ${e.localizedMessage ?: e.message}"
            Log.e(TAG, "Failed to initialize BrickIMUV2 with UID: $uid", e)
            _config.value = _config.value.copy(errorMessage = errorMsg)
        }
    }

    private fun stopUsbDirect() {
        usbDirectJob?.cancel()
        usbDirectJob = null

        val conn = usbConnection
        val iface = usbInterface

        usbConnection = null
        usbInterface = null

        if (conn != null) {
            try {
                if (iface != null) {
                    conn.releaseInterface(iface)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing USB interface: ${e.message}")
            }
            try {
                conn.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing UsbDeviceConnection: ${e.message}")
            }
        }
    }

    private fun safelyDisconnectIpcon() {
        val conn = ipcon
        ipcon = null
        imu = null
        if (conn != null) {
            try {
                if (conn.getConnectionState() != IPConnection.CONNECTION_STATE_DISCONNECTED) {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ignored exception during IPConnection disconnect: ${e.message}")
            }
        }
    }

    fun disconnect() {
        retryJob?.cancel()
        retryJob = null
        stopSimulation()
        stopUsbDirect()
        scope.launch(Dispatchers.IO) {
            try {
                safelyDisconnectIpcon()
                _config.value = _config.value.copy(isConnected = false)
                Log.d(TAG, "Disconnected IMU Handler")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting IMU Handler", e)
            }
        }
    }

    private fun startSimulation() {
        stopSimulation()
        simulationJob = scope.launch {
            while (isActive) {
                emitTelemetry(
                    TelemetryData(
                        headingDegrees = simHeading,
                        inclinationDegrees = simRoll,
                        pitchDegrees = simPitch,
                        accelX = simAccelX,
                        accelY = simAccelY,
                        accelZ = simAccelZ
                    )
                )
                delay(50) // 20Hz update rate
            }
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    // Manual Simulation Control Methods
    fun setSimulatedOrientation(heading: Double, roll: Double, pitch: Double, accelX: Double = 0.0, accelY: Double = 0.0, accelZ: Double = 9.81) {
        simHeading = heading
        simRoll = roll
        simPitch = pitch
        simAccelX = accelX
        simAccelY = accelY
        simAccelZ = accelZ

        if (_config.value.isSimulationMode) {
            emitTelemetry(
                TelemetryData(
                    headingDegrees = simHeading,
                    inclinationDegrees = simRoll,
                    pitchDegrees = simPitch,
                    accelX = simAccelX,
                    accelY = simAccelY,
                    accelZ = simAccelZ
                )
            )
        }
    }
}

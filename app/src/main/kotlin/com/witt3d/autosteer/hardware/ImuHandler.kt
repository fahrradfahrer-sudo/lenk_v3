package com.witt3d.autosteer.hardware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialPort
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
    private var usbSerialPort: UsbSerialPort? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        val deviceList = usbManager.deviceList
        val tinkerforgeOrSerialDevices = mutableListOf<UsbDevice>()

        val prober = getCustomUsbProber()

        for ((_, device) in deviceList) {
            val isTinkerforge = device.vendorId == 5840 || device.vendorId == 0x16D0
            val isSerial = prober.probeDevice(device) != null

            if (isTinkerforge || isSerial) {
                tinkerforgeOrSerialDevices.add(device)
            }
        }

        val firstDevice = tinkerforgeOrSerialDevices.firstOrNull()
        if (firstDevice != null) {
            val hasPerm = usbManager.hasPermission(firstDevice)
            val devName = "${firstDevice.manufacturerName ?: "Tinkerforge"} (${firstDevice.productName ?: "IMU Brick 2.0"})"
            _config.value = _config.value.copy(
                usbDeviceDetected = true,
                usbDeviceName = if (hasPerm) devName else "$devName (Permission Required)"
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

        return tinkerforgeOrSerialDevices
    }

    private fun requestUsbPermission(context: Context, usbManager: UsbManager, device: UsbDevice) {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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
     * Tries opening a direct USB Serial connection to the IMU Brick 2.0 when connected via USB C / Hub.
     */
    private suspend fun tryConnectUsbDirect(context: Context): Boolean = withContext(Dispatchers.IO) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return@withContext false
        val prober = getCustomUsbProber()
        val availableDrivers = prober.findAllDrivers(usbManager)

        val driver = availableDrivers.firstOrNull { d ->
            d.device.vendorId == 5840 || d.device.vendorId == 0x16D0 || d.ports.isNotEmpty()
        } ?: return@withContext false

        if (!usbManager.hasPermission(driver.device)) {
            Log.w(TAG, "USB permission missing for device: ${driver.device.deviceName}")
            requestUsbPermission(context, usbManager, driver.device)
            return@withContext false
        }

        val connection = usbManager.openDevice(driver.device) ?: run {
            Log.w(TAG, "Could not open USB device connection")
            return@withContext false
        }

        val serialPort = driver.ports.firstOrNull() ?: return@withContext false
        try {
            serialPort.open(connection)
            serialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            usbSerialPort = serialPort
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open USB serial port", e)
            return@withContext false
        }

        Log.d(TAG, "Direct USB Serial connection established to IMU Brick v2")

        // Build and send 'Set Orientation Period' packet (Function ID 22, Period 50ms)
        val periodPacket = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0) // UID = 0 (broadcast)
            put(12.toByte()) // Length = 12 bytes
            put(22.toByte()) // Function ID 22 = SET_ORIENTATION_PERIOD
            put(0.toByte())  // Sequence / Options
            put(0.toByte())  // Error
            putInt(50)       // Period = 50ms (20Hz)
        }.array()

        // Build and send 'Set Linear Acceleration Period' packet (Function ID 24, Period 50ms)
        val accelPeriodPacket = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0)
            put(12.toByte())
            put(24.toByte()) // Function ID 24 = SET_LINEAR_ACCELERATION_PERIOD
            put(0.toByte())
            put(0.toByte())
            putInt(50)
        }.array()

        try {
            serialPort.write(periodPacket, 1000)
            serialPort.write(accelPeriodPacket, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring USB IMU periods", e)
        }

        // Start background USB reader loop
        usbDirectJob?.cancel()
        usbDirectJob = scope.launch(Dispatchers.IO) {
            val readBuffer = ByteArray(1024)
            val packetBuffer = ByteArray(2048)
            var packetBufLen = 0

            while (isActive) {
                try {
                    val read = serialPort.read(readBuffer, 200)
                    if (read > 0) {
                        System.arraycopy(readBuffer, 0, packetBuffer, packetBufLen, read)
                        packetBufLen += read

                        // Parse full Tinkerforge packets from buffer
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
                        val getOrientationPacket = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
                            putInt(0)
                            put(8.toByte())
                            put(5.toByte()) // Function ID 5 = GET_ORIENTATION
                            put(0.toByte())
                            put(0.toByte())
                        }.array()
                        serialPort.write(getOrientationPacket, 200)
                    }
                } catch (e: Exception) {
                    if (isActive) Log.e(TAG, "Error reading USB IMU stream", e)
                    delay(100)
                }
            }
        }

        return@withContext true
    }

    private fun parseTinkerforgeUsbPacket(data: ByteArray, length: Int, functionId: Int) {
        if ((functionId == 38 || functionId == 5) && length >= 14) {
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
        } else if ((functionId == 36 || functionId == 6) && length >= 14) {
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
        scope.launch(Dispatchers.IO) {
            if (context != null) {
                scanUsbDevices(context)
            }

            val currentConfig = _config.value
            if (currentConfig.isSimulationMode) {
                startSimulation()
                _config.value = currentConfig.copy(isConnected = true, errorMessage = null)
                Log.d(TAG, "Started Simulated IMU Mode")
                return@launch
            }

            stopSimulation()

            // Step 1: If host is localhost/127.0.0.1 and context is available, attempt USB Direct connection
            if ((currentConfig.host == "127.0.0.1" || currentConfig.host == "localhost") && context != null) {
                val usbDirectSuccess = tryConnectUsbDirect(context)
                if (usbDirectSuccess) {
                    _config.value = currentConfig.copy(isConnected = true, errorMessage = null)
                    Log.d(TAG, "Successfully connected to IMU Brick v2 via Direct USB Serial")
                    return@launch
                }
            }

            // Step 2: Fall back to TCP IPConnection (for remote host PC/brickd)
            try {
                try {
                    ipcon?.disconnect()
                } catch (_: Exception) {}

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
                Log.e(TAG, "Error connecting to Tinkerforge IMU Brick v2", e)
                _config.value = _config.value.copy(isConnected = false, errorMessage = errorMsg)
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

        try {
            usbSerialPort?.close()
            usbSerialPort = null
        } catch (_: Exception) {}
    }

    fun disconnect() {
        stopSimulation()
        stopUsbDirect()
        scope.launch(Dispatchers.IO) {
            try {
                imu = null
                ipcon?.disconnect()
                ipcon = null
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

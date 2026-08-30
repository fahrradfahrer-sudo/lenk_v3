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
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.tinkerforge.BrickIMUV2
import com.tinkerforge.IPConnection
import com.witt3d.autosteer.data.models.ImuConfig
import com.witt3d.autosteer.data.models.TelemetryData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ImuHandler {
    private val TAG = "ImuHandler"
    private val ACTION_USB_PERMISSION = "com.witt3d.autosteer.USB_PERMISSION"

    private var ipcon: IPConnection? = null
    private var imu: BrickIMUV2? = null

    private val _config = MutableStateFlow(ImuConfig())
    val config: StateFlow<ImuConfig> = _config.asStateFlow()

    private var onTelemetryUpdate: ((TelemetryData) -> Unit)? = null

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Simulated telemetry state
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

    fun setTelemetryListener(listener: (TelemetryData) -> Unit) {
        onTelemetryUpdate = listener
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
            Log.d(TAG, "Detected USB IMU/Serial Device: $devName, Has Permission: $hasPerm")

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

            try {
                // Close existing connection if any
                try {
                    ipcon?.disconnect()
                } catch (_: Exception) {}

                val connection = IPConnection()
                ipcon = connection
                connection.connect(currentConfig.host, currentConfig.port)

                // Auto-discovery via enumeration listener if UID is not explicitly provided
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
                Log.d(TAG, "Connected to Tinkerforge at ${currentConfig.host}:${currentConfig.port}")
            } catch (e: Exception) {
                val host = currentConfig.host
                val port = currentConfig.port
                val errorMsg = if (host == "127.0.0.1" || host == "localhost") {
                    if (_config.value.usbDeviceDetected) {
                        "IMU Brick 2.0 detected via USB (${_config.value.usbDeviceName}).\n" +
                        "• Tinkerforge API connects via network TCP to a brickd daemon on port $port.\n" +
                        "• To connect to physical IMU, enter the IP address of the host/PC running brickd.\n" +
                        "• Or enable 'Simulated IMU Mode' below to test 3D orientation & movement live."
                    } else {
                        "No brickd daemon running on local port $port.\n" +
                        "• Enter remote brickd host IP address or enable Simulated Mode below."
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

            // Register Orientation Listener (heading, roll, pitch in 1/16 deg units)
            brick.addOrientationListener { heading, roll, pitch ->
                val headingDeg = heading / 16.0
                val rollDeg = roll / 16.0
                val pitchDeg = pitch / 16.0

                onTelemetryUpdate?.invoke(
                    TelemetryData(
                        headingDegrees = headingDeg,
                        inclinationDegrees = rollDeg,
                        pitchDegrees = pitchDeg,
                        accelX = simAccelX,
                        accelY = simAccelY,
                        accelZ = simAccelZ
                    )
                )
            }

            // Register Linear Acceleration Listener (m/s^2 * 100)
            brick.addLinearAccelerationListener { x, y, z ->
                simAccelX = x / 100.0
                simAccelY = y / 100.0
                simAccelZ = z / 100.0
            }

            // Set period for orientation callbacks safely
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

    fun disconnect() {
        stopSimulation()
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
                onTelemetryUpdate?.invoke(
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
            onTelemetryUpdate?.invoke(
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

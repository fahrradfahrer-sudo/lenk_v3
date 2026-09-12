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
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.witt3d.autosteer.data.models.MotorConfig
import com.witt3d.autosteer.data.models.MotorTelemetry
import com.witt3d.autosteer.data.models.MotorType
import com.witt3d.autosteer.data.repository.MotorRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class MotorHandler {
    private val TAG = "MotorHandler"
    private val ACTION_USB_PERMISSION = "com.witt3d.autosteer.MOTOR_USB_PERMISSION"

    private val _config = MutableStateFlow(MotorConfig())
    val config: StateFlow<MotorConfig> = _config.asStateFlow()

    private val _telemetry = MutableStateFlow(MotorTelemetry())
    val telemetry: StateFlow<MotorTelemetry> = _telemetry.asStateFlow()

    private var usbPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null

    private var phidgetDcMotor: com.phidget22.DCMotor? = null
    private var phidgetCurrentInput: com.phidget22.CurrentInput? = null
    private var phidgetEncoder: com.phidget22.Encoder? = null
    private var phidgetVoltageInput: com.phidget22.VoltageInput? = null

    private var communicationJob: Job? = null
    private var phidgetConnectionJob: Job? = null
    private var simulationJob: Job? = null
    private var activityRef: java.lang.ref.WeakReference<android.app.Activity>? = null
    private var lastContext: Context? = null
    private var isUsbReceiverRegistered = false
    private var isPermissionPending = false
    private var permissionReceiver: BroadcastReceiver? = null

    private fun findActivity(context: Context?): android.app.Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentTargetPowerPercent: Float = 0f

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                Log.d(TAG, "Motor USB Device Attached broadcast received")
                connect(context)
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                Log.d(TAG, "Motor USB Device Detached broadcast received")
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (device != null && isMotorDevice(device, _config.value.motorType)) {
                    disconnect()
                }
            }
        }
    }

    fun isConnectedAndActive(): Boolean {
        if (!_config.value.isConnected) return false
        if (_config.value.motorType == MotorType.PHIDGETS) {
            val motor = phidgetDcMotor ?: return false
            return try { motor.attached } catch (e: Exception) { false }
        }
        return true
    }

    private fun registerUsbReceiver(context: Context) {
        if (isUsbReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(usbReceiver, filter)
            }
            isUsbReceiverRegistered = true
            Log.d(TAG, "Registered Motor USB Attached/Detached BroadcastReceiver")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Motor USB BroadcastReceiver: ${e.message}")
        }
    }

    private fun getCustomMotorProber(): UsbSerialProber {
        val probeTable = UsbSerialProber.getDefaultProbeTable()
        // Roboteq VIDs (0x20D1 / 8401, 0x0525 / 1317, 0x0B4D / 2893, 0x0483 / 1155, 0x0403 FTDI)
        // Add common Roboteq product IDs for both CDC ACM and FTDI drivers
        probeTable.addProduct(0x20D1, 0x5740, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x20D1, 0x5741, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x20D1, 0x0001, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x20D1, 0x0002, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x0525, 0xA4A7, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x0525, 0xA4A8, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x0B4D, 0x0001, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x0B4D, 0x0002, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x0483, 0x5740, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x0483, 0x5741, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x0403, 0x6001, FtdiSerialDriver::class.java)
        probeTable.addProduct(0x0403, 0x6010, FtdiSerialDriver::class.java)
        probeTable.addProduct(0x0403, 0x6014, FtdiSerialDriver::class.java)
        probeTable.addProduct(0x0403, 0x6015, FtdiSerialDriver::class.java)
        return UsbSerialProber(probeTable)
    }

    private fun findMotorDrivers(usbManager: UsbManager, motorType: MotorType): List<com.hoho.android.usbserial.driver.UsbSerialDriver> {
        val customProber = getCustomMotorProber()
        val defaultProber = UsbSerialProber.getDefaultProber()

        val drivers = mutableListOf<com.hoho.android.usbserial.driver.UsbSerialDriver>()
        drivers.addAll(customProber.findAllDrivers(usbManager).filter { isMotorDevice(it.device, motorType) })
        drivers.addAll(defaultProber.findAllDrivers(usbManager).filter { isMotorDevice(it.device, motorType) })

        if (drivers.isEmpty()) {
            val matchedDevice = usbManager.deviceList.values.firstOrNull { isMotorDevice(it, motorType) }
            if (matchedDevice != null) {
                try {
                    val driver = if (matchedDevice.vendorId == 0x0403) {
                        FtdiSerialDriver(matchedDevice)
                    } else {
                        CdcAcmSerialDriver(matchedDevice)
                    }
                    if (driver.ports.isNotEmpty()) {
                        drivers.add(driver)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Dynamic serial driver creation failed for ${matchedDevice.deviceName}: ${e.message}")
                }
            }
        }
        return drivers.distinctBy { it.device.deviceId }
    }

    private fun isMotorDevice(device: UsbDevice, targetMotorType: MotorType): Boolean {
        val vid = device.vendorId
        // NEVER match GPS or IMU devices
        if (vid == 0x1546 || vid == 0x1A86 || vid == 0x4348 || vid == 5840 || vid == 0x16D0) {
            return false
        }
        return when (targetMotorType) {
            MotorType.ROBOTEQ -> vid == 0x20D1 || vid == 0x0525 || vid == 0x0B4D || vid == 0x0403 || vid == 0x0483 || vid == 0x10C4 || vid == 0x067B
            MotorType.PHIDGETS -> vid == 0x06C2
            MotorType.NONE_SIMULATED -> vid == 0x20D1 || vid == 0x0525 || vid == 0x0B4D || vid == 0x06C2 || vid == 0x0403 || vid == 0x0483 || vid == 0x10C4 || vid == 0x067B
        }
    }

    fun updateConfig(newConfig: MotorConfig) {
        val oldConfig = _config.value
        _config.value = newConfig

        if (oldConfig.motorType != newConfig.motorType || oldConfig.isSimulationMode != newConfig.isSimulationMode) {
            if (newConfig.isConnected) {
                connect(lastContext)
            }
        }
    }

    fun scanUsbDevices(context: Context) {
        val act = findActivity(context)
        if (act != null) {
            this.activityRef = java.lang.ref.WeakReference(act)
        }
        this.lastContext = context.applicationContext

        val currentType = _config.value.motorType
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        if (currentType == MotorType.PHIDGETS) {
            val phidgetDevice = usbManager.deviceList.values.firstOrNull { isMotorDevice(it, MotorType.PHIDGETS) }
            if (phidgetDevice != null) {
                _config.value = _config.value.copy(
                    usbDeviceDetected = true,
                    usbDeviceName = "Phidget Motor Controller 1065_1B (${phidgetDevice.manufacturerName ?: "Phidgets"} - ${phidgetDevice.deviceName})"
                )
            } else {
                _config.value = _config.value.copy(
                    usbDeviceDetected = false,
                    usbDeviceName = null
                )
            }
            return
        }

        val availableDrivers = findMotorDrivers(usbManager, currentType)

        if (availableDrivers.isNotEmpty()) {
            val device = availableDrivers[0].device
            val devName = when (device.vendorId) {
                0x06C2 -> "Phidget Motor Controller 1065_1B (${device.manufacturerName ?: "Phidgets"} - ${device.deviceName})"
                0x20D1, 0x0525, 0x0B4D, 0x0483 -> "Roboteq Motor Controller (${device.manufacturerName ?: "Roboteq"} - ${device.deviceName})"
                else -> "Motor Controller (${device.deviceName})"
            }
            _config.value = _config.value.copy(
                usbDeviceDetected = true,
                usbDeviceName = devName
            )
            return
        }

        val rawDevices = usbManager.deviceList.values
        val matchedDevice = rawDevices.firstOrNull { isMotorDevice(it, currentType) }

        if (matchedDevice != null) {
            val devName = when (matchedDevice.vendorId) {
                0x06C2 -> "Phidget Motor Controller 1065_1B (${matchedDevice.manufacturerName ?: "Phidgets"} - ${matchedDevice.deviceName})"
                0x20D1, 0x0525 -> "Roboteq Motor Controller (${matchedDevice.manufacturerName ?: "Roboteq"} - ${matchedDevice.deviceName})"
                else -> "Motor Controller (${matchedDevice.deviceName})"
            }
            _config.value = _config.value.copy(
                usbDeviceDetected = true,
                usbDeviceName = devName
            )
        } else {
            _config.value = _config.value.copy(
                usbDeviceDetected = false,
                usbDeviceName = null
            )
        }
    }

    fun setTargetPowerPercent(powerPercent: Float) {
        val clampedPower = powerPercent.coerceIn(-100f, 100f)
        currentTargetPowerPercent = clampedPower
        _telemetry.value = _telemetry.value.copy(targetPowerPercent = clampedPower)

        if (_config.value.motorType == MotorType.ROBOTEQ && _config.value.isConnected && !_config.value.isSimulationMode) {
            sendRoboteqPowerCommand(clampedPower)
        } else if (_config.value.motorType == MotorType.PHIDGETS && _config.value.isConnected && !_config.value.isSimulationMode) {
            sendPhidgetPowerCommand(clampedPower)
        }
    }

    fun emergencyStop() {
        setTargetPowerPercent(0f)
    }

    fun connect(context: Context? = null) {
        if (isConnectedAndActive()) {
            Log.d(TAG, "Motor already actively connected. Skipping redundant connection attempt.")
            return
        }
        disconnect()

        if (context != null) {
            lastContext = context.applicationContext
            registerUsbReceiver(context.applicationContext)
        }
        val activeContext = context ?: lastContext

        val currentConfig = _config.value
        if (currentConfig.isSimulationMode || currentConfig.motorType == MotorType.NONE_SIMULATED) {
            startSimulationMode()
            return
        }

        if (activeContext == null) {
            _config.value = currentConfig.copy(
                isConnected = false,
                errorMessage = "Context required for USB connection"
            )
            return
        }

        if (currentConfig.motorType == MotorType.PHIDGETS) {
            val usbManager = activeContext.getSystemService(Context.USB_SERVICE) as UsbManager
            val phidgetDevice = usbManager.deviceList.values.firstOrNull { isMotorDevice(it, MotorType.PHIDGETS) }

            if (phidgetDevice == null) {
                _config.value = currentConfig.copy(
                    isConnected = false,
                    usbDeviceDetected = false,
                    usbDeviceName = null,
                    errorMessage = "No Phidgets Motor Controller found. Switch to Simulated mode for testing."
                )
                return
            }

            _config.value = currentConfig.copy(
                usbDeviceDetected = true,
                usbDeviceName = "Phidget Motor Controller 1065_1B (${phidgetDevice.manufacturerName ?: "Phidgets"} - ${phidgetDevice.deviceName})"
            )

            if (!usbManager.hasPermission(phidgetDevice)) {
                requestUsbPermission(activeContext, usbManager, phidgetDevice)
                return
            }

            connectPhidgetsMotorDirect()
            return
        }

        val usbManager = activeContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = findMotorDrivers(usbManager, currentConfig.motorType)

        if (availableDrivers.isNotEmpty()) {
            val driver = availableDrivers[0]
            val device: UsbDevice = driver.device

            if (!usbManager.hasPermission(device)) {
                requestUsbPermission(activeContext, usbManager, device)
                return
            }

            var connToClose: UsbDeviceConnection? = null
            try {
                val connection = usbManager.openDevice(device)
                    ?: throw IOException("Failed to open USB motor controller device")
                connToClose = connection

                val baud = if (currentConfig.motorType == MotorType.ROBOTEQ) currentConfig.roboteqBaudRate else 115200
                val port = driver.ports[0]
                port.open(connection)
                port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                usbPort = port

                _config.value = currentConfig.copy(
                    isConnected = true,
                    usbDeviceDetected = true,
                    usbDeviceName = "${device.manufacturerName ?: "Motor"} (${device.deviceName})",
                    errorMessage = null
                )

                _telemetry.value = _telemetry.value.copy(
                    isConnected = true,
                    statusMessage = "Connected to ${currentConfig.motorType.name}"
                )

                startHardwareCommunicationLoop()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to USB motor device via serial driver, attempting raw USB fallback", e)
                try {
                    connToClose?.close()
                } catch (ignored: Exception) {}
            }
        }

        // Check raw USB devices as fallback
        val rawDevices = usbManager.deviceList.values
        val motorDevice = rawDevices.firstOrNull { isMotorDevice(it, currentConfig.motorType) }

        if (motorDevice == null) {
            _config.value = currentConfig.copy(
                isConnected = false,
                errorMessage = "No matching ${currentConfig.motorType.name} Motor Controller found. Switch to Simulated mode for testing."
            )
            return
        }

        if (!usbManager.hasPermission(motorDevice)) {
            requestUsbPermission(activeContext, usbManager, motorDevice)
            return
        }

        try {
            connectRawUsbDevice(usbManager, motorDevice)
            return
        } catch (e: Exception) {
            Log.e(TAG, "Failed raw USB connection to motor device", e)
            _config.value = currentConfig.copy(
                isConnected = false,
                errorMessage = e.message ?: "Raw USB connection error"
            )
        }
    }

    private fun connectRawUsbDevice(usbManager: UsbManager, device: UsbDevice) {
        val connection = usbManager.openDevice(device)
            ?: throw IOException("Failed to open UsbDeviceConnection for ${device.deviceName}")

        try {
            val iface = device.getInterface(0)
            if (!connection.claimInterface(iface, true)) {
                connection.close()
                throw IOException("Could not claim USB Interface 0 for ${device.deviceName}")
            }

            usbConnection = connection
            usbInterface = iface

            val currentConfig = _config.value
            val devName = "${device.manufacturerName ?: "Roboteq"} (${device.productName ?: "Motor Controller"})"

            _config.value = currentConfig.copy(
                isConnected = true,
                usbDeviceDetected = true,
                usbDeviceName = devName,
                errorMessage = null
            )

            _telemetry.value = _telemetry.value.copy(
                isConnected = true,
                statusMessage = "Connected to ${currentConfig.motorType.name} USB Device"
            )

            startRawUsbCommunicationLoop(connection, iface)
        } catch (e: Exception) {
            try {
                connection.close()
            } catch (ignored: Exception) {}
            throw e
        }
    }

    private fun startRawUsbCommunicationLoop(connection: UsbDeviceConnection, iface: UsbInterface) {
        communicationJob = scope.launch {
            var endpointIn: UsbEndpoint? = null

            for (i in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(i)
                if (ep.direction == UsbConstants.USB_DIR_IN) {
                    endpointIn = ep
                }
            }

            val readBuffer = ByteArray(256)
            val sb = StringBuilder()

            while (isActive && usbConnection == connection) {
                try {
                    if (endpointIn != null && isActive && usbConnection == connection) {
                        var len = -1
                        if (endpointIn.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            len = try {
                                connection.bulkTransfer(endpointIn, readBuffer, 0, readBuffer.size, 100)
                            } catch (e: Exception) {
                                Log.w(TAG, "Bulk transfer error on USB IN endpoint: ${e.message}")
                                -1
                            }
                        } else if (endpointIn.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                            try {
                                val inRequest = UsbRequest()
                                val initialized = try { inRequest.initialize(connection, endpointIn) } catch (e: Exception) { false }
                                if (initialized) {
                                    val inBuf = ByteBuffer.allocate(readBuffer.size)
                                    if (inRequest.queue(inBuf)) {
                                        val req = connection.requestWait(100)
                                        if (req == inRequest) {
                                            inBuf.flip()
                                            len = inBuf.remaining()
                                            if (len > 0) {
                                                inBuf.get(readBuffer, 0, len)
                                            }
                                        }
                                    }
                                    inRequest.close()
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "UsbRequest error on USB INT IN endpoint: ${e.message}")
                                len = -1
                            }
                        }

                        if (len > 0) {
                            val safeLen = len.coerceAtMost(readBuffer.size)
                            val text = String(readBuffer, 0, safeLen, Charsets.US_ASCII)
                            sb.append(text)

                            var newlineIndex = sb.indexOf("\r")
                            if (newlineIndex < 0) newlineIndex = sb.indexOf("\n")

                            while (newlineIndex >= 0) {
                                val line = sb.substring(0, newlineIndex).trim()
                                sb.delete(0, newlineIndex + 1)
                                if (line.isNotEmpty()) {
                                    parseHardwareTelemetryResponse(line)
                                }
                                newlineIndex = sb.indexOf("\r")
                                if (newlineIndex < 0) newlineIndex = sb.indexOf("\n")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in raw USB communication loop", e)
                }
                delay(100)
            }
        }
    }

    private fun requestUsbPermission(context: Context, usbManager: UsbManager, device: UsbDevice) {
        if (isPermissionPending) {
            Log.d(TAG, "USB Permission request already pending for device: ${device.deviceName}")
            return
        }
        val activityContext = findActivity(context) ?: findActivity(lastContext) ?: activityRef?.get()
        if (activityContext == null) {
            Log.d(TAG, "Cannot request USB permission without Activity context for device: ${device.deviceName}")
            _config.value = _config.value.copy(
                errorMessage = "Activity context required to request USB permission for ${device.productName ?: device.deviceName}"
            )
            return
        }
        isPermissionPending = true

        permissionReceiver?.let { oldRx ->
            try { activityContext.unregisterReceiver(oldRx) } catch (ignored: Exception) {}
            permissionReceiver = null
        }

        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val intent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(activityContext.packageName)
            }
            val permissionIntent = PendingIntent.getBroadcast(activityContext, 1003, intent, flags)

            val filter = IntentFilter(ACTION_USB_PERMISSION)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (ACTION_USB_PERMISSION == intent?.action) {
                        synchronized(this) {
                            try {
                                c?.unregisterReceiver(this)
                            } catch (e: Exception) {
                                Log.w(TAG, "Unregistering motor permission receiver failed: ${e.message}")
                            }
                            permissionReceiver = null
                            isPermissionPending = false
                            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            Log.d(TAG, "Motor USB Permission callback result: $granted")
                            if (granted) {
                                try {
                                    com.phidget22.usb.Manager.Initialize(c?.applicationContext ?: activityContext.applicationContext)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Re-initializing Phidgets USB manager after permission grant: ${e.message}")
                                }
                                connect(activityContext)
                            } else {
                                _config.value = _config.value.copy(
                                    isConnected = false,
                                    errorMessage = "USB permission denied by user."
                                )
                            }
                        }
                    }
                }
            }
            permissionReceiver = receiver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activityContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                activityContext.registerReceiver(receiver, filter)
            }

            usbManager.requestPermission(device, permissionIntent)
            _config.value = _config.value.copy(
                isConnected = false,
                errorMessage = "Requesting USB permissions..."
            )
            Log.d(TAG, "Requested USB Permission for motor device: ${device.deviceName}")
        } catch (e: Exception) {
            isPermissionPending = false
            permissionReceiver = null
            Log.e(TAG, "Error requesting USB permission for motor device", e)
        }
    }

    private fun connectPhidgetsMotorDirect() {
        phidgetConnectionJob?.cancel()
        phidgetConnectionJob = scope.launch(Dispatchers.IO) {
            safelyClosePhidgets()

            val currentConfig = _config.value
            try {
                val motor = com.phidget22.DCMotor()
                phidgetDcMotor = motor

                if (currentConfig.phidgetSerialNumber > 0) {
                    motor.deviceSerialNumber = currentConfig.phidgetSerialNumber
                }
                motor.channel = currentConfig.phidgetChannel

                motor.addAttachListener { e ->
                    Log.d(TAG, "Phidget DCMotor Attached: ${e.source}")
                    scope.launch(Dispatchers.IO) {
                        try {
                            val sn = motor.deviceSerialNumber
                            if (sn > 0 && _config.value.phidgetSerialNumber != sn) {
                                _config.value = _config.value.copy(phidgetSerialNumber = sn)
                                lastContext?.let { ctx ->
                                    MotorRepository.saveMotorConfig(ctx, _config.value)
                                }
                            }
                        } catch (ex: Exception) {
                            Log.w(TAG, "Could not read Phidget deviceSerialNumber: ${ex.message}")
                        }

                        try {
                            val minAccel = motor.minAcceleration
                            val maxAccel = motor.maxAcceleration
                            val clampedAccel = currentConfig.phidgetAcceleration.coerceIn(minAccel, maxAccel)
                            motor.acceleration = clampedAccel
                            Log.d(TAG, "Set Phidget DCMotor acceleration: $clampedAccel (min: $minAccel, max: $maxAccel)")
                        } catch (ex: Exception) {
                            Log.w(TAG, "Could not set Phidget acceleration: ${ex.message}")
                        }

                        try {
                            val minCurrent = motor.minCurrentLimit
                            val maxCurrent = motor.maxCurrentLimit
                            val clampedCurrent = currentConfig.phidgetCurrentLimit.coerceIn(minCurrent, maxCurrent)
                            motor.currentLimit = clampedCurrent
                            Log.d(TAG, "Set Phidget DCMotor currentLimit: $clampedCurrent (min: $minCurrent, max: $maxCurrent)")
                        } catch (ex: Exception) {
                            Log.w(TAG, "Could not set Phidget currentLimit: ${ex.message}")
                        }
                    }
                }

                motor.addDetachListener { e ->
                    Log.d(TAG, "Phidget DCMotor Detached: ${e.source}")
                    _config.value = _config.value.copy(isConnected = false)
                    _telemetry.value = _telemetry.value.copy(
                        isConnected = false,
                        statusMessage = "Phidget Motor Detached"
                    )
                }

                motor.addVelocityUpdateListener { e ->
                    val actualPct = (e.velocity * 100.0).toFloat()
                    _telemetry.value = _telemetry.value.copy(actualPowerPercent = actualPct)
                }

                motor.open(5000)

                // Attach Encoder channel on channel 0
                try {
                    val encoder = com.phidget22.Encoder()
                    phidgetEncoder = encoder
                    val sn = try { motor.deviceSerialNumber } catch (ex: Exception) { currentConfig.phidgetSerialNumber }
                    if (sn > 0) {
                        encoder.deviceSerialNumber = sn
                    }
                    encoder.channel = currentConfig.phidgetChannel
                    var runningCount = 0L
                    encoder.addPositionChangeListener { e ->
                        runningCount += e.positionChange
                        _telemetry.value = _telemetry.value.copy(encoderCount = runningCount)
                    }
                    encoder.open(2000)
                } catch (e: Exception) {
                    Log.d(TAG, "Optional Phidget Encoder not attached: ${e.message}")
                }

                // Attach CurrentInput channel in background (Channel 0 / phidgetChannel)
                scope.launch(Dispatchers.IO) {
                    try {
                        val currentInput = com.phidget22.CurrentInput()
                        phidgetCurrentInput = currentInput
                        val sn = try { motor.deviceSerialNumber } catch (ex: Exception) { currentConfig.phidgetSerialNumber }
                        if (sn > 0) {
                            currentInput.deviceSerialNumber = sn
                        }
                        currentInput.channel = currentConfig.phidgetChannel
                        currentInput.addCurrentChangeListener { e ->
                            _telemetry.value = _telemetry.value.copy(currentAmps = e.current)
                        }
                        currentInput.open(5000)
                        try {
                            currentInput.dataInterval = 100
                            currentInput.currentChangeTrigger = 0.05
                        } catch (ex: Exception) {
                            Log.w(TAG, "Could not set CurrentInput interval/trigger: ${ex.message}")
                        }
                        Log.d(TAG, "Successfully attached Phidget CurrentInput on channel ${currentConfig.phidgetChannel}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Phidget CurrentInput not attached: ${e.message}")
                    }
                }

                // Attach VoltageInput channel in background (Channel 2 for 1065_1B external supply voltage)
                scope.launch(Dispatchers.IO) {
                    try {
                        val voltageInput = com.phidget22.VoltageInput()
                        phidgetVoltageInput = voltageInput
                        val sn = try { motor.deviceSerialNumber } catch (ex: Exception) { currentConfig.phidgetSerialNumber }
                        if (sn > 0) {
                            voltageInput.deviceSerialNumber = sn
                        }
                        voltageInput.channel = 2
                        voltageInput.addVoltageChangeListener { e ->
                            _telemetry.value = _telemetry.value.copy(voltageVolts = e.voltage)
                        }
                        voltageInput.open(5000)
                        try {
                            voltageInput.dataInterval = 250
                            voltageInput.voltageChangeTrigger = 0.1
                        } catch (ex: Exception) {
                            Log.w(TAG, "Could not set VoltageInput interval/trigger: ${ex.message}")
                        }
                        Log.d(TAG, "Successfully attached Phidget VoltageInput on channel 2")
                    } catch (e: Exception) {
                        Log.w(TAG, "Phidget VoltageInput not attached: ${e.message}")
                    }
                }

                _config.value = _config.value.copy(
                    isConnected = true,
                    usbDeviceDetected = true,
                    usbDeviceName = "Phidget Motor Controller 1065_1B",
                    errorMessage = null
                )

                _telemetry.value = _telemetry.value.copy(
                    isConnected = true,
                    statusMessage = "Connected via Phidget22 API"
                )

                Log.d(TAG, "Successfully connected to Phidget DCMotor via Phidget22 API")
            } catch (e: Exception) {
                if (!isActive) return@launch
                Log.e(TAG, "Error connecting Phidgets DCMotor via Phidget22 API", e)
                safelyClosePhidgets()
                _config.value = _config.value.copy(
                    isConnected = false,
                    errorMessage = "Phidget22 Error: ${e.message}"
                )
            }
        }
    }

    @Synchronized
    private fun safelyClosePhidgets() {
        val ci = phidgetCurrentInput
        phidgetCurrentInput = null
        if (ci != null) {
            try { ci.close() } catch (ignored: Exception) {}
        }

        val enc = phidgetEncoder
        phidgetEncoder = null
        if (enc != null) {
            try { enc.close() } catch (ignored: Exception) {}
        }

        val vi = phidgetVoltageInput
        phidgetVoltageInput = null
        if (vi != null) {
            try { vi.close() } catch (ignored: Exception) {}
        }

        val m = phidgetDcMotor
        phidgetDcMotor = null
        if (m != null) {
            try {
                m.targetVelocity = 0.0
                m.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing Phidget DCMotor: ${e.message}")
            }
        }
    }

    fun disconnect() {
        runBlocking {
            communicationJob?.cancelAndJoin()
        }
        communicationJob = null
        phidgetConnectionJob?.cancel()
        phidgetConnectionJob = null
        simulationJob?.cancel()
        simulationJob = null
        isPermissionPending = false

        safelyClosePhidgets()

        try {
            usbPort?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing motor USB port", e)
        }
        usbPort = null

        val conn = usbConnection
        val iface = usbInterface
        usbConnection = null
        usbInterface = null

        if (conn != null) {
            try {
                if (iface != null) {
                    conn.releaseInterface(iface)
                }
                conn.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing raw USB connection", e)
            }
        }

        _config.value = _config.value.copy(isConnected = false)
        _telemetry.value = _telemetry.value.copy(
            isConnected = false,
            targetPowerPercent = 0f,
            actualPowerPercent = 0f,
            statusMessage = "Disconnected"
        )
    }

    private fun startHardwareCommunicationLoop() {
        communicationJob = scope.launch {
            val buffer = ByteArray(1024)
            val sb = StringBuilder()

            while (isActive) {
                try {
                    val port = usbPort ?: break

                    if (_config.value.motorType == MotorType.ROBOTEQ) {
                        val ch = _config.value.roboteqChannel
                        val queryCmd = "?C $ch\r?A $ch\r?V\r?S $ch\r"
                        port.write(queryCmd.toByteArray(Charsets.US_ASCII), 500)
                    }

                    val len = port.read(buffer, 200)
                    if (len > 0) {
                        val text = String(buffer, 0, len, Charsets.US_ASCII)
                        sb.append(text)

                        var newlineIndex = sb.indexOf("\r")
                        if (newlineIndex < 0) newlineIndex = sb.indexOf("\n")

                        while (newlineIndex >= 0) {
                            val line = sb.substring(0, newlineIndex).trim()
                            sb.delete(0, newlineIndex + 1)
                            if (line.isNotEmpty()) {
                                parseHardwareTelemetryResponse(line)
                            }
                            newlineIndex = sb.indexOf("\r")
                            if (newlineIndex < 0) newlineIndex = sb.indexOf("\n")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Motor hardware comm error", e)
                }
                delay(100)
            }
        }
    }

    private fun sendRoboteqPowerCommand(powerPercent: Float) {
        val port = usbPort ?: return
        val config = _config.value

        var effectivePowerPercent = powerPercent
        if (config.invertDirection) {
            effectivePowerPercent = -effectivePowerPercent
        }

        val maxLimitRatio = (config.maxPowerLimitPercent.coerceIn(0, 100) / 100.0f)
        val scaledPower = (effectivePowerPercent * maxLimitRatio * 10.0f).roundToInt().coerceIn(-1000, 1000)

        val command = "!G ${config.roboteqChannel} $scaledPower\r"
        scope.launch {
            try {
                port.write(command.toByteArray(Charsets.US_ASCII), 200)
                _telemetry.value = _telemetry.value.copy(actualPowerPercent = effectivePowerPercent * maxLimitRatio)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending Roboteq command", e)
            }
        }
    }

    private fun sendPhidgetPowerCommand(powerPercent: Float) {
        val config = _config.value

        var effectivePowerPercent = powerPercent
        if (config.invertDirection) {
            effectivePowerPercent = -effectivePowerPercent
        }

        val maxLimitRatio = (config.maxPowerLimitPercent.coerceIn(0, 100) / 100.0f)
        val dutyCycle = (effectivePowerPercent * maxLimitRatio).coerceIn(-100f, 100f)

        val motor = phidgetDcMotor
        if (motor != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val targetVel = (dutyCycle / 100.0).coerceIn(-1.0, 1.0)
                    motor.targetVelocity = targetVel
                    _telemetry.value = _telemetry.value.copy(actualPowerPercent = dutyCycle)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting Phidget DCMotor targetVelocity", e)
                }
            }
        }
    }

    fun parseHardwareTelemetryResponse(response: String) {
        val current = _telemetry.value

        try {
            when {
                response.startsWith("C=") -> {
                    val count = response.substring(2).trim().toLongOrNull()
                    if (count != null) {
                        _telemetry.value = current.copy(encoderCount = count)
                    }
                }
                response.startsWith("P=") -> {
                    val count = response.substring(2).trim().toLongOrNull()
                    if (count != null) {
                        _telemetry.value = current.copy(encoderCount = count)
                    }
                }
                response.startsWith("A=") -> {
                    val rawAmps = response.substring(2).trim().toDoubleOrNull()
                    if (rawAmps != null) {
                        _telemetry.value = current.copy(currentAmps = rawAmps / 10.0)
                    }
                }
                response.startsWith("I=") -> {
                    val amps = response.substring(2).trim().toDoubleOrNull()
                    if (amps != null) {
                        _telemetry.value = current.copy(currentAmps = amps)
                    }
                }
                response.startsWith("V=") -> {
                    val rawStr = response.substring(2).trim()
                    if (rawStr.contains(":")) {
                        val parts = rawStr.split(":")
                        val volts = parts[0].toDoubleOrNull()
                        if (volts != null) {
                            _telemetry.value = current.copy(voltageVolts = volts / 10.0)
                        }
                    } else {
                        val volts = rawStr.toDoubleOrNull()
                        if (volts != null) {
                            val actualVolts = if (volts > 50.0) volts / 10.0 else volts
                            _telemetry.value = current.copy(voltageVolts = actualVolts)
                        }
                    }
                }
                response.startsWith("S=") -> {
                    val rpm = response.substring(2).trim().toIntOrNull()
                    if (rpm != null) {
                        _telemetry.value = current.copy(speedRpm = rpm)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing telemetry response: $response", e)
        }
    }

    private fun startSimulationMode() {
        _config.value = _config.value.copy(
            isConnected = true,
            errorMessage = null
        )

        simulationJob = scope.launch {
            var simEncoder = 0L

            while (isActive) {
                val config = _config.value
                var power = currentTargetPowerPercent

                if (config.invertDirection) {
                    power = -power
                }

                val maxLimitRatio = (config.maxPowerLimitPercent.coerceIn(0, 100) / 100.0f)
                val effectivePower = power * maxLimitRatio

                val deltaEncoder = (effectivePower * 5.0).roundToInt()
                simEncoder += deltaEncoder

                val simSpeedRpm = (effectivePower * 30.0).roundToInt()
                val simCurrentAmps = (kotlin.math.abs(effectivePower) * 0.12)
                val simVoltageVolts = 12.4 + (if (effectivePower != 0f) -0.3 else 0.0)

                _telemetry.value = _telemetry.value.copy(
                    isConnected = true,
                    targetPowerPercent = currentTargetPowerPercent,
                    actualPowerPercent = effectivePower,
                    encoderCount = simEncoder,
                    speedRpm = simSpeedRpm,
                    currentAmps = simCurrentAmps,
                    voltageVolts = simVoltageVolts,
                    statusMessage = "Simulated ${config.motorType.name} Active"
                )

                delay(100)
            }
        }
    }
}

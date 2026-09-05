package com.witt3d.autosteer.hardware

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.*
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.witt3d.autosteer.data.models.MotorConfig
import com.witt3d.autosteer.data.models.MotorTelemetry
import com.witt3d.autosteer.data.models.MotorType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import kotlin.math.roundToInt

class MotorHandler {
    private val TAG = "MotorHandler"

    private val _config = MutableStateFlow(MotorConfig())
    val config: StateFlow<MotorConfig> = _config.asStateFlow()

    private val _telemetry = MutableStateFlow(MotorTelemetry())
    val telemetry: StateFlow<MotorTelemetry> = _telemetry.asStateFlow()

    private var usbPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null

    private var communicationJob: Job? = null
    private var simulationJob: Job? = null
    private var lastContext: Context? = null
    private var isPermissionPending = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentTargetPowerPercent: Float = 0f

    private fun getCustomMotorProber(): UsbSerialProber {
        val probeTable = UsbSerialProber.getDefaultProbeTable()
        // Phidgets VIDs (0x06C2 / 1730)
        probeTable.addProduct(0x06C2, 0x003E, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x06C2, 0x0040, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x06C2, 0x0045, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x06C2, 0x0058, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x06C2, 0x0081, CdcAcmSerialDriver::class.java)
        // Roboteq VIDs (0x20D1 / 8401, 0x0525 / 1317)
        probeTable.addProduct(0x20D1, 0x5740, CdcAcmSerialDriver::class.java)
        probeTable.addProduct(0x0525, 0xA4A7, CdcAcmSerialDriver::class.java)
        return UsbSerialProber(probeTable)
    }

    private fun isMotorDevice(device: UsbDevice, targetMotorType: MotorType): Boolean {
        val vid = device.vendorId
        // NEVER match GPS or IMU devices
        if (vid == 0x1546 || vid == 0x1A86 || vid == 0x4348 || vid == 5840 || vid == 0x16D0) {
            return false
        }
        return when (targetMotorType) {
            MotorType.ROBOTEQ -> vid == 0x20D1 || vid == 0x0525 || vid == 0x0403
            MotorType.PHIDGETS -> vid == 0x06C2
            MotorType.NONE_SIMULATED -> vid == 0x20D1 || vid == 0x0525 || vid == 0x06C2 || vid == 0x0403
        }
    }

    fun updateConfig(newConfig: MotorConfig) {
        val oldConfig = _config.value
        _config.value = newConfig

        if (oldConfig.motorType != newConfig.motorType || oldConfig.isSimulationMode != newConfig.isSimulationMode) {
            if (newConfig.isConnected) {
                // Reconnect with new type/mode settings if currently connected
                connect(lastContext)
            }
        }
    }

    fun scanUsbDevices(context: Context) {
        lastContext = context
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val currentType = _config.value.motorType
        val prober = getCustomMotorProber()
        val availableDrivers = prober.findAllDrivers(usbManager).filter { isMotorDevice(it.device, currentType) }

        if (availableDrivers.isNotEmpty()) {
            val device = availableDrivers[0].device
            if (usbManager.hasPermission(device)) {
                isPermissionPending = false
            }
            val devName = when (device.vendorId) {
                0x06C2 -> "Phidget Motor Controller 1065_1B (${device.manufacturerName ?: "Phidgets"} - ${device.deviceName})"
                0x20D1, 0x0525 -> "Roboteq Motor Controller (${device.manufacturerName ?: "Roboteq"} - ${device.deviceName})"
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
            if (usbManager.hasPermission(matchedDevice)) {
                isPermissionPending = false
            }
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
        disconnect()

        if (context != null) {
            lastContext = context
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

        val usbManager = activeContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val prober = getCustomMotorProber()
        val availableDrivers = prober.findAllDrivers(usbManager).filter { isMotorDevice(it.device, currentConfig.motorType) }

        if (availableDrivers.isNotEmpty()) {
            val driver = availableDrivers[0]
            val device: UsbDevice = driver.device

            if (!usbManager.hasPermission(device)) {
                requestUsbPermission(activeContext, usbManager, device)
                return
            }

            try {
                val connection = usbManager.openDevice(device)
                    ?: throw IOException("Failed to open USB motor controller device")

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
                Log.e(TAG, "Failed to connect to USB motor device", e)
                _config.value = currentConfig.copy(
                    isConnected = false,
                    errorMessage = e.message ?: "USB connection error"
                )
            }
        } else {
            // Check raw USB devices as fallback (e.g. Phidgets 1065_1B USB HID / raw USB Host connection)
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

            // Direct Raw USB Host Connection for Phidgets / USB HID
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
    }

    private fun connectRawUsbDevice(usbManager: UsbManager, device: UsbDevice) {
        val connection = usbManager.openDevice(device)
            ?: throw IOException("Failed to open UsbDeviceConnection for ${device.deviceName}")

        val iface = device.getInterface(0)
        if (!connection.claimInterface(iface, true)) {
            connection.close()
            throw IOException("Could not claim USB Interface 0 for ${device.deviceName}")
        }

        usbConnection = connection
        usbInterface = iface

        val currentConfig = _config.value
        val devName = "${device.manufacturerName ?: "Phidgets"} (${device.productName ?: "1065_1B"})"

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
    }

    private fun startRawUsbCommunicationLoop(connection: UsbDeviceConnection, iface: UsbInterface) {
        communicationJob = scope.launch {
            var endpointIn: UsbEndpoint? = null

            for (i in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(i)
                if (ep.direction == UsbConstants.USB_DIR_IN) {
                    endpointIn = ep
                    break
                }
            }

            val readBuffer = ByteArray(64)
            while (isActive) {
                try {
                    if (endpointIn != null) {
                        val len = connection.bulkTransfer(endpointIn, readBuffer, readBuffer.size, 100)
                        if (len > 0) {
                            val text = String(readBuffer, 0, len, Charsets.US_ASCII)
                            if (text.contains("=")) {
                                parseHardwareTelemetryResponse(text)
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
        isPermissionPending = true
        val intent = Intent("com.witt3d.autosteer.USB_PERMISSION").apply {
            setPackage(context.packageName)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE
        val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
        usbManager.requestPermission(device, permissionIntent)
        _config.value = _config.value.copy(
            isConnected = false,
            errorMessage = "Requesting USB permissions..."
        )
    }

    fun disconnect() {
        communicationJob?.cancel()
        communicationJob = null
        simulationJob?.cancel()
        simulationJob = null
        isPermissionPending = false

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

                    // Periodically query telemetry based on controller type
                    if (_config.value.motorType == MotorType.ROBOTEQ) {
                        val ch = _config.value.roboteqChannel
                        val queryCmd = "?C $ch\r?A $ch\r?V\r?S $ch\r"
                        port.write(queryCmd.toByteArray(Charsets.US_ASCII), 500)
                    } else if (_config.value.motorType == MotorType.PHIDGETS) {
                        val queryCmd = "?P\r?I\r?V\r"
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

        val port = usbPort
        if (port != null) {
            val command = "!DUTY ${config.phidgetChannel} ${String.format("%.2f", dutyCycle)}\r"
            scope.launch {
                try {
                    port.write(command.toByteArray(Charsets.US_ASCII), 200)
                    _telemetry.value = _telemetry.value.copy(actualPowerPercent = dutyCycle)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending Phidget command over serial", e)
                }
            }
        } else {
            val conn = usbConnection
            val iface = usbInterface
            if (conn != null && iface != null) {
                scope.launch {
                    try {
                        var endpointOut: UsbEndpoint? = null
                        for (i in 0 until iface.endpointCount) {
                            val ep = iface.getEndpoint(i)
                            if (ep.direction == UsbConstants.USB_DIR_OUT) {
                                endpointOut = ep
                                break
                            }
                        }
                        val command = "!DUTY ${config.phidgetChannel} ${String.format("%.2f", dutyCycle)}\r"
                        val bytes = command.toByteArray(Charsets.US_ASCII)
                        if (endpointOut != null) {
                            conn.bulkTransfer(endpointOut, bytes, bytes.size, 200)
                        } else {
                            conn.controlTransfer(0x21, 0x09, 0x0200, 0, bytes, bytes.size, 200)
                        }
                        _telemetry.value = _telemetry.value.copy(actualPowerPercent = dutyCycle)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending Phidget command over raw USB", e)
                    }
                }
            }
        }
    }

    fun parseHardwareTelemetryResponse(response: String) {
        val current = _telemetry.value

        try {
            when {
                // Roboteq Encoder query response (e.g., "C=12345" or "C=-5432")
                response.startsWith("C=") -> {
                    val count = response.substring(2).trim().toLongOrNull()
                    if (count != null) {
                        _telemetry.value = current.copy(encoderCount = count)
                    }
                }
                // Roboteq Motor Amps (e.g., "A=15" meaning 1.5A or 15A)
                response.startsWith("A=") -> {
                    val rawAmps = response.substring(2).trim().toDoubleOrNull()
                    if (rawAmps != null) {
                        _telemetry.value = current.copy(currentAmps = rawAmps / 10.0)
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
                // Roboteq Speed RPM (e.g., "S=1500")
                response.startsWith("S=") -> {
                    val rpm = response.substring(2).trim().toIntOrNull()
                    if (rpm != null) {
                        _telemetry.value = current.copy(speedRpm = rpm)
                    }
                }
                // Phidget responses (e.g., "P=12345", "I=2.5")
                response.startsWith("P=") -> {
                    val count = response.substring(2).trim().toLongOrNull()
                    if (count != null) {
                        _telemetry.value = current.copy(encoderCount = count)
                    }
                }
                response.startsWith("I=") -> {
                    val amps = response.substring(2).trim().toDoubleOrNull()
                    if (amps != null) {
                        _telemetry.value = current.copy(currentAmps = amps)
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

                // Simulate encoder step based on power (e.g., at 100% power = 500 counts per 100ms tick)
                val deltaEncoder = (effectivePower * 5.0).roundToInt()
                simEncoder += deltaEncoder

                val simSpeedRpm = (effectivePower * 30.0).roundToInt() // Max ~3000 RPM at 100%
                val simCurrentAmps = (kotlin.math.abs(effectivePower) * 0.12) // Max ~12A at 100%
                val simVoltageVolts = 12.4 + (if (effectivePower != 0f) -0.3 else 0.0) // Slight dip under load

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

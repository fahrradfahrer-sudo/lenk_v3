package com.witt3d.autosteer.hardware.gps

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.witt3d.autosteer.data.models.GpsConfig
import com.witt3d.autosteer.data.models.GpsFixStatus
import com.witt3d.autosteer.data.models.TelemetryData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

class GpsHandler {
    private val TAG = "GpsHandler"
    private val ACTION_USB_PERMISSION = "com.witt3d.autosteer.USB_PERMISSION"

    private val _config = MutableStateFlow(GpsConfig())
    val config: StateFlow<GpsConfig> = _config.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private val _lastRawGga = MutableStateFlow<String?>(null)
    val lastRawGga: StateFlow<String?> = _lastRawGga.asStateFlow()

    private val parser = NmeaParser()
    private var usbPort: UsbSerialPort? = null
    private var connectionJob: Job? = null
    private var simulationJob: Job? = null
    private var lastContext: Context? = null
    private var isUsbReceiverRegistered = false
    private var isPermissionPending = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                Log.d(TAG, "GPS USB Device Attached broadcast received")
                connect(context)
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                Log.d(TAG, "GPS USB Device Detached broadcast received")
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (device != null && isGpsDevice(device)) {
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
                context.applicationContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(usbReceiver, filter)
            }
            isUsbReceiverRegistered = true
            Log.d(TAG, "Registered GPS USB Attached/Detached BroadcastReceiver")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register GPS USB BroadcastReceiver: ${e.message}")
        }
    }

    fun updateConfig(newConfig: GpsConfig) {
        _config.value = newConfig
    }

    private fun isGpsDevice(device: UsbDevice): Boolean {
        val vid = device.vendorId
        // Exclude Motor Controllers (Phidgets, Roboteq) and IMUs (Tinkerforge)
        if (vid == 0x06C2 || vid == 1730 || vid == 0x20D1 || vid == 8401 || vid == 0x0525 || vid == 1317 || vid == 0x16D0 || vid == 5840) {
            return false
        }
        // Match u-blox, CH340/CH341, FTDI, Prolific, Silicon Labs CP210x
        return vid == 0x1546 || vid == 5446 || vid == 0x1A86 || vid == 6790 || vid == 0x4348 || vid == 0x0403 || vid == 0x10C4 || vid == 0x067B
    }

    fun scanUsbDevices(context: Context) {
        lastContext = context
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val gpsDrivers = availableDrivers.filter { isGpsDevice(it.device) }

        if (gpsDrivers.isNotEmpty()) {
            val device = gpsDrivers[0].device
            val hasPerm = usbManager.hasPermission(device)
            if (hasPerm) {
                isPermissionPending = false
            }
            val devName = "${device.manufacturerName ?: "u-blox"} (${device.productName ?: "GPS Receiver"})"
            _config.value = _config.value.copy(
                usbDeviceDetected = true,
                usbDeviceName = devName
            )
            if (!hasPerm && !isPermissionPending) {
                requestUsbPermission(context, usbManager, device)
            }
        } else {
            val rawDevices = usbManager.deviceList.values
            val matchedDevice = rawDevices.firstOrNull { isGpsDevice(it) }
            if (matchedDevice != null) {
                val hasPerm = usbManager.hasPermission(matchedDevice)
                if (hasPerm) {
                    isPermissionPending = false
                }
                val devName = "${matchedDevice.manufacturerName ?: "u-blox"} (${matchedDevice.productName ?: "GPS Receiver"})"
                _config.value = _config.value.copy(
                    usbDeviceDetected = true,
                    usbDeviceName = devName
                )
                if (!hasPerm && !isPermissionPending) {
                    requestUsbPermission(context, usbManager, matchedDevice)
                }
            } else {
                _config.value = _config.value.copy(
                    usbDeviceDetected = false,
                    usbDeviceName = null
                )
            }
        }
    }

    private fun requestUsbPermission(context: Context, usbManager: UsbManager, device: UsbDevice) {
        if (isPermissionPending) {
            Log.d(TAG, "GPS USB Permission request already pending for device: ${device.deviceName}")
            return
        }
        isPermissionPending = true
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
                            try {
                                c?.unregisterReceiver(this)
                            } catch (e: Exception) {
                                Log.w(TAG, "Unregistering GPS permission receiver failed: ${e.message}")
                            }
                            isPermissionPending = false
                            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            Log.d(TAG, "GPS USB Permission callback result: $granted")
                            if (granted) {
                                scanUsbDevices(context)
                                connect(context)
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            usbManager.requestPermission(device, permissionIntent)
            _config.value = _config.value.copy(
                isConnected = false,
                errorMessage = "Requesting USB permissions..."
            )
            Log.d(TAG, "Requested GPS USB Permission for device: ${device.deviceName}")
        } catch (e: Exception) {
            isPermissionPending = false
            Log.e(TAG, "Error requesting GPS USB permission", e)
        }
    }

    fun connect(context: Context? = null) {
        disconnect()

        if (context != null) {
            lastContext = context.applicationContext
            registerUsbReceiver(context.applicationContext)
        }
        val activeContext = context ?: lastContext

        val currentConfig = _config.value
        if (currentConfig.isSimulationMode) {
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
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val gpsDrivers = availableDrivers.filter { isGpsDevice(it.device) }

        if (gpsDrivers.isEmpty()) {
            _config.value = currentConfig.copy(
                isConnected = false,
                errorMessage = "No USB GPS receiver found. Enable Simulation mode to test."
            )
            return
        }

        val driver = gpsDrivers[0]
        val device: UsbDevice = driver.device

        if (!usbManager.hasPermission(device)) {
            requestUsbPermission(activeContext, usbManager, device)
            return
        }

        connectionJob = scope.launch {
            try {
                val connection = usbManager.openDevice(device)
                    ?: throw IOException("Failed to open USB device")

                val port = driver.ports[0]
                port.open(connection)
                port.setParameters(currentConfig.baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                usbPort = port

                _config.value = currentConfig.copy(
                    isConnected = true,
                    usbDeviceDetected = true,
                    usbDeviceName = device.deviceName,
                    errorMessage = null
                )

                val buffer = ByteArray(2048)
                val sb = StringBuilder()

                while (isActive) {
                    val len = port.read(buffer, 1000)
                    if (len > 0) {
                        val text = String(buffer, 0, len, Charsets.US_ASCII)
                        sb.append(text)

                        var newlineIndex = sb.indexOf("\n")
                        while (newlineIndex >= 0) {
                            val line = sb.substring(0, newlineIndex).trim()
                            sb.delete(0, newlineIndex + 1)
                            if (line.isNotEmpty()) {
                                processNmeaLine(line)
                            }
                            newlineIndex = sb.indexOf("\n")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "USB Read Exception", e)
                _config.value = _config.value.copy(
                    isConnected = false,
                    errorMessage = e.message ?: "USB connection error"
                )
            }
        }
    }

    fun writeRtcmData(data: ByteArray) {
        val port = usbPort ?: return
        if (!_config.value.isConnected) return
        scope.launch {
            try {
                port.write(data, 1000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write RTCM data to u-blox serial port", e)
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        simulationJob?.cancel()
        simulationJob = null
        isPermissionPending = false

        try {
            usbPort?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing USB port", e)
        }
        usbPort = null

        _config.value = _config.value.copy(
            isConnected = false
        )
    }

    private fun processNmeaLine(line: String) {
        val parsed = parser.parseSentence(line) ?: return
        parsed.rawGga?.let { gga ->
            _lastRawGga.value = gga
        }

        val current = _telemetry.value
        var newLat = parsed.latitude ?: current.latitude
        var newLon = parsed.longitude ?: current.longitude
        var newSpeed = parsed.speedKmh ?: current.speedKmh
        var newHeading = parsed.headingDegrees ?: current.headingDegrees
        var newFix = parsed.fixStatus ?: current.fixStatus
        var newAccuracy = parsed.accuracyMeters ?: current.accuracyMeters

        var utmX = current.utmX
        var utmY = current.utmY
        if (newLat != 0.0 && newLon != 0.0) {
            val utm = UtmConverter.latLonToUtm(newLat, newLon)
            utmX = utm.easting
            utmY = utm.northing
        }

        _telemetry.value = current.copy(
            latitude = newLat,
            longitude = newLon,
            speedKmh = newSpeed,
            headingDegrees = newHeading,
            fixStatus = newFix,
            accuracyMeters = newAccuracy,
            utmX = utmX,
            utmY = utmY
        )
    }

    private fun startSimulationMode() {
        _config.value = _config.value.copy(
            isConnected = true,
            errorMessage = null
        )

        simulationJob = scope.launch {
            var simLat = 48.137154
            var simLon = 11.576124
            var simHeading = 90.0
            val speedKmh = 12.0 // 12 km/h typical tractor speed

            while (isActive) {
                // Move position based on speed & heading
                val distanceMetersPerSec = (speedKmh * 1000.0 / 3600.0) * 0.2 // 200ms tick
                val deltaLat = (distanceMetersPerSec * Math.cos(Math.toRadians(simHeading))) / 111111.0
                val deltaLon = (distanceMetersPerSec * Math.sin(Math.toRadians(simHeading))) / (111111.0 * Math.cos(Math.toRadians(simLat)))

                simLat += deltaLat
                simLon += deltaLon

                val utm = UtmConverter.latLonToUtm(simLat, simLon)

                val fakeGga = generateFakeGga(simLat, simLon)
                _lastRawGga.value = fakeGga

                _telemetry.value = _telemetry.value.copy(
                    latitude = simLat,
                    longitude = simLon,
                    speedKmh = speedKmh,
                    headingDegrees = simHeading,
                    fixStatus = GpsFixStatus.RTK_FIX,
                    accuracyMeters = 0.014,
                    utmX = utm.easting,
                    utmY = utm.northing
                )

                delay(200)
            }
        }
    }

    private fun generateFakeGga(lat: Double, lon: Double): String {
        val latDeg = lat.toInt()
        val latMin = (lat - latDeg) * 60.0
        val latStr = String.format("%02d%07.4f", latDeg, latMin)

        val lonDeg = lon.toInt()
        val lonMin = (lon - lonDeg) * 60.0
        val lonStr = String.format("%03d%07.4f", lonDeg, lonMin)

        val body = "GNGGA,120000.00,$latStr,N,$lonStr,E,4,12,0.8,500.0,M,0.0,M,,"
        var checksum = 0
        for (ch in body) {
            checksum = checksum xor ch.code
        }
        val hexCheck = String.format("%02X", checksum)
        return "\$$body*$hexCheck"
    }
}

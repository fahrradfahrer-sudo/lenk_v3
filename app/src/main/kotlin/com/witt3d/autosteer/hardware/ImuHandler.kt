package com.witt3d.autosteer.hardware

import android.util.Log
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

    fun setTelemetryListener(listener: (TelemetryData) -> Unit) {
        onTelemetryUpdate = listener
    }

    fun updateConfig(newConfig: ImuConfig) {
        val oldConfig = _config.value
        _config.value = newConfig

        if (oldConfig.isSimulationMode != newConfig.isSimulationMode) {
            disconnect()
            connect()
        }
    }

    fun connect() {
        val currentConfig = _config.value
        if (currentConfig.isSimulationMode) {
            startSimulation()
            _config.value = currentConfig.copy(isConnected = true)
            Log.d(TAG, "Started Simulated IMU Mode")
            return
        }

        stopSimulation()

        try {
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

            _config.value = _config.value.copy(isConnected = true)
            Log.d(TAG, "Connected to Tinkerforge at ${currentConfig.host}:${currentConfig.port}")
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Tinkerforge IMU Brick v2", e)
            _config.value = _config.value.copy(isConnected = false)
        }
    }

    private fun setupImuDevice(connection: IPConnection, uid: String) {
        try {
            val brick = BrickIMUV2(uid, connection)
            imu = brick
            _config.value = _config.value.copy(uid = uid)

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

            // Set period for orientation callbacks (e.g. 50ms = 20Hz)
            brick.setOrientationPeriod(50)
            brick.setLinearAccelerationPeriod(50)

            Log.d(TAG, "IMU Brick v2 initialized with UID: $uid")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BrickIMUV2 with UID: $uid", e)
        }
    }

    fun disconnect() {
        stopSimulation()
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

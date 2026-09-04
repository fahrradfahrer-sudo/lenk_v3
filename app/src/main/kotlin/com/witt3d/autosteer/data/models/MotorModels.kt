package com.witt3d.autosteer.data.models

enum class MotorType {
    NONE_SIMULATED,
    ROBOTEQ,
    PHIDGETS
}

data class MotorConfig(
    val motorType: MotorType = MotorType.NONE_SIMULATED,
    val roboteqChannel: Int = 1,
    val roboteqBaudRate: Int = 115200,
    val phidgetChannel: Int = 0,
    val phidgetSerialNumber: Int = -1, // -1 means any device
    val phidgetAcceleration: Double = 100.0, // percent/s or duty cycle acceleration
    val phidgetCurrentLimit: Double = 10.0, // Amperes
    val invertDirection: Boolean = false,
    val maxPowerLimitPercent: Int = 100, // 0 to 100%
    val resetOnRelease: Boolean = false,
    val isSimulationMode: Boolean = false,
    val isConnected: Boolean = false,
    val usbDeviceDetected: Boolean = false,
    val usbDeviceName: String? = null,
    val errorMessage: String? = null
)

data class MotorTelemetry(
    val targetPowerPercent: Float = 0f,
    val actualPowerPercent: Float = 0f,
    val encoderCount: Long = 0L,
    val currentAmps: Double = 0.0,
    val voltageVolts: Double = 0.0,
    val speedRpm: Int = 0,
    val isConnected: Boolean = false,
    val statusMessage: String = "Disconnected"
)

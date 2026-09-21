package com.witt3d.autosteer.data.models

enum class SteeringAlgorithm(val displayName: String) {
    PURE_PURSUIT("Pure Pursuit"),
    STANLEY("Stanley")
}

enum class MotorControlMode(val displayName: String) {
    PASSES_PER_DEGREE("Passes Per Degree (Cerea)"),
    PID("PID Closed Loop")
}

enum class HeadingSource(val displayName: String) {
    AUTO_FUSED("Auto / Fused (IMU low speed, GPS high speed)"),
    IMU_ONLY("IMU Only"),
    GPS_COG_ONLY("GPS Course Over Ground Only")
}

data class SteeringConfig(
    val algorithm: SteeringAlgorithm = SteeringAlgorithm.PURE_PURSUIT,
    val controlMode: MotorControlMode = MotorControlMode.PASSES_PER_DEGREE,
    val headingSource: HeadingSource = HeadingSource.AUTO_FUSED,
    val minHeadingSpeedKmh: Double = 1.5,
    val maxMotorCurrentCutoffAmps: Double = 15.0,
    val lookaheadDistanceMeters: Double = 4.0,
    val lookaheadSpeedGain: Double = 0.5,
    val stanleyK: Double = 1.0,
    val stanleyHeadingGain: Double = 1.0,
    val passesPerDegree: Int = 300,
    val encoderCenterOffset: Long = 0L,
    val kp: Double = 1.5,
    val ki: Double = 0.0,
    val kd: Double = 0.1,
    val deadbandDegrees: Double = 0.5,
    val maxOutputPowerPercent: Int = 100
)

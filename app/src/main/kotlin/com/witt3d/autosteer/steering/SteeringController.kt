package com.witt3d.autosteer.steering

import com.witt3d.autosteer.data.models.MotorControlMode
import com.witt3d.autosteer.data.models.MotorTelemetry
import com.witt3d.autosteer.data.models.SteeringConfig
import kotlin.math.abs

class SteeringController {
    private var integralError = 0.0
    private var lastError = 0.0
    private var lastTimestampMs = 0L

    fun resetPid() {
        integralError = 0.0
        lastError = 0.0
        lastTimestampMs = 0L
    }

    /**
     * Estimates current steering angle in degrees based on encoder count and zero-center offset.
     */
    fun estimateCurrentSteeringAngleDegrees(
        motorTelemetry: MotorTelemetry,
        steeringConfig: SteeringConfig
    ): Double {
        val passesPerDegree = if (steeringConfig.passesPerDegree != 0) steeringConfig.passesPerDegree else 300
        var relativeEncoder = (motorTelemetry.encoderCount - steeringConfig.encoderCenterOffset).toDouble()
        if (steeringConfig.invertEncoderSign) {
            relativeEncoder = -relativeEncoder
        }
        return relativeEncoder / passesPerDegree.toDouble()
    }

    /**
     * Calculates required motor target power percent (-100% to +100%) to achieve target steering angle.
     */
    fun calculateMotorPowerPercent(
        targetSteeringAngleDegrees: Double,
        motorTelemetry: MotorTelemetry,
        steeringConfig: SteeringConfig
    ): Float {
        val currentAngleDeg = estimateCurrentSteeringAngleDegrees(motorTelemetry, steeringConfig)
        val errorDegrees = targetSteeringAngleDegrees - currentAngleDeg

        if (abs(errorDegrees) <= steeringConfig.deadbandDegrees) {
            return 0f
        }

        val maxPower = steeringConfig.maxOutputPowerPercent.toFloat()

        var rawPower = when (steeringConfig.controlMode) {
            MotorControlMode.PASSES_PER_DEGREE -> {
                // Passes per degree proportional control (Cerea style)
                (errorDegrees * steeringConfig.kp).toFloat()
            }
            MotorControlMode.PID -> {
                val currentTimeMs = System.currentTimeMillis()
                val dtSec = if (lastTimestampMs > 0) (currentTimeMs - lastTimestampMs) / 1000.0 else 0.05
                lastTimestampMs = currentTimeMs

                integralError += errorDegrees * dtSec
                // Anti-windup clamping
                integralError = integralError.coerceIn(-20.0, 20.0)

                val derivative = if (dtSec > 0) (errorDegrees - lastError) / dtSec else 0.0
                lastError = errorDegrees

                val output = steeringConfig.kp * errorDegrees +
                        steeringConfig.ki * integralError +
                        steeringConfig.kd * derivative

                output.toFloat()
            }
        }

        if (steeringConfig.invertMotorOutput) {
            rawPower = -rawPower
        }

        return rawPower.coerceIn(-maxPower, maxPower)
    }
}

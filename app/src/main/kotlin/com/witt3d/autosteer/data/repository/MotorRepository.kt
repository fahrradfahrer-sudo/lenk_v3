package com.witt3d.autosteer.data.repository

import android.content.Context
import com.witt3d.autosteer.data.models.MotorConfig
import com.witt3d.autosteer.data.models.MotorType

object MotorRepository {
    private const val PREFS_NAME = "motor_prefs"

    private const val KEY_MOTOR_TYPE = "motor_type"
    private const val KEY_ROBOTEQ_CHANNEL = "roboteq_channel"
    private const val KEY_ROBOTEQ_BAUD = "roboteq_baud"
    private const val KEY_PHIDGET_CHANNEL = "phidget_channel"
    private const val KEY_PHIDGET_SERIAL = "phidget_serial"
    private const val KEY_PHIDGET_ACCEL = "phidget_accel"
    private const val KEY_PHIDGET_CURRENT_LIMIT = "phidget_current_limit"
    private const val KEY_INVERT_DIRECTION = "invert_direction"
    private const val KEY_MAX_POWER_LIMIT = "max_power_limit"
    private const val KEY_RESET_ON_RELEASE = "reset_on_release"
    private const val KEY_IS_SIMULATION = "is_simulation"

    fun loadMotorConfig(context: Context): MotorConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val typeStr = prefs.getString(KEY_MOTOR_TYPE, MotorType.NONE_SIMULATED.name)
        val motorType = try {
            MotorType.valueOf(typeStr ?: MotorType.NONE_SIMULATED.name)
        } catch (e: Exception) {
            MotorType.NONE_SIMULATED
        }

        return MotorConfig(
            motorType = motorType,
            roboteqChannel = prefs.getInt(KEY_ROBOTEQ_CHANNEL, 1),
            roboteqBaudRate = prefs.getInt(KEY_ROBOTEQ_BAUD, 115200),
            phidgetChannel = prefs.getInt(KEY_PHIDGET_CHANNEL, 0),
            phidgetSerialNumber = prefs.getInt(KEY_PHIDGET_SERIAL, -1),
            phidgetAcceleration = prefs.getFloat(KEY_PHIDGET_ACCEL, 100f).toDouble(),
            phidgetCurrentLimit = prefs.getFloat(KEY_PHIDGET_CURRENT_LIMIT, 10f).toDouble(),
            invertDirection = prefs.getBoolean(KEY_INVERT_DIRECTION, false),
            maxPowerLimitPercent = prefs.getInt(KEY_MAX_POWER_LIMIT, 100),
            resetOnRelease = prefs.getBoolean(KEY_RESET_ON_RELEASE, false),
            isSimulationMode = prefs.getBoolean(KEY_IS_SIMULATION, false)
        )
    }

    fun saveMotorConfig(context: Context, config: MotorConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_MOTOR_TYPE, config.motorType.name)
            .putInt(KEY_ROBOTEQ_CHANNEL, config.roboteqChannel)
            .putInt(KEY_ROBOTEQ_BAUD, config.roboteqBaudRate)
            .putInt(KEY_PHIDGET_CHANNEL, config.phidgetChannel)
            .putInt(KEY_PHIDGET_SERIAL, config.phidgetSerialNumber)
            .putFloat(KEY_PHIDGET_ACCEL, config.phidgetAcceleration.toFloat())
            .putFloat(KEY_PHIDGET_CURRENT_LIMIT, config.phidgetCurrentLimit.toFloat())
            .putBoolean(KEY_INVERT_DIRECTION, config.invertDirection)
            .putInt(KEY_MAX_POWER_LIMIT, config.maxPowerLimitPercent)
            .putBoolean(KEY_RESET_ON_RELEASE, config.resetOnRelease)
            .putBoolean(KEY_IS_SIMULATION, config.isSimulationMode)
            .apply()
    }
}

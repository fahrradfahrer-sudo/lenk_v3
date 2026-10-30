package com.witt3d.autosteer.data.repository

import android.content.Context
import com.witt3d.autosteer.data.models.MotorControlMode
import com.witt3d.autosteer.data.models.SteeringAlgorithm
import com.witt3d.autosteer.data.models.SteeringConfig
import com.witt3d.autosteer.data.models.TractorConfig

object TractorRepository {
    private const val PREFS_NAME = "tractor_steering_prefs"

    // Tractor config keys
    private const val KEY_WHEELBASE_CM = "wheelbase_cm"
    private const val KEY_ANTENNA_REAR_AXLE_CM = "antenna_rear_axle_cm"
    private const val KEY_ANTENNA_HEIGHT_CM = "antenna_height_cm"
    private const val KEY_TRACK_WIDTH_CM = "track_width_cm"
    private const val KEY_MAX_STEERING_ANGLE = "max_steering_angle"
    private const val KEY_ANTENNA_OFFSET_CM = "antenna_offset_cm"

    // Steering config keys
    private const val KEY_ALGORITHM = "steering_algorithm"
    private const val KEY_CONTROL_MODE = "control_mode"
    private const val KEY_HEADING_SOURCE = "heading_source"
    private const val KEY_MIN_HEADING_SPEED = "min_heading_speed"
    private const val KEY_MAX_MOTOR_CURRENT_CUTOFF = "max_motor_current_cutoff"
    private const val KEY_LOOKAHEAD_DIST = "lookahead_dist"
    private const val KEY_LOOKAHEAD_GAIN = "lookahead_gain"
    private const val KEY_STANLEY_K = "stanley_k"
    private const val KEY_STANLEY_HEADING_GAIN = "stanley_heading_gain"
    private const val KEY_PASSES_PER_DEGREE = "passes_per_degree"
    private const val KEY_ENCODER_CENTER_OFFSET = "encoder_center_offset"
    private const val KEY_KP = "kp"
    private const val KEY_KI = "ki"
    private const val KEY_KD = "kd"
    private const val KEY_DEADBAND = "deadband_degrees"
    private const val KEY_MIN_MOTOR_OUTPUT_POWER = "min_motor_output_power"
    private const val KEY_MAX_OUTPUT_POWER = "max_output_power"
    private const val KEY_INVERT_ENCODER_SIGN = "invert_encoder_sign"
    private const val KEY_INVERT_MOTOR_OUTPUT = "invert_motor_output"
    private const val KEY_ENABLE_FILTERING = "enable_filtering"
    private const val KEY_HEADING_SMOOTHING_ALPHA = "heading_smoothing_alpha"
    private const val KEY_POSITION_SMOOTHING_ALPHA = "position_smoothing_alpha"
    private const val KEY_AUTOSTEER_LOOP_FREQ_HZ = "autosteer_loop_freq_hz"
    private const val KEY_HEADING_OFFSET_DEGREES = "heading_offset_degrees"

    fun loadTractorConfig(context: Context): TractorConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return TractorConfig(
            wheelbaseCm = prefs.getFloat(KEY_WHEELBASE_CM, 280f).toDouble(),
            antennaToRearAxleCm = prefs.getFloat(KEY_ANTENNA_REAR_AXLE_CM, 120f).toDouble(),
            antennaHeightCm = prefs.getFloat(KEY_ANTENNA_HEIGHT_CM, 300f).toDouble(),
            trackWidthCm = prefs.getFloat(KEY_TRACK_WIDTH_CM, 200f).toDouble(),
            maxSteeringAngleDegrees = prefs.getFloat(KEY_MAX_STEERING_ANGLE, 35f).toDouble(),
            antennaOffsetCm = prefs.getFloat(KEY_ANTENNA_OFFSET_CM, 0f).toDouble()
        )
    }

    fun saveTractorConfig(context: Context, config: TractorConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_WHEELBASE_CM, config.wheelbaseCm.toFloat())
            .putFloat(KEY_ANTENNA_REAR_AXLE_CM, config.antennaToRearAxleCm.toFloat())
            .putFloat(KEY_ANTENNA_HEIGHT_CM, config.antennaHeightCm.toFloat())
            .putFloat(KEY_TRACK_WIDTH_CM, config.trackWidthCm.toFloat())
            .putFloat(KEY_MAX_STEERING_ANGLE, config.maxSteeringAngleDegrees.toFloat())
            .putFloat(KEY_ANTENNA_OFFSET_CM, config.antennaOffsetCm.toFloat())
            .apply()
    }

    fun loadSteeringConfig(context: Context): SteeringConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val algoStr = prefs.getString(KEY_ALGORITHM, SteeringAlgorithm.PURE_PURSUIT.name)
        val algorithm = try {
            SteeringAlgorithm.valueOf(algoStr ?: SteeringAlgorithm.PURE_PURSUIT.name)
        } catch (e: Exception) {
            SteeringAlgorithm.PURE_PURSUIT
        }

        val modeStr = prefs.getString(KEY_CONTROL_MODE, MotorControlMode.PASSES_PER_DEGREE.name)
        val controlMode = try {
            MotorControlMode.valueOf(modeStr ?: MotorControlMode.PASSES_PER_DEGREE.name)
        } catch (e: Exception) {
            MotorControlMode.PASSES_PER_DEGREE
        }

        val headingSourceStr = prefs.getString(KEY_HEADING_SOURCE, com.witt3d.autosteer.data.models.HeadingSource.AUTO_FUSED.name)
        val headingSource = try {
            com.witt3d.autosteer.data.models.HeadingSource.valueOf(headingSourceStr ?: com.witt3d.autosteer.data.models.HeadingSource.AUTO_FUSED.name)
        } catch (e: Exception) {
            com.witt3d.autosteer.data.models.HeadingSource.AUTO_FUSED
        }

        return SteeringConfig(
            algorithm = algorithm,
            controlMode = controlMode,
            headingSource = headingSource,
            minHeadingSpeedKmh = prefs.getFloat(KEY_MIN_HEADING_SPEED, 1.5f).toDouble(),
            maxMotorCurrentCutoffAmps = prefs.getFloat(KEY_MAX_MOTOR_CURRENT_CUTOFF, 15.0f).toDouble(),
            lookaheadDistanceMeters = prefs.getFloat(KEY_LOOKAHEAD_DIST, 4.0f).toDouble(),
            lookaheadSpeedGain = prefs.getFloat(KEY_LOOKAHEAD_GAIN, 0.5f).toDouble(),
            stanleyK = prefs.getFloat(KEY_STANLEY_K, 1.0f).toDouble(),
            stanleyHeadingGain = prefs.getFloat(KEY_STANLEY_HEADING_GAIN, 1.0f).toDouble(),
            passesPerDegree = prefs.getInt(KEY_PASSES_PER_DEGREE, 300),
            encoderCenterOffset = prefs.getLong(KEY_ENCODER_CENTER_OFFSET, 0L),
            kp = prefs.getFloat(KEY_KP, 1.5f).toDouble(),
            ki = prefs.getFloat(KEY_KI, 0.0f).toDouble(),
            kd = prefs.getFloat(KEY_KD, 0.1f).toDouble(),
            deadbandDegrees = prefs.getFloat(KEY_DEADBAND, 0.5f).toDouble(),
            minMotorOutputPowerPercent = prefs.getInt(KEY_MIN_MOTOR_OUTPUT_POWER, 20),
            maxOutputPowerPercent = prefs.getInt(KEY_MAX_OUTPUT_POWER, 100),
            invertEncoderSign = prefs.getBoolean(KEY_INVERT_ENCODER_SIGN, false),
            invertMotorOutput = prefs.getBoolean(KEY_INVERT_MOTOR_OUTPUT, false),
            enableFiltering = prefs.getBoolean(KEY_ENABLE_FILTERING, true),
            headingSmoothingAlpha = prefs.getFloat(KEY_HEADING_SMOOTHING_ALPHA, 0.3f).toDouble(),
            positionSmoothingAlpha = prefs.getFloat(KEY_POSITION_SMOOTHING_ALPHA, 0.5f).toDouble(),
            autoSteerLoopFrequencyHz = prefs.getInt(KEY_AUTOSTEER_LOOP_FREQ_HZ, 10),
            headingOffsetDegrees = prefs.getFloat(KEY_HEADING_OFFSET_DEGREES, 0.0f).toDouble()
        )
    }

    fun saveSteeringConfig(context: Context, config: SteeringConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ALGORITHM, config.algorithm.name)
            .putString(KEY_CONTROL_MODE, config.controlMode.name)
            .putString(KEY_HEADING_SOURCE, config.headingSource.name)
            .putFloat(KEY_MIN_HEADING_SPEED, config.minHeadingSpeedKmh.toFloat())
            .putFloat(KEY_MAX_MOTOR_CURRENT_CUTOFF, config.maxMotorCurrentCutoffAmps.toFloat())
            .putFloat(KEY_LOOKAHEAD_DIST, config.lookaheadDistanceMeters.toFloat())
            .putFloat(KEY_LOOKAHEAD_GAIN, config.lookaheadSpeedGain.toFloat())
            .putFloat(KEY_STANLEY_K, config.stanleyK.toFloat())
            .putFloat(KEY_STANLEY_HEADING_GAIN, config.stanleyHeadingGain.toFloat())
            .putInt(KEY_PASSES_PER_DEGREE, config.passesPerDegree)
            .putLong(KEY_ENCODER_CENTER_OFFSET, config.encoderCenterOffset)
            .putFloat(KEY_KP, config.kp.toFloat())
            .putFloat(KEY_KI, config.ki.toFloat())
            .putFloat(KEY_KD, config.kd.toFloat())
            .putFloat(KEY_DEADBAND, config.deadbandDegrees.toFloat())
            .putInt(KEY_MIN_MOTOR_OUTPUT_POWER, config.minMotorOutputPowerPercent)
            .putInt(KEY_MAX_OUTPUT_POWER, config.maxOutputPowerPercent)
            .putBoolean(KEY_INVERT_ENCODER_SIGN, config.invertEncoderSign)
            .putBoolean(KEY_INVERT_MOTOR_OUTPUT, config.invertMotorOutput)
            .putBoolean(KEY_ENABLE_FILTERING, config.enableFiltering)
            .putFloat(KEY_HEADING_SMOOTHING_ALPHA, config.headingSmoothingAlpha.toFloat())
            .putFloat(KEY_POSITION_SMOOTHING_ALPHA, config.positionSmoothingAlpha.toFloat())
            .putInt(KEY_AUTOSTEER_LOOP_FREQ_HZ, config.autoSteerLoopFrequencyHz)
            .putFloat(KEY_HEADING_OFFSET_DEGREES, config.headingOffsetDegrees.toFloat())
            .apply()
    }
}

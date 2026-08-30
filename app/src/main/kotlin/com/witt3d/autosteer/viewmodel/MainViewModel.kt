package com.witt3d.autosteer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.witt3d.autosteer.data.models.ImuConfig
import com.witt3d.autosteer.data.models.TelemetryData
import com.witt3d.autosteer.hardware.ImuHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    val imuHandler = ImuHandler()
    val imuConfig: StateFlow<ImuConfig> = imuHandler.config

    init {
        imuHandler.setTelemetryListener { newTelemetry ->
            _telemetry.value = _telemetry.value.copy(
                headingDegrees = newTelemetry.headingDegrees,
                inclinationDegrees = newTelemetry.inclinationDegrees,
                pitchDegrees = newTelemetry.pitchDegrees,
                accelX = newTelemetry.accelX,
                accelY = newTelemetry.accelY,
                accelZ = newTelemetry.accelZ
            )
        }
        // Auto-connect IMU handler on ViewModel creation
        imuHandler.connect()
    }

    fun scanUsbDevices(context: Context) {
        imuHandler.scanUsbDevices(context)
    }

    fun updateTelemetry(newData: TelemetryData) {
        _telemetry.value = newData
    }

    fun updateImuConfig(newConfig: ImuConfig) {
        imuHandler.updateConfig(newConfig)
    }

    fun connectImu() {
        imuHandler.connect()
    }

    fun disconnectImu() {
        imuHandler.disconnect()
    }

    fun setSimulatedImuOrientation(
        heading: Double,
        roll: Double,
        pitch: Double,
        accelX: Double = 0.0,
        accelY: Double = 0.0,
        accelZ: Double = 9.81
    ) {
        imuHandler.setSimulatedOrientation(heading, roll, pitch, accelX, accelY, accelZ)
    }

    override fun onCleared() {
        super.onCleared()
        imuHandler.disconnect()
    }
}

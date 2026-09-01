package com.witt3d.autosteer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.witt3d.autosteer.data.models.GpsConfig
import com.witt3d.autosteer.data.models.ImuConfig
import com.witt3d.autosteer.data.models.NtripConfig
import com.witt3d.autosteer.data.models.TelemetryData
import com.witt3d.autosteer.data.repository.GpsRepository
import com.witt3d.autosteer.data.repository.HardwareRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    val imuHandler = HardwareRepository.imuHandler
    val imuConfig: StateFlow<ImuConfig> = imuHandler.config

    val gpsHandler = HardwareRepository.gpsHandler
    val gpsConfig: StateFlow<GpsConfig> = gpsHandler.config

    val ntripClient = HardwareRepository.ntripClient
    val ntripConfig: StateFlow<NtripConfig> = ntripClient.config

    init {
        // Collect IMU telemetry updates
        viewModelScope.launch {
            imuHandler.telemetry.collect { imuTele ->
                _telemetry.value = _telemetry.value.copy(
                    headingDegrees = if (_telemetry.value.headingDegrees == 0.0) imuTele.headingDegrees else _telemetry.value.headingDegrees,
                    inclinationDegrees = imuTele.inclinationDegrees,
                    pitchDegrees = imuTele.pitchDegrees,
                    accelX = imuTele.accelX,
                    accelY = imuTele.accelY,
                    accelZ = imuTele.accelZ
                )
            }
        }

        // Collect GPS telemetry updates
        viewModelScope.launch {
            gpsHandler.telemetry.collect { gpsTele ->
                _telemetry.value = _telemetry.value.copy(
                    latitude = gpsTele.latitude,
                    longitude = gpsTele.longitude,
                    utmX = gpsTele.utmX,
                    utmY = gpsTele.utmY,
                    speedKmh = gpsTele.speedKmh,
                    headingDegrees = if (gpsTele.headingDegrees != 0.0) gpsTele.headingDegrees else _telemetry.value.headingDegrees,
                    fixStatus = gpsTele.fixStatus,
                    accuracyMeters = gpsTele.accuracyMeters
                )
            }
        }

        if (!imuConfig.value.isConnected) {
            imuHandler.connect()
        }
    }

    fun scanUsbDevices(context: Context) {
        imuHandler.scanUsbDevices(context)
        gpsHandler.scanUsbDevices(context)
    }

    fun updateTelemetry(newData: TelemetryData) {
        _telemetry.value = newData
    }

    fun updateImuConfig(newConfig: ImuConfig) {
        imuHandler.updateConfig(newConfig)
    }

    fun connectImu(context: Context? = null) {
        imuHandler.connect(context)
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

    // GPS & NTRIP Methods
    fun updateGpsConfig(context: Context, newConfig: GpsConfig) {
        GpsRepository.saveGpsConfig(context, newConfig)
        gpsHandler.updateConfig(newConfig)
    }

    fun connectGps(context: Context? = null) {
        gpsHandler.connect(context)
    }

    fun disconnectGps() {
        gpsHandler.disconnect()
    }

    fun updateNtripConfig(context: Context, newConfig: NtripConfig) {
        GpsRepository.saveNtripConfig(context, newConfig)
        ntripClient.updateConfig(newConfig)
    }

    fun connectNtrip() {
        ntripClient.connect()
    }

    fun disconnectNtrip() {
        ntripClient.disconnect()
    }
}

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
                    headingDegrees = imuTele.headingDegrees,
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

    fun addNtripProfile(context: Context) {
        val current = ntripConfig.value
        val newProfile = com.witt3d.autosteer.data.models.NtripProfile(
            name = "NTRIP Service ${current.profiles.size + 1}",
            priority = current.profiles.size
        )
        val updatedProfiles = current.profiles + newProfile
        updateNtripConfig(context, current.copy(profiles = updatedProfiles))
    }

    fun updateNtripProfile(context: Context, profile: com.witt3d.autosteer.data.models.NtripProfile) {
        val current = ntripConfig.value
        val updatedProfiles = current.profiles.map { if (it.id == profile.id) profile else it }
        updateNtripConfig(context, current.copy(profiles = updatedProfiles))
    }

    fun deleteNtripProfile(context: Context, profileId: String) {
        val current = ntripConfig.value
        val updatedProfiles = current.profiles.filterNot { it.id == profileId }.mapIndexed { index, prof ->
            prof.copy(priority = index)
        }
        updateNtripConfig(context, current.copy(profiles = updatedProfiles))
    }

    fun moveNtripProfileUp(context: Context, index: Int) {
        if (index <= 0) return
        val current = ntripConfig.value
        val list = current.profiles.toMutableList()
        val item = list.removeAt(index)
        list.add(index - 1, item)
        val reordered = list.mapIndexed { idx, prof -> prof.copy(priority = idx) }
        updateNtripConfig(context, current.copy(profiles = reordered))
    }

    fun moveNtripProfileDown(context: Context, index: Int) {
        val current = ntripConfig.value
        if (index >= current.profiles.size - 1) return
        val list = current.profiles.toMutableList()
        val item = list.removeAt(index)
        list.add(index + 1, item)
        val reordered = list.mapIndexed { idx, prof -> prof.copy(priority = idx) }
        updateNtripConfig(context, current.copy(profiles = reordered))
    }

    fun connectNtrip() {
        ntripClient.connect()
    }

    fun disconnectNtrip() {
        ntripClient.disconnect()
    }
}

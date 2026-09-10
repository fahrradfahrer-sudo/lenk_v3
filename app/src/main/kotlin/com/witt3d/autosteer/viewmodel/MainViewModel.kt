package com.witt3d.autosteer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.witt3d.autosteer.data.models.AbLine
import com.witt3d.autosteer.data.models.GpsConfig
import com.witt3d.autosteer.data.models.GuidanceState
import com.witt3d.autosteer.data.models.ImuConfig
import com.witt3d.autosteer.data.models.MotorConfig
import com.witt3d.autosteer.data.models.MotorTelemetry
import com.witt3d.autosteer.data.models.NtripConfig
import com.witt3d.autosteer.data.models.SteeringConfig
import com.witt3d.autosteer.data.models.TelemetryData
import com.witt3d.autosteer.data.models.TractorConfig
import com.witt3d.autosteer.data.models.UtmPoint
import com.witt3d.autosteer.data.repository.GpsRepository
import com.witt3d.autosteer.data.repository.HardwareRepository
import com.witt3d.autosteer.data.repository.MotorRepository
import com.witt3d.autosteer.data.repository.TractorRepository
import com.witt3d.autosteer.steering.SteeringAlgorithms
import com.witt3d.autosteer.steering.SteeringController
import com.witt3d.autosteer.steering.TiltCompensator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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

    val motorHandler = HardwareRepository.motorHandler
    val motorConfig: StateFlow<MotorConfig> = motorHandler.config
    val motorTelemetry: StateFlow<MotorTelemetry> = motorHandler.telemetry

    // Tractor & Steering Configuration State
    private val _tractorConfig = MutableStateFlow(TractorConfig())
    val tractorConfig: StateFlow<TractorConfig> = _tractorConfig.asStateFlow()

    private val _steeringConfig = MutableStateFlow(SteeringConfig())
    val steeringConfig: StateFlow<SteeringConfig> = _steeringConfig.asStateFlow()

    private val _guidanceState = MutableStateFlow(GuidanceState())
    val guidanceState: StateFlow<GuidanceState> = _guidanceState.asStateFlow()

    private val steeringController = SteeringController()

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

        // AutoSteer Control Loop (20Hz = 50ms)
        viewModelScope.launch {
            while (isActive) {
                processAutoSteerLoop()
                delay(50)
            }
        }
    }

    fun loadPersistedConfigs(context: Context) {
        _tractorConfig.value = TractorRepository.loadTractorConfig(context)
        _steeringConfig.value = TractorRepository.loadSteeringConfig(context)
    }

    private fun processAutoSteerLoop() {
        val currentTele = _telemetry.value
        val currentTractor = _tractorConfig.value
        val currentSteering = _steeringConfig.value
        val currentGuidance = _guidanceState.value

        val antennaUtm = UtmPoint(currentTele.utmX, currentTele.utmY)
        val groundAxleUtm = TiltCompensator.computeGroundRearAxlePosition(
            antennaUtm = antennaUtm,
            headingDegrees = currentTele.headingDegrees,
            rollDegrees = currentTele.inclinationDegrees,
            tractorConfig = currentTractor
        )

        val currentSteerAngle = steeringController.estimateCurrentSteeringAngleDegrees(
            motorTelemetry = motorTelemetry.value,
            steeringConfig = currentSteering
        )

        val activeLine = currentGuidance.abLine

        if (activeLine != null) {
            val cteMeters = activeLine.calculateCrossTrackErrorMeters(groundAxleUtm)
            val cteCm = cteMeters * 100.0

            val targetSteeringAngle = SteeringAlgorithms.calculateTargetSteeringAngle(
                groundAxleUtm = groundAxleUtm,
                headingDegrees = currentTele.headingDegrees,
                speedKmh = currentTele.speedKmh,
                abLine = activeLine,
                tractorConfig = currentTractor,
                steeringConfig = currentSteering
            )

            val motorPower = steeringController.calculateMotorPowerPercent(
                targetSteeringAngleDegrees = targetSteeringAngle,
                motorTelemetry = motorTelemetry.value,
                steeringConfig = currentSteering
            )

            if (currentGuidance.isAutoSteerEngaged) {
                motorHandler.setTargetPowerPercent(motorPower)
            }

            _guidanceState.value = currentGuidance.copy(
                crossTrackErrorCm = cteCm,
                targetSteeringAngleDegrees = targetSteeringAngle,
                compensatedUtmX = groundAxleUtm.x,
                compensatedUtmY = groundAxleUtm.y,
                currentSteeringAngleDegrees = currentSteerAngle,
                steeringStatusMessage = if (currentGuidance.isAutoSteerEngaged) "AUTOSTEER ENGAGED" else "Guidance Active (Disengaged)"
            )
        } else {
            _guidanceState.value = currentGuidance.copy(
                crossTrackErrorCm = 0.0,
                targetSteeringAngleDegrees = 0.0,
                compensatedUtmX = groundAxleUtm.x,
                compensatedUtmY = groundAxleUtm.y,
                currentSteeringAngleDegrees = currentSteerAngle,
                steeringStatusMessage = if (currentGuidance.pointA == null) "Set Point A" else "Set Point B"
            )
        }
    }

    fun scanUsbDevices(context: Context) {
        loadPersistedConfigs(context)
        imuHandler.scanUsbDevices(context)
        gpsHandler.scanUsbDevices(context)
        motorHandler.scanUsbDevices(context)
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

    // Motor Methods
    fun updateMotorConfig(context: Context, newConfig: MotorConfig) {
        MotorRepository.saveMotorConfig(context, newConfig)
        motorHandler.updateConfig(newConfig)
    }

    fun connectMotor(context: Context? = null) {
        motorHandler.connect(context)
    }

    fun disconnectMotor() {
        motorHandler.disconnect()
    }

    fun setMotorTargetPowerPercent(powerPercent: Float) {
        motorHandler.setTargetPowerPercent(powerPercent)
    }

    fun emergencyStopMotor() {
        disengageAutoSteer()
        motorHandler.emergencyStop()
    }

    // Tractor & Steering Configuration
    fun updateTractorConfig(context: Context, newConfig: TractorConfig) {
        TractorRepository.saveTractorConfig(context, newConfig)
        _tractorConfig.value = newConfig
    }

    fun updateSteeringConfig(context: Context, newConfig: SteeringConfig) {
        TractorRepository.saveSteeringConfig(context, newConfig)
        _steeringConfig.value = newConfig
        steeringController.resetPid()
    }

    fun setSteeringCenter(context: Context) {
        val currentEncoder = motorTelemetry.value.encoderCount
        val updatedConfig = _steeringConfig.value.copy(encoderCenterOffset = currentEncoder)
        updateSteeringConfig(context, updatedConfig)
    }

    // AB Line & Guidance Methods
    fun setPointA() {
        val currentGuidance = _guidanceState.value
        val point = UtmPoint(currentGuidance.compensatedUtmX, currentGuidance.compensatedUtmY)
        val newAbLine = if (currentGuidance.pointB != null) AbLine(point, currentGuidance.pointB) else null
        _guidanceState.value = currentGuidance.copy(
            pointA = point,
            abLine = newAbLine
        )
    }

    fun setPointB() {
        val currentGuidance = _guidanceState.value
        val point = UtmPoint(currentGuidance.compensatedUtmX, currentGuidance.compensatedUtmY)
        val newAbLine = if (currentGuidance.pointA != null) AbLine(currentGuidance.pointA, point) else null
        _guidanceState.value = currentGuidance.copy(
            pointB = point,
            abLine = newAbLine
        )
    }

    fun clearAbLine() {
        disengageAutoSteer()
        _guidanceState.value = _guidanceState.value.copy(
            pointA = null,
            pointB = null,
            abLine = null,
            crossTrackErrorCm = 0.0,
            targetSteeringAngleDegrees = 0.0,
            steeringStatusMessage = "AB Line Cleared"
        )
    }

    fun toggleAutoSteer() {
        val current = _guidanceState.value
        if (current.isAutoSteerEngaged) {
            disengageAutoSteer()
        } else {
            if (current.abLine != null) {
                _guidanceState.value = current.copy(
                    isAutoSteerEngaged = true,
                    steeringStatusMessage = "AUTOSTEER ENGAGED"
                )
            } else {
                _guidanceState.value = current.copy(
                    steeringStatusMessage = "Set Point A and Point B first"
                )
            }
        }
    }

    fun disengageAutoSteer() {
        motorHandler.setTargetPowerPercent(0f)
        steeringController.resetPid()
        _guidanceState.value = _guidanceState.value.copy(
            isAutoSteerEngaged = false,
            steeringStatusMessage = "AutoSteer Disengaged"
        )
    }
}

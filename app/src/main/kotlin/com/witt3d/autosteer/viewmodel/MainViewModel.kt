package com.witt3d.autosteer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.witt3d.autosteer.data.models.AbLine
import com.witt3d.autosteer.data.models.DriveDirection
import com.witt3d.autosteer.data.models.GpsConfig
import com.witt3d.autosteer.data.models.SteeringAlgorithm
import com.witt3d.autosteer.data.models.GuidanceState
import com.witt3d.autosteer.data.models.HeadingSource
import com.witt3d.autosteer.data.models.ImuConfig
import com.witt3d.autosteer.data.models.MotorConfig
import com.witt3d.autosteer.data.models.MotorTelemetry
import com.witt3d.autosteer.data.models.NtripConfig
import com.witt3d.autosteer.data.models.SteeringConfig
import com.witt3d.autosteer.data.models.TelemetryData
import com.witt3d.autosteer.data.models.TractorConfig
import com.witt3d.autosteer.data.models.TerminalLogEntry
import com.witt3d.autosteer.data.models.UtmPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
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
import kotlin.math.hypot

class MainViewModel : ViewModel() {
    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    val imuHandler = HardwareRepository.imuHandler
    val imuConfig: StateFlow<ImuConfig> = imuHandler.config
    val imuTerminalLogs: StateFlow<List<TerminalLogEntry>> = imuHandler.terminalLogs

    val gpsHandler = HardwareRepository.gpsHandler
    val gpsConfig: StateFlow<GpsConfig> = gpsHandler.config
    val gpsTerminalLogs: StateFlow<List<TerminalLogEntry>> = gpsHandler.terminalLogs

    val ntripClient = HardwareRepository.ntripClient
    val ntripConfig: StateFlow<NtripConfig> = ntripClient.config
    val ntripTerminalLogs: StateFlow<List<TerminalLogEntry>> = ntripClient.terminalLogs

    val motorHandler = HardwareRepository.motorHandler
    val motorConfig: StateFlow<MotorConfig> = motorHandler.config
    val motorTelemetry: StateFlow<MotorTelemetry> = motorHandler.telemetry
    val motorTerminalLogs: StateFlow<List<TerminalLogEntry>> = motorHandler.terminalLogs

    // Tractor & Steering Configuration State
    private val _tractorConfig = MutableStateFlow(TractorConfig())
    val tractorConfig: StateFlow<TractorConfig> = _tractorConfig.asStateFlow()

    private val _steeringConfig = MutableStateFlow(SteeringConfig())
    val steeringConfig: StateFlow<SteeringConfig> = _steeringConfig.asStateFlow()

    private val _guidanceState = MutableStateFlow(GuidanceState())
    val guidanceState: StateFlow<GuidanceState> = _guidanceState.asStateFlow()

    private val _steeringDiagnosticLogs = MutableStateFlow<List<com.witt3d.autosteer.data.models.SteeringDiagnosticEntry>>(emptyList())
    val steeringDiagnosticLogs: StateFlow<List<com.witt3d.autosteer.data.models.SteeringDiagnosticEntry>> = _steeringDiagnosticLogs.asStateFlow()

    private val steeringController = SteeringController()

    // Smooth filtering / drive direction state
    private var filteredUtmX: Double? = null
    private var filteredUtmY: Double? = null
    private var filteredHeading: Double? = null
    private var lastUtmX: Double? = null
    private var lastUtmY: Double? = null
    private var currentDriveDirection: DriveDirection = DriveDirection.FORWARD
    private var appContext: Context? = null

    fun clearSteeringDiagnosticLogs() {
        _steeringDiagnosticLogs.value = emptyList()
    }

    private fun addSteeringDiagnosticEntry(entry: com.witt3d.autosteer.data.models.SteeringDiagnosticEntry) {
        val currentList = _steeringDiagnosticLogs.value.toMutableList()
        currentList.add(entry)
        if (currentList.size > 100) {
            currentList.removeAt(0)
        }
        _steeringDiagnosticLogs.value = currentList
    }

    init {
        // Collect IMU telemetry updates
        viewModelScope.launch {
            imuHandler.telemetry.collect { imuTele ->
                val config = _steeringConfig.value
                val currentGpsHeading = gpsHandler.telemetry.value.headingDegrees
                val currentSpeed = _telemetry.value.speedKmh
                val rawHeading = calculateEffectiveHeading(
                    imuHeading = imuTele.headingDegrees,
                    gpsHeading = currentGpsHeading,
                    speedKmh = currentSpeed,
                    config = config
                )

                val smoothHeading = if (config.enableFiltering) {
                    smoothAngle(filteredHeading, rawHeading, config.headingSmoothingAlpha).also { filteredHeading = it }
                } else {
                    rawHeading
                }

                _telemetry.value = _telemetry.value.copy(
                    headingDegrees = smoothHeading,
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
                val config = _steeringConfig.value
                val currentImuHeading = imuHandler.telemetry.value.headingDegrees
                val rawHeading = calculateEffectiveHeading(
                    imuHeading = currentImuHeading,
                    gpsHeading = gpsTele.headingDegrees,
                    speedKmh = gpsTele.speedKmh,
                    config = config
                )

                val smoothHeading = if (config.enableFiltering) {
                    smoothAngle(filteredHeading, rawHeading, config.headingSmoothingAlpha).also { filteredHeading = it }
                } else {
                    rawHeading
                }

                // Smooth UTM coordinates
                var rawX = gpsTele.utmX
                var rawY = gpsTele.utmY

                if (rawX != 0.0 && rawY != 0.0) {
                    // Update Drive Direction based on position delta vs heading
                    val prevX = lastUtmX
                    val prevY = lastUtmY
                    if (prevX != null && prevY != null && gpsTele.speedKmh >= 0.3) {
                        val dx = rawX - prevX
                        val dy = rawY - prevY
                        val movementDist = hypot(dx, dy)
                        if (movementDist >= 0.05) {
                            var movementHeading = Math.toDegrees(atan2(dx, dy))
                            if (movementHeading < 0) movementHeading += 360.0

                            // When IMU is available, compare position movement vector against physical IMU chassis heading
                            val rawImu = imuHandler.telemetry.value.headingDegrees
                            val chassisHeading = if (rawImu != 0.0) {
                                var hdg = rawImu + config.headingOffsetDegrees
                                while (hdg >= 360.0) hdg -= 360.0
                                while (hdg < 0.0) hdg += 360.0
                                hdg
                            } else smoothHeading

                            var angleDiff = movementHeading - chassisHeading
                            while (angleDiff > 180.0) angleDiff -= 360.0
                            while (angleDiff < -180.0) angleDiff += 360.0

                            currentDriveDirection = if (abs(angleDiff) > 90.0) DriveDirection.REVERSE else DriveDirection.FORWARD
                        }
                    }
                    lastUtmX = rawX
                    lastUtmY = rawY

                    if (config.enableFiltering) {
                        val alpha = config.positionSmoothingAlpha
                        val fx = filteredUtmX?.let { it + alpha * (rawX - it) } ?: rawX
                        val fy = filteredUtmY?.let { it + alpha * (rawY - it) } ?: rawY
                        filteredUtmX = fx
                        filteredUtmY = fy
                        rawX = fx
                        rawY = fy
                    }
                }

                _telemetry.value = _telemetry.value.copy(
                    latitude = gpsTele.latitude,
                    longitude = gpsTele.longitude,
                    utmX = rawX,
                    utmY = rawY,
                    speedKmh = gpsTele.speedKmh,
                    headingDegrees = smoothHeading,
                    fixStatus = gpsTele.fixStatus,
                    accuracyMeters = gpsTele.accuracyMeters,
                    driveDirection = currentDriveDirection
                )
            }
        }

        if (!imuConfig.value.isConnected) {
            imuHandler.connect()
        }

        logSteeringConfigParameters()

        // AutoSteer Control Loop
        viewModelScope.launch {
            while (isActive) {
                processAutoSteerLoop()
                val freq = _steeringConfig.value.autoSteerLoopFrequencyHz.coerceIn(1, 50)
                val delayMs = (1000L / freq).coerceIn(20L, 1000L)
                delay(delayMs)
            }
        }
    }

    fun logSteeringConfigParameters() {
        val config = _steeringConfig.value
        val tractor = _tractorConfig.value
        val summary = "Algo: %s | Mode: %s | StanleyK: %.1f | StanleyHdgGain: %.1f | Kp: %.1f | Ki: %.1f | Kd: %.1f | MinPwr: %d%% | MaxPwr: %d%% | MaxSteer: %.0f° | EncInvert: %b | MtrInvert: %b".format(
            Locale.US,
            config.algorithm.displayName,
            config.controlMode.displayName,
            config.stanleyK,
            config.stanleyHeadingGain,
            config.kp,
            config.ki,
            config.kd,
            config.minMotorOutputPowerPercent,
            config.maxOutputPowerPercent,
            tractor.maxSteeringAngleDegrees,
            config.invertEncoderSign,
            config.invertMotorOutput
        )
        addSteeringDiagnosticEntry(
            com.witt3d.autosteer.data.models.SteeringDiagnosticEntry(
                configSummary = summary
            )
        )
    }

    private fun calculateEffectiveHeading(
        imuHeading: Double,
        gpsHeading: Double,
        speedKmh: Double,
        config: SteeringConfig
    ): Double {
        val calibratedImu = if (imuHeading != 0.0) {
            var hdg = imuHeading + config.headingOffsetDegrees
            while (hdg >= 360.0) hdg -= 360.0
            while (hdg < 0.0) hdg += 360.0
            hdg
        } else 0.0

        return when (config.headingSource) {
            HeadingSource.IMU_ONLY -> if (calibratedImu != 0.0) calibratedImu else gpsHeading
            HeadingSource.GPS_COG_ONLY -> if (gpsHeading != 0.0) gpsHeading else calibratedImu
            HeadingSource.AUTO_FUSED -> {
                if (speedKmh >= config.minHeadingSpeedKmh && gpsHeading != 0.0) {
                    gpsHeading
                } else if (calibratedImu != 0.0) {
                    calibratedImu
                } else {
                    gpsHeading
                }
            }
        }
    }

    /**
     * Dynamically auto-calibrates IMU heading offset when driving straight forward above minimum speed.
     */
    private fun performAutoHeadingCalibration(
        rawImuHeading: Double,
        gpsHeading: Double,
        speedKmh: Double,
        wheelAngleDeg: Double,
        driveDirection: DriveDirection
    ) {
        if (rawImuHeading == 0.0 || gpsHeading == 0.0) return
        if (driveDirection != DriveDirection.FORWARD) return
        if (speedKmh < _steeringConfig.value.minHeadingSpeedKmh) return
        if (abs(wheelAngleDeg) > 2.5) return

        val currentConfig = _steeringConfig.value
        val currentOffset = currentConfig.headingOffsetDegrees
        var calibratedImu = (rawImuHeading + currentOffset) % 360.0
        if (calibratedImu < 0) calibratedImu += 360.0

        var error = gpsHeading - calibratedImu
        while (error > 180.0) error -= 360.0
        while (error < -180.0) error += 360.0

        if (abs(error) < 45.0 && abs(error) > 0.05) {
            val alpha = 0.01 // Smooth, subtle convergence
            var newOffset = currentOffset + alpha * error
            while (newOffset > 180.0) newOffset -= 360.0
            while (newOffset < -180.0) newOffset += 360.0

            val updatedConfig = currentConfig.copy(headingOffsetDegrees = newOffset)
            _steeringConfig.value = updatedConfig
            appContext?.let { ctx ->
                TractorRepository.saveSteeringConfig(ctx, updatedConfig)
            }
        }
    }

    fun calibrateHeadingOffset(context: Context): Boolean {
        appContext = context.applicationContext
        val currentGpsHeading = gpsHandler.telemetry.value.headingDegrees
        val currentSpeed = _telemetry.value.speedKmh
        val rawImuHeading = imuHandler.telemetry.value.headingDegrees

        if (rawImuHeading != 0.0 && currentGpsHeading != 0.0 && currentSpeed >= 1.0) {
            var diff = currentGpsHeading - rawImuHeading
            while (diff > 180.0) diff -= 360.0
            while (diff < -180.0) diff += 360.0
            val updatedConfig = _steeringConfig.value.copy(headingOffsetDegrees = diff)
            updateSteeringConfig(context, updatedConfig)
            return true
        }
        return false
    }

    private fun smoothAngle(prev: Double?, current: Double, alpha: Double): Double {
        if (prev == null) return current
        var diff = current - prev
        while (diff > 180.0) diff -= 360.0
        while (diff < -180.0) diff += 360.0
        var result = prev + alpha * diff
        while (result >= 360.0) result -= 360.0
        while (result < 0.0) result += 360.0
        return result
    }

    fun loadPersistedConfigs(context: Context) {
        appContext = context.applicationContext
        _tractorConfig.value = TractorRepository.loadTractorConfig(context)
        _steeringConfig.value = TractorRepository.loadSteeringConfig(context)
        logSteeringConfigParameters()
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

        // Stanley regulates cross-track/heading error at the FRONT axle; Pure Pursuit
        // at the rear axle. Feeding Stanley the rear-axle point (as before) introduces
        // a wheelbase-sized bias that grows with heading error.
        val steeringReferenceUtm = if (currentSteering.algorithm == SteeringAlgorithm.STANLEY) {
            TiltCompensator.computeGroundFrontAxlePosition(
                groundRearAxleUtm = groundAxleUtm,
                headingDegrees = currentTele.headingDegrees,
                tractorConfig = currentTractor
            )
        } else {
            groundAxleUtm
        }

        val currentMotorTelemetry = motorTelemetry.value
        val currentSteerAngle = steeringController.estimateCurrentSteeringAngleDegrees(
            motorTelemetry = currentMotorTelemetry,
            steeringConfig = currentSteering
        )

        // Perform background dynamic heading auto-calibration
        performAutoHeadingCalibration(
            rawImuHeading = imuHandler.telemetry.value.headingDegrees,
            gpsHeading = gpsHandler.telemetry.value.headingDegrees,
            speedKmh = currentTele.speedKmh,
            wheelAngleDeg = currentSteerAngle,
            driveDirection = currentTele.driveDirection
        )

        // Motor Over-Current Cutoff Check
        val currentAmps = currentMotorTelemetry.currentAmps
        val maxAllowedCurrent = currentSteering.maxMotorCurrentCutoffAmps
        if (maxAllowedCurrent > 0.0 && currentAmps >= maxAllowedCurrent) {
            if (currentGuidance.isAutoSteerEngaged) {
                disengageAutoSteer()
                _guidanceState.value = _guidanceState.value.copy(
                    crossTrackErrorCm = currentGuidance.crossTrackErrorCm,
                    targetSteeringAngleDegrees = 0.0,
                    compensatedUtmX = groundAxleUtm.x,
                    compensatedUtmY = groundAxleUtm.y,
                    currentSteeringAngleDegrees = currentSteerAngle,
                    steeringStatusMessage = "OVER-CURRENT CUTOFF (%.1fA >= %.1fA)".format(currentAmps, maxAllowedCurrent)
                )
                return
            }
        }

        val activeLine = currentGuidance.abLine

        if (activeLine != null && activeLine.lengthMeters >= 0.1) {
            val passWidthMeters = if (currentTractor.trackWidthCm > 50.0) currentTractor.trackWidthCm / 100.0 else 2.0
            val targetLine = activeLine.getNearestParallelLine(steeringReferenceUtm, passWidthMeters)

            val cteMeters = targetLine.calculateCrossTrackErrorMeters(steeringReferenceUtm)
            val cteCm = cteMeters * 100.0

            val targetSteeringAngle = SteeringAlgorithms.calculateTargetSteeringAngle(
                groundAxleUtm = steeringReferenceUtm,
                headingDegrees = currentTele.headingDegrees,
                speedKmh = currentTele.speedKmh,
                abLine = targetLine,
                tractorConfig = currentTractor,
                steeringConfig = currentSteering,
                driveDirection = currentTele.driveDirection
            )

            val motorPower = steeringController.calculateMotorPowerPercent(
                targetSteeringAngleDegrees = targetSteeringAngle,
                motorTelemetry = currentMotorTelemetry,
                steeringConfig = currentSteering
            )

            if (currentGuidance.isAutoSteerEngaged) {
                motorHandler.setTargetPowerPercent(motorPower)
            }

            val statusMsg = if (currentGuidance.isAutoSteerEngaged) "AUTOSTEER ENGAGED" else "Guidance Active (Disengaged)"
            _guidanceState.value = currentGuidance.copy(
                crossTrackErrorCm = cteCm,
                targetSteeringAngleDegrees = targetSteeringAngle,
                compensatedUtmX = groundAxleUtm.x,
                compensatedUtmY = groundAxleUtm.y,
                currentSteeringAngleDegrees = currentSteerAngle,
                steeringStatusMessage = statusMsg
            )

            addSteeringDiagnosticEntry(
                com.witt3d.autosteer.data.models.SteeringDiagnosticEntry(
                    speedKmh = currentTele.speedKmh,
                    headingDegrees = currentTele.headingDegrees,
                    crossTrackErrorCm = cteCm,
                    targetSteeringAngleDegrees = targetSteeringAngle,
                    currentSteeringAngleDegrees = currentSteerAngle,
                    motorPowerPercent = motorPower,
                    motorCurrentAmps = currentMotorTelemetry.currentAmps,
                    statusMessage = statusMsg,
                    driveDirection = currentTele.driveDirection
                )
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

    // Terminal Commands
    fun sendImuCommand(command: String) = imuHandler.sendCustomCommand(command)
    fun clearImuTerminal() = imuHandler.clearTerminalLogs()

    fun sendGpsCommand(command: String) = gpsHandler.sendCustomCommand(command)
    fun clearGpsTerminal() = gpsHandler.clearTerminalLogs()

    fun sendNtripCommand(command: String) = ntripClient.sendCustomCommand(command)
    fun clearNtripTerminal() = ntripClient.clearTerminalLogs()

    fun sendMotorCommand(command: String) = motorHandler.sendCustomCommand(command)
    fun clearMotorTerminal() = motorHandler.clearTerminalLogs()

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
        logSteeringConfigParameters()
    }

    fun updateSteeringConfig(context: Context, newConfig: SteeringConfig) {
        TractorRepository.saveSteeringConfig(context, newConfig)
        _steeringConfig.value = newConfig
        steeringController.resetPid()
        logSteeringConfigParameters()
    }

    fun setSteeringCenter(context: Context) {
        val currentEncoder = motorTelemetry.value.encoderCount
        val updatedConfig = _steeringConfig.value.copy(encoderCenterOffset = currentEncoder)
        updateSteeringConfig(context, updatedConfig)
        _guidanceState.value = _guidanceState.value.copy(
            currentSteeringAngleDegrees = 0.0
        )
    }

    // AB Line & Guidance Methods
    fun setPointA() {
        val currentGuidance = _guidanceState.value
        val point = UtmPoint(currentGuidance.compensatedUtmX, currentGuidance.compensatedUtmY)
        val newAbLine = if (currentGuidance.pointB != null && hypot(point.x - currentGuidance.pointB.x, point.y - currentGuidance.pointB.y) >= 0.1) {
            AbLine(point, currentGuidance.pointB)
        } else null
        _guidanceState.value = currentGuidance.copy(
            pointA = point,
            abLine = newAbLine
        )
    }

    fun setPointB() {
        val currentGuidance = _guidanceState.value
        val point = UtmPoint(currentGuidance.compensatedUtmX, currentGuidance.compensatedUtmY)
        val newAbLine = if (currentGuidance.pointA != null && hypot(point.x - currentGuidance.pointA.x, point.y - currentGuidance.pointA.y) >= 0.1) {
            AbLine(currentGuidance.pointA, point)
        } else null
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
            if (current.abLine != null && current.abLine.lengthMeters >= 0.1) {
                logSteeringConfigParameters()
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

package com.witt3d.autosteer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.witt3d.autosteer.data.models.MotorControlMode
import com.witt3d.autosteer.data.models.MotorType
import com.witt3d.autosteer.data.models.SteeringAlgorithm
import com.witt3d.autosteer.ui.components.ImuVisualizer3D
import com.witt3d.autosteer.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val telemetry by viewModel.telemetry.collectAsState()
    val imuConfig by viewModel.imuConfig.collectAsState()
    val gpsConfig by viewModel.gpsConfig.collectAsState()
    val ntripConfig by viewModel.ntripConfig.collectAsState()
    val motorConfig by viewModel.motorConfig.collectAsState()
    val motorTelemetry by viewModel.motorTelemetry.collectAsState()
    val tractorConfig by viewModel.tractorConfig.collectAsState()
    val steeringConfig by viewModel.steeringConfig.collectAsState()
    val guidanceState by viewModel.guidanceState.collectAsState()

    var motorSliderValue by remember(motorTelemetry.targetPowerPercent) { mutableStateOf(motorTelemetry.targetPowerPercent) }

    // State for IMU Configuration text fields
    var hostText by remember(imuConfig.host) { mutableStateOf(imuConfig.host) }
    var portText by remember(imuConfig.port) { mutableStateOf(imuConfig.port.toString()) }
    var uidText by remember(imuConfig.uid) { mutableStateOf(imuConfig.uid) }

    // State for GPS Baud Rate
    var baudRateText by remember(gpsConfig.baudRate) { mutableStateOf(gpsConfig.baudRate.toString()) }

    // State for Motor text fields
    var roboteqBaudText by remember(motorConfig.roboteqBaudRate) { mutableStateOf(motorConfig.roboteqBaudRate.toString()) }
    var phidgetChannelText by remember(motorConfig.phidgetChannel) { mutableStateOf(motorConfig.phidgetChannel.toString()) }
    var phidgetSerialText by remember(motorConfig.phidgetSerialNumber) { mutableStateOf(motorConfig.phidgetSerialNumber.toString()) }
    var phidgetAccelText by remember(motorConfig.phidgetAcceleration) { mutableStateOf(motorConfig.phidgetAcceleration.toString()) }
    var phidgetCurrentLimitText by remember(motorConfig.phidgetCurrentLimit) { mutableStateOf(motorConfig.phidgetCurrentLimit.toString()) }

    // Tractor Measurements State
    var wheelbaseText by remember(tractorConfig.wheelbaseCm) { mutableStateOf(tractorConfig.wheelbaseCm.toString()) }
    var antennaToRearText by remember(tractorConfig.antennaToRearAxleCm) { mutableStateOf(tractorConfig.antennaToRearAxleCm.toString()) }
    var antennaHeightText by remember(tractorConfig.antennaHeightCm) { mutableStateOf(tractorConfig.antennaHeightCm.toString()) }
    var trackWidthText by remember(tractorConfig.trackWidthCm) { mutableStateOf(tractorConfig.trackWidthCm.toString()) }
    var maxSteeringAngleText by remember(tractorConfig.maxSteeringAngleDegrees) { mutableStateOf(tractorConfig.maxSteeringAngleDegrees.toString()) }
    var antennaOffsetText by remember(tractorConfig.antennaOffsetCm) { mutableStateOf(tractorConfig.antennaOffsetCm.toString()) }

    // Steering Algorithm & Motor Tuning State
    var lookaheadDistText by remember(steeringConfig.lookaheadDistanceMeters) { mutableStateOf(steeringConfig.lookaheadDistanceMeters.toString()) }
    var lookaheadGainText by remember(steeringConfig.lookaheadSpeedGain) { mutableStateOf(steeringConfig.lookaheadSpeedGain.toString()) }
    var stanleyKText by remember(steeringConfig.stanleyK) { mutableStateOf(steeringConfig.stanleyK.toString()) }
    var stanleyHeadingGainText by remember(steeringConfig.stanleyHeadingGain) { mutableStateOf(steeringConfig.stanleyHeadingGain.toString()) }
    var passesPerDegreeText by remember(steeringConfig.passesPerDegree) { mutableStateOf(steeringConfig.passesPerDegree.toString()) }
    var kpText by remember(steeringConfig.kp) { mutableStateOf(steeringConfig.kp.toString()) }
    var kiText by remember(steeringConfig.ki) { mutableStateOf(steeringConfig.ki.toString()) }
    var kdText by remember(steeringConfig.kd) { mutableStateOf(steeringConfig.kd.toString()) }
    var deadbandText by remember(steeringConfig.deadbandDegrees) { mutableStateOf(steeringConfig.deadbandDegrees.toString()) }

    // Interactive simulation slider values
    var simRoll by remember { mutableStateOf(0f) }
    var simPitch by remember { mutableStateOf(0f) }
    var simHeading by remember { mutableStateOf(0f) }
    var simAccelX by remember { mutableStateOf(0f) }
    var simAccelY by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        viewModel.scanUsbDevices(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Hardware & Steering Configuration", style = MaterialTheme.typography.headlineMedium) }

        // Tractor Dimensions Card
        item {
            HardwareSection("Tractor Measurements (Centimeters)") {
                Text(
                    "Configure standard tractor dimensions used for tilt compensation and steering kinematics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = wheelbaseText,
                        onValueChange = {
                            wheelbaseText = it
                            it.toDoubleOrNull()?.let { wb ->
                                viewModel.updateTractorConfig(context, tractorConfig.copy(wheelbaseCm = wb))
                            }
                        },
                        label = { Text("Wheelbase (cm)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = antennaToRearText,
                        onValueChange = {
                            antennaToRearText = it
                            it.toDoubleOrNull()?.let { ar ->
                                viewModel.updateTractorConfig(context, tractorConfig.copy(antennaToRearAxleCm = ar))
                            }
                        },
                        label = { Text("Antenna -> Rear Axle (cm)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = antennaHeightText,
                        onValueChange = {
                            antennaHeightText = it
                            it.toDoubleOrNull()?.let { ah ->
                                viewModel.updateTractorConfig(context, tractorConfig.copy(antennaHeightCm = ah))
                            }
                        },
                        label = { Text("Antenna Height (cm)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = trackWidthText,
                        onValueChange = {
                            trackWidthText = it
                            it.toDoubleOrNull()?.let { tw ->
                                viewModel.updateTractorConfig(context, tractorConfig.copy(trackWidthCm = tw))
                            }
                        },
                        label = { Text("Track Width (cm)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = maxSteeringAngleText,
                        onValueChange = {
                            maxSteeringAngleText = it
                            it.toDoubleOrNull()?.let { ma ->
                                viewModel.updateTractorConfig(context, tractorConfig.copy(maxSteeringAngleDegrees = ma))
                            }
                        },
                        label = { Text("Max Steering Angle (°)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = antennaOffsetText,
                        onValueChange = {
                            antennaOffsetText = it
                            it.toDoubleOrNull()?.let { ao ->
                                viewModel.updateTractorConfig(context, tractorConfig.copy(antennaOffsetCm = ao))
                            }
                        },
                        label = { Text("Antenna Offset (cm)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Steering Algorithm & Motor Parameters Card
        item {
            HardwareSection("Steering Algorithm & Control Parameters") {
                Text("Select steering algorithm and configure motor control feedback.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))

                Text("Algorithm Selection:", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = steeringConfig.algorithm == SteeringAlgorithm.PURE_PURSUIT,
                        onClick = { viewModel.updateSteeringConfig(context, steeringConfig.copy(algorithm = SteeringAlgorithm.PURE_PURSUIT)) },
                        label = { Text("Pure Pursuit") }
                    )
                    FilterChip(
                        selected = steeringConfig.algorithm == SteeringAlgorithm.STANLEY,
                        onClick = { viewModel.updateSteeringConfig(context, steeringConfig.copy(algorithm = SteeringAlgorithm.STANLEY)) },
                        label = { Text("Stanley") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (steeringConfig.algorithm == SteeringAlgorithm.PURE_PURSUIT) {
                    Text("Pure Pursuit Parameters:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = lookaheadDistText,
                            onValueChange = {
                                lookaheadDistText = it
                                it.toDoubleOrNull()?.let { ld ->
                                    viewModel.updateSteeringConfig(context, steeringConfig.copy(lookaheadDistanceMeters = ld))
                                }
                            },
                            label = { Text("Lookahead Dist (m)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = lookaheadGainText,
                            onValueChange = {
                                lookaheadGainText = it
                                it.toDoubleOrNull()?.let { lg ->
                                    viewModel.updateSteeringConfig(context, steeringConfig.copy(lookaheadSpeedGain = lg))
                                }
                            },
                            label = { Text("Speed Gain (k)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Text("Stanley Parameters:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = stanleyKText,
                            onValueChange = {
                                stanleyKText = it
                                it.toDoubleOrNull()?.let { sk ->
                                    viewModel.updateSteeringConfig(context, steeringConfig.copy(stanleyK = sk))
                                }
                            },
                            label = { Text("Cross-Track Gain (k)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stanleyHeadingGainText,
                            onValueChange = {
                                stanleyHeadingGainText = it
                                it.toDoubleOrNull()?.let { shg ->
                                    viewModel.updateSteeringConfig(context, steeringConfig.copy(stanleyHeadingGain = shg))
                                }
                            },
                            label = { Text("Heading Gain") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Motor Steering Control Mode:", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = steeringConfig.controlMode == MotorControlMode.PASSES_PER_DEGREE,
                        onClick = { viewModel.updateSteeringConfig(context, steeringConfig.copy(controlMode = MotorControlMode.PASSES_PER_DEGREE)) },
                        label = { Text("Passes-per-Degree (Cerea)") }
                    )
                    FilterChip(
                        selected = steeringConfig.controlMode == MotorControlMode.PID,
                        onClick = { viewModel.updateSteeringConfig(context, steeringConfig.copy(controlMode = MotorControlMode.PID)) },
                        label = { Text("PID Closed Loop") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = passesPerDegreeText,
                        onValueChange = {
                            passesPerDegreeText = it
                            it.toIntOrNull()?.let { ppd ->
                                viewModel.updateSteeringConfig(context, steeringConfig.copy(passesPerDegree = ppd))
                            }
                        },
                        label = { Text("Passes per Degree (Encoder)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = deadbandText,
                        onValueChange = {
                            deadbandText = it
                            it.toDoubleOrNull()?.let { db ->
                                viewModel.updateSteeringConfig(context, steeringConfig.copy(deadbandDegrees = db))
                            }
                        },
                        label = { Text("Deadband (°)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (steeringConfig.controlMode == MotorControlMode.PID) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = kpText,
                            onValueChange = {
                                kpText = it
                                it.toDoubleOrNull()?.let { p -> viewModel.updateSteeringConfig(context, steeringConfig.copy(kp = p)) }
                            },
                            label = { Text("Kp") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = kiText,
                            onValueChange = {
                                kiText = it
                                it.toDoubleOrNull()?.let { i -> viewModel.updateSteeringConfig(context, steeringConfig.copy(ki = i)) }
                            },
                            label = { Text("Ki") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = kdText,
                            onValueChange = {
                                kdText = it
                                it.toDoubleOrNull()?.let { d -> viewModel.updateSteeringConfig(context, steeringConfig.copy(kd = d)) }
                            },
                            label = { Text("Kd") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Zero Center Calibration Card
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Steering Encoder Center Calibration", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Current Encoder Raw: ${motorTelemetry.encoderCount} | Zero-Center Offset: ${steeringConfig.encoderCenterOffset}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Estimated Current Wheel Angle: ${String.format("%.1f", guidanceState.currentSteeringAngleDegrees)}°",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.setSteeringCenter(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("SET CURRENT STEERING AS CENTER (0°)")
                        }
                    }
                }
            }
        }

        // 3D Visualizer
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "IMU 3D Orientation & Movement", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    ImuVisualizer3D(
                        rollDegrees = telemetry.inclinationDegrees,
                        pitchDegrees = telemetry.pitchDegrees,
                        headingDegrees = telemetry.headingDegrees,
                        accelX = telemetry.accelX,
                        accelY = telemetry.accelY,
                        accelZ = telemetry.accelZ
                    )
                }
            }
        }

        // IMU Brick v2 Configuration Card
        item {
            HardwareSection("IMU (Brick v2)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (imuConfig.isConnected) "Status: Connected ${if (imuConfig.isSimulationMode) "(Simulated)" else ""}" else "Status: Disconnected",
                            color = if (imuConfig.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = if (imuConfig.usbDeviceDetected) "USB: ${imuConfig.usbDeviceName ?: "Detected"}" else "USB: No USB device detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (imuConfig.usbDeviceDetected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.scanUsbDevices(context) }) {
                            Text("Scan USB")
                        }
                        if (imuConfig.isConnected) {
                            Button(onClick = { viewModel.disconnectImu() }) {
                                Text("Disconnect")
                            }
                        } else {
                            Button(onClick = { viewModel.connectImu(context) }) {
                                Text("Connect")
                            }
                        }
                    }
                }

                imuConfig.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hostText,
                        onValueChange = {
                            hostText = it
                            viewModel.updateImuConfig(imuConfig.copy(host = it))
                        },
                        label = { Text("Host IP / Hostname") },
                        modifier = Modifier.weight(1.5f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            portText = it
                            it.toIntOrNull()?.let { p ->
                                viewModel.updateImuConfig(imuConfig.copy(port = p))
                            }
                        },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uidText,
                        onValueChange = {
                            uidText = it
                            viewModel.updateImuConfig(imuConfig.copy(uid = it))
                        },
                        label = { Text("UID (Optional)") },
                        modifier = Modifier.weight(1.5f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Simulation Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Simulated IMU Mode")
                    Switch(
                        checked = imuConfig.isSimulationMode,
                        onCheckedChange = { isSim ->
                            viewModel.updateImuConfig(imuConfig.copy(isSimulationMode = isSim))
                        }
                    )
                }

                // Interactive simulation sliders
                if (imuConfig.isSimulationMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Simulation Interactive Controls", style = MaterialTheme.typography.titleMedium)

                    Text("Roll (Tilt): ${String.format("%.1f", simRoll)}°")
                    Slider(
                        value = simRoll,
                        onValueChange = {
                            simRoll = it
                            viewModel.setSimulatedImuOrientation(simHeading.toDouble(), simRoll.toDouble(), simPitch.toDouble(), simAccelX.toDouble(), simAccelY.toDouble())
                        },
                        valueRange = -45f..45f
                    )

                    Text("Pitch: ${String.format("%.1f", simPitch)}°")
                    Slider(
                        value = simPitch,
                        onValueChange = {
                            simPitch = it
                            viewModel.setSimulatedImuOrientation(simHeading.toDouble(), simRoll.toDouble(), simPitch.toDouble(), simAccelX.toDouble(), simAccelY.toDouble())
                        },
                        valueRange = -45f..45f
                    )

                    Text("Heading (Yaw): ${String.format("%.0f", simHeading)}°")
                    Slider(
                        value = simHeading,
                        onValueChange = {
                            simHeading = it
                            viewModel.setSimulatedImuOrientation(simHeading.toDouble(), simRoll.toDouble(), simPitch.toDouble(), simAccelX.toDouble(), simAccelY.toDouble())
                        },
                        valueRange = 0f..360f
                    )

                    Text("Accel X (Lateral): ${String.format("%.2f", simAccelX)} m/s²")
                    Slider(
                        value = simAccelX,
                        onValueChange = {
                            simAccelX = it
                            viewModel.setSimulatedImuOrientation(simHeading.toDouble(), simRoll.toDouble(), simPitch.toDouble(), simAccelX.toDouble(), simAccelY.toDouble())
                        },
                        valueRange = -5f..5f
                    )

                    Text("Accel Y (Forward): ${String.format("%.2f", simAccelY)} m/s²")
                    Slider(
                        value = simAccelY,
                        onValueChange = {
                            simAccelY = it
                            viewModel.setSimulatedImuOrientation(simHeading.toDouble(), simRoll.toDouble(), simPitch.toDouble(), simAccelX.toDouble(), simAccelY.toDouble())
                        },
                        valueRange = -5f..5f
                    )
                }
            }
        }

        // GPS Card (u-blox ZED-F9P)
        item {
            HardwareSection("GPS (u-blox ZED-F9P)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (gpsConfig.isConnected) "Status: Connected ${if (gpsConfig.isSimulationMode) "(Simulated)" else ""}" else "Status: Disconnected",
                            color = if (gpsConfig.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = if (gpsConfig.usbDeviceDetected) "USB Serial: ${gpsConfig.usbDeviceName ?: "u-blox Device Detected"}" else "USB Serial: No device detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (gpsConfig.usbDeviceDetected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Fix: ${telemetry.fixStatus} | Acc: ${String.format("%.2f", telemetry.accuracyMeters)}m | Lat: ${String.format("%.6f", telemetry.latitude)}, Lon: ${String.format("%.6f", telemetry.longitude)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.scanUsbDevices(context) }) {
                            Text("Scan USB")
                        }
                        if (gpsConfig.isConnected) {
                            Button(onClick = { viewModel.disconnectGps() }) {
                                Text("Disconnect")
                            }
                        } else {
                            Button(onClick = { viewModel.connectGps(context) }) {
                                Text("Connect")
                            }
                        }
                    }
                }

                gpsConfig.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = baudRateText,
                    onValueChange = {
                        baudRateText = it
                        it.toIntOrNull()?.let { baud ->
                            viewModel.updateGpsConfig(context, gpsConfig.copy(baudRate = baud))
                        }
                    },
                    label = { Text("Serial Baud Rate") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Simulated GPS Mode")
                    Switch(
                        checked = gpsConfig.isSimulationMode,
                        onCheckedChange = { isSim ->
                            viewModel.updateGpsConfig(context, gpsConfig.copy(isSimulationMode = isSim))
                        }
                    )
                }
            }
        }

        // NTRIP Configuration Card
        item {
            HardwareSection("NTRIP Correction Client") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeProfileName = ntripConfig.profiles.find { it.id == ntripConfig.activeProfileId }?.name
                    Column {
                        Text(
                            text = if (ntripConfig.isConnected) "Status: Connected (${activeProfileName ?: "Active"})" else "Status: Disconnected",
                            color = if (ntripConfig.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        ntripConfig.errorMessage?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.addNtripProfile(context) }) {
                            Text("Add Service")
                        }
                        if (ntripConfig.isConnected) {
                            Button(onClick = { viewModel.disconnectNtrip() }) {
                                Text("Disconnect")
                            }
                        } else {
                            Button(onClick = { viewModel.connectNtrip() }) {
                                Text("Connect")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Datastream Statistics Display
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Datastream Metrics", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val stats = ntripConfig.stats
                            Column {
                                Text("Incoming Rate (RTCM): ${stats.formatRate(stats.bytesReceivedPerSec)}")
                                Text("Total Received: ${stats.formatTotalBytes(stats.totalBytesReceived)}")
                            }
                            Column {
                                Text("Outgoing Rate (GGA): ${stats.formatRate(stats.bytesSentPerSec)}")
                                Text("Total Sent: ${stats.formatTotalBytes(stats.totalBytesSent)}")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Prioritized Services List (First active enabled service connects first):", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                if (ntripConfig.profiles.isEmpty()) {
                    Text("No NTRIP profiles added yet. Click 'Add Service' to create one.", style = MaterialTheme.typography.bodyMedium)
                }

                ntripConfig.profiles.forEachIndexed { index, profile ->
                    NtripProfileCard(
                        profile = profile,
                        index = index,
                        totalCount = ntripConfig.profiles.size,
                        isActive = profile.id == ntripConfig.activeProfileId && ntripConfig.isConnected,
                        onUpdate = { updatedProf -> viewModel.updateNtripProfile(context, updatedProf) },
                        onDelete = { viewModel.deleteNtripProfile(context, profile.id) },
                        onMoveUp = { viewModel.moveNtripProfileUp(context, index) },
                        onMoveDown = { viewModel.moveNtripProfileDown(context, index) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-connect NTRIP on startup")
                    Switch(
                        checked = ntripConfig.autoConnect,
                        onCheckedChange = { autoConn ->
                            viewModel.updateNtripConfig(context, ntripConfig.copy(autoConnect = autoConn))
                        }
                    )
                }
            }
        }

        // Motor Controller Section
        item {
            HardwareSection("Motor Controller (Roboteq & Phidgets)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (motorConfig.isConnected) "Status: Connected ${if (motorConfig.isSimulationMode || motorConfig.motorType == MotorType.NONE_SIMULATED) "(Simulated)" else ""}" else "Status: Disconnected",
                            color = if (motorConfig.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = if (motorConfig.usbDeviceDetected) "USB: ${motorConfig.usbDeviceName ?: "Detected"}" else "USB: No USB device detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (motorConfig.usbDeviceDetected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.scanUsbDevices(context) }) {
                            Text("Scan USB")
                        }
                        if (motorConfig.isConnected) {
                            Button(onClick = { viewModel.disconnectMotor() }) {
                                Text("Disconnect")
                            }
                        } else {
                            Button(onClick = { viewModel.connectMotor(context) }) {
                                Text("Connect")
                            }
                        }
                    }
                }

                motorConfig.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Controller Model Selection:", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = motorConfig.motorType == MotorType.NONE_SIMULATED,
                        onClick = { viewModel.updateMotorConfig(context, motorConfig.copy(motorType = MotorType.NONE_SIMULATED)) },
                        label = { Text("None / Simulated") }
                    )
                    FilterChip(
                        selected = motorConfig.motorType == MotorType.ROBOTEQ,
                        onClick = { viewModel.updateMotorConfig(context, motorConfig.copy(motorType = MotorType.ROBOTEQ)) },
                        label = { Text("Roboteq USB") }
                    )
                    FilterChip(
                        selected = motorConfig.motorType == MotorType.PHIDGETS,
                        onClick = { viewModel.updateMotorConfig(context, motorConfig.copy(motorType = MotorType.PHIDGETS)) },
                        label = { Text("Phidgets 1065_1B") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Roboteq Specific Settings
                if (motorConfig.motorType == MotorType.ROBOTEQ) {
                    Text("Roboteq Channel Selection:", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = motorConfig.roboteqChannel == 1,
                            onClick = { viewModel.updateMotorConfig(context, motorConfig.copy(roboteqChannel = 1)) },
                            label = { Text("Channel 1") }
                        )
                        FilterChip(
                            selected = motorConfig.roboteqChannel == 2,
                            onClick = { viewModel.updateMotorConfig(context, motorConfig.copy(roboteqChannel = 2)) },
                            label = { Text("Channel 2") }
                        )
                        FilterChip(
                            selected = motorConfig.roboteqChannel == 3,
                            onClick = { viewModel.updateMotorConfig(context, motorConfig.copy(roboteqChannel = 3)) },
                            label = { Text("Dual Mode (Ch 1 + 2)") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = roboteqBaudText,
                        onValueChange = {
                            roboteqBaudText = it
                            it.toIntOrNull()?.let { baud ->
                                viewModel.updateMotorConfig(context, motorConfig.copy(roboteqBaudRate = baud))
                            }
                        },
                        label = { Text("Baud Rate") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Phidgets Specific Settings
                if (motorConfig.motorType == MotorType.PHIDGETS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = phidgetChannelText,
                            onValueChange = {
                                phidgetChannelText = it
                                it.toIntOrNull()?.let { ch ->
                                    viewModel.updateMotorConfig(context, motorConfig.copy(phidgetChannel = ch))
                                }
                            },
                            label = { Text("Channel") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = phidgetSerialText,
                            onValueChange = {
                                phidgetSerialText = it
                                it.toIntOrNull()?.let { serial ->
                                    viewModel.updateMotorConfig(context, motorConfig.copy(phidgetSerialNumber = serial))
                                }
                            },
                            label = { Text("Serial (-1 = Any)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = phidgetAccelText,
                            onValueChange = {
                                phidgetAccelText = it
                                it.toDoubleOrNull()?.let { accel ->
                                    viewModel.updateMotorConfig(context, motorConfig.copy(phidgetAcceleration = accel))
                                }
                            },
                            label = { Text("Acceleration") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = phidgetCurrentLimitText,
                            onValueChange = {
                                phidgetCurrentLimitText = it
                                it.toDoubleOrNull()?.let { limit ->
                                    viewModel.updateMotorConfig(context, motorConfig.copy(phidgetCurrentLimit = limit))
                                }
                            },
                            label = { Text("Current Limit (A)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Motor Safety & Limits
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Invert Motor Direction")
                    Switch(
                        checked = motorConfig.invertDirection,
                        onCheckedChange = { invert ->
                            viewModel.updateMotorConfig(context, motorConfig.copy(invertDirection = invert))
                        }
                    )
                }

                Text("Max Power Limit: ${motorConfig.maxPowerLimitPercent}%")
                Slider(
                    value = motorConfig.maxPowerLimitPercent.toFloat(),
                    onValueChange = {
                        viewModel.updateMotorConfig(context, motorConfig.copy(maxPowerLimitPercent = it.toInt()))
                    },
                    valueRange = 0f..100f
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Simulated Motor Mode")
                    Switch(
                        checked = motorConfig.isSimulationMode,
                        onCheckedChange = { isSim ->
                            viewModel.updateMotorConfig(context, motorConfig.copy(isSimulationMode = isSim))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Motor Test & Live Telemetry Controls Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Motor Test & Live Telemetry", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Target Power Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Target Power: ${String.format("%.1f", motorSliderValue)}%",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Button(
                                onClick = {
                                    motorSliderValue = 0f
                                    viewModel.emergencyStopMotor()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("EMERGENCY STOP")
                            }
                        }

                        Slider(
                            value = motorSliderValue,
                            onValueChange = {
                                motorSliderValue = it
                                viewModel.setMotorTargetPowerPercent(it)
                            },
                            onValueChangeFinished = {
                                if (motorConfig.resetOnRelease) {
                                    motorSliderValue = 0f
                                    viewModel.setMotorTargetPowerPercent(0f)
                                }
                            },
                            valueRange = -100f..100f
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Reset slider to 0% on release")
                            Switch(
                                checked = motorConfig.resetOnRelease,
                                onCheckedChange = { reset ->
                                    viewModel.updateMotorConfig(context, motorConfig.copy(resetOnRelease = reset))
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // Live Metrics Readout
                        Text("Live Metrics:", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Actual Power: ${String.format("%.1f", motorTelemetry.actualPowerPercent)}%")
                                Text("Encoder Count: ${motorTelemetry.encoderCount}")
                                Text("Speed: ${motorTelemetry.speedRpm} RPM")
                            }
                            Column {
                                Text("Current: ${String.format("%.2f", motorTelemetry.currentAmps)} A")
                                Text("Voltage: ${String.format("%.1f", motorTelemetry.voltageVolts)} V")
                                Text("Status: ${motorTelemetry.statusMessage}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NtripProfileCard(
    profile: com.witt3d.autosteer.data.models.NtripProfile,
    index: Int,
    totalCount: Int,
    isActive: Boolean,
    onUpdate: (com.witt3d.autosteer.data.models.NtripProfile) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var nameText by remember(profile.name) { mutableStateOf(profile.name) }
    var hostText by remember(profile.host) { mutableStateOf(profile.host) }
    var portText by remember(profile.port) { mutableStateOf(profile.port.toString()) }
    var mountText by remember(profile.mountpoint) { mutableStateOf(profile.mountpoint) }
    var userText by remember(profile.username) { mutableStateOf(profile.username) }
    var passText by remember(profile.password) { mutableStateOf(profile.password) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("#${index + 1}", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = {
                            nameText = it
                            onUpdate(profile.copy(name = it))
                        },
                        label = { Text("Service Name") },
                        modifier = Modifier.width(180.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled")
                    Switch(
                        checked = profile.enabled,
                        onCheckedChange = { onUpdate(profile.copy(enabled = it)) }
                    )
                    IconButton(onClick = onMoveUp, enabled = index > 0) {
                        Text("▲")
                    }
                    IconButton(onClick = onMoveDown, enabled = index < totalCount - 1) {
                        Text("▼")
                    }
                    IconButton(onClick = onDelete) {
                        Text("✕", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = hostText,
                    onValueChange = {
                        hostText = it
                        onUpdate(profile.copy(host = it))
                    },
                    label = { Text("Host / IP") },
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = {
                        portText = it
                        it.toIntOrNull()?.let { p -> onUpdate(profile.copy(port = p)) }
                    },
                    label = { Text("Port") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = mountText,
                onValueChange = {
                    mountText = it
                    onUpdate(profile.copy(mountpoint = it))
                },
                label = { Text("Mountpoint") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = userText,
                    onValueChange = {
                        userText = it
                        onUpdate(profile.copy(username = it))
                    },
                    label = { Text("Username") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = passText,
                    onValueChange = {
                        passText = it
                        onUpdate(profile.copy(password = it))
                    },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun HardwareSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

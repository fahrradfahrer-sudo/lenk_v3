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
import com.witt3d.autosteer.data.models.MotorType
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

    var motorSliderValue by remember(motorTelemetry.targetPowerPercent) { mutableStateOf(motorTelemetry.targetPowerPercent) }

    // State for IMU Configuration text fields
    var hostText by remember(imuConfig.host) { mutableStateOf(imuConfig.host) }
    var portText by remember(imuConfig.port) { mutableStateOf(imuConfig.port.toString()) }
    var uidText by remember(imuConfig.uid) { mutableStateOf(imuConfig.uid) }

    // State for GPS Baud Rate
    var baudRateText by remember(gpsConfig.baudRate) { mutableStateOf(gpsConfig.baudRate.toString()) }

    // State for Motor text fields
    var roboteqChannelText by remember(motorConfig.roboteqChannel) { mutableStateOf(motorConfig.roboteqChannel.toString()) }
    var roboteqBaudText by remember(motorConfig.roboteqBaudRate) { mutableStateOf(motorConfig.roboteqBaudRate.toString()) }
    var phidgetChannelText by remember(motorConfig.phidgetChannel) { mutableStateOf(motorConfig.phidgetChannel.toString()) }
    var phidgetSerialText by remember(motorConfig.phidgetSerialNumber) { mutableStateOf(motorConfig.phidgetSerialNumber.toString()) }
    var phidgetAccelText by remember(motorConfig.phidgetAcceleration) { mutableStateOf(motorConfig.phidgetAcceleration.toString()) }
    var phidgetCurrentLimitText by remember(motorConfig.phidgetCurrentLimit) { mutableStateOf(motorConfig.phidgetCurrentLimit.toString()) }

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
        item { Text("Hardware Configuration", style = MaterialTheme.typography.headlineMedium) }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = roboteqChannelText,
                            onValueChange = {
                                roboteqChannelText = it
                                it.toIntOrNull()?.let { ch ->
                                    viewModel.updateMotorConfig(context, motorConfig.copy(roboteqChannel = ch))
                                }
                            },
                            label = { Text("Roboteq Channel") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = roboteqBaudText,
                            onValueChange = {
                                roboteqBaudText = it
                                it.toIntOrNull()?.let { baud ->
                                    viewModel.updateMotorConfig(context, motorConfig.copy(roboteqBaudRate = baud))
                                }
                            },
                            label = { Text("Baud Rate") },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // Live Telemetry Readouts
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

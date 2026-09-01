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
import com.witt3d.autosteer.ui.components.ImuVisualizer3D
import com.witt3d.autosteer.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val telemetry by viewModel.telemetry.collectAsState()
    val imuConfig by viewModel.imuConfig.collectAsState()
    val gpsConfig by viewModel.gpsConfig.collectAsState()
    val ntripConfig by viewModel.ntripConfig.collectAsState()

    var motorSliderValue by remember { mutableStateOf(0f) }

    // State for IMU Configuration text fields
    var hostText by remember(imuConfig.host) { mutableStateOf(imuConfig.host) }
    var portText by remember(imuConfig.port) { mutableStateOf(imuConfig.port.toString()) }
    var uidText by remember(imuConfig.uid) { mutableStateOf(imuConfig.uid) }

    // State for GPS Baud Rate
    var baudRateText by remember(gpsConfig.baudRate) { mutableStateOf(gpsConfig.baudRate.toString()) }

    // State for NTRIP Configuration text fields
    var ntripHostText by remember(ntripConfig.host) { mutableStateOf(ntripConfig.host) }
    var ntripPortText by remember(ntripConfig.port) { mutableStateOf(ntripConfig.port.toString()) }
    var ntripMountpointText by remember(ntripConfig.mountpoint) { mutableStateOf(ntripConfig.mountpoint) }
    var ntripUsernameText by remember(ntripConfig.username) { mutableStateOf(ntripConfig.username) }
    var ntripPasswordText by remember(ntripConfig.password) { mutableStateOf(ntripConfig.password) }

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
                    Text(
                        text = if (ntripConfig.isConnected) "Status: Connected & Streaming RTCM" else "Status: Disconnected",
                        color = if (ntripConfig.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                ntripConfig.errorMessage?.let { error ->
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
                        value = ntripHostText,
                        onValueChange = {
                            ntripHostText = it
                            viewModel.updateNtripConfig(context, ntripConfig.copy(host = it))
                        },
                        label = { Text("Caster Host / IP") },
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = ntripPortText,
                        onValueChange = {
                            ntripPortText = it
                            it.toIntOrNull()?.let { p ->
                                viewModel.updateNtripConfig(context, ntripConfig.copy(port = p))
                            }
                        },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ntripMountpointText,
                    onValueChange = {
                        ntripMountpointText = it
                        viewModel.updateNtripConfig(context, ntripConfig.copy(mountpoint = it))
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
                        value = ntripUsernameText,
                        onValueChange = {
                            ntripUsernameText = it
                            viewModel.updateNtripConfig(context, ntripConfig.copy(username = it))
                        },
                        label = { Text("Username") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ntripPasswordText,
                        onValueChange = {
                            ntripPasswordText = it
                            viewModel.updateNtripConfig(context, ntripConfig.copy(password = it))
                        },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

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

        item {
            HardwareSection("Motor Controller (Roboteq)") {
                Text("Status: Disconnected")
                Slider(
                    value = motorSliderValue,
                    onValueChange = { motorSliderValue = it },
                    valueRange = -1000f..1000f
                )
                Text("Target Power: ${motorSliderValue.toInt()}")
                Text("Encoder: 0")
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

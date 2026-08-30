package com.witt3d.autosteer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.witt3d.autosteer.ui.components.ImuVisualizer3D
import com.witt3d.autosteer.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel = viewModel()) {
    val telemetry by viewModel.telemetry.collectAsState()
    val imuConfig by viewModel.imuConfig.collectAsState()

    var motorSliderValue by remember { mutableStateOf(0f) }

    // State for IMU Configuration text fields
    var hostText by remember(imuConfig.host) { mutableStateOf(imuConfig.host) }
    var portText by remember(imuConfig.port) { mutableStateOf(imuConfig.port.toString()) }
    var uidText by remember(imuConfig.uid) { mutableStateOf(imuConfig.uid) }

    // Interactive simulation slider values
    var simRoll by remember { mutableStateOf(0f) }
    var simPitch by remember { mutableStateOf(0f) }
    var simHeading by remember { mutableStateOf(0f) }
    var simAccelX by remember { mutableStateOf(0f) }
    var simAccelY by remember { mutableStateOf(0f) }

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
                    Text(
                        text = if (imuConfig.isConnected) "Status: Connected ${if (imuConfig.isSimulationMode) "(Simulated)" else ""}" else "Status: Disconnected",
                        color = if (imuConfig.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (imuConfig.isConnected) {
                            Button(onClick = { viewModel.disconnectImu() }) {
                                Text("Disconnect")
                            }
                        } else {
                            Button(onClick = { viewModel.connectImu() }) {
                                Text("Connect")
                            }
                        }
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
                        label = { Text("Host") },
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

        item {
            HardwareSection("GPS (u-blox ZED-F9P)") {
                Text("Status: Disconnected")
                Button(onClick = { /* TODO */ }) { Text("Connect") }
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

        item {
            HardwareSection("NTRIP Settings") {
                TextField(value = "", onValueChange = {}, label = { Text("Host") })
                TextField(value = "", onValueChange = {}, label = { Text("Mountpoint") })
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

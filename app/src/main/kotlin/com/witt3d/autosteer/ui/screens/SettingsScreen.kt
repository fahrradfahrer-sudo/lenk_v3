package com.witt3d.autosteer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.witt3d.autosteer.viewmodel.MainViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel = viewModel()) {
    val telemetry by viewModel.telemetry.collectAsState()
    var motorSliderValue by remember { mutableStateOf(0f) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Hardware Configuration", style = MaterialTheme.typography.headlineMedium) }

        item {
            HardwareSection("GPS (u-blox ZED-F9P)") {
                Text("Status: Disconnected")
                Button(onClick = { /* TODO */ }) { Text("Connect") }
            }
        }

        item {
            HardwareSection("IMU (Brick v2)") {
                Text("Status: Disconnected") // TODO: Observe connection state
                Text("Orientation: Inclination: ${String.format("%.1f", telemetry.inclinationDegrees)}°")
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
                Text("Encoder: 0") // TODO: Observe encoder value
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

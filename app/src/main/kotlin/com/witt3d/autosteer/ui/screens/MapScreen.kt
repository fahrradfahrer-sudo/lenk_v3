package com.witt3d.autosteer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.witt3d.autosteer.ui.components.CustomGridViewer
import com.witt3d.autosteer.viewmodel.MainViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MapScreen(viewModel: MainViewModel = viewModel()) {
    val telemetry by viewModel.telemetry.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        CustomGridViewer()

        // Telemetry overlays (Floating at top)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryCard("Speed", "${String.format("%.1f", telemetry.speedKmh)} km/h", Modifier.weight(1f))
            TelemetryCard("Heading", "${String.format("%.0f", telemetry.headingDegrees)}°", Modifier.weight(1f))
            TelemetryCard("Inclination", "${String.format("%.1f", telemetry.inclinationDegrees)}°", Modifier.weight(1f))
            TelemetryCard("Accuracy", "${String.format("%.2f", telemetry.accuracyMeters)}m", Modifier.weight(1f), statusColor(telemetry.fixStatus))
        }
    }
}

@Composable
fun statusColor(status: com.witt3d.autosteer.data.models.GpsFixStatus): Color {
    return when (status) {
        com.witt3d.autosteer.data.models.GpsFixStatus.RTK_FIX -> Color.Green
        com.witt3d.autosteer.data.models.GpsFixStatus.FLOAT -> Color.Yellow
        com.witt3d.autosteer.data.models.GpsFixStatus.FIX -> Color.Cyan
        com.witt3d.autosteer.data.models.GpsFixStatus.DGPS -> Color.Blue
        com.witt3d.autosteer.data.models.GpsFixStatus.NO_FIX -> Color.Red
    }
}

@Composable
fun TelemetryCard(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = valueColor)
        }
    }
}

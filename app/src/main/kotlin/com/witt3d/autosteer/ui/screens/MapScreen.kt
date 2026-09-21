package com.witt3d.autosteer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.witt3d.autosteer.data.models.GpsFixStatus
import com.witt3d.autosteer.ui.components.CustomGridViewer
import com.witt3d.autosteer.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MapScreen(viewModel: MainViewModel = viewModel()) {
    val telemetry by viewModel.telemetry.collectAsState()
    val tractorConfig by viewModel.tractorConfig.collectAsState()
    val guidanceState by viewModel.guidanceState.collectAsState()
    val diagnosticLogs by viewModel.steeringDiagnosticLogs.collectAsState()

    var showDiagnosticPanel by remember { mutableStateOf(false) }

    val trackWidthMeters = tractorConfig.trackWidthCm / 100.0

    Box(modifier = Modifier.fillMaxSize()) {
        // Map Grid Canvas
        CustomGridViewer(
            guidanceState = guidanceState,
            headingDegrees = telemetry.headingDegrees,
            trackWidthMeters = trackWidthMeters
        )

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

            val statusText = telemetry.fixStatus.displayText
            val accText = "${String.format("%.2f", telemetry.accuracyMeters)}m"
            TelemetryCard(
                label = "Status / Accuracy",
                value = "$statusText ($accText)",
                modifier = Modifier.weight(1f),
                valueColor = statusColor(telemetry.fixStatus)
            )

            // Cross-Track Error Display
            val cteText = "${if (guidanceState.crossTrackErrorCm >= 0) "+" else ""}${String.format("%.1f", guidanceState.crossTrackErrorCm)} cm"
            TelemetryCard(
                label = "Cross-Track Error",
                value = cteText,
                modifier = Modifier.weight(1.2f),
                valueColor = if (guidanceState.isAutoSteerEngaged) Color(0xFF00E676) else Color.Unspecified
            )
        }

        // AutoSteer & AB Line Control Panel (Floating at bottom)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status banner & steering angles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = guidanceState.steeringStatusMessage,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (guidanceState.isAutoSteerEngaged) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Target: ${String.format("%.1f", guidanceState.targetSteeringAngleDegrees)}° | Wheel: ${String.format("%.1f", guidanceState.currentSteeringAngleDegrees)}°",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { showDiagnosticPanel = !showDiagnosticPanel }) {
                            Text(if (showDiagnosticPanel) "HIDE LOGS" else "STEERING LOGS")
                        }
                    }
                }

                if (showDiagnosticPanel) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Live Steering Behavior Log", style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { viewModel.clearSteeringDiagnosticLogs() }) {
                                    Text("Clear")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(diagnosticLogs.reversed()) { log ->
                                    Text(
                                        text = "[${timeFormat.format(Date(log.timestampMs))}] Spd: %.1f km/h | Hdg: %.0f° | CTE: %+.1f cm | Tgt: %+.1f° | Wheel: %+.1f° | Pwr: %+.0f%% | Curr: %.1fA".format(
                                            log.speedKmh, log.headingDegrees, log.crossTrackErrorCm,
                                            log.targetSteeringAngleDegrees, log.currentSteeringAngleDegrees,
                                            log.motorPowerPercent, log.motorCurrentAmps
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // AB Line Setting Buttons
                    OutlinedButton(
                        onClick = { viewModel.setPointA() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (guidanceState.pointA == null) "SET A" else "RESET A")
                    }

                    OutlinedButton(
                        onClick = { viewModel.setPointB() },
                        modifier = Modifier.weight(1f),
                        enabled = guidanceState.pointA != null
                    ) {
                        Text(if (guidanceState.pointB == null) "SET B" else "RESET B")
                    }

                    OutlinedButton(
                        onClick = { viewModel.clearAbLine() },
                        modifier = Modifier.weight(1f),
                        enabled = guidanceState.pointA != null || guidanceState.pointB != null
                    ) {
                        Text("CLEAR")
                    }

                    // Prominent AutoSteer Toggle Button
                    Button(
                        onClick = { viewModel.toggleAutoSteer() },
                        modifier = Modifier
                            .weight(2f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (guidanceState.isAutoSteerEngaged) Color(0xFFD50000) else Color(0xFF00C853)
                        )
                    ) {
                        Text(
                            text = if (guidanceState.isAutoSteerEngaged) "DISENGAGE AUTOSTEER" else "ENGAGE AUTOSTEER",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun statusColor(status: GpsFixStatus): Color {
    return when (status) {
        GpsFixStatus.RTK_FIX -> Color(0xFF00C853) // Green
        GpsFixStatus.FLOAT -> Color(0xFFFF9800)   // Orange
        else -> Color(0xFFE53935)                 // Red for DGPS, Single, No Fix, etc.
    }
}

@Composable
fun TelemetryCard(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
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

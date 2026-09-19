package com.witt3d.autosteer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.witt3d.autosteer.data.models.TerminalLogDirection
import com.witt3d.autosteer.data.models.TerminalLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DropdownTerminal(
    title: String = "Terminal / Traffic Log",
    logs: List<TerminalLogEntry>,
    onSendCommand: (String) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var commandInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    LaunchedEffect(logs.size, isExpanded) {
        if (isExpanded && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${logs.size} entries",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { isExpanded = !isExpanded }) {
                        Text(if (isExpanded) "Hide Terminal ▲" else "Show Terminal ▼")
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Live Traffic Console",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (logs.isNotEmpty()) {
                                        val formatted = logs.joinToString("\n") { entry ->
                                            val timeStr = timeFormatter.format(Date(entry.timestamp))
                                            val prefix = when (entry.direction) {
                                                TerminalLogDirection.TX -> "TX ->"
                                                TerminalLogDirection.RX -> "RX <-"
                                                TerminalLogDirection.INFO -> "INFO  "
                                                TerminalLogDirection.ERROR -> "ERR ! "
                                            }
                                            "[$timeStr] $prefix ${entry.message}"
                                        }
                                        clipboardManager.setText(AnnotatedString(formatted))
                                        Toast.makeText(context, "Terminal log copied to clipboard", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Terminal log is empty", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = onClearLogs,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Log output area
                    Surface(
                        color = Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No terminal traffic logged yet.",
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(logs) { entry ->
                                    val timeStr = timeFormatter.format(Date(entry.timestamp))
                                    val (prefix, color) = when (entry.direction) {
                                        TerminalLogDirection.TX -> "TX ->" to Color(0xFF4FC3F7) // Light Blue
                                        TerminalLogDirection.RX -> "RX <-" to Color(0xFF81C784) // Light Green
                                        TerminalLogDirection.INFO -> "INFO  " to Color(0xFFFFB74D) // Amber/Orange
                                        TerminalLogDirection.ERROR -> "ERR ! " to Color(0xFFE57373) // Red
                                    }

                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "[$timeStr] ",
                                            color = Color.Gray,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "$prefix ",
                                            color = color,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = entry.message,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom Command Input Box
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commandInput,
                            onValueChange = { commandInput = it },
                            placeholder = { Text("Enter command (e.g., ?C 1, !G 1 100)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        )
                        Button(
                            onClick = {
                                if (commandInput.isNotBlank()) {
                                    onSendCommand(commandInput)
                                    commandInput = ""
                                }
                            }
                        ) {
                            Text("Send")
                        }
                    }
                }
            }
        }
    }
}

package com.witt3d.autosteer.data.models

enum class TerminalLogDirection {
    TX,   // Transmitted
    RX,   // Received
    INFO, // System / Connection Info
    ERROR // Error message
}

data class TerminalLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: TerminalLogDirection,
    val message: String
)

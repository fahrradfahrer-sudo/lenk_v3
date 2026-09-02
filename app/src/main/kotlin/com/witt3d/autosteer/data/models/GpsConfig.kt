package com.witt3d.autosteer.data.models

data class GpsConfig(
    val baudRate: Int = 115200,
    val isSimulationMode: Boolean = false,
    val isConnected: Boolean = false,
    val usbDeviceDetected: Boolean = false,
    val usbDeviceName: String? = null,
    val errorMessage: String? = null
)

data class NtripProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "NTRIP Service",
    val host: String = "",
    val port: Int = 2101,
    val mountpoint: String = "",
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0
)

data class NtripStreamStats(
    val bytesReceivedPerSec: Long = 0,
    val bytesSentPerSec: Long = 0,
    val totalBytesReceived: Long = 0,
    val totalBytesSent: Long = 0
) {
    fun formatRate(bytesPerSec: Long): String {
        return if (bytesPerSec >= 1024) {
            String.format("%.2f KB/s", bytesPerSec / 1024.0)
        } else {
            "$bytesPerSec B/s"
        }
    }

    fun formatTotalBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes Bytes"
        }
    }
}

data class NtripConfig(
    val profiles: List<NtripProfile> = emptyList(),
    val activeProfileId: String? = null,
    val autoConnect: Boolean = false,
    val isConnected: Boolean = false,
    val errorMessage: String? = null,
    val stats: NtripStreamStats = NtripStreamStats()
)

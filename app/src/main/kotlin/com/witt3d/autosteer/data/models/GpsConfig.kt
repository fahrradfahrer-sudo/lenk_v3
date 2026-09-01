package com.witt3d.autosteer.data.models

data class GpsConfig(
    val baudRate: Int = 115200,
    val isSimulationMode: Boolean = false,
    val isConnected: Boolean = false,
    val usbDeviceDetected: Boolean = false,
    val usbDeviceName: String? = null,
    val errorMessage: String? = null
)

data class NtripConfig(
    val host: String = "",
    val port: Int = 2101,
    val mountpoint: String = "",
    val username: String = "",
    val password: String = "",
    val autoConnect: Boolean = false,
    val isConnected: Boolean = false,
    val errorMessage: String? = null
)

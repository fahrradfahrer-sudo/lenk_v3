package com.witt3d.autosteer.data.models

data class ImuConfig(
    val host: String = "127.0.0.1",
    val port: Int = 4223,
    val uid: String = "",
    val isSimulationMode: Boolean = false,
    val isConnected: Boolean = false,
    val usbDeviceDetected: Boolean = false,
    val usbDeviceName: String? = null,
    val errorMessage: String? = null
)

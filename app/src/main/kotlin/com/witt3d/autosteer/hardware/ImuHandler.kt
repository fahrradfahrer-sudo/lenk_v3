package com.witt3d.autosteer.hardware

import com.tinkerforge.IPConnection
import com.tinkerforge.BrickIMUV2
import android.util.Log

class ImuHandler {
    private val host = "localhost"
    private val port = 4223
    private val ipcon = IPConnection()
    private var imu: BrickIMUV2? = null

    fun connect() {
        try {
            ipcon.connect(host, port)
            // IMU Brick v2 usually has a UID you'd need to provide
            // imu = BrickIMUV2("YOUR_UID", ipcon)
            Log.d("ImuHandler", "Connected to Tinkerforge")
        } catch (e: Exception) {
            Log.e("ImuHandler", "Error connecting to Tinkerforge", e)
        }
    }

    fun disconnect() {
        try {
            ipcon.disconnect()
        } catch (e: Exception) {
            Log.e("ImuHandler", "Error disconnecting", e)
        }
    }
}

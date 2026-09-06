package com.witt3d.autosteer.data.repository

import android.content.Context
import com.witt3d.autosteer.hardware.ImuHandler
import com.witt3d.autosteer.hardware.MotorHandler
import com.witt3d.autosteer.hardware.gps.GpsHandler
import com.witt3d.autosteer.hardware.gps.NtripClient

object HardwareRepository {
    val imuHandler = ImuHandler()
    val gpsHandler = GpsHandler()
    val ntripClient = NtripClient(
        onRtcmReceived = { rtcmBytes ->
            gpsHandler.writeRtcmData(rtcmBytes)
        }
    )
    val motorHandler = MotorHandler()

    fun scanAndConnectAll(context: Context) {
        imuHandler.scanUsbDevices(context)
        imuHandler.connect(context)

        gpsHandler.scanUsbDevices(context)
        gpsHandler.connect(context)

        motorHandler.scanUsbDevices(context)
        motorHandler.connect(context)
    }
}

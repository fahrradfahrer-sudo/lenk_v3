package com.witt3d.autosteer.data.repository

import com.witt3d.autosteer.hardware.ImuHandler
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
}

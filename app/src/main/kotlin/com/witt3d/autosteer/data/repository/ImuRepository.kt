package com.witt3d.autosteer.data.repository

import com.witt3d.autosteer.hardware.ImuHandler

object ImuRepository {
    val imuHandler: ImuHandler
        get() = HardwareRepository.imuHandler
}

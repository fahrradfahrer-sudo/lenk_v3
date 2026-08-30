package com.witt3d.autosteer.hardware

import com.witt3d.autosteer.data.models.ImuConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImuHandlerTest {

    @Test
    fun testSimulationModeEmitsTelemetryData() {
        val imuHandler = ImuHandler()

        imuHandler.updateConfig(
            ImuConfig(
                isSimulationMode = true,
                isConnected = false
            )
        )

        imuHandler.setSimulatedOrientation(
            heading = 120.0,
            roll = -15.5,
            pitch = 5.0
        )

        val telemetry = imuHandler.telemetry.value
        assertEquals(120.0, telemetry.headingDegrees, 0.01)
        assertEquals(-15.5, telemetry.inclinationDegrees, 0.01)
        assertTrue(imuHandler.config.value.isSimulationMode)
    }
}

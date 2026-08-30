package com.witt3d.autosteer.hardware

import com.witt3d.autosteer.data.models.ImuConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImuHandlerTest {

    @Test
    fun testSimulationModeEmitsTelemetryData() {
        val imuHandler = ImuHandler()
        var receivedHeading = 0.0
        var receivedRoll = 0.0

        imuHandler.setTelemetryListener { telemetry ->
            receivedHeading = telemetry.headingDegrees
            receivedRoll = telemetry.inclinationDegrees
        }

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

        assertEquals(120.0, receivedHeading, 0.01)
        assertEquals(-15.5, receivedRoll, 0.01)
        assertTrue(imuHandler.config.value.isSimulationMode)
    }
}

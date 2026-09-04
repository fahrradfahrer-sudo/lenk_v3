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

    @Test
    fun testDisconnectResilienceWithoutThrowing() {
        val imuHandler = ImuHandler()
        // Disconnecting when not connected should not throw NotConnectedException or crash
        imuHandler.disconnect()
        assertTrue(!imuHandler.config.value.isConnected)
    }

    @Test
    fun testSimulationConnectAndDisconnectSequence() {
        val imuHandler = ImuHandler()
        imuHandler.updateConfig(ImuConfig(isSimulationMode = true))
        Thread.sleep(800)
        assertTrue(imuHandler.config.value.isConnected)

        imuHandler.disconnect()
        Thread.sleep(800)
        assertTrue(!imuHandler.config.value.isConnected)

        imuHandler.connect()
        Thread.sleep(800)
        assertTrue(imuHandler.config.value.isConnected)
    }
}

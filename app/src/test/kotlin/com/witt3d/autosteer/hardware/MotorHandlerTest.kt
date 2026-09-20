package com.witt3d.autosteer.hardware

import com.witt3d.autosteer.data.models.MotorConfig
import com.witt3d.autosteer.data.models.MotorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorHandlerTest {

    @Test
    fun testDefaultMotorConfigAndUpdates() {
        val handler = MotorHandler()
        val defaultConfig = handler.config.value

        assertEquals(MotorType.NONE_SIMULATED, defaultConfig.motorType)
        assertEquals(1, defaultConfig.roboteqChannel)
        assertEquals(0, defaultConfig.phidgetChannel)
        assertFalse(defaultConfig.isConnected)

        val updated = defaultConfig.copy(
            motorType = MotorType.ROBOTEQ,
            roboteqChannel = 3, // Dual Mode
            commandLoopFrequencyHz = 20,
            invertDirection = true,
            maxPowerLimitPercent = 80
        )
        handler.updateConfig(updated)

        assertEquals(MotorType.ROBOTEQ, handler.config.value.motorType)
        assertEquals(3, handler.config.value.roboteqChannel)
        assertEquals(20, handler.config.value.commandLoopFrequencyHz)
        assertTrue(handler.config.value.invertDirection)
        assertEquals(80, handler.config.value.maxPowerLimitPercent)
    }

    @Test
    fun testSimulationModeTelemetryUpdates() {
        val handler = MotorHandler()
        handler.updateConfig(
            MotorConfig(
                motorType = MotorType.NONE_SIMULATED,
                isSimulationMode = true
            )
        )
        handler.connect()

        Thread.sleep(200)
        assertTrue(handler.config.value.isConnected)

        handler.setTargetPowerPercent(50f)
        Thread.sleep(300)

        val tele = handler.telemetry.value
        assertEquals(50f, tele.targetPowerPercent, 0.01f)
        assertEquals(50f, tele.actualPowerPercent, 0.01f)
        assertTrue(tele.encoderCount > 0)
        assertTrue(tele.currentAmps > 0.0)

        handler.emergencyStop()
        assertEquals(0f, handler.telemetry.value.targetPowerPercent, 0.01f)

        handler.disconnect()
        assertFalse(handler.config.value.isConnected)
    }

    @Test
    fun testTerminalLogsAndCustomCommands() {
        val handler = MotorHandler()
        handler.updateConfig(MotorConfig(motorType = MotorType.ROBOTEQ))
        assertEquals(0, handler.terminalLogs.value.size)

        handler.sendCustomCommand("?C 1")
        val logsAfterCmd = handler.terminalLogs.value
        assertTrue(logsAfterCmd.isNotEmpty())
        assertEquals("?C 1", logsAfterCmd.first().message)

        handler.parseHardwareTelemetryResponse("C=54321")
        val logsAfterRx = handler.terminalLogs.value
        assertEquals("C=54321", logsAfterRx.last().message)
        assertEquals(54321L, handler.telemetry.value.encoderCount)

        // Test Roboteq query echo and error/ack response logging
        handler.parseHardwareTelemetryResponse(": ?V")
        val logsAfterEcho = handler.terminalLogs.value
        assertEquals(": ?V", logsAfterEcho.last().message)

        handler.parseHardwareTelemetryResponse("E=1")
        val logsAfterErr = handler.terminalLogs.value
        assertEquals("E=1", logsAfterErr.last().message)

        handler.clearTerminalLogs()
        assertEquals(0, handler.terminalLogs.value.size)
    }

    @Test
    fun testHardwareTelemetryParsing() {
        val handler = MotorHandler()

        // Test Roboteq Encoder
        handler.parseHardwareTelemetryResponse("C=12345")
        assertEquals(12345L, handler.telemetry.value.encoderCount)

        // Test Roboteq Encoder with prompt prefix (":C=67890")
        handler.parseHardwareTelemetryResponse(":C=67890")
        assertEquals(67890L, handler.telemetry.value.encoderCount)

        // Test Roboteq Encoder with command echo prefix ("?C 1 C=9999")
        handler.parseHardwareTelemetryResponse("?C 1 C=9999")
        assertEquals(9999L, handler.telemetry.value.encoderCount)

        // Test Roboteq Voltage with command echo ("?V V=120:121:5040")
        handler.parseHardwareTelemetryResponse("?V V=120:121:5040")
        assertEquals(12.1, handler.telemetry.value.voltageVolts, 0.01)

        // Test Multi-Metric Response in single line ("C=8888 A=20 V=120:125:5040 S=1200")
        handler.parseHardwareTelemetryResponse("C=8888 A=20 V=120:125:5040 S=1200")
        assertEquals(8888L, handler.telemetry.value.encoderCount)
        assertEquals(2.0, handler.telemetry.value.currentAmps, 0.01)
        assertEquals(12.5, handler.telemetry.value.voltageVolts, 0.01)
        assertEquals(1200, handler.telemetry.value.speedRpm)

        // Test Roboteq Amps (A=15 -> 1.5A)
        handler.parseHardwareTelemetryResponse("A=15")
        assertEquals(1.5, handler.telemetry.value.currentAmps, 0.01)

        // Test Roboteq Amps with acknowledgment prefix ("+A=25")
        handler.parseHardwareTelemetryResponse("+A=25")
        assertEquals(2.5, handler.telemetry.value.currentAmps, 0.01)

        // Test Roboteq Voltages (V=124:240:50 -> main battery V+ is index 1 = 24.0V)
        handler.parseHardwareTelemetryResponse("V=124:240:50")
        assertEquals(24.0, handler.telemetry.value.voltageVolts, 0.01)

        // Test Roboteq Multi-Channel Response for Channel 2
        handler.updateConfig(MotorConfig(motorType = MotorType.ROBOTEQ, roboteqChannel = 2))
        handler.parseHardwareTelemetryResponse("C=100:200")
        assertEquals(200L, handler.telemetry.value.encoderCount)

        handler.parseHardwareTelemetryResponse("A=15:35")
        assertEquals(3.5, handler.telemetry.value.currentAmps, 0.01)

        // Test Roboteq Speed (S=1800 -> 1800 RPM)
        handler.parseHardwareTelemetryResponse("S=1800")
        assertEquals(1800, handler.telemetry.value.speedRpm)

        // Test Roboteq Temperature acknowledgment / parsing
        handler.parseHardwareTelemetryResponse("T=35:38")
        // Verify RX log entry was captured in terminal
        val rxLogs = handler.terminalLogs.value
        assertEquals("T=35:38", rxLogs.last().message)

        // Test Phidgets Encoder & Current & Voltage
        handler.parseHardwareTelemetryResponse("P=-9876")
        assertEquals(-9876L, handler.telemetry.value.encoderCount)

        handler.parseHardwareTelemetryResponse("I=4.2")
        assertEquals(4.2, handler.telemetry.value.currentAmps, 0.01)

        handler.parseHardwareTelemetryResponse("V=12.5")
        assertEquals(12.5, handler.telemetry.value.voltageVolts, 0.01)
    }

    @Test
    fun testPhidgetsConfigAndSimulation() {
        val handler = MotorHandler()
        val phidgetConfig = MotorConfig(
            motorType = MotorType.PHIDGETS,
            phidgetChannel = 0,
            phidgetCurrentLimit = 8.0,
            phidgetAcceleration = 50.0,
            phidgetSerialNumber = 623505,
            isSimulationMode = true
        )
        handler.updateConfig(phidgetConfig)
        handler.connect()

        Thread.sleep(200)
        assertTrue(handler.config.value.isConnected)
        assertEquals(MotorType.PHIDGETS, handler.config.value.motorType)
        assertEquals(623505, handler.config.value.phidgetSerialNumber)

        handler.setTargetPowerPercent(-75f)
        Thread.sleep(300)

        val tele = handler.telemetry.value
        assertEquals(-75f, tele.targetPowerPercent, 0.01f)
        assertEquals(-75f, tele.actualPowerPercent, 0.01f)
        assertTrue(tele.encoderCount < 0)

        handler.disconnect()
        assertFalse(handler.config.value.isConnected)
    }

    @Test
    fun testPhidgetsDisconnectSafety() {
        val handler = MotorHandler()
        val phidgetConfig = MotorConfig(
            motorType = MotorType.PHIDGETS,
            phidgetChannel = 0,
            phidgetSerialNumber = 623505,
            isSimulationMode = false
        )
        handler.updateConfig(phidgetConfig)
        // Disconnecting before or without active hardware connection must complete without exception
        handler.disconnect()
        assertFalse(handler.config.value.isConnected)
        assertEquals(0f, handler.telemetry.value.targetPowerPercent, 0.01f)
    }
}

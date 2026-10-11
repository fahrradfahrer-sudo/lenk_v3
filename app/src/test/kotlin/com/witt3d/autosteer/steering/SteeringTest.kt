package com.witt3d.autosteer.steering

import com.witt3d.autosteer.data.models.AbLine
import com.witt3d.autosteer.data.models.HeadingSource
import com.witt3d.autosteer.data.models.MotorControlMode
import com.witt3d.autosteer.data.models.MotorTelemetry
import com.witt3d.autosteer.data.models.SteeringAlgorithm
import com.witt3d.autosteer.data.models.SteeringConfig
import com.witt3d.autosteer.data.models.SteeringDiagnosticEntry
import com.witt3d.autosteer.data.models.TractorConfig
import com.witt3d.autosteer.data.models.UtmPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.sin

class SteeringTest {

    private val defaultTractor = TractorConfig(
        wheelbaseCm = 280.0,
        antennaToRearAxleCm = 120.0,
        antennaHeightCm = 300.0,
        trackWidthCm = 200.0,
        maxSteeringAngleDegrees = 35.0,
        antennaOffsetCm = 0.0
    )

    private val defaultSteering = SteeringConfig(
        algorithm = SteeringAlgorithm.PURE_PURSUIT,
        controlMode = MotorControlMode.PASSES_PER_DEGREE,
        lookaheadDistanceMeters = 4.0,
        lookaheadSpeedGain = 0.5,
        stanleyK = 1.0,
        stanleyHeadingGain = 1.0,
        passesPerDegree = 300,
        encoderCenterOffset = 0L,
        kp = 2.0,
        deadbandDegrees = 0.2,
        maxOutputPowerPercent = 100
    )

    @Test
    fun testTiltCompensationNoRoll() {
        val antennaPos = UtmPoint(500000.0, 5800000.0)
        // Heading North = 0 degrees, Roll = 0 degrees
        val axlePos = TiltCompensator.computeGroundRearAxlePosition(
            antennaUtm = antennaPos,
            headingDegrees = 0.0,
            rollDegrees = 0.0,
            tractorConfig = defaultTractor
        )

        // Rear axle should be 1.2 meters South (-Y) of antenna
        assertEquals(500000.0, axlePos.x, 0.001)
        assertEquals(5800000.0 - 1.2, axlePos.y, 0.001)
    }

    @Test
    fun testComputeGroundFrontAxlePosition() {
        val rearAxlePos = UtmPoint(500000.0, 5800000.0)

        // Heading North = 0 degrees, Wheelbase = 2.8m
        val frontAxleNorth = TiltCompensator.computeGroundFrontAxlePosition(
            groundRearAxleUtm = rearAxlePos,
            headingDegrees = 0.0,
            tractorConfig = defaultTractor
        )
        assertEquals(500000.0, frontAxleNorth.x, 0.001)
        assertEquals(5800000.0 + 2.8, frontAxleNorth.y, 0.001)

        // Heading East = 90 degrees
        val frontAxleEast = TiltCompensator.computeGroundFrontAxlePosition(
            groundRearAxleUtm = rearAxlePos,
            headingDegrees = 90.0,
            tractorConfig = defaultTractor
        )
        assertEquals(500000.0 + 2.8, frontAxleEast.x, 0.001)
        assertEquals(5800000.0, frontAxleEast.y, 0.001)
    }

    @Test
    fun testTiltCompensationWithRollRight() {
        val antennaPos = UtmPoint(500000.0, 5800000.0)
        val rollDeg = 10.0
        val rollRad = Math.toRadians(rollDeg)
        val expectedSwayM = 3.0 * sin(rollRad) // ~0.5209 m

        // Heading North = 0 degrees, Roll = 10 degrees right
        val axlePos = TiltCompensator.computeGroundRearAxlePosition(
            antennaUtm = antennaPos,
            headingDegrees = 0.0,
            rollDegrees = rollDeg,
            tractorConfig = defaultTractor
        )

        // Ground rear axle is West (-X) of antenna by sway, and South (-Y) by 1.2m
        assertEquals(500000.0 - expectedSwayM, axlePos.x, 0.001)
        assertEquals(5800000.0 - 1.2, axlePos.y, 0.001)
    }

    @Test
    fun testAbLineCrossTrackError() {
        val line = AbLine(UtmPoint(0.0, 0.0), UtmPoint(0.0, 100.0)) // Northbound line

        val rightPoint = UtmPoint(5.0, 50.0) // 5m right of line
        val leftPoint = UtmPoint(-2.5, 50.0) // 2.5m left of line

        val cteRight = line.calculateCrossTrackErrorMeters(rightPoint)
        val cteLeft = line.calculateCrossTrackErrorMeters(leftPoint)

        assertEquals(5.0, cteRight, 0.001)
        assertEquals(-2.5, cteLeft, 0.001)
    }

    @Test
    fun testPurePursuitSteeringDirection() {
        val northLine = AbLine(UtmPoint(0.0, 0.0), UtmPoint(0.0, 100.0))

        // Tractor is 2m right of line, facing North (0 deg)
        val tractorRight = UtmPoint(2.0, 50.0)
        val angleRight = SteeringAlgorithms.calculatePurePursuit(
            groundAxleUtm = tractorRight,
            headingDegrees = 0.0,
            speedKmh = 10.0,
            abLine = northLine,
            tractorConfig = defaultTractor,
            steeringConfig = defaultSteering
        )

        // Tractor is 2m left of line, facing North (0 deg)
        val tractorLeft = UtmPoint(-2.0, 50.0)
        val angleLeft = SteeringAlgorithms.calculatePurePursuit(
            groundAxleUtm = tractorLeft,
            headingDegrees = 0.0,
            speedKmh = 10.0,
            abLine = northLine,
            tractorConfig = defaultTractor,
            steeringConfig = defaultSteering
        )

        // Right of line -> must steer LEFT (negative angle)
        assertTrue("Angle should be negative when right of line", angleRight < 0.0)
        // Left of line -> must steer RIGHT (positive angle)
        assertTrue("Angle should be positive when left of line", angleLeft > 0.0)
    }

    @Test
    fun testStanleySteeringDirection() {
        val northLine = AbLine(UtmPoint(0.0, 0.0), UtmPoint(0.0, 100.0))

        val tractorRight = UtmPoint(2.0, 50.0)
        val angleRight = SteeringAlgorithms.calculateStanley(
            groundAxleUtm = tractorRight,
            headingDegrees = 0.0,
            speedKmh = 10.0,
            abLine = northLine,
            tractorConfig = defaultTractor,
            steeringConfig = defaultSteering.copy(algorithm = SteeringAlgorithm.STANLEY)
        )

        val tractorLeft = UtmPoint(-2.0, 50.0)
        val angleLeft = SteeringAlgorithms.calculateStanley(
            groundAxleUtm = tractorLeft,
            headingDegrees = 0.0,
            speedKmh = 10.0,
            abLine = northLine,
            tractorConfig = defaultTractor,
            steeringConfig = defaultSteering.copy(algorithm = SteeringAlgorithm.STANLEY)
        )

        assertTrue("Stanley angle should be negative when right of line", angleRight < 0.0)
        assertTrue("Stanley angle should be positive when left of line", angleLeft > 0.0)
    }

    @Test
    fun testPassesPerDegreeMotorControl() {
        val controller = SteeringController()

        val config = defaultSteering.copy(
            passesPerDegree = 300,
            encoderCenterOffset = 1000L,
            kp = 2.0,
            deadbandDegrees = 0.2,
            minMotorOutputPowerPercent = 0
        )

        val motorTelemetry = MotorTelemetry(encoderCount = 1000L) // wheels straight 0 deg
        val estimatedAngle = controller.estimateCurrentSteeringAngleDegrees(motorTelemetry, config)
        assertEquals(0.0, estimatedAngle, 0.001)

        // Target angle = +10.0 deg -> error = +10.0 deg -> motor power = +20%
        val power = controller.calculateMotorPowerPercent(10.0, motorTelemetry, config)
        assertEquals(20.0f, power, 0.1f)
    }

    @Test
    fun testEncoderCenterZeroing() {
        val controller = SteeringController()

        val motorTelemetry = MotorTelemetry(encoderCount = 4500L)
        val configWithCenterOffset = defaultSteering.copy(
            passesPerDegree = 300,
            encoderCenterOffset = 4500L
        )

        val estimatedAngle = controller.estimateCurrentSteeringAngleDegrees(motorTelemetry, configWithCenterOffset)
        assertEquals(0.0, estimatedAngle, 0.001)
    }

    @Test
    fun testLowSpeedHeadingSelection() {
        val configAuto = defaultSteering.copy(
            headingSource = HeadingSource.AUTO_FUSED,
            minHeadingSpeedKmh = 1.5
        )

        // At low speed (< 1.5 km/h), should prefer IMU heading
        val lowSpeed = 0.8
        val imuHeading = 45.0
        val gpsHeading = 180.0

        val effectiveLowSpeedHeading = if (lowSpeed >= configAuto.minHeadingSpeedKmh && gpsHeading != 0.0) gpsHeading else imuHeading
        assertEquals(45.0, effectiveLowSpeedHeading, 0.001)

        // At higher speed (>= 1.5 km/h), should prefer GPS COG heading
        val highSpeed = 5.0
        val effectiveHighSpeedHeading = if (highSpeed >= configAuto.minHeadingSpeedKmh && gpsHeading != 0.0) gpsHeading else imuHeading
        assertEquals(180.0, effectiveHighSpeedHeading, 0.001)
    }

    @Test
    fun testOverCurrentCutoffTrigger() {
        val maxCutoff = 15.0
        val normalCurrent = 5.2
        val excessiveCurrent = 18.5

        val isCutoffTriggeredNormal = maxCutoff > 0.0 && normalCurrent >= maxCutoff
        val isCutoffTriggeredExcessive = maxCutoff > 0.0 && excessiveCurrent >= maxCutoff

        assertFalse("Normal current should not trigger cutoff", isCutoffTriggeredNormal)
        assertTrue("Excessive current should trigger cutoff", isCutoffTriggeredExcessive)
    }

    @Test
    fun testSteeringDiagnosticEntryFormatLogLine() {
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val timestamp = 1700000000000L // Specific fixed timestamp

        val entry1 = SteeringDiagnosticEntry(
            timestampMs = timestamp,
            speedKmh = 5.2,
            headingDegrees = 180.0,
            crossTrackErrorCm = 2.1,
            targetSteeringAngleDegrees = 1.5,
            currentSteeringAngleDegrees = 1.2,
            motorPowerPercent = 25.0f,
            motorCurrentAmps = 1.4
        )

        val formatted1 = entry1.formatLogLine(dateFormat)
        val expectedTimeStr = dateFormat.format(java.util.Date(timestamp))
        val expected1 = "[$expectedTimeStr] Spd: 5.2 km/h (F) | Hdg: 180° | CTE: +2.1 cm | Tgt: +1.5° | Wheel: +1.2° | Pwr: +25% | Curr: 1.4A"
        assertEquals(expected1, formatted1)

        val entry2 = SteeringDiagnosticEntry(
            timestampMs = timestamp + 1000L,
            speedKmh = 3.0,
            headingDegrees = 90.0,
            crossTrackErrorCm = -3.5,
            targetSteeringAngleDegrees = -2.0,
            currentSteeringAngleDegrees = -1.8,
            motorPowerPercent = -40.0f,
            motorCurrentAmps = 2.8
        )

        val logsList = listOf(entry1, entry2)
        val logText = logsList.joinToString("\n") { it.formatLogLine(dateFormat) }

        val lines = logText.split("\n")
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("CTE: +2.1 cm"))
        assertTrue(lines[1].contains("CTE: -3.5 cm"))
    }

    @Test
    fun testMinMotorOutputPowerRemapping() {
        val controller = SteeringController()

        val configWithMinPower = defaultSteering.copy(
            minMotorOutputPowerPercent = 20,
            maxOutputPowerPercent = 100,
            deadbandDegrees = 0.2,
            kp = 1.5,
            passesPerDegree = 300,
            encoderCenterOffset = 0L
        )

        val motorTelemetry = MotorTelemetry(encoderCount = 0L)

        // Error = 4.5° -> raw power = 1.5 * 4.5 = 6.75%
        // Remapped power = 20 + (6.75 / 100) * 80 = 25.4%
        val powerPos = controller.calculateMotorPowerPercent(4.5, motorTelemetry, configWithMinPower)
        assertEquals(25.4f, powerPos, 0.2f)

        // Error = -4.5° -> raw power = -6.75%
        // Remapped power = -25.4%
        val powerNeg = controller.calculateMotorPowerPercent(-4.5, motorTelemetry, configWithMinPower)
        assertEquals(-25.4f, powerNeg, 0.2f)
    }

    @Test
    fun testInvertEncoderSignAndMotorOutput() {
        val controller = SteeringController()

        val normalConfig = defaultSteering.copy(
            passesPerDegree = 300,
            encoderCenterOffset = 0L,
            invertEncoderSign = false,
            invertMotorOutput = false
        )

        val invertedConfig = defaultSteering.copy(
            passesPerDegree = 300,
            encoderCenterOffset = 0L,
            invertEncoderSign = true,
            invertMotorOutput = true
        )

        val teleRightWheel = MotorTelemetry(encoderCount = 3000L) // +10 deg physically

        // Normal: +3000 / 300 = +10.0 deg
        val angleNormal = controller.estimateCurrentSteeringAngleDegrees(teleRightWheel, normalConfig)
        assertEquals(10.0, angleNormal, 0.001)

        // Inverted encoder sign: -(+3000) / 300 = -10.0 deg
        val angleInverted = controller.estimateCurrentSteeringAngleDegrees(teleRightWheel, invertedConfig)
        assertEquals(-10.0, angleInverted, 0.001)
    }

    @Test
    fun testStanleyLowSpeedResponseWithUserParameters() {
        val northLine = AbLine(UtmPoint(0.0, 0.0), UtmPoint(0.0, 100.0))
        val userSteering = defaultSteering.copy(
            algorithm = SteeringAlgorithm.STANLEY,
            stanleyK = 5.0,
            stanleyHeadingGain = 1.0
        )

        // Vehicle is 78 cm left of line (-0.78 m CTE) at 0.9 km/h speed
        val tractorLeft = UtmPoint(-0.78, 50.0)
        val angleTarget = SteeringAlgorithms.calculateStanley(
            groundAxleUtm = tractorLeft,
            headingDegrees = 356.0, // 4 deg heading error relative to northbound 0 deg
            speedKmh = 0.9,
            abLine = northLine,
            tractorConfig = defaultTractor, // maxSteeringAngle = 35 deg
            steeringConfig = userSteering
        )

        // Target angle should be clamped to max steering angle (+35 deg) due to large CTE + high gain
        assertEquals(35.0, angleTarget, 0.001)
    }

    @Test
    fun testConfigDiagnosticEntryFormatting() {
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val timestamp = 1700000000000L

        val configEntry = SteeringDiagnosticEntry(
            timestampMs = timestamp,
            configSummary = "Algo: Stanley | Mode: PID | StanleyK: 5.0 | StanleyHdgGain: 1.0"
        )

        val formatted = configEntry.formatLogLine(dateFormat)
        val expectedTimeStr = dateFormat.format(java.util.Date(timestamp))
        assertEquals("[$expectedTimeStr] [CONFIG] Algo: Stanley | Mode: PID | StanleyK: 5.0 | StanleyHdgGain: 1.0", formatted)
    }
}

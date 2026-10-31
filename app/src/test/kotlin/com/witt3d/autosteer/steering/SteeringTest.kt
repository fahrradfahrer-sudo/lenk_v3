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
        SteeringAlgorithms.resetState()
        val userSteering = defaultSteering.copy(
            algorithm = SteeringAlgorithm.STANLEY,
            stanleyK = 5.0,
            stanleyHeadingGain = 1.0,
            stanleySofteningSpeedMps = 0.1 // small softening to test clamping behavior
        )

        // Vehicle is 78 cm left of line (-0.78 m CTE) at 0.9 km/h speed
        val tractorLeft = UtmPoint(-0.78, 50.0)
        val angleTarget = SteeringAlgorithms.calculateStanley(
            groundAxleUtm = tractorLeft,
            headingDegrees = 356.0, // heading error = 356 - 0 = -4 deg (pointing slightly West)
            speedKmh = 0.9,
            abLine = northLine,
            tractorConfig = defaultTractor, // maxSteeringAngle = 35 deg
            steeringConfig = userSteering
        )

        // Target angle should be clamped to max steering angle (+35 deg) due to large CTE + high gain (steer right)
        assertEquals(35.0, angleTarget, 0.001)
    }

    @Test
    fun testStanleySofteningReducesLowSpeedExcessiveSteering() {
        val northLine = AbLine(UtmPoint(0.0, 0.0), UtmPoint(0.0, 100.0))
        SteeringAlgorithms.resetState()

        // CTE = 30 cm right of line (+0.30m), speed = 3.6 km/h (1.0 m/s), heading matches line (0 deg)
        val tractorPos = UtmPoint(0.30, 50.0)

        // Without softening (softening = 0.1)
        val unsoftenedConfig = defaultSteering.copy(
            algorithm = SteeringAlgorithm.STANLEY,
            stanleyK = 1.0,
            stanleyHeadingGain = 1.0,
            stanleySofteningSpeedMps = 0.1,
            stanleyDampingGain = 0.0,
            stanleyIntegralGain = 0.0
        )
        val unsoftenedAngle = SteeringAlgorithms.calculateStanley(
            groundAxleUtm = tractorPos,
            headingDegrees = 0.0,
            speedKmh = 3.6,
            abLine = northLine,
            tractorConfig = defaultTractor,
            steeringConfig = unsoftenedAngleConfig(unsoftenedConfig)
        )

        // With default softening (softening = 1.2 m/s)
        val softenedConfig = defaultSteering.copy(
            algorithm = SteeringAlgorithm.STANLEY,
            stanleyK = 1.0,
            stanleyHeadingGain = 1.0,
            stanleySofteningSpeedMps = 1.2,
            stanleyDampingGain = 0.0,
            stanleyIntegralGain = 0.0
        )
        val softenedAngle = SteeringAlgorithms.calculateStanley(
            groundAxleUtm = tractorPos,
            headingDegrees = 0.0,
            speedKmh = 3.6,
            abLine = northLine,
            tractorConfig = defaultTractor,
            steeringConfig = softenedConfig
        )

        // Softened target angle magnitude should be significantly smaller (gentler, more stable) than unsoftened
        assertTrue("Softened angle ($softenedAngle) should have smaller magnitude than unsoftened ($unsoftenedAngle)",
            kotlin.math.abs(softenedAngle) < kotlin.math.abs(unsoftenedAngle))
    }

    private fun unsoftenedAngleConfig(base: SteeringConfig) = base.copy(stanleySofteningSpeedMps = 0.1)

    @Test
    fun testStanleyDampingUnwindsSteeringWhenApproachingLine() {
        val northLine = AbLine(UtmPoint(0.0, 0.0), UtmPoint(0.0, 100.0))
        SteeringAlgorithms.resetState()

        val dampedConfig = defaultSteering.copy(
            algorithm = SteeringAlgorithm.STANLEY,
            stanleyK = 1.0,
            stanleyHeadingGain = 1.0,
            stanleySofteningSpeedMps = 1.2,
            stanleyDampingGain = 0.5,
            stanleyIntegralGain = 0.0
        )

        // Step 1: Tractor at CTE = +0.20m, heading = 350 deg (pointing 10 deg left, towards line)
        SteeringAlgorithms.calculateStanley(
            groundAxleUtm = UtmPoint(0.20, 50.0),
            headingDegrees = 350.0,
            speedKmh = 3.0,
            abLine = northLine,
            tractorConfig = defaultTractor,
            steeringConfig = dampedConfig,
            dtSec = 0.1
        )

        // Step 2: 0.1s later, tractor moves closer to line: CTE = +0.12m
        val step2Damped = SteeringAlgorithms.calculateStanley(
            groundAxleUtm = UtmPoint(0.12, 50.0),
            headingDegrees = 350.0,
            speedKmh = 3.0,
            abLine = northLine,
            tractorConfig = defaultTractor,
            steeringConfig = dampedConfig,
            dtSec = 0.1
        )

        // Without damping:
        SteeringAlgorithms.resetState()
        val undampedConfig = dampedConfig.copy(stanleyDampingGain = 0.0)
        SteeringAlgorithms.calculateStanley(
            groundAxleUtm = UtmPoint(0.20, 50.0),
            headingDegrees = 350.0,
            speedKmh = 3.0,
            abLine = northLine,
            tractorConfig = defaultTractor,
            steeringConfig = undampedConfig,
            dtSec = 0.1
        )
        val step2Undamped = SteeringAlgorithms.calculateStanley(
            groundAxleUtm = UtmPoint(0.12, 50.0),
            headingDegrees = 350.0,
            speedKmh = 3.0,
            abLine = northLine,
            tractorConfig = defaultTractor,
            steeringConfig = undampedConfig,
            dtSec = 0.1
        )

        // Because CTE is rapidly decreasing (approaching line), damping applies positive counter-steering
        // so step2Damped unwinds faster toward 0 / opposite direction compared to step2Undamped.
        assertTrue("Damped target ($step2Damped) should be more positive than undamped ($step2Undamped)",
            step2Damped > step2Undamped)
    }

    @Test
    fun testPurePursuitAutomaticIntegralEliminatesSteadyStateOffset() {
        val northLine = AbLine(UtmPoint(0.0, 0.0), UtmPoint(0.0, 100.0))
        SteeringAlgorithms.resetState()

        val ppConfig = defaultSteering.copy(
            algorithm = SteeringAlgorithm.PURE_PURSUIT,
            purePursuitIntegralGain = 0.2,
            lookaheadDistanceMeters = 4.0,
            lookaheadSpeedGain = 0.0
        )

        // Tractor steady at CTE = +0.30m (30cm right of line), heading parallel to line (0 deg)
        val steadyPos = UtmPoint(0.30, 50.0)

        // Initial target angle with 0 integral
        val initialTarget = SteeringAlgorithms.calculatePurePursuit(
            groundAxleUtm = steadyPos,
            headingDegrees = 0.0,
            speedKmh = 2.0,
            abLine = northLine,
            tractorConfig = defaultTractor,
            steeringConfig = ppConfig,
            dtSec = 0.1
        )

        // Simulate 20 control cycles at CTE = +0.30m (persisting offset)
        var accumulatedTarget = initialTarget
        for (i in 1..20) {
            accumulatedTarget = SteeringAlgorithms.calculatePurePursuit(
                groundAxleUtm = steadyPos,
                headingDegrees = 0.0,
                speedKmh = 2.0,
                abLine = northLine,
                tractorConfig = defaultTractor,
                steeringConfig = ppConfig,
                dtSec = 0.1
            )
        }

        // Target steering should steer MORE strongly to the left (more negative) due to integral action
        assertTrue("Target with accumulated integral ($accumulatedTarget) must be more negative than initial ($initialTarget)",
            accumulatedTarget < initialTarget)
    }

    @Test
    fun testRealWorldLogScenarioUserLogData() {
        // Reproducing user's real-world test log scenario:
        // Northbound AB line (heading 0 deg)
        val abLine = AbLine(UtmPoint(0.0, 0.0), UtmPoint(0.0, 100.0))

        // Tractor is 77 cm LEFT of line (-0.77 m CTE)
        val tractorPosLeft = UtmPoint(-0.77, 50.0)

        // Heading is 7 degrees (pointing slightly East), speed 0.8 km/h
        val headingDeg = 7.0
        val speedKmh = 0.8

        val stanleyConfig = defaultSteering.copy(
            algorithm = SteeringAlgorithm.STANLEY,
            stanleyK = 1.0,
            stanleyHeadingGain = 1.0
        )

        val targetAngleStanley = SteeringAlgorithms.calculateStanley(
            groundAxleUtm = tractorPosLeft,
            headingDegrees = headingDeg,
            speedKmh = speedKmh,
            abLine = abLine,
            tractorConfig = defaultTractor,
            steeringConfig = stanleyConfig
        )

        val purePursuitConfig = defaultSteering.copy(
            algorithm = SteeringAlgorithm.PURE_PURSUIT,
            lookaheadDistanceMeters = 4.0,
            lookaheadSpeedGain = 0.5
        )

        val targetAnglePurePursuit = SteeringAlgorithms.calculatePurePursuit(
            groundAxleUtm = tractorPosLeft,
            headingDegrees = headingDeg,
            speedKmh = speedKmh,
            abLine = abLine,
            tractorConfig = defaultTractor,
            steeringConfig = purePursuitConfig
        )

        // When left of line (-0.77m CTE), tractor MUST steer RIGHT (+ deg) towards line
        assertTrue("Stanley target steering angle must be positive (steer right) when left of line, got $targetAngleStanley", targetAngleStanley > 0.0)
        assertTrue("Pure Pursuit target steering angle must be positive (steer right) when left of line, got $targetAnglePurePursuit", targetAnglePurePursuit > 0.0)
    }

    @Test
    fun testNearestParallelLineGuidance() {
        val originalLine = AbLine(UtmPoint(0.0, 0.0), UtmPoint(0.0, 100.0)) // Northbound line along X=0
        val passWidth = 3.0 // 3 meters pass width

        // Point at X = 7.0, Y = 50.0 (7 meters right of original AB line)
        val pointRight = UtmPoint(7.0, 50.0)

        // Nearest pass should be pass #2 (2 * 3.0m = 6.0m shift to X=6.0)
        val nearestLine = originalLine.getNearestParallelLine(pointRight, passWidth)

        assertEquals(6.0, nearestLine.pointA.x, 0.001)
        assertEquals(6.0, nearestLine.pointB.x, 0.001)

        // CTE relative to the nearest parallel line should be 7.0 - 6.0 = 1.0 meter
        val cteToParallel = nearestLine.calculateCrossTrackErrorMeters(pointRight)
        assertEquals(1.0, cteToParallel, 0.001)
    }

    @Test
    fun testHeadingOffsetCalibrationCalculation() {
        val rawImuHeading = 250.0
        val realTractorMotionHeading = 275.0 // Tractor moving West-Northwest (275 deg)

        // Calculated offset: 275 - 250 = +25 degrees
        var headingOffset = realTractorMotionHeading - rawImuHeading
        while (headingOffset > 180.0) headingOffset -= 360.0
        while (headingOffset < -180.0) headingOffset += 360.0

        assertEquals(25.0, headingOffset, 0.001)

        // Applying offset to raw IMU heading
        var calibratedHeading = rawImuHeading + headingOffset
        while (calibratedHeading >= 360.0) calibratedHeading -= 360.0
        while (calibratedHeading < 0.0) calibratedHeading += 360.0

        assertEquals(realTractorMotionHeading, calibratedHeading, 0.001)
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

package com.witt3d.autosteer.steering

import com.witt3d.autosteer.data.models.AbLine
import com.witt3d.autosteer.data.models.SteeringAlgorithm
import com.witt3d.autosteer.data.models.SteeringConfig
import com.witt3d.autosteer.data.models.TractorConfig
import com.witt3d.autosteer.data.models.UtmPoint
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

object SteeringAlgorithms {

    /**
     * Calculates target steering angle in degrees based on the configured algorithm.
     */
    fun calculateTargetSteeringAngle(
        groundAxleUtm: UtmPoint,
        headingDegrees: Double,
        speedKmh: Double,
        abLine: AbLine,
        tractorConfig: TractorConfig,
        steeringConfig: SteeringConfig,
        driveDirection: com.witt3d.autosteer.data.models.DriveDirection = com.witt3d.autosteer.data.models.DriveDirection.FORWARD
    ): Double {
        return when (steeringConfig.algorithm) {
            SteeringAlgorithm.PURE_PURSUIT -> calculatePurePursuit(
                groundAxleUtm, headingDegrees, speedKmh, abLine, tractorConfig, steeringConfig, driveDirection
            )
            SteeringAlgorithm.STANLEY -> calculateStanley(
                groundAxleUtm, headingDegrees, speedKmh, abLine, tractorConfig, steeringConfig, driveDirection
            )
        }
    }

    /**
     * Pure Pursuit Algorithm Implementation
     */
    fun calculatePurePursuit(
        groundAxleUtm: UtmPoint,
        headingDegrees: Double,
        speedKmh: Double,
        abLine: AbLine,
        tractorConfig: TractorConfig,
        steeringConfig: SteeringConfig,
        driveDirection: com.witt3d.autosteer.data.models.DriveDirection = com.witt3d.autosteer.data.models.DriveDirection.FORWARD
    ): Double {
        val speedMps = max(speedKmh / 3.6, 0.0)
        val lookaheadDist = max(
            steeringConfig.lookaheadDistanceMeters + steeringConfig.lookaheadSpeedGain * speedMps,
            1.0
        )

        val lineLen = abLine.lengthMeters
        if (lineLen < 1e-6) return 0.0
        val lineUx = abLine.dx / lineLen
        val lineUy = abLine.dy / lineLen

        // Effective motion heading based on drive direction
        val motionHeading = if (driveDirection == com.witt3d.autosteer.data.models.DriveDirection.REVERSE) {
            (headingDegrees + 180.0) % 360.0
        } else {
            headingDegrees
        }

        var headingDiff = motionHeading - abLine.headingDegrees
        while (headingDiff > 180.0) headingDiff -= 360.0
        while (headingDiff < -180.0) headingDiff += 360.0

        val lineDir = if (abs(headingDiff) > 90.0) -1.0 else 1.0

        // Project axle position on AB line
        val projPoint = abLine.projectPoint(groundAxleUtm)

        // Target lookahead point on AB line along path direction
        val targetX = projPoint.x + lineUx * lookaheadDist * lineDir
        val targetY = projPoint.y + lineUy * lookaheadDist * lineDir

        // Transform target point to local tractor frame (X = Right, Y = Forward)
        val dxUtm = targetX - groundAxleUtm.x
        val dyUtm = targetY - groundAxleUtm.y

        val headingRad = Math.toRadians(headingDegrees)
        val localX = dxUtm * cos(headingRad) - dyUtm * sin(headingRad)
        val localY = dxUtm * sin(headingRad) + dyUtm * cos(headingRad)

        val actualLookaheadDist = max(hypot(localX, localY), 0.1)

        val wheelbaseM = tractorConfig.wheelbaseCm / 100.0
        var curvature = (2.0 * localX) / (actualLookaheadDist * actualLookaheadDist)

        if (driveDirection == com.witt3d.autosteer.data.models.DriveDirection.REVERSE) {
            curvature = -curvature
        }

        var steeringAngleDeg = Math.toDegrees(atan(curvature * wheelbaseM))

        // Clamp to max tractor steering angle
        val maxAngle = tractorConfig.maxSteeringAngleDegrees
        steeringAngleDeg = steeringAngleDeg.coerceIn(-maxAngle, maxAngle)

        return steeringAngleDeg
    }

    /**
     * Stanley Algorithm Implementation
     */
    fun calculateStanley(
        groundAxleUtm: UtmPoint,
        headingDegrees: Double,
        speedKmh: Double,
        abLine: AbLine,
        tractorConfig: TractorConfig,
        steeringConfig: SteeringConfig,
        driveDirection: com.witt3d.autosteer.data.models.DriveDirection = com.witt3d.autosteer.data.models.DriveDirection.FORWARD
    ): Double {
        val speedMps = max(speedKmh / 3.6, 0.1) // minimum 0.1 m/s to prevent division by near zero while preserving low-speed responsiveness

        val motionHeading = if (driveDirection == com.witt3d.autosteer.data.models.DriveDirection.REVERSE) {
            (headingDegrees + 180.0) % 360.0
        } else {
            headingDegrees
        }

        var headingDiff = motionHeading - abLine.headingDegrees
        while (headingDiff > 180.0) headingDiff -= 360.0
        while (headingDiff < -180.0) headingDiff += 360.0

        val lineDir = if (abs(headingDiff) > 90.0) -1.0 else 1.0
        val effectiveLineHeading = if (lineDir < 0.0) (abLine.headingDegrees + 180.0) % 360.0 else abLine.headingDegrees

        var headingErrorDeg = motionHeading - effectiveLineHeading
        while (headingErrorDeg > 180.0) headingErrorDeg -= 360.0
        while (headingErrorDeg < -180.0) headingErrorDeg += 360.0

        val rawCteMeters = abLine.calculateCrossTrackErrorMeters(groundAxleUtm)
        val effectiveCteMeters = rawCteMeters * lineDir

        // When left of line (effectiveCteMeters < 0), crossTrackSteerDeg should be positive (+) to steer RIGHT towards the line.
        // Therefore, atan2(-stanleyK * effectiveCteMeters, speedMps) correctly yields positive rad when effectiveCteMeters < 0.
        val crossTrackSteerRad = atan2(-steeringConfig.stanleyK * effectiveCteMeters, speedMps)
        val crossTrackSteerDeg = Math.toDegrees(crossTrackSteerRad)

        // Heading error: headingErrorDeg = motionHeading - effectiveLineHeading
        // E.g., driving North (0 deg) along line (0 deg) -> headingErrorDeg = 0
        // If pointing East (90 deg), motionHeading (90) - line (0) = +90 deg (turned right of line).
        // To correct, we need to steer LEFT (negative angle), so heading term must be -(headingGain * headingErrorDeg).
        var totalSteerDeg = -(steeringConfig.stanleyHeadingGain * headingErrorDeg) + crossTrackSteerDeg

        if (driveDirection == com.witt3d.autosteer.data.models.DriveDirection.REVERSE) {
            totalSteerDeg = -totalSteerDeg
        }

        val maxAngle = tractorConfig.maxSteeringAngleDegrees
        totalSteerDeg = totalSteerDeg.coerceIn(-maxAngle, maxAngle)

        return totalSteerDeg
    }
}

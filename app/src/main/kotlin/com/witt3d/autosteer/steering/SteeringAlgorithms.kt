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

    // Dynamic state for integral offset correction & derivative damping
    private var purePursuitIntegralMeters = 0.0
    private var stanleyIntegralMeters = 0.0
    private var lastStanleyCteMeters: Double? = null
    private var lastHeadingErrorDeg: Double? = null
    private var lastTimestampMs = 0L

    fun resetState() {
        purePursuitIntegralMeters = 0.0
        stanleyIntegralMeters = 0.0
        lastStanleyCteMeters = null
        lastHeadingErrorDeg = null
        lastTimestampMs = 0L
    }

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
        driveDirection: com.witt3d.autosteer.data.models.DriveDirection = com.witt3d.autosteer.data.models.DriveDirection.FORWARD,
        dtSec: Double = 0.1
    ): Double {
        return when (steeringConfig.algorithm) {
            SteeringAlgorithm.PURE_PURSUIT -> calculatePurePursuit(
                groundAxleUtm, headingDegrees, speedKmh, abLine, tractorConfig, steeringConfig, driveDirection, dtSec
            )
            SteeringAlgorithm.STANLEY -> calculateStanley(
                groundAxleUtm, headingDegrees, speedKmh, abLine, tractorConfig, steeringConfig, driveDirection, dtSec
            )
        }
    }

    /**
     * Pure Pursuit Algorithm Implementation with configurable lookahead clamp
     * and automatic cross-track integral trim to eliminate steady-state offsets.
     */
    fun calculatePurePursuit(
        groundAxleUtm: UtmPoint,
        headingDegrees: Double,
        speedKmh: Double,
        abLine: AbLine,
        tractorConfig: TractorConfig,
        steeringConfig: SteeringConfig,
        driveDirection: com.witt3d.autosteer.data.models.DriveDirection = com.witt3d.autosteer.data.models.DriveDirection.FORWARD,
        dtSec: Double = 0.1
    ): Double {
        val speedMps = max(speedKmh / 3.6, 0.0)
        val minLookahead = max(steeringConfig.minLookaheadDistanceMeters, 0.5)
        val lookaheadDist = max(
            steeringConfig.lookaheadDistanceMeters + steeringConfig.lookaheadSpeedGain * speedMps,
            minLookahead
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

        // Raw and effective signed cross-track error
        val rawCteMeters = abLine.calculateCrossTrackErrorMeters(groundAxleUtm)
        val effectiveCteMeters = rawCteMeters * lineDir

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

        var baseSteeringAngleDeg = Math.toDegrees(atan(curvature * wheelbaseM))

        // Automatic cross-track integral trim to eliminate steady-state offsets (PI Pure Pursuit)
        val iGain = steeringConfig.purePursuitIntegralGain
        var integralTrimDeg = 0.0
        if (iGain > 0.0 && dtSec > 0.0 && speedMps > 0.1) {
            purePursuitIntegralMeters += effectiveCteMeters * dtSec
            // Anti-windup clamping to +/- 10.0 meters*sec
            purePursuitIntegralMeters = purePursuitIntegralMeters.coerceIn(-10.0, 10.0)
            // When CTE > 0 (right of line), steer left (negative angle)
            integralTrimDeg = -iGain * purePursuitIntegralMeters
        }

        var steeringAngleDeg = baseSteeringAngleDeg + integralTrimDeg

        // Clamp to max tractor steering angle
        val maxAngle = tractorConfig.maxSteeringAngleDegrees
        steeringAngleDeg = steeringAngleDeg.coerceIn(-maxAngle, maxAngle)

        return steeringAngleDeg
    }

    /**
     * Stanley Algorithm Implementation with low-speed softening constant (k_soft),
     * heading/CTE derivative damping to eliminate overshoot, and automatic integral trim.
     */
    fun calculateStanley(
        groundAxleUtm: UtmPoint,
        headingDegrees: Double,
        speedKmh: Double,
        abLine: AbLine,
        tractorConfig: TractorConfig,
        steeringConfig: SteeringConfig,
        driveDirection: com.witt3d.autosteer.data.models.DriveDirection = com.witt3d.autosteer.data.models.DriveDirection.FORWARD,
        dtSec: Double = 0.1
    ): Double {
        val speedMps = max(speedKmh / 3.6, 0.0)

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

        // Softened velocity denominator prevents low-speed gain explosion and limit cycling:
        // atan2(-k * effectiveCte, v + k_soft)
        val softeningMps = max(steeringConfig.stanleySofteningSpeedMps, 0.1)
        val effectiveVelocity = speedMps + softeningMps

        // When left of line (effectiveCteMeters < 0), crossTrackSteerDeg is positive (+) to steer RIGHT towards the line.
        val crossTrackSteerRad = atan2(-steeringConfig.stanleyK * effectiveCteMeters, effectiveVelocity)
        val crossTrackSteerDeg = Math.toDegrees(crossTrackSteerRad)

        // Heading error term: -(headingGain * headingErrorDeg)
        val headingTermDeg = -(steeringConfig.stanleyHeadingGain * headingErrorDeg)

        // Damping term to anticipate line arrival and unwind steering before crossing:
        // When vehicle is pointing into the line (e.g. headingError negative while CTE positive),
        // vehicle is approaching the line at rate ~ v * sin(headingError).
        // Damping term opposes rate-of-change of CTE (-Kd * d(CTE)/dt) to unwind steering smoothly
        // before crossing the line and prevent overshooting.
        var dampingTermDeg = 0.0
        val prevCte = lastStanleyCteMeters
        if (steeringConfig.stanleyDampingGain > 0.0 && dtSec > 0.0 && prevCte != null) {
            val deltaCte = effectiveCteMeters - prevCte
            // Only differentiate continuous tracking, ignoring discontinuous line switches or jump calls (> 1.0 m)
            if (abs(deltaCte) <= 1.0) {
                val cteRateOfChange = deltaCte / dtSec
                dampingTermDeg = (-steeringConfig.stanleyDampingGain * cteRateOfChange * 5.0).coerceIn(-15.0, 15.0)
            }
        }
        lastStanleyCteMeters = effectiveCteMeters
        lastHeadingErrorDeg = headingErrorDeg

        // Cross-track integral term to eliminate steady-state mechanical alignment or side-slope bias
        val iGain = steeringConfig.stanleyIntegralGain
        var integralTermDeg = 0.0
        if (iGain > 0.0 && dtSec > 0.0 && speedMps > 0.1) {
            stanleyIntegralMeters += effectiveCteMeters * dtSec
            stanleyIntegralMeters = stanleyIntegralMeters.coerceIn(-10.0, 10.0)
            integralTermDeg = -iGain * stanleyIntegralMeters * 10.0
        }

        var totalSteerDeg = headingTermDeg + crossTrackSteerDeg + dampingTermDeg + integralTermDeg

        if (driveDirection == com.witt3d.autosteer.data.models.DriveDirection.REVERSE) {
            totalSteerDeg = -totalSteerDeg
        }

        val maxAngle = tractorConfig.maxSteeringAngleDegrees
        totalSteerDeg = totalSteerDeg.coerceIn(-maxAngle, maxAngle)

        return totalSteerDeg
    }
}

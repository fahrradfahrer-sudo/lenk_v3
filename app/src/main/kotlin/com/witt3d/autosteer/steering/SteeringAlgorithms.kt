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
        steeringConfig: SteeringConfig
    ): Double {
        return when (steeringConfig.algorithm) {
            SteeringAlgorithm.PURE_PURSUIT -> calculatePurePursuit(
                groundAxleUtm, headingDegrees, speedKmh, abLine, tractorConfig, steeringConfig
            )
            SteeringAlgorithm.STANLEY -> calculateStanley(
                groundAxleUtm, headingDegrees, speedKmh, abLine, tractorConfig, steeringConfig
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
        steeringConfig: SteeringConfig
    ): Double {
        val speedMps = max(speedKmh / 3.6, 0.0)
        val lookaheadDist = max(
            steeringConfig.lookaheadDistanceMeters + steeringConfig.lookaheadSpeedGain * speedMps,
            1.0
        )

        // Line vector
        val lineLen = abLine.lengthMeters
        if (lineLen < 1e-6) return 0.0
        val lineUx = abLine.dx / lineLen
        val lineUy = abLine.dy / lineLen

        // Project axle position on AB line
        val projPoint = abLine.projectPoint(groundAxleUtm)

        // Target lookahead point on AB line ahead of projection
        val targetX = projPoint.x + lineUx * lookaheadDist
        val targetY = projPoint.y + lineUy * lookaheadDist

        // Transform target point to local tractor frame (X = Right, Y = Forward)
        val dxUtm = targetX - groundAxleUtm.x
        val dyUtm = targetY - groundAxleUtm.y

        val headingRad = Math.toRadians(headingDegrees)
        val localX = dxUtm * cos(headingRad) - dyUtm * sin(headingRad)
        val localY = dxUtm * sin(headingRad) + dyUtm * cos(headingRad)

        val actualLookaheadDist = max(hypot(localX, localY), 0.1)

        val wheelbaseM = tractorConfig.wheelbaseCm / 100.0
        // Curvature = 2 * localX / (L^2)
        val curvature = (2.0 * localX) / (actualLookaheadDist * actualLookaheadDist)

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
        steeringConfig: SteeringConfig
    ): Double {
        val speedMps = max(speedKmh / 3.6, 0.5) // minimum 0.5 m/s to prevent division by near zero

        // Heading error (tractor heading - line heading) in degrees, normalized to [-180, 180]
        var headingErrorDeg = headingDegrees - abLine.headingDegrees
        while (headingErrorDeg > 180.0) headingErrorDeg -= 360.0
        while (headingErrorDeg < -180.0) headingErrorDeg += 360.0

        // Signed cross-track error in meters (positive = tractor is right of AB line)
        val cteMeters = abLine.calculateCrossTrackErrorMeters(groundAxleUtm)

        // Stanley control law: delta = - (heading_gain * theta_e) + atan(- k * cte / v)
        val crossTrackSteerRad = atan2(-steeringConfig.stanleyK * cteMeters, speedMps)
        val crossTrackSteerDeg = Math.toDegrees(crossTrackSteerRad)

        var totalSteerDeg = -(steeringConfig.stanleyHeadingGain * headingErrorDeg) + crossTrackSteerDeg

        val maxAngle = tractorConfig.maxSteeringAngleDegrees
        totalSteerDeg = totalSteerDeg.coerceIn(-maxAngle, maxAngle)

        return totalSteerDeg
    }
}

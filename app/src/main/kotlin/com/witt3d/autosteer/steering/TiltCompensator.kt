package com.witt3d.autosteer.steering

import com.witt3d.autosteer.data.models.TractorConfig
import com.witt3d.autosteer.data.models.UtmPoint
import kotlin.math.cos
import kotlin.math.sin

object TiltCompensator {

    /**
     * Calculates the ground-level rear-axle UTM position from GPS antenna position,
     * heading, roll (inclination), and tractor geometry.
     *
     * @param antennaUtm Position of antenna in UTM coordinates (meters)
     * @param headingDegrees Vehicle heading in degrees (0 = North, 90 = East, clockwise)
     * @param rollDegrees Vehicle roll/inclination angle in degrees (positive = roll right, negative = roll left)
     * @param tractorConfig Vehicle geometric measurements in centimeters
     * @return Compensated ground-level rear-axle UTM position
     */
    fun computeGroundRearAxlePosition(
        antennaUtm: UtmPoint,
        headingDegrees: Double,
        rollDegrees: Double,
        tractorConfig: TractorConfig
    ): UtmPoint {
        val headingRad = Math.toRadians(headingDegrees)
        val rollRad = Math.toRadians(rollDegrees)

        val antennaHeightM = tractorConfig.antennaHeightCm / 100.0
        val antennaToRearM = tractorConfig.antennaToRearAxleCm / 100.0
        val antennaOffsetM = tractorConfig.antennaOffsetCm / 100.0

        // Antenna roll sway (lateral shift to the right when rolling right)
        val swayM = antennaHeightM * sin(rollRad)
        val totalRightOffsetM = antennaOffsetM + swayM

        // Forward and Right unit vectors in UTM coordinate frame (X = East, Y = North)
        val fwdX = sin(headingRad)
        val fwdY = cos(headingRad)

        val rightX = cos(headingRad)
        val rightY = -sin(headingRad)

        // Rear axle position is obtained by moving backwards by antennaToRearM
        // and left by totalRightOffsetM from antenna location
        val axleX = antennaUtm.x - (antennaToRearM * fwdX) - (totalRightOffsetM * rightX)
        val axleY = antennaUtm.y - (antennaToRearM * fwdY) - (totalRightOffsetM * rightY)

        return UtmPoint(axleX, axleY)
    }

    /**
     * Projects the ground-level rear-axle position forward along the heading by the
     * tractor's wheelbase to obtain the ground-level FRONT-axle position.
     *
     * The Stanley controller regulates cross-track and heading error at the front
     * axle (that's where its bicycle-model derivation is referenced), so it must be
     * fed this point instead of [computeGroundRearAxlePosition]'s rear-axle point.
     * Pure Pursuit should keep using the rear-axle point.
     */
    fun computeGroundFrontAxlePosition(
        groundRearAxleUtm: UtmPoint,
        headingDegrees: Double,
        tractorConfig: TractorConfig
    ): UtmPoint {
        val headingRad = Math.toRadians(headingDegrees)
        val wheelbaseM = tractorConfig.wheelbaseCm / 100.0
        val fwdX = sin(headingRad)
        val fwdY = cos(headingRad)
        return UtmPoint(
            groundRearAxleUtm.x + wheelbaseM * fwdX,
            groundRearAxleUtm.y + wheelbaseM * fwdY
        )
    }
}

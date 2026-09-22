package com.witt3d.autosteer.data.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.cos

data class UtmPoint(
    val x: Double,
    val y: Double
)

data class AbLine(
    val pointA: UtmPoint,
    val pointB: UtmPoint
) {
    val dx: Double get() = pointB.x - pointA.x
    val dy: Double get() = pointB.y - pointA.y
    val lengthMeters: Double get() = hypot(dx, dy)

    /**
     * Heading of AB line in degrees (0..360, clockwise from North / UTM Y-axis)
     */
    val headingDegrees: Double
        get() {
            var deg = Math.toDegrees(atan2(dx, dy))
            if (deg < 0) deg += 360.0
            return deg
        }

    val headingRadians: Double get() = Math.toRadians(headingDegrees)

    /**
     * Calculates signed cross-track error in meters from line AB to point P.
     * Positive = point is to the RIGHT of line A -> B.
     * Negative = point is to the LEFT of line A -> B.
     */
    fun calculateCrossTrackErrorMeters(point: UtmPoint): Double {
        val len = lengthMeters
        if (len < 1e-6) return 0.0
        val apX = point.x - pointA.x
        val apY = point.y - pointA.y
        return (apX * dy - apY * dx) / len
    }

    /**
     * Projection of point P onto line AB
     */
    fun projectPoint(point: UtmPoint): UtmPoint {
        val len = lengthMeters
        if (len < 1e-6) return pointA
        val uX = dx / len
        val uY = dy / len
        val apX = point.x - pointA.x
        val apY = point.y - pointA.y
        val atd = apX * uX + apY * uY
        return UtmPoint(pointA.x + atd * uX, pointA.y + atd * uY)
    }
}

data class SteeringDiagnosticEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val speedKmh: Double = 0.0,
    val headingDegrees: Double = 0.0,
    val crossTrackErrorCm: Double = 0.0,
    val targetSteeringAngleDegrees: Double = 0.0,
    val currentSteeringAngleDegrees: Double = 0.0,
    val motorPowerPercent: Float = 0f,
    val motorCurrentAmps: Double = 0.0,
    val statusMessage: String = "",
    val driveDirection: DriveDirection = DriveDirection.FORWARD
) {
    fun formatLogLine(timeFormat: SimpleDateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)): String {
        val dirChar = if (driveDirection == DriveDirection.REVERSE) "R" else "F"
        return "[${timeFormat.format(Date(timestampMs))}] Spd: %.1f km/h (%s) | Hdg: %.0f° | CTE: %+.1f cm | Tgt: %+.1f° | Wheel: %+.1f° | Pwr: %+.0f%% | Curr: %.1fA".format(
            Locale.US,
            speedKmh, dirChar, headingDegrees, crossTrackErrorCm,
            targetSteeringAngleDegrees, currentSteeringAngleDegrees,
            motorPowerPercent, motorCurrentAmps
        )
    }
}

data class GuidanceState(
    val isAutoSteerEngaged: Boolean = false,
    val abLine: AbLine? = null,
    val pointA: UtmPoint? = null,
    val pointB: UtmPoint? = null,
    val crossTrackErrorCm: Double = 0.0,
    val targetSteeringAngleDegrees: Double = 0.0,
    val compensatedUtmX: Double = 0.0,
    val compensatedUtmY: Double = 0.0,
    val currentSteeringAngleDegrees: Double = 0.0,
    val steeringStatusMessage: String = "Ready"
)

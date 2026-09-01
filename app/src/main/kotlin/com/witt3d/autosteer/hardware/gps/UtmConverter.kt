package com.witt3d.autosteer.hardware.gps

import kotlin.math.*

data class UtmPoint(
    val easting: Double,
    val northing: Double,
    val zoneNumber: Int,
    val isNorthern: Boolean
)

object UtmConverter {
    // WGS84 Ellipsoid constants
    private const val A = 6378137.0 // semi-major axis
    private const val F = 1.0 / 298.257223563 // flattening
    private const val K0 = 0.9996 // scale factor

    fun latLonToUtm(lat: Double, lon: Double): UtmPoint {
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        var zoneNumber = ((lon + 180) / 6).toInt() + 1
        if (lat >= 56.0 && lat < 64.0 && lon >= 3.0 && lon < 12.0) {
            zoneNumber = 32
        }
        // Special zones for Svalbard
        if (lat >= 72.0 && lat < 84.0) {
            if (lon >= 0.0 && lon < 9.0) zoneNumber = 31
            else if (lon >= 9.0 && lon < 21.0) zoneNumber = 33
            else if (lon >= 21.0 && lon < 33.0) zoneNumber = 35
            else if (lon >= 33.0 && lon < 42.0) zoneNumber = 37
        }

        val lonOrigin = (zoneNumber - 1) * 6 - 180 + 3 // +3 puts origin in middle of zone
        val lonOriginRad = Math.toRadians(lonOrigin.toDouble())

        val eccSquared = 2 * F - F * F
        val eccPrimeSquared = eccSquared / (1 - eccSquared)

        val N = A / sqrt(1 - eccSquared * sin(latRad) * sin(latRad))
        val T = tan(latRad) * tan(latRad)
        val C = eccPrimeSquared * cos(latRad) * cos(latRad)
        val A_val = cos(latRad) * (lonRad - lonOriginRad)

        val M = A * (
            (1 - eccSquared / 4 - 3 * eccSquared * eccSquared / 64 - 5 * eccSquared * eccSquared * eccSquared / 256) * latRad
            - (3 * eccSquared / 8 + 3 * eccSquared * eccSquared / 32 + 45 * eccSquared * eccSquared * eccSquared / 1024) * sin(2 * latRad)
            + (15 * eccSquared * eccSquared / 256 + 45 * eccSquared * eccSquared * eccSquared / 1024) * sin(4 * latRad)
            - (35 * eccSquared * eccSquared * eccSquared / 3072) * sin(6 * latRad)
        )

        val utmEasting = K0 * N * (
            A_val + (1 - T + C) * A_val * A_val * A_val / 6
            + (5 - 18 * T + T * T + 72 * C - 58 * eccPrimeSquared) * A_val * A_val * A_val * A_val * A_val / 120
        ) + 500000.0

        var utmNorthing = K0 * (
            M + N * tan(latRad) * (
                A_val * A_val / 2 + (5 - T + 9 * C + 4 * C * C) * A_val * A_val * A_val * A_val / 24
                + (61 - 58 * T + T * T + 600 * C - 330 * eccPrimeSquared) * A_val * A_val * A_val * A_val * A_val * A_val / 720
            )
        )

        val isNorthern = lat >= 0
        if (!isNorthern) {
            utmNorthing += 10000000.0 // 10,000,000 meter offset for southern hemisphere
        }

        return UtmPoint(utmEasting, utmNorthing, zoneNumber, isNorthern)
    }
}

package com.witt3d.autosteer.hardware.gps

import com.witt3d.autosteer.data.models.GpsFixStatus

data class NmeaParseResult(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val fixStatus: GpsFixStatus? = null,
    val accuracyMeters: Double? = null,
    val speedKmh: Double? = null,
    val headingDegrees: Double? = null,
    val rawGga: String? = null
)

class NmeaParser {

    /**
     * Parse a NMEA sentence string (e.g., $GNGGA,..., or $GNRMC,...)
     */
    fun parseSentence(sentence: String): NmeaParseResult? {
        val trimmed = sentence.trim()
        if (!trimmed.startsWith("$")) return null

        // Check checksum if present
        if (trimmed.contains("*")) {
            val parts = trimmed.substring(1).split("*")
            if (parts.size == 2) {
                val data = parts[0]
                val expectedChecksum = parts[1].trim()
                var calcChecksum = 0
                for (ch in data) {
                    calcChecksum = calcChecksum xor ch.code
                }
                val calcHex = String.format("%02X", calcChecksum)
                if (!calcHex.equals(expectedChecksum, ignoreCase = true)) {
                    return null // Invalid checksum
                }
            }
        }

        val tokens = trimmed.split("*")[0].split(",")
        if (tokens.isEmpty()) return null

        val messageId = tokens[0].substring(1) // E.g., GNGGA, GPGGA, GNRMC, GPRMC

        return when {
            messageId.endsWith("GGA") -> parseGga(tokens, trimmed)
            messageId.endsWith("RMC") -> parseRmc(tokens)
            else -> null
        }
    }

    private fun parseGga(tokens: List<String>, rawGga: String): NmeaParseResult? {
        // Example: $GNGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47
        // Indices: 0: $GNGGA, 1: UTC Time, 2: Lat, 3: N/S, 4: Lon, 5: E/W, 6: Quality, 7: NumSat, 8: HDOP, 9: Alt
        if (tokens.size < 10) return null

        val lat = parseLatitude(tokens.getOrNull(2), tokens.getOrNull(3))
        val lon = parseLongitude(tokens.getOrNull(4), tokens.getOrNull(5))

        val fixQualityCode = tokens.getOrNull(6)?.toIntOrNull() ?: 0
        val fixStatus = when (fixQualityCode) {
            1 -> GpsFixStatus.FIX
            2 -> GpsFixStatus.DGPS
            4 -> GpsFixStatus.RTK_FIX
            5 -> GpsFixStatus.FLOAT
            else -> GpsFixStatus.NO_FIX
        }

        val hdop = tokens.getOrNull(8)?.toDoubleOrNull() ?: 1.0
        val accuracyMeters = when (fixStatus) {
            GpsFixStatus.RTK_FIX -> 0.02 * hdop
            GpsFixStatus.FLOAT -> 0.2 * hdop
            GpsFixStatus.DGPS -> 0.5 * hdop
            GpsFixStatus.FIX -> 1.5 * hdop
            else -> 10.0
        }

        val altitude = tokens.getOrNull(9)?.toDoubleOrNull()

        return NmeaParseResult(
            latitude = lat,
            longitude = lon,
            altitudeMeters = altitude,
            fixStatus = fixStatus,
            accuracyMeters = accuracyMeters,
            rawGga = rawGga
        )
    }

    private fun parseRmc(tokens: List<String>): NmeaParseResult? {
        // Example: $GNRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A
        // Indices: 0: $GNRMC, 1: Time, 2: Status (A=valid, V=void), 3: Lat, 4: N/S, 5: Lon, 6: E/W, 7: Speed in knots, 8: Track angle (heading)
        if (tokens.size < 9) return null

        val status = tokens.getOrNull(2)
        if (status != "A") {
            return NmeaParseResult(fixStatus = GpsFixStatus.NO_FIX)
        }

        val lat = parseLatitude(tokens.getOrNull(3), tokens.getOrNull(4))
        val lon = parseLongitude(tokens.getOrNull(5), tokens.getOrNull(6))
        val speedKnots = tokens.getOrNull(7)?.toDoubleOrNull()
        val speedKmh = speedKnots?.let { it * 1.852 }
        val heading = tokens.getOrNull(8)?.toDoubleOrNull()

        return NmeaParseResult(
            latitude = lat,
            longitude = lon,
            speedKmh = speedKmh,
            headingDegrees = heading
        )
    }

    private fun parseLatitude(raw: String?, hemisphere: String?): Double? {
        if (raw.isNullOrEmpty() || raw.length < 4) return null
        return try {
            val deg = raw.substring(0, 2).toDouble()
            val min = raw.substring(2).toDouble()
            var dec = deg + (min / 60.0)
            if (hemisphere.equals("S", ignoreCase = true)) dec = -dec
            dec
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLongitude(raw: String?, hemisphere: String?): Double? {
        if (raw.isNullOrEmpty() || raw.length < 5) return null
        return try {
            val deg = raw.substring(0, 3).toDouble()
            val min = raw.substring(3).toDouble()
            var dec = deg + (min / 60.0)
            if (hemisphere.equals("W", ignoreCase = true)) dec = -dec
            dec
        } catch (e: Exception) {
            null
        }
    }
}

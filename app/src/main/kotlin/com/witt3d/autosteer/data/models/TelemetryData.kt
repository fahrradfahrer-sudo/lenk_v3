package com.witt3d.autosteer.data.models

enum class GpsFixStatus(val displayText: String) {
    NO_FIX("No Fix"),
    FIX("Single"),
    DGPS("DGPS"),
    FLOAT("FRTK"),
    RTK_FIX("RTK")
}

data class TelemetryData(
    val speedKmh: Double = 0.0,
    val headingDegrees: Double = 0.0,
    val inclinationDegrees: Double = 0.0, // Roll
    val pitchDegrees: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val fixStatus: GpsFixStatus = GpsFixStatus.NO_FIX,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val utmX: Double = 0.0,
    val utmY: Double = 0.0,
    val accelX: Double = 0.0,
    val accelY: Double = 0.0,
    val accelZ: Double = 0.0
)

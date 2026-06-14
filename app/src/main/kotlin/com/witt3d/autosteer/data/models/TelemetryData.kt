package com.witt3d.autosteer.data.models

enum class GpsFixStatus {
    NO_FIX, FIX, FLOAT, RTK_FIX, DGPS
}

data class TelemetryData(
    val speedKmh: Double = 0.0,
    val headingDegrees: Double = 0.0,
    val inclinationDegrees: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val fixStatus: GpsFixStatus = GpsFixStatus.NO_FIX,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val utmX: Double = 0.0,
    val utmY: Double = 0.0
)

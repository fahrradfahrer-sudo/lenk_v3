package com.witt3d.autosteer.data.models

data class TractorConfig(
    val wheelbaseCm: Double = 280.0,
    val antennaToRearAxleCm: Double = 120.0,
    val antennaHeightCm: Double = 300.0,
    val trackWidthCm: Double = 200.0,
    val maxSteeringAngleDegrees: Double = 35.0,
    val antennaOffsetCm: Double = 0.0
)

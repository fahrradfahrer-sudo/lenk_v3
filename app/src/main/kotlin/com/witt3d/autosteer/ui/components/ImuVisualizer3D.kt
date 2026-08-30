package com.witt3d.autosteer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

data class Point3D(val x: Float, val y: Float, val z: Float)

@Composable
fun ImuVisualizer3D(
    rollDegrees: Double,
    pitchDegrees: Double,
    headingDegrees: Double,
    accelX: Double = 0.0,
    accelY: Double = 0.0,
    accelZ: Double = 0.0,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E2C)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radRoll = Math.toRadians(rollDegrees).toFloat()
            val radPitch = Math.toRadians(pitchDegrees).toFloat()
            val radHeading = Math.toRadians(headingDegrees).toFloat()

            // Define 3D bounding box dimensions for tractor/IMU body
            val dx = 100f
            val dy = 50f
            val dz = 40f

            // 8 vertices of a box centered at origin
            val vertices = listOf(
                Point3D(-dx, -dy, -dz), Point3D(dx, -dy, -dz),
                Point3D(dx, dy, -dz), Point3D(-dx, dy, -dz),
                Point3D(-dx, -dy, dz), Point3D(dx, -dy, dz),
                Point3D(dx, dy, dz), Point3D(-dx, dy, dz)
            )

            // Rotation matrices combined (Yaw/Heading Z, Pitch Y, Roll X)
            fun rotate(p: Point3D): Point3D {
                // Pitch (Y axis)
                val x1 = p.x * cos(radPitch) + p.z * sin(radPitch)
                val y1 = p.y
                val z1 = -p.x * sin(radPitch) + p.z * cos(radPitch)

                // Roll (X axis)
                val x2 = x1
                val y2 = y1 * cos(radRoll) - z1 * sin(radRoll)
                val z2 = y1 * sin(radRoll) + z1 * cos(radRoll)

                // Yaw (Z axis)
                val x3 = x2 * cos(radHeading) - y2 * sin(radHeading)
                val y3 = x2 * sin(radHeading) + y2 * cos(radHeading)
                val z3 = z2

                return Point3D(x3, y3, z3)
            }

            // Simple Isometric Perspective Projection to 2D Screen
            fun project(p: Point3D): Offset {
                val scale = 0.9f
                val screenX = center.x + (p.x - p.y * 0.5f) * scale
                val screenY = center.y + (p.z + p.y * 0.3f) * scale
                return Offset(screenX, screenY)
            }

            val rotated = vertices.map { rotate(it) }
            val projected = rotated.map { project(it) }

            // Define 6 faces (each with list of 4 vertex indices)
            val faces = listOf(
                listOf(0, 1, 2, 3), // Bottom
                listOf(4, 5, 6, 7), // Top
                listOf(0, 1, 5, 4), // Front
                listOf(2, 3, 7, 6), // Back
                listOf(0, 3, 7, 4), // Left
                listOf(1, 2, 6, 5)  // Right
            )

            val faceColors = listOf(
                Color(0xFF2C3E50), Color(0xFF3498DB),
                Color(0xFFE74C3C), Color(0xFF2ECC71),
                Color(0xFFF1C40F), Color(0xFF9B59B6)
            )

            // Calculate average Z depth for sorting faces (Painter's Algorithm)
            val sortedFaces = faces.mapIndexed { idx, faceIndices ->
                val avgZ = faceIndices.map { rotated[it].y }.average()
                Triple(idx, faceIndices, avgZ)
            }.sortedBy { it.third }

            // Draw 3D Box Faces
            for ((idx, faceIndices, _) in sortedFaces) {
                val path = Path().apply {
                    moveTo(projected[faceIndices[0]].x, projected[faceIndices[0]].y)
                    for (i in 1 until faceIndices.size) {
                        lineTo(projected[faceIndices[i]].x, projected[faceIndices[i]].y)
                    }
                    close()
                }

                drawPath(path = path, color = faceColors[idx].copy(alpha = 0.65f))
                drawPath(path = path, color = Color.White.copy(alpha = 0.8f), style = Stroke(width = 2f))
            }

            // Draw Forward Heading Arrow Indicator
            val noseVector = rotate(Point3D(0f, -dy - 40f, 0f))
            val noseProjected = project(noseVector)
            drawLine(
                color = Color.Cyan,
                start = center,
                end = noseProjected,
                strokeWidth = 5f
            )
            drawCircle(Color.Cyan, radius = 6f, center = noseProjected)

            // Draw Acceleration / Movement Vector Arrow
            val accelVec = rotate(Point3D(accelX.toFloat() * 15f, -accelY.toFloat() * 15f, -accelZ.toFloat() * 15f))
            val accelProjected = project(accelVec)
            drawLine(
                color = Color(0xFFFF5722),
                start = center,
                end = accelProjected,
                strokeWidth = 4f
            )
            drawCircle(Color(0xFFFF5722), radius = 5f, center = accelProjected)
        }

        // Overlay orientation info text legend
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = "Roll: ${String.format("%.1f", rollDegrees)}° | Pitch: ${String.format("%.1f", pitchDegrees)}° | Yaw: ${String.format("%.1f", headingDegrees)}°",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
            Text(
                text = "Accel (X, Y, Z): ${String.format("%.2f", accelX)}, ${String.format("%.2f", accelY)}, ${String.format("%.2f", accelZ)} m/s²",
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray
            )
        }
    }
}

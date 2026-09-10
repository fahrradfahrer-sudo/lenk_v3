package com.witt3d.autosteer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.witt3d.autosteer.data.models.GuidanceState
import com.witt3d.autosteer.data.models.UtmPoint

@Composable
fun CustomGridViewer(
    guidanceState: GuidanceState,
    headingDegrees: Double = 0.0,
    trackWidthMeters: Double = 2.0
) {
    var zoomScale by remember { mutableStateOf(10f) } // pixels per meter
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val currentAxleUtm = UtmPoint(guidanceState.compensatedUtmX, guidanceState.compensatedUtmY)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(2f, 100f)
                    panOffset += pan
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2f, height / 2f)

        // Reference center in UTM (tractor position or origin if tractor is at 0)
        val refX = if (currentAxleUtm.x != 0.0) currentAxleUtm.x else 0.0
        val refY = if (currentAxleUtm.y != 0.0) currentAxleUtm.y else 0.0

        fun utmToScreen(utmPoint: UtmPoint): Offset {
            val dx = (utmPoint.x - refX).toFloat()
            val dy = (utmPoint.y - refY).toFloat()
            // North is UP (-Y on screen), East is RIGHT (+X on screen)
            val screenX = center.x + panOffset.x + (dx * zoomScale)
            val screenY = center.y + panOffset.y - (dy * zoomScale)
            return Offset(screenX, screenY)
        }

        // Draw Grid Background (10m grid)
        val gridStepM = 10f
        val gridStepPx = gridStepM * zoomScale

        val startX = ((0f - center.x - panOffset.x) / gridStepPx).toInt() - 1
        val endX = ((width - center.x - panOffset.x) / gridStepPx).toInt() + 1
        val startY = ((0f - center.y - panOffset.y) / gridStepPx).toInt() - 1
        val endY = ((height - center.y - panOffset.y) / gridStepPx).toInt() + 1

        for (i in startX..endX) {
            val sx = center.x + panOffset.x + (i * gridStepPx)
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(sx, 0f),
                end = Offset(sx, height),
                strokeWidth = 1f
            )
        }

        for (j in startY..endY) {
            val sy = center.y + panOffset.y + (j * gridStepPx)
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, sy),
                end = Offset(width, sy),
                strokeWidth = 1f
            )
        }

        // Draw AB Guidance Line & Parallel Tracks
        val abLine = guidanceState.abLine
        if (abLine != null) {
            val lineDirX = abLine.dx / abLine.lengthMeters
            val lineDirY = abLine.dy / abLine.lengthMeters

            // Normal vector pointing RIGHT of AB line
            val normX = lineDirY
            val normY = -lineDirX

            // Main AB Line (extended far)
            val extM = 500.0
            val startUtm = UtmPoint(abLine.pointA.x - lineDirX * extM, abLine.pointA.y - lineDirY * extM)
            val endUtm = UtmPoint(abLine.pointB.x + lineDirX * extM, abLine.pointB.y + lineDirY * extM)

            val pStart = utmToScreen(startUtm)
            val pEnd = utmToScreen(endUtm)

            // Draw parallel track lines
            val passes = 10
            val spacing = if (trackWidthMeters > 0.5) trackWidthMeters else 2.0
            for (p in -passes..passes) {
                if (p == 0) continue
                val offsetM = p * spacing
                val p1 = UtmPoint(startUtm.x + normX * offsetM, startUtm.y + normY * offsetM)
                val p2 = UtmPoint(endUtm.x + normX * offsetM, endUtm.y + normY * offsetM)
                drawLine(
                    color = Color.Cyan.copy(alpha = 0.35f),
                    start = utmToScreen(p1),
                    end = utmToScreen(p2),
                    strokeWidth = 1.5f
                )
            }

            // Draw main AB line
            drawLine(
                color = if (guidanceState.isAutoSteerEngaged) Color(0xFF00E676) else Color(0xFF29B6F6),
                start = pStart,
                end = pEnd,
                strokeWidth = 4f
            )
        }

        // Draw Point A
        guidanceState.pointA?.let { pA ->
            val screenA = utmToScreen(pA)
            drawCircle(color = Color.Red, radius = 8f, center = screenA)
            drawCircle(color = Color.White, radius = 4f, center = screenA)
        }

        // Draw Point B
        guidanceState.pointB?.let { pB ->
            val screenB = utmToScreen(pB)
            drawCircle(color = Color(0xFF00C853), radius = 8f, center = screenB)
            drawCircle(color = Color.White, radius = 4f, center = screenB)
        }

        // Draw Tractor Icon at Ground Rear Axle position
        val tractorScreenPos = utmToScreen(currentAxleUtm)

        withTransform({
            translate(tractorScreenPos.x, tractorScreenPos.y)
            rotate(headingDegrees.toFloat(), Offset.Zero)
        }) {
            // Draw tractor body rectangle (width 18px, length 36px)
            val bodyWidth = 18f
            val bodyLength = 36f

            // Tractor rear axle at center (0,0)
            drawRect(
                color = if (guidanceState.isAutoSteerEngaged) Color(0xFF00E676) else Color(0xFFFF9800),
                topLeft = Offset(-bodyWidth / 2f, -bodyLength + 8f),
                size = androidx.compose.ui.geometry.Size(bodyWidth, bodyLength),
                style = Stroke(width = 3f)
            )

            // Front wheels with steering angle rotation
            val steerAngle = guidanceState.currentSteeringAngleDegrees.toFloat()
            val frontAxleY = -bodyLength + 12f

            // Left Front Wheel
            withTransform({
                translate(-bodyWidth / 2f, frontAxleY)
                rotate(steerAngle, Offset.Zero)
            }) {
                drawLine(Color.Black, Offset(0f, -6f), Offset(0f, 6f), strokeWidth = 4f)
            }

            // Right Front Wheel
            withTransform({
                translate(bodyWidth / 2f, frontAxleY)
                rotate(steerAngle, Offset.Zero)
            }) {
                drawLine(Color.Black, Offset(0f, -6f), Offset(0f, 6f), strokeWidth = 4f)
            }

            // Direction arrow triangle pointing forward (-Y)
            val arrowPath = Path().apply {
                moveTo(0f, -bodyLength - 6f)
                lineTo(-6f, -bodyLength + 4f)
                lineTo(6f, -bodyLength + 4f)
                close()
            }
            drawPath(arrowPath, color = Color.Red)
        }
    }
}

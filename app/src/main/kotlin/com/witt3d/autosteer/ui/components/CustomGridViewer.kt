package com.witt3d.autosteer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun CustomGridViewer() {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    offset += pan
                }
            }
    ) {
        val width = size.width
        val height = size.height

        withTransform({
            translate(offset.x, offset.y)
            scale(scale, scale, Offset.Zero)
        }) {
            // Draw Grid Lines
            val startX = -offset.x / scale
            val startY = -offset.y / scale
            val endX = (width - offset.x) / scale
            val endY = (height - offset.y) / scale

            // Vertical lines
            var x = (startX / 50f).toInt() * 50f
            while (x < endX) {
                drawLine(
                    color = Color.LightGray,
                    start = Offset(x, startY),
                    end = Offset(x, endY),
                    strokeWidth = 1f / scale
                )
                x += 50f
            }

            // Horizontal lines
            var y = (startY / 50f).toInt() * 50f
            while (y < endY) {
                drawLine(
                    color = Color.LightGray,
                    start = Offset(startX, y),
                    end = Offset(endX, y),
                    strokeWidth = 1f / scale
                )
                y += 50f
            }

            // Origin marker (center of the world)
            drawCircle(Color.Red, radius = 5f / scale, center = Offset.Zero)
        }
    }
}

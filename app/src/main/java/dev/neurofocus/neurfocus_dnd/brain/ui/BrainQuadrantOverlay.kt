package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNegative
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroPositive
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue

/**
 * Dashed quadrant grid, top-left highlight, and status dots — matches brain-mapping mock.
 */
@Composable
fun BrainQuadrantOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val outline = BrainGeometry.outlinePath(size)
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

        clipPath(outline) {
            drawRect(
                color = NeuroSkyBlue.copy(alpha = 0.38f),
                topLeft = Offset(0f, 0f),
                size = Size(cx, cy),
            )
            drawLine(
                color = NeuroSkyBlue.copy(alpha = 0.85f),
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dash,
            )
            drawLine(
                color = NeuroSkyBlue.copy(alpha = 0.85f),
                start = Offset(cx, 0f),
                end = Offset(cx, size.height),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dash,
            )
        }

        val dotR = size.minDimension * 0.012f
        val dots = listOf(
            Offset(size.width * 0.38f, size.height * 0.22f) to NeuroPositive,
            Offset(size.width * 0.78f, size.height * 0.42f) to NeuroNegative,
            Offset(size.width * 0.22f, size.height * 0.68f) to NeuroPositive,
        )
        dots.forEach { (o, c) ->
            drawCircle(color = c, radius = dotR, center = o)
            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = dotR * 0.35f, center = o)
        }
    }
}

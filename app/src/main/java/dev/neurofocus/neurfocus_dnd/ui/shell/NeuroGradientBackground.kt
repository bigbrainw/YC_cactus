package dev.neurofocus.neurfocus_dnd.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroGradientMid
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroGradientTop
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSurfaceWhite

@Composable
fun NeuroGradientBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NeuroGradientTop,
                        NeuroGradientMid,
                        NeuroSurfaceWhite,
                    ),
                ),
            ),
    )
}

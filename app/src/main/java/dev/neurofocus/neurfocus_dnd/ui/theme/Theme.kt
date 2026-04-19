package dev.neurofocus.neurfocus_dnd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeuroLightScheme = lightColorScheme(
    primary = NeuroNavy,
    onPrimary = Color.White,
    primaryContainer = NeuroSkyBlue,
    onPrimaryContainer = NeuroNavy,
    secondary = NeuroSkyBlue,
    onSecondary = NeuroNavy,
    secondaryContainer = NeuroCream,
    onSecondaryContainer = NeuroNavy,
    tertiary = NeuroCream,
    onTertiary = NeuroNavy,
    background = NeuroGradientTop,
    onBackground = NeuroTextPrimary,
    surface = NeuroSurfaceWhite,
    onSurface = NeuroTextPrimary,
    surfaceVariant = NeuroSkyBlue.copy(alpha = 0.35f),
    onSurfaceVariant = NeuroTextSecondary,
    outline = NeuroTextSecondary.copy(alpha = 0.35f),
    outlineVariant = NeuroSkyBlue.copy(alpha = 0.5f),
)

@Composable
fun NeurfocusdndTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = NeuroLightScheme,
        typography = Typography,
        content = content,
    )
}

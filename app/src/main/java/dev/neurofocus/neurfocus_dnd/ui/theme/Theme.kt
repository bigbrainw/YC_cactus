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
    secondary = NeuroNavy,
    onSecondary = Color.White,
    // selected-tab indicator pill
    secondaryContainer = NeuroSkyBlue,
    onSecondaryContainer = NeuroNavy,
    tertiary = NeuroCream,
    onTertiary = NeuroNavy,
    background = NeuroGradientTop,
    onBackground = NeuroTextPrimary,
    surface = NeuroSurfaceWhite,
    onSurface = NeuroTextPrimary,
    // surfaceContainer drives NavigationBar background in Material3
    surfaceContainer = NeuroNavBar,
    surfaceVariant = NeuroSkyBlue.copy(alpha = 0.35f),
    // onSurfaceVariant = unselected icon + label color
    onSurfaceVariant = NeuroSkyBlue,
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

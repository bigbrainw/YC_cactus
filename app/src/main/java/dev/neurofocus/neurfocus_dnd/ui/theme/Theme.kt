package dev.neurofocus.neurfocus_dnd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val NeuroLightScheme = lightColorScheme(
    // ── Primary actions & buttons ──────────────────────────────────────────
    primary             = NeuroNavy,
    onPrimary           = NeuroSurfaceWhite,
    primaryContainer    = NeuroSkyBlue,
    onPrimaryContainer  = NeuroNavy,

    // ── Secondary / nav-tab indicator ─────────────────────────────────────
    secondary            = NeuroNavy,
    onSecondary          = NeuroSurfaceWhite,
    secondaryContainer   = NeuroSkyBlue,        // selected-tab indicator pill
    onSecondaryContainer = NeuroNavy,           // icon inside selected pill

    // ── Tertiary accent ───────────────────────────────────────────────────
    tertiary    = NeuroCream,
    onTertiary  = NeuroNavy,

    // ── Backgrounds ───────────────────────────────────────────────────────
    background   = NeuroGradientTop,
    onBackground = NeuroTextPrimary,

    // ── Surfaces ──────────────────────────────────────────────────────────
    surface             = NeuroSurfaceWhite,
    onSurface           = NeuroTextPrimary,         // body text, stats numbers
    surfaceContainer    = NeuroNavBar,              // NavigationBar background
    surfaceVariant      = NeuroSkyBlue.copy(alpha = 0.25f),
    onSurfaceVariant    = NeuroTextSecondary,       // subtitles, captions, unselected labels

    // ── Outlines & dividers ───────────────────────────────────────────────
    outline        = NeuroTextSecondary.copy(alpha = 0.35f),
    outlineVariant = NeuroSkyBlue.copy(alpha = 0.50f),
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

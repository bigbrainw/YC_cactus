package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Color tokens for the brain visualization.
 *
 * Two factories cover the full design brief:
 *   - [cool]  — high-contrast, alert mood (good battery / high focus)
 *   - [warm]  — dim, "Night Shift" rest mood (low battery / fatigue)
 *
 * Held outside [androidx.compose.material3.MaterialTheme] on purpose:
 * the brain visualization needs controlled, brand-consistent colors that
 * do not shift with dynamic-color wallpaper sampling.
 */
@Immutable
data class BrainPalette(
    val outline: Color,
    val regionIdle: Color,
    val regionActive: Color,
    val electrode: Color,
    val electrodeGlow: Color,
    val highlight: Color,
    val vignette: Color,
) {
    companion object {
        fun cool(): BrainPalette = BrainPalette(
            outline = Color(0xFF111111),
            regionIdle = Color(0xFFF0F0F0),
            regionActive = Color(0xFF007AFF),
            electrode = Color(0xFF000000),
            electrodeGlow = Color(0x66007AFF),
            highlight = Color(0xBBFFFFFF),
            vignette = Color(0x44000000),
        )

        fun warm(): BrainPalette = BrainPalette(
            outline = Color(0xFF221100),
            regionIdle = Color(0xFFFDF5E6),
            regionActive = Color(0xFFFF9500),
            electrode = Color(0xFF8B4513),
            electrodeGlow = Color(0x66FF9500),
            highlight = Color(0x99FFFFFF),
            vignette = Color(0x55442200),
        )
    }
}

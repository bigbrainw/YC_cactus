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
            outline = Color(0xFF1F2933),
            regionIdle = Color(0xFFEEF1F4),
            regionActive = Color(0xFF2563EB),
            electrode = Color(0xFF1D4ED8),
            electrodeGlow = Color(0x553B82F6),
            highlight = Color(0x99FFFFFF),
            vignette = Color(0x33000814),
        )

        fun warm(): BrainPalette = BrainPalette(
            outline = Color(0xFF3B2C1F),
            regionIdle = Color(0xFFF4ECDF),
            regionActive = Color(0xFFE38F2D),
            electrode = Color(0xFFC76E1F),
            electrodeGlow = Color(0x55EA9E48),
            highlight = Color(0x77FFFFFF),
            vignette = Color(0x33180A00),
        )

        /** Choose palette by battery — Night Shift mood under 25%. */
        fun forBattery(percent: Int): BrainPalette =
            if (percent < WARM_THRESHOLD_PERCENT) warm() else cool()

        private const val WARM_THRESHOLD_PERCENT = 25
    }
}

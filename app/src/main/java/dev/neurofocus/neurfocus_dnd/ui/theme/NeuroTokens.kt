package dev.neurofocus.neurfocus_dnd.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Single source for spacing, radii, and shell dimensions so screens stay
 * consistent and easy to tune without hunting literals.
 */
object NeuroTokens {
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 16.dp
    val spaceLg = 20.dp

    val cornerIcon = 18.dp
    val cornerCard = 28.dp
    val cornerPill = 22.dp
    val cornerTooltip = 20.dp

    val topBarIconSize = 48.dp
    val topBarHorizontalPadding = spaceLg
    val topBarVerticalPadding = 12.dp
    val topBarIconSpacing = 10.dp
    val topBarIconShadow = 6.dp

    val glassCardPadding = spaceLg
    val glassCardElevation = 10.dp

    val shellContentHorizontal = spaceXs
    val contentAboveFloatingNav = 88.dp

    const val brainAspectHeightRatio = 0.85f
}

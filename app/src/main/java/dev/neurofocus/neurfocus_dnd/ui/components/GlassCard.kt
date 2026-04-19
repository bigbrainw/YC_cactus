package dev.neurofocus.neurfocus_dnd.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSurfaceWhite
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroTokens

/**
 * Rounded “glass” surface. Content is laid out in a [Column] so multiple
 * composables (title, rows, etc.) stack vertically — a plain [Box] would
 * overlap them at the same position.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = NeuroSurfaceWhite,
    contentPadding: Dp = NeuroTokens.glassCardPadding,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(NeuroTokens.cornerCard)
    Surface(
        modifier = modifier
            .shadow(
                elevation = NeuroTokens.glassCardElevation,
                shape = shape,
                ambientColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                spotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            ),
        shape = shape,
        color = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
        ) {
            content()
        }
    }
}

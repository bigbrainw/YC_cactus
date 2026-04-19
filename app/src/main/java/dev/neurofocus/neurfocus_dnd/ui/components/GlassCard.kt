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
import androidx.compose.ui.unit.dp
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSurfaceWhite

/**
 * Rounded “glass” surface. Content is laid out in a [Column] so multiple
 * composables (title, rows, etc.) stack vertically — a plain [Box] would
 * overlap them at the same position.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = NeuroSurfaceWhite,
    contentPadding: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
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

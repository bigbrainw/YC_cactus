package dev.neurofocus.neurfocus_dnd.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavBar
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSurfaceWhite

data class NeuroNavDestination(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun NeuroFloatingNav(
    destinations: List<NeuroNavDestination>,
    selectedIndex: Int,
    onDestinationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(32.dp),
                ambientColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                spotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            )
            .clip(RoundedCornerShape(32.dp))
            .background(NeuroNavBar.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEachIndexed { index, dest ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .semantics {
                        contentDescription = dest.label
                    }
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onDestinationSelected(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(NeuroSurfaceWhite, CircleShape),
                    )
                }
                Icon(
                    imageVector = dest.icon,
                    contentDescription = null,
                    tint = if (selected) NeuroNavBar else NeuroSurfaceWhite.copy(alpha = 0.55f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

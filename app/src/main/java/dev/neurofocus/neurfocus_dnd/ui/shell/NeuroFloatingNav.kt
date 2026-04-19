package dev.neurofocus.neurfocus_dnd.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
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
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroTokens

data class NeuroNavDestination(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun NeuroFloatingNav(
    destinations: List<NeuroNavDestination>,
    /** Which tab is active; `null` = none highlighted (e.g. settings opened from profile). */
    selectedIndex: Int?,
    onDestinationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .wrapContentWidth()
            .padding(horizontal = NeuroTokens.spaceMd, vertical = NeuroTokens.navBarOuterVertical)
            .shadow(
                elevation = NeuroTokens.navBarShadow,
                shape = RoundedCornerShape(NeuroTokens.cornerNavBar),
                ambientColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                spotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            )
            .clip(RoundedCornerShape(NeuroTokens.cornerNavBar))
            .background(NeuroNavBar.copy(alpha = 0.92f))
            .padding(
                horizontal = NeuroTokens.navBarInnerHorizontal,
                vertical = NeuroTokens.navBarInnerVertical,
            ),
        horizontalArrangement = Arrangement.spacedBy(NeuroTokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEachIndexed { index, dest ->
            val selected = selectedIndex != null && index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(NeuroTokens.navItemSlot)
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
                            .size(NeuroTokens.navItemInnerSelected)
                            .background(NeuroSurfaceWhite, CircleShape),
                    )
                }
                Icon(
                    imageVector = dest.icon,
                    contentDescription = null,
                    tint = if (selected) NeuroNavBar else NeuroSurfaceWhite.copy(alpha = 0.55f),
                    modifier = Modifier.size(NeuroTokens.navIconSize),
                )
            }
        }
    }
}

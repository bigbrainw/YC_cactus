package dev.neurofocus.neurfocus_dnd.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSurfaceWhite
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroTokens

@Composable
fun NeuroTopBar(
    modifier: Modifier = Modifier,
    onBleDevicesClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    isConnected: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = NeuroTokens.topBarHorizontalPadding,
                vertical = NeuroTokens.topBarVerticalPadding,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundedSquareIconButton(
            onClick = onBleDevicesClick,
            accessibilityLabel = "Bluetooth devices",
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Outlined.BluetoothConnected else Icons.AutoMirrored.Outlined.BluetoothSearching,
                contentDescription = null,
                tint = if (isConnected) NeuroSkyBlue else MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(NeuroTokens.topBarIconSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundedSquareIconButton(
                onClick = onNotificationsClick,
                accessibilityLabel = "Notifications",
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            RoundedSquareIconButton(
                onClick = onProfileClick,
                accessibilityLabel = "Profile",
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun RoundedSquareIconButton(
    onClick: () -> Unit,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = NeuroTokens.topBarIconShadow,
                shape = RoundedCornerShape(NeuroTokens.cornerIcon),
                ambientColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                spotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            )
            .size(NeuroTokens.topBarIconSize)
            .background(
                color = NeuroSurfaceWhite,
                shape = RoundedCornerShape(NeuroTokens.cornerIcon),
            ),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(NeuroTokens.topBarIconSize)
                .semantics { contentDescription = accessibilityLabel },
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            content()
        }
    }
}

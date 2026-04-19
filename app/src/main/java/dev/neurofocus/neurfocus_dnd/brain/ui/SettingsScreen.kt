package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
import dev.neurofocus.neurfocus_dnd.ui.components.GlassCard
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavy
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroTokens
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

@Composable
fun SettingsScreen(
    profile: UserProfile,
    onResetProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = NeuroTokens.spaceMd, vertical = NeuroTokens.spaceSm)
            .padding(bottom = NeuroTokens.contentAboveFloatingNav),
        verticalArrangement = Arrangement.spacedBy(NeuroTokens.spaceMd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "Account and device preferences.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NeuroNavy,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onResetProfile,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeuroSkyBlue.copy(alpha = 0.55f),
                contentColor = NeuroNavy,
            ),
        ) {
            Text(
                text = "Reset to default name",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SettingsScreenPreview() {
    NeurfocusdndTheme {
        SettingsScreen(
            profile = UserProfile("Ada", "Lovelace"),
            onResetProfile = {},
        )
    }
}

package dev.neurofocus.neurfocus_dnd.brain.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neurofocus.neurfocus_dnd.NeuroApp
import dev.neurofocus.neurfocus_dnd.R
import dev.neurofocus.neurfocus_dnd.cactus.CactusModelPrefs
import dev.neurofocus.neurfocus_dnd.cactus.CactusModelRepository
import dev.neurofocus.neurfocus_dnd.cactus.DownloadLogStore
import dev.neurofocus.neurfocus_dnd.cactus.DownloadUiState
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
    cactusSettingsViewModel: CactusSettingsViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val neuro = context.applicationContext as NeuroApp
    val repo = neuro.cactusModelRepository
    val prefs = remember(context) { CactusModelPrefs(context.applicationContext) }

    var urlField by rememberSaveable {
        mutableStateOf(prefs.modelDownloadUrl.orEmpty())
    }

    val downloadState by cactusSettingsViewModel.downloadState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        GlassCard(containerColor = NeuroSkyBlue.copy(alpha = 0.25f)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ON-DEVICE MODEL (CACTUS)",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.cactus_model_url_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(context.getString(R.string.cactus_hf_org_url)),
                            ),
                        )
                    }) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Open Hugging Face")
                    }
                    Text(
                        text = "Cactus weights on Hugging Face",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeuroNavy,
                    )
                }
                OutlinedTextField(
                    value = urlField,
                    onValueChange = { urlField = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Direct model file URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            prefs.modelDownloadUrl = urlField.trim().ifEmpty { null }
                        },
                    ),
                )
                val resolved = repo.resolvedModelFile()
                Text(
                    text = if (resolved != null) {
                        "Local file: ${resolved.absolutePath}\n${resolved.length()} bytes"
                    } else {
                        "No model file yet (expected name: ${CactusModelRepository.MODEL_FILENAME})"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NeuroNavy,
                )
                when (val st = downloadState) {
                    is DownloadUiState.Downloading -> {
                        val total = st.totalBytes
                        if (total != null && total > 0) {
                            LinearProgressIndicator(
                                progress = (st.bytesReceived.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text = "Downloading… ${st.bytesReceived} B",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeuroNavy,
                        )
                    }
                    is DownloadUiState.Done -> {
                        Text(
                            text = "Ready: ${st.path}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeuroNavy,
                        )
                    }
                    is DownloadUiState.Error -> {
                        Text(
                            text = "Error: ${st.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    DownloadUiState.Idle -> {}
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = {
                            val u = urlField.trim()
                            prefs.modelDownloadUrl = u.ifEmpty { null }
                            if (u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true)) {
                                DownloadLogStore.append("User started download")
                                cactusSettingsViewModel.startDownload(u)
                            } else {
                                DownloadLogStore.append("Invalid URL (must start with http/https)")
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeuroSkyBlue.copy(alpha = 0.65f),
                            contentColor = NeuroNavy,
                        ),
                    ) { Text("Download / replace") }
                    Button(
                        onClick = { cactusSettingsViewModel.deleteModel() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) { Text("Delete local") }
                }
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
        Text("Settings preview — run app for full Cactus card + ViewModel.")
    }
}

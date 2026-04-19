package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.cactus.InsightUiState
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
import dev.neurofocus.neurfocus_dnd.ui.components.GlassCard
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavy
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroTokens
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

@Composable
fun BrainScreen(
    profile: UserProfile,
    modifier: Modifier = Modifier,
    viewModel: BrainViewModel = rememberBrainViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val insight by viewModel.insight.collectAsStateWithLifecycle()
    BrainScreenContent(
        state = state,
        profile = profile,
        insight = insight,
        modifier = modifier,
    )
}

@Composable
private fun BrainScreenContent(
    state: BrainState,
    profile: UserProfile,
    insight: InsightUiState,
    modifier: Modifier = Modifier,
) {
    val palette = BrainPalette.pro()

    val scroll = rememberScrollState()
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = NeuroTokens.spaceLg)
                .padding(top = NeuroTokens.spaceSm, bottom = NeuroTokens.contentAboveFloatingNav),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NeuroTokens.spaceLg),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Brain mapping",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Hi, ${profile.firstName} · ${state.statusLine()}",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnalyticalInsightCard(insight = insight)

            Spacer(modifier = Modifier.weight(1f))

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val brainWidth = maxWidth * 0.95f
                val brainHeight = brainWidth * NeuroTokens.brainAspectHeightRatio
                val constrainedHeight = minOf(brainHeight, 400.dp)
                val finalWidth = constrainedHeight / NeuroTokens.brainAspectHeightRatio

                Box(
                    modifier = Modifier
                        .width(finalWidth)
                        .height(constrainedHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    BrainCanvas(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        palette = palette,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state is BrainState.Live) {
                BrainMeter(state = state)
            }

            AdviceLine(state = state)

            Spacer(Modifier.height(NeuroTokens.spaceSm))
        }
    }
}

@Composable
private fun AnalyticalInsightCard(insight: InsightUiState) {
    GlassCard(containerColor = NeuroSkyBlue.copy(alpha = 0.35f), contentPadding = NeuroTokens.spaceMd) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Analytical insight",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (insight.loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(28.dp)
                        .width(28.dp),
                    color = NeuroSkyBlue,
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = insight.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = NeuroNavy,
                textAlign = TextAlign.Start,
            )
            insight.error?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (insight.usedNativeModel) {
                Text(
                    text = "On-device Cactus engine active (native handle open).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun BrainState.statusLine(): String = when (this) {
    BrainState.Idle -> "READY TO CONNECT"
    BrainState.Searching -> "SCANNING FOR HEADBAND..."
    BrainState.Connecting -> "ESTABLISHING LINK..."
    is BrainState.Live -> "CONNECTED  •  ${debugStats.effectiveRateSps.toInt()}SPS  ${debugStats.transportMode.uppercase()}"
    is BrainState.Reconnecting -> "RECONNECTING (attempt $attempt)..."
    is BrainState.Error -> "DISCONNECTED  •  $message"
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun BrainScreenLivePreview() {
    NeurfocusdndTheme {
        BrainScreenContent(
            state = BrainState.Live(
                focus = dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore(0.65f),
                bandPowers = mapOf(
                    EegBand.Delta to 0.30f,
                    EegBand.Theta to 0.25f,
                    EegBand.Alpha to 0.55f,
                    EegBand.LowBeta to 0.40f,
                    EegBand.HighBeta to 0.15f,
                    EegBand.Gamma to 0.35f,
                ),
                rawEnvelopeUv = 60f,
                debugStats = dev.neurofocus.neurfocus_dnd.brain.domain.EegDebugStats(
                    totalSamples = 5000, effectiveRateSps = 253f,
                    bleNotifyCount = 5000, seqGaps = 0, ignoredPayloads = 0,
                    lastRawCount = 127000, lastMicrovoltsCorrected = 48f,
                    windowRmsUv = 52f, windowSamples = 1265,
                    transportMode = "ascii", lastNotifyMs = System.currentTimeMillis(),
                ),
            ),
            profile = UserProfile("Ada", "Lovelace"),
            insight = InsightUiState(
                text = "Frontal-motor band energy is elevated versus alpha. Dominant relative band: γ Gamma.",
                loading = false,
                error = null,
                usedNativeModel = false,
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun BrainScreenSearchingPreview() {
    NeurfocusdndTheme {
        BrainScreenContent(
            state = BrainState.Searching,
            profile = UserProfile("Ada", "Lovelace"),
            insight = InsightUiState.Idle,
        )
    }
}

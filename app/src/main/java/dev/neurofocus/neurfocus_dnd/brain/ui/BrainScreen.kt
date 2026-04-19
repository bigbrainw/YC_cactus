package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
import dev.neurofocus.neurfocus_dnd.ui.components.GlassCard
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavy
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSurfaceWhite
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroTokens
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

@Composable
fun BrainScreen(
    profile: UserProfile,
    modifier: Modifier = Modifier,
    viewModel: BrainViewModel = rememberBrainViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BrainScreenContent(state = state, profile = profile, modifier = modifier)
}

private enum class MappingMode { Cognitive, Emotional }

@Composable
private fun BrainScreenContent(
    state: BrainState,
    profile: UserProfile,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(MappingMode.Cognitive) }
    var advancedResearch by rememberSaveable { mutableStateOf(false) }

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

            SegmentedModeControl(
                mode = mode,
                onModeChange = { mode = it },
            )

            Spacer(modifier = Modifier.weight(1f))

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val brainWidth = maxWidth * 0.95f
                val brainHeight = brainWidth * NeuroTokens.brainAspectHeightRatio
                val constrainedHeight = minOf(brainHeight, 400.dp) // Larger brain
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
                    TooltipBubble(
                        text = when (mode) {
                            MappingMode.Cognitive -> "Analytical thinking"
                            MappingMode.Emotional -> "Emotional balance"
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-8).dp, y = (-4).dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state is BrainState.Live) {
                BrainMeter(state = state)
            }

            Column(verticalArrangement = Arrangement.spacedBy(NeuroTokens.spaceMd)) {
                AdvancedResearchRow(
                    checked = advancedResearch,
                    onCheckedChange = { advancedResearch = it },
                )

                AdviceLine(state = state)
            }
            Spacer(Modifier.height(NeuroTokens.spaceSm))
        }
    }
}

@Composable
private fun SegmentedModeControl(
    mode: MappingMode,
    onModeChange: (MappingMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = NeuroSurfaceWhite.copy(alpha = 0.85f),
                shape = RoundedCornerShape(NeuroTokens.cornerPill),
            )
            .padding(NeuroTokens.spaceXs),
        horizontalArrangement = Arrangement.spacedBy(NeuroTokens.spaceXs),
    ) {
        MappingMode.entries.forEach { m ->
            val selected = m == mode
            val label = when (m) {
                MappingMode.Cognitive -> "Cognitive enhancement"
                MappingMode.Emotional -> "Emotional regulation"
            }
            val interaction = remember(m) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .shadow(
                        elevation = if (selected) NeuroTokens.topBarIconShadow else 0.dp,
                        shape = RoundedCornerShape(NeuroTokens.cornerIcon),
                    )
                    .background(
                        color = if (selected) NeuroSkyBlue.copy(alpha = 0.55f) else NeuroSurfaceWhite.copy(alpha = 0.01f),
                        shape = RoundedCornerShape(NeuroTokens.cornerIcon),
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onModeChange(m) },
                    )
                    .padding(
                        vertical = NeuroTokens.spaceMd - NeuroTokens.spaceXs,
                        horizontal = NeuroTokens.spaceSm,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    color = NeuroNavy,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TooltipBubble(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .shadow(NeuroTokens.spaceXs, RoundedCornerShape(NeuroTokens.cornerTooltip))
            .background(NeuroSurfaceWhite, RoundedCornerShape(NeuroTokens.cornerTooltip))
            .padding(horizontal = NeuroTokens.spaceMd, vertical = NeuroTokens.spaceXs + NeuroTokens.spaceXs),
        style = MaterialTheme.typography.labelLarge,
        color = NeuroNavy,
    )
}

@Composable
private fun AdvancedResearchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    GlassCard(containerColor = NeuroSkyBlue.copy(alpha = 0.4f), contentPadding = NeuroTokens.spaceMd) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Advanced research",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = NeuroNavy,
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeuroSurfaceWhite,
                    checkedTrackColor = NeuroNavy.copy(alpha = 0.55f),
                    uncheckedThumbColor = NeuroSurfaceWhite,
                    uncheckedTrackColor = NeuroNavy.copy(alpha = 0.2f),
                ),
            )
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
                    EegBand.Delta    to 0.30f,
                    EegBand.Theta    to 0.25f,
                    EegBand.Alpha    to 0.55f,
                    EegBand.LowBeta  to 0.40f,
                    EegBand.HighBeta to 0.15f,
                    EegBand.Gamma    to 0.35f,
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
        )
    }
}

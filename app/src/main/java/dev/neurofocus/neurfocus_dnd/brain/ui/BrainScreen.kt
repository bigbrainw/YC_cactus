package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.neurofocus.neurfocus_dnd.brain.domain.BatteryPercent
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.ElectrodeSite
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
import dev.neurofocus.neurfocus_dnd.ui.components.GlassCard
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavy
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSurfaceWhite
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

private const val BRAIN_ASPECT = 0.85f

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

    val palette = when (mode) {
        MappingMode.Cognitive -> BrainPalette.cool()
        MappingMode.Emotional -> BrainPalette.warm()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Brain mapping",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Hi, ${profile.firstName} · ${state.statusLine()}",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SegmentedModeControl(
            mode = mode,
            onModeChange = { mode = it },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / BRAIN_ASPECT),
            contentAlignment = Alignment.Center,
        ) {
            BrainCanvas(
                state = state,
                modifier = Modifier.fillMaxSize(),
                palette = palette,
            )
            BrainQuadrantOverlay(modifier = Modifier.fillMaxSize())
            TooltipBubble(
                text = when (mode) {
                    MappingMode.Cognitive -> "Analytical thinking"
                    MappingMode.Emotional -> "Emotional balance"
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 12.dp, y = 8.dp),
            )
        }

        AdvancedResearchRow(
            checked = advancedResearch,
            onCheckedChange = { advancedResearch = it },
        )

        AdviceLine(state = state)
        Spacer(Modifier.height(8.dp))
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
                shape = RoundedCornerShape(22.dp),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                        elevation = if (selected) 6.dp else 0.dp,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .background(
                        color = if (selected) NeuroSkyBlue.copy(alpha = 0.55f) else NeuroSurfaceWhite.copy(alpha = 0.01f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onModeChange(m) },
                    )
                    .padding(vertical = 12.dp, horizontal = 8.dp),
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
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .background(NeuroSurfaceWhite, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = NeuroNavy,
    )
}

@Composable
private fun AdvancedResearchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    GlassCard(containerColor = NeuroSkyBlue.copy(alpha = 0.4f), contentPadding = 16.dp) {
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
    BrainState.Idle -> "IDLE"
    BrainState.Searching -> "SEARCHING…"
    BrainState.Connecting -> "CONNECTING…"
    is BrainState.Live -> "LIVE  •  ${electrodeSite.name.uppercase()}"
    is BrainState.Error -> "ERROR  •  $message"
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun BrainScreenLivePreview() {
    NeurfocusdndTheme {
        BrainScreenContent(
            state = BrainState.Live(
                battery = BatteryPercent(72),
                focus = FocusScore(0.65f),
                bandPowers = mapOf(
                    EegBand.Delta to 0.30f,
                    EegBand.Theta to 0.25f,
                    EegBand.Alpha to 0.55f,
                    EegBand.Beta to 0.55f,
                    EegBand.Gamma to 0.35f,
                ),
                rawEnvelope = 0.6f,
                electrodeSite = ElectrodeSite.Fp1,
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

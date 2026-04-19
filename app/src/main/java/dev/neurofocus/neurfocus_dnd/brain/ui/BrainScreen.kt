package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neurofocus.neurfocus_dnd.brain.domain.BatteryPercent
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.ElectrodeSite
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

/**
 * Top-level dashboard. Stateful container that pulls [BrainState] from
 * [BrainViewModel] and forwards it to the stateless [BrainScreenContent].
 */
@Composable
fun BrainScreen(
    modifier: Modifier = Modifier,
    viewModel: BrainViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BrainScreenContent(state = state, modifier = modifier)
}

@Composable
private fun BrainScreenContent(
    state: BrainState,
    modifier: Modifier = Modifier,
) {
    val palette = when (state) {
        is BrainState.Live -> BrainPalette.forBattery(state.battery.value)
        BrainState.Idle, BrainState.Searching, BrainState.Connecting,
        is BrainState.Error -> BrainPalette.cool()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Header(state = state)
        BrainCanvas(state = state, palette = palette)
    }
}

@Composable
private fun Header(state: BrainState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = state.headline(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
        )
        state.subline()?.let { subline ->
            Text(
                text = subline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

private fun BrainState.headline(): String = when (this) {
    BrainState.Idle -> "Idle"
    BrainState.Searching -> "Searching for device…"
    BrainState.Connecting -> "Connecting…"
    is BrainState.Live -> "Brain Battery ${battery.value}%"
    is BrainState.Error -> "Error"
}

private fun BrainState.subline(): String? = when (this) {
    is BrainState.Live -> "Focus ${(focus.value * 100).toInt()}%  •  ${electrodeSite.name}"
    is BrainState.Error -> message
    BrainState.Idle, BrainState.Searching, BrainState.Connecting -> null
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
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
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun BrainScreenSearchingPreview() {
    NeurfocusdndTheme {
        BrainScreenContent(state = BrainState.Searching)
    }
}

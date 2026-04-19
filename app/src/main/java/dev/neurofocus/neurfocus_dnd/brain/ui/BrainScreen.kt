package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neurofocus.neurfocus_dnd.brain.domain.BatteryPercent
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.ElectrodeSite
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

/**
 * Top-level dashboard. Stateful container that pulls [BrainState] from
 * [BrainViewModel] and forwards it to the stateless [BrainScreenContent].
 */
@Composable
fun BrainScreen(
    profile: UserProfile,
    modifier: Modifier = Modifier,
    viewModel: BrainViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BrainScreenContent(state = state, profile = profile, modifier = modifier)
}

@Composable
private fun BrainScreenContent(
    state: BrainState,
    profile: UserProfile,
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
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(state = state, firstName = profile.firstName)

        BrainCanvas(state = state, palette = palette)

        if (state is BrainState.Live) {
            BrainMeter(state = state)
        }

        AdviceLine(state = state)

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header(state: BrainState, firstName: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Hi, $firstName",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.statusLine(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
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

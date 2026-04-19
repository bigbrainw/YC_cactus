package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.neurofocus.neurfocus_dnd.brain.domain.BatteryPercent
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.ElectrodeSite
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

/**
 * Single sentence of "do this next" advice.
 *
 * v1: deterministic German-minimalist copy keyed to focus + battery.
 * v2 (later): replace [deriveAdvice] with a Gemma-backed generator. The
 * composable signature stays the same — UI never knows the source.
 */
@Composable
fun AdviceLine(
    state: BrainState,
    modifier: Modifier = Modifier,
) {
    val advice = deriveAdvice(state) ?: return
    Text(
        text = advice,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Light,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
    )
}

private fun deriveAdvice(state: BrainState): String? = when (state) {
    is BrainState.Live -> {
        val focus = state.focus.value
        val battery = state.battery.value
        when {
            battery < 20 -> "Reserves are low. Recover, do not push."
            focus >= 0.70f && battery > 50 -> "Peak focus. Begin your most demanding task now."
            focus >= 0.50f -> "Steady. Ship something difficult before it slips."
            focus >= 0.30f -> "Focus dipping. A short pause prevents a long one."
            else -> "Three minutes of stillness. Then return."
        }
    }
    BrainState.Idle, BrainState.Searching, BrainState.Connecting -> null
    is BrainState.Error -> null
}

@Preview(showBackground = true, widthDp = 360, heightDp = 80)
@Composable
private fun AdviceLinePreview() {
    NeurfocusdndTheme {
        AdviceLine(
            state = BrainState.Live(
                battery = BatteryPercent(72),
                focus = FocusScore(0.75f),
                bandPowers = mapOf(EegBand.Beta to 0.6f),
                rawEnvelope = 0.6f,
                electrodeSite = ElectrodeSite.Fp1,
            ),
        )
    }
}

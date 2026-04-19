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
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.EegDebugStats
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

/**
 * Single sentence of "do this next" advice.
 *
 * v1: deterministic physiological copy keyed to focus heuristic.
 * Battery: firmware doesn't report it, so advice never references it.
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
        when {
            focus >= 0.70f -> "Peak focus. Begin your most demanding task now."
            focus >= 0.50f -> "Steady. Ship something difficult before it slips."
            focus >= 0.30f -> "Focus dipping. A short pause prevents a long one."
            else           -> "Three minutes of stillness. Then return."
        }
    }
    BrainState.Idle, BrainState.Searching, BrainState.Connecting -> null
    is BrainState.Reconnecting -> "Reconnecting to headband…"
    is BrainState.Error -> null
}

@Preview(showBackground = true, widthDp = 360, heightDp = 80)
@Composable
private fun AdviceLinePreview() {
    NeurfocusdndTheme {
        AdviceLine(
            state = BrainState.Live(
                focus = FocusScore(0.75f),
                bandPowers = mapOf(EegBand.LowBeta to 0.6f),
                rawEnvelopeUv = 60f,
                debugStats = EegDebugStats(
                    totalSamples = 1000, effectiveRateSps = 253f,
                    bleNotifyCount = 1000, seqGaps = 0, ignoredPayloads = 0,
                    lastRawCount = 127000, lastMicrovoltsCorrected = 48f,
                    windowRmsUv = 52f, windowSamples = 1265,
                    transportMode = "ascii", lastNotifyMs = System.currentTimeMillis(),
                ),
            ),
        )
    }
}

package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.EegDebugStats
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

/**
 * Braun ET66-inspired stats row.
 * Battery: firmware does not report it — shows "--" instead of a fake number.
 */
@Composable
fun BrainMeter(
    state: BrainState.Live,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Battery: show "--" because firmware does not transmit this
        val batteryDisplay = state.battery?.value ?: -1
        if (batteryDisplay >= 0) {
            Stat(label = "BATTERY", percent = batteryDisplay)
        } else {
            StatUnavailable(label = "BATTERY", reason = "N/A")
        }
        VerticalDivider(
            modifier = Modifier.height(48.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
        Stat(label = "FOCUS", percent = (state.focus.value * 100f).toInt())
    }
}

@Composable
private fun Stat(label: String, percent: Int) {
    val animatedFraction by animateFloatAsState(
        targetValue = (percent.coerceIn(0, 100)).toFloat() / 100f,
        animationSpec = tween(durationMillis = 400),
        label = "$label-bar",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(BAR_WIDTH)
                .height(BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun StatUnavailable(label: String, reason: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = reason,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
}

private val BAR_WIDTH = 72.dp
private val BAR_HEIGHT = 3.dp

@Preview(showBackground = true, widthDp = 360, heightDp = 140)
@Composable
private fun BrainMeterPreview() {
    NeurfocusdndTheme {
        BrainMeter(
            state = BrainState.Live(
                focus = FocusScore(0.65f),
                bandPowers = mapOf(EegBand.Alpha to 0.5f),
                rawEnvelopeUv = 50f,
                debugStats = EegDebugStats(
                    totalSamples = 1000, effectiveRateSps = 253f,
                    bleNotifyCount = 1000, seqGaps = 0, ignoredPayloads = 0,
                    lastRawCount = 127000, lastMicrovoltsCorrected = 48f,
                    windowRmsUv = 52f, windowSamples = 1265,
                    transportMode = "ascii", lastNotifyMs = System.currentTimeMillis(),
                ),
                battery = null,  // firmware does not report battery
            ),
        )
    }
}

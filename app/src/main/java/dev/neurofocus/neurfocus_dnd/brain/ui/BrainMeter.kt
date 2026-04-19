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
import dev.neurofocus.neurfocus_dnd.brain.domain.BatteryPercent
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.ElectrodeSite
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

/**
 * Braun ET66-inspired stats row.
 *
 * Two columns (Battery, Focus) — monospaced numerals, all-caps tracking-out
 * label, thin animated horizontal bar. No chrome, no shadows, no drop-fills.
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
        Stat(label = "BATTERY", percent = state.battery.value)
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

private val BAR_WIDTH = 72.dp
private val BAR_HEIGHT = 3.dp

@Preview(showBackground = true, widthDp = 360, heightDp = 140)
@Composable
private fun BrainMeterPreview() {
    NeurfocusdndTheme {
        BrainMeter(
            state = BrainState.Live(
                battery = BatteryPercent(72),
                focus = FocusScore(0.65f),
                bandPowers = mapOf(EegBand.Alpha to 0.5f),
                rawEnvelope = 0.5f,
                electrodeSite = ElectrodeSite.Fp1,
            ),
        )
    }
}

package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neurofocus.neurfocus_dnd.brain.data.ble.BleSpec
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
import dev.neurofocus.neurfocus_dnd.ui.components.GlassCard
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroCream
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavy
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNegative
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroPositive
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme
import kotlin.math.sin

@Composable
fun HomeScreen(
    profile: UserProfile,
    modifier: Modifier = Modifier,
    viewModel: BrainViewModel = rememberBrainViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreenContent(state = state, profile = profile, modifier = modifier)
}

@Composable
private fun HomeScreenContent(
    state: BrainState,
    profile: UserProfile,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Hi, ${profile.firstName}",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Activity overview",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = connectionStatusLine(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BrainActivityCard(state = state)

        EegSignalCard(state = state)
    }
}

@Composable
private fun BrainActivityCard(state: BrainState) {
    val live = state as? BrainState.Live
    val cognitive = live?.let { metricCognitive(it.bandPowers) } ?: 72
    val focus = live?.let { (it.focus.value * 100f).toInt() } ?: 65
    val creativity = live?.let { metricCreativity(it.bandPowers) } ?: 58
    val stress = live?.let { metricStress(it.bandPowers) } ?: 40

    GlassCard(containerColor = NeuroSkyBlue.copy(alpha = 0.55f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Brain Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NeuroNavy,
            )
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = "Menu",
                    tint = NeuroNavy.copy(alpha = 0.7f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        MetricRow(
            label = "Cognitive load",
            percent = cognitive,
            up = true,
        )
        MetricRow(
            label = "Focus",
            percent = focus,
            up = true,
        )
        MetricRow(
            label = "Creativity",
            percent = creativity,
            up = false,
        )
        MetricRow(
            label = "Stress",
            percent = stress,
            up = false,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Info",
                    tint = NeuroNavy.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    percent: Int,
    up: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = NeuroNavy.copy(alpha = 0.85f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = if (up) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = if (up) NeuroPositive else NeuroNegative,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NeuroNavy,
            )
        }
    }
}

@Composable
private fun EegSignalCard(state: BrainState) {
    val envelope = (state as? BrainState.Live)?.rawEnvelope ?: 0.45f
    GlassCard(containerColor = NeuroCream.copy(alpha = 0.85f)) {
        Text(
            text = "EEG signal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = NeuroNavy,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = NeuroNavy.copy(alpha = 0.9f)
                val grid = NeuroNavy.copy(alpha = 0.12f)
                val w = size.width
                val h = size.height
                val pad = 8.dp.toPx()
                drawLine(grid, Offset(pad, h * 0.25f), Offset(w - pad, h * 0.25f), strokeWidth = 1.dp.toPx())
                drawLine(grid, Offset(pad, h * 0.5f), Offset(w - pad, h * 0.5f), strokeWidth = 1.dp.toPx())
                drawLine(grid, Offset(pad, h * 0.75f), Offset(w - pad, h * 0.75f), strokeWidth = 1.dp.toPx())
                val path = Path()
                val steps = 48
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val x = pad + t * (w - pad * 2)
                    val wave =
                        sin(t * 12f + envelope * 6f) * 0.22f +
                            sin(t * 5f) * 0.12f
                    val y = h * 0.55f + wave * h * 0.35f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = stroke,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            Text(
                text = "2000",
                style = MaterialTheme.typography.labelSmall,
                color = NeuroNavy.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.TopStart),
            )
            Text(
                text = "0",
                style = MaterialTheme.typography.labelSmall,
                color = NeuroNavy.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

private fun metricCognitive(bands: Map<EegBand, Float>): Int {
    val beta = bands[EegBand.Beta] ?: 0f
    val gamma = bands[EegBand.Gamma] ?: 0f
    return ((beta * 0.55f + gamma * 0.45f) * 100f).toInt().coerceIn(0, 100)
}

private fun metricCreativity(bands: Map<EegBand, Float>): Int {
    val alpha = bands[EegBand.Alpha] ?: 0f
    val theta = bands[EegBand.Theta] ?: 0f
    return ((alpha * 0.45f + theta * 0.55f) * 100f).toInt().coerceIn(0, 100)
}

private fun connectionStatusLine(state: BrainState): String = when (state) {
    BrainState.Idle -> "Headband: idle"
    BrainState.Searching -> "Headband: scanning for ${BleSpec.DEVICE_NAME_PREFIX}…"
    BrainState.Connecting -> "Headband: connecting…"
    is BrainState.Live -> "Headband: live · receiving data"
    is BrainState.Error -> "Headband: ${state.message}"
}

private fun metricStress(bands: Map<EegBand, Float>): Int {
    val beta = bands[EegBand.Beta] ?: 0f
    val alpha = bands[EegBand.Alpha] ?: 0f
    val raw = beta * 0.7f + (1f - alpha) * 0.3f
    return (raw * 100f).toInt().coerceIn(0, 100)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun HomeScreenPreview() {
    NeurfocusdndTheme {
        HomeScreenContent(
            state = BrainState.Searching,
            profile = UserProfile("Ada", "Lovelace"),
        )
    }
}

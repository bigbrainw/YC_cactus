package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.neurofocus.neurfocus_dnd.brain.domain.BatteryPercent
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainRegion
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.ElectrodeSite
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * The hero brain visualization. Stateless render of [BrainState].
 *
 * Layers, back to front:
 *   1. Brain shape filled with idle tone
 *   2. Per-region radial-gradient glows, animated independently
 *   3. Central longitudinal fissure (midline groove)
 *   4. Top-left highlight + edge vignette (fake-3D depth)
 *   5. Electrode dot + glow, pulsing with raw signal envelope
 *   6. Brain outline stroke
 *
 * Animations run independently of EEG sample arrival — the data layer can
 * push state at any rate without dropping frames.
 */
@Composable
fun BrainCanvas(
    state: BrainState,
    modifier: Modifier = Modifier,
    palette: BrainPalette = BrainPalette.cool(),
) {
    val activations = animateRegionActivations(state)
    val envelope = animateElectrodeEnvelope(state)
    val electrodeSite = (state as? BrainState.Live)?.electrodeSite

    Canvas(
        modifier = modifier
            .aspectRatio(BRAIN_ASPECT_RATIO)
            .padding(8.dp),
    ) {
        val outline = BrainGeometry.outlinePath(size)

        drawBrainBase(outline, palette)
        drawRegionWashes(outline, activations, palette)
        drawCentralFissure(outline, palette)
        drawDepthShading(outline, palette)
        electrodeSite?.let { site ->
            drawElectrodeDot(site, envelope, palette)
        }
        drawOutlineStroke(outline, palette)
    }
}

@Composable
private fun animateRegionActivations(state: BrainState): Map<BrainRegion, Float> {
    val targets = state.toRegionActivations()
    val spec = tween<Float>(REGION_FADE_MS)

    val frontalL by animateFloatAsState(targets[BrainRegion.FrontalL] ?: 0f, spec, label = "FrontalL")
    val frontalR by animateFloatAsState(targets[BrainRegion.FrontalR] ?: 0f, spec, label = "FrontalR")
    val parietalL by animateFloatAsState(targets[BrainRegion.ParietalL] ?: 0f, spec, label = "ParietalL")
    val parietalR by animateFloatAsState(targets[BrainRegion.ParietalR] ?: 0f, spec, label = "ParietalR")
    val temporalL by animateFloatAsState(targets[BrainRegion.TemporalL] ?: 0f, spec, label = "TemporalL")
    val temporalR by animateFloatAsState(targets[BrainRegion.TemporalR] ?: 0f, spec, label = "TemporalR")
    val occipitalL by animateFloatAsState(targets[BrainRegion.OccipitalL] ?: 0f, spec, label = "OccipitalL")
    val occipitalR by animateFloatAsState(targets[BrainRegion.OccipitalR] ?: 0f, spec, label = "OccipitalR")

    return mapOf(
        BrainRegion.FrontalL to frontalL,
        BrainRegion.FrontalR to frontalR,
        BrainRegion.ParietalL to parietalL,
        BrainRegion.ParietalR to parietalR,
        BrainRegion.TemporalL to temporalL,
        BrainRegion.TemporalR to temporalR,
        BrainRegion.OccipitalL to occipitalL,
        BrainRegion.OccipitalR to occipitalR,
    )
}

@Composable
private fun animateElectrodeEnvelope(state: BrainState): Float {
    val target = (state as? BrainState.Live)?.rawEnvelope ?: 0f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(ENVELOPE_FADE_MS),
        label = "envelope",
    )
    return animated
}

private fun BrainState.toRegionActivations(): Map<BrainRegion, Float> = when (this) {
    is BrainState.Live -> BrainGeometry.bandPowersToRegionActivations(bandPowers)
    BrainState.Idle, BrainState.Searching, BrainState.Connecting,
    is BrainState.Error -> BrainRegion.entries.associateWith { 0f }
}

private fun DrawScope.drawBrainBase(outline: Path, palette: BrainPalette) {
    clipPath(outline) {
        drawRect(color = palette.regionIdle, topLeft = Offset.Zero, size = size)
    }
}

private fun DrawScope.drawRegionWashes(
    outline: Path,
    activations: Map<BrainRegion, Float>,
    palette: BrainPalette,
) {
    val radius = BrainGeometry.regionRadius(size)
    clipPath(outline) {
        BrainRegion.entries.forEach { region ->
            if (region == BrainRegion.Whole) return@forEach
            val activation = activations[region] ?: 0f
            if (activation <= 0f) return@forEach

            val center = BrainGeometry.regionCenter(region, size)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.regionActive.copy(alpha = activation * MAX_REGION_ALPHA),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                center = center,
                radius = radius,
            )
        }
    }
}

private fun DrawScope.drawCentralFissure(outline: Path, palette: BrainPalette) {
    clipPath(outline) {
        val fissure = BrainGeometry.centralFissurePath(size)
        // Darker spine line
        drawPath(
            path = fissure,
            color = palette.outline.copy(alpha = 0.55f),
            style = Stroke(width = 1.5.dp.toPx()),
        )
        // Soft highlight just to the right of the spine — fakes a recessed groove
        drawPath(
            path = fissure,
            color = Color.White.copy(alpha = 0.25f),
            style = Stroke(width = 0.6.dp.toPx()),
        )
    }
}

private fun DrawScope.drawDepthShading(outline: Path, palette: BrainPalette) {
    clipPath(outline) {
        // Top-left highlight — fake light source from above-left
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(palette.highlight, Color.Transparent),
                center = Offset(size.width * 0.30f, size.height * 0.18f),
                radius = size.minDimension * 0.55f,
            ),
            size = size,
        )
        // Edge vignette — fake rim shadow for dimensional feel
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, palette.vignette),
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = size.minDimension * 0.58f,
            ),
            size = size,
        )
    }
}

private fun DrawScope.drawElectrodeDot(
    site: ElectrodeSite,
    envelope: Float,
    palette: BrainPalette,
) {
    val center = BrainGeometry.electrodeCenter(site, size)
    val baseRadius = size.minDimension * ELECTRODE_BASE_RADIUS_RATIO
    val pulseRadius = baseRadius * (1f + envelope * ELECTRODE_PULSE_GAIN)

    drawCircle(
        color = palette.electrodeGlow,
        radius = pulseRadius * 2.6f,
        center = center,
    )
    drawCircle(
        color = palette.electrode,
        radius = pulseRadius,
        center = center,
    )
}

private fun DrawScope.drawOutlineStroke(outline: Path, palette: BrainPalette) {
    drawPath(
        path = outline,
        color = palette.outline.copy(alpha = 0.7f),
        style = Stroke(width = 2.dp.toPx()),
    )
}

private const val BRAIN_ASPECT_RATIO = 0.85f
private const val REGION_FADE_MS = 350
private const val ENVELOPE_FADE_MS = 150
private const val MAX_REGION_ALPHA = 0.45f
private const val ELECTRODE_BASE_RADIUS_RATIO = 0.024f
private const val ELECTRODE_PULSE_GAIN = 0.6f

// region Previews ------------------------------------------------------------

@Preview(showBackground = true, widthDp = 320, heightDp = 380)
@Composable
private fun BrainCanvasIdlePreview() {
    NeurfocusdndTheme {
        BrainCanvas(state = BrainState.Idle, modifier = Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 380)
@Composable
private fun BrainCanvasStaticLivePreview() {
    NeurfocusdndTheme {
        BrainCanvas(
            state = BrainState.Live(
                battery = BatteryPercent(72),
                focus = FocusScore(0.65f),
                bandPowers = mapOf(
                    EegBand.Delta to 0.30f,
                    EegBand.Theta to 0.25f,
                    EegBand.Alpha to 0.60f,
                    EegBand.Beta to 0.55f,
                    EegBand.Gamma to 0.35f,
                ),
                rawEnvelope = 0.7f,
                electrodeSite = ElectrodeSite.Fp1,
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 380)
@Composable
private fun BrainCanvasWarmPreview() {
    NeurfocusdndTheme {
        BrainCanvas(
            state = BrainState.Live(
                battery = BatteryPercent(12),
                focus = FocusScore(0.18f),
                bandPowers = mapOf(
                    EegBand.Delta to 0.55f,
                    EegBand.Theta to 0.50f,
                    EegBand.Alpha to 0.45f,
                    EegBand.Beta to 0.18f,
                    EegBand.Gamma to 0.12f,
                ),
                rawEnvelope = 0.4f,
                electrodeSite = ElectrodeSite.Fp1,
            ),
            modifier = Modifier.fillMaxSize(),
            palette = BrainPalette.warm(),
        )
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 380)
@Composable
private fun BrainCanvasAnimatedPreview() {
    val state by produceState<BrainState>(initialValue = BrainState.Idle) {
        val started = System.currentTimeMillis()
        while (true) {
            val t = (System.currentTimeMillis() - started) / 1_000f
            value = BrainState.Live(
                battery = BatteryPercent(72),
                focus = FocusScore(0.5f),
                bandPowers = mapOf(
                    EegBand.Delta to previewWave(t, 17f, 0.0f, 0.30f, 0.15f),
                    EegBand.Theta to previewWave(t, 11f, 1.1f, 0.25f, 0.20f),
                    EegBand.Alpha to previewWave(t, 7f, 2.3f, 0.45f, 0.30f),
                    EegBand.Beta  to previewWave(t, 5f, 0.7f, 0.40f, 0.30f),
                    EegBand.Gamma to previewWave(t, 3f, 1.9f, 0.20f, 0.20f),
                ),
                rawEnvelope = previewWave(t, 0.5f, 0f, 0.5f, 0.45f),
                electrodeSite = ElectrodeSite.Fp1,
            )
            delay(100L)
        }
    }
    NeurfocusdndTheme {
        BrainCanvas(state = state, modifier = Modifier.fillMaxSize())
    }
}

private fun previewWave(
    tSec: Float,
    periodSec: Float,
    phase: Float,
    base: Float,
    amp: Float,
): Float {
    val raw = base + amp * sin(2f * PI.toFloat() * tSec / periodSec + phase)
    return raw.coerceIn(0f, 1f)
}

// endregion

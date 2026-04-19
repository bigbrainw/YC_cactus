package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.neurofocus.neurfocus_dnd.R
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

// ─────────────────────────────────────────────────────────────────────────────
// Palette (kept for BrainScreen compatibility)
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class BrainPalette(
    val outline: Color,
    val regionIdle: Color,
    val regionActive: Color,
    val electrode: Color,
    val electrodeGlow: Color,
    val highlight: Color,
    val vignette: Color,
) {
    companion object {
        fun pro(): BrainPalette = BrainPalette(
            outline       = Color.Black,
            regionIdle    = Color.White,
            regionActive  = Color(0xFF007AFF),
            electrode     = Color.Black,
            electrodeGlow = Color(0x44007AFF),
            highlight     = Color(0x66FFFFFF),
            vignette      = Color(0x22000000),
        )
        fun cool() = pro()
        fun warm() = pro()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-band glow colors (soft, saturated)
// ─────────────────────────────────────────────────────────────────────────────

private val BandGlowColors = mapOf(
    EegBand.Delta to Color(0xFF5C6BC0), // indigo
    EegBand.Theta to Color(0xFF00BCD4), // cyan
    EegBand.Alpha to Color(0xFF66BB6A), // green
    EegBand.LowBeta to Color(0xFFFFCA28), // amber
    EegBand.HighBeta to Color(0xFFFFAB40), // orange
    EegBand.Gamma to Color(0xFFFF7043), // deep-orange
)

// State-level glow colors
private val ColorSearching  = Color(0xFF90CAF9) // pale blue
private val ColorConnecting = Color(0xFF4DD0E1) // teal
private val ColorError      = Color(0xFFEF9A9A) // pale red
private val ColorIdle       = Color(0xFFB0BEC5) // blue-grey

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Discriminator int so we can detect state-KIND changes (not just value changes). */
private fun BrainState.kindOrdinal(): Int = when (this) {
    BrainState.Idle       -> 0
    BrainState.Searching  -> 1
    BrainState.Connecting -> 2
    is BrainState.Live    -> 3
    is BrainState.Error   -> 4
    is BrainState.Reconnecting -> 5
}

// ─────────────────────────────────────────────────────────────────────────────
// Public composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Brain SVG icon with layered blurry radial-gradient glows.
 *
 * Glow semantics:
 *   Idle       → faint grey glow, no pulse
 *   Searching  → slow pulsing pale-blue glow
 *   Connecting → faster pulsing teal glow
 *   Live       → per-band soft colored glows; sizes driven by band powers
 *                + overall envelope scales every glow
 *   Error      → flickering red glow
 *
 * Blink: whenever BrainState KIND changes (e.g. Searching→Connecting,
 * Connecting→Live) the icon flashes white briefly then fades back.
 */
@Composable
fun BrainCanvas(
    state: BrainState,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") palette: BrainPalette = BrainPalette.pro(),
) {
    val brainPainter: Painter = painterResource(id = R.drawable.ic_brain)

    // ── State-change blink ──────────────────────────────────────────────────
    // Track how many times the state KIND has changed; bump the counter to
    // trigger the blink animation.
    var blinkTrigger by remember { mutableIntStateOf(0) }
    val currentKind  = state.kindOrdinal()
    // Use `key` to fire a side-effect when kind changes
    key(currentKind) {
        // Increment trigger inside a remembered side-effect so it fires once
        // per kind change, not on every recomposition.
        blinkTrigger++
    }

    // ── Envelope (live signal intensity) ───────────────────────────────────
    // rawEnvelopeUv is in µV. Normalize to 0-1 for glow scaling.
    // Typical EEG at Fp1 post in-amp: 10-200µV. Divide by 150, clamp to 0-1.
    val envelopeTarget = ((state as? BrainState.Live)?.rawEnvelopeUv?.div(150f))?.coerceIn(0f, 1f) ?: 0f
    val envelope by animateFloatAsState(
        targetValue   = envelopeTarget,
        animationSpec = tween(150, easing = LinearEasing),
        label         = "envelope",
    )

    // ── Infinite pulse for non-live states ─────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "glows")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val fastPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fastPulse",
    )

    // ── Per-band animated powers ────────────────────────────────────────────
    val livePowers = (state as? BrainState.Live)?.bandPowers ?: emptyMap()
    val animDelta by animateFloatAsState(livePowers[EegBand.Delta] ?: 0f, tween(350), label = "delta")
    val animTheta by animateFloatAsState(livePowers[EegBand.Theta] ?: 0f, tween(350), label = "theta")
    val animAlpha by animateFloatAsState(livePowers[EegBand.Alpha] ?: 0f, tween(350), label = "alpha")
    val animBeta  by animateFloatAsState(livePowers[EegBand.LowBeta]  ?: 0f, tween(350), label = "beta")
    val animGamma by animateFloatAsState(livePowers[EegBand.Gamma] ?: 0f, tween(350), label = "gamma")

    val animBandPowers = mapOf(
        EegBand.Delta to animDelta,
        EegBand.Theta to animTheta,
        EegBand.Alpha to animAlpha,
        EegBand.LowBeta  to animBeta,
        EegBand.Gamma to animGamma,
    )

    // ── Icon tint ───────────────────────────────────────────────────────────
    val iconTint = when (state) {
        BrainState.Idle       -> Color(0xFF9E9E9E)
        is BrainState.Error   -> Color(0xFFEF5350)
        else                  -> Color(0xFF1A1A2E)
    }

    Box(
        modifier          = modifier.aspectRatio(1f).padding(12.dp),
        contentAlignment  = Alignment.Center,
    ) {
        // ── Layer 1: glow canvas (behind icon) ─────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (state) {
                BrainState.Idle -> {
                    drawStateGlow(ColorIdle, alpha = 0.18f + pulse * 0.08f)
                }
                BrainState.Searching -> {
                    drawStateGlow(ColorSearching, alpha = 0.22f + pulse * 0.28f)
                }
                BrainState.Connecting -> {
                    drawStateGlow(ColorConnecting, alpha = 0.30f + fastPulse * 0.40f)
                }
                is BrainState.Live -> {
                    drawLiveGlows(animBandPowers, envelope)
                }
                is BrainState.Error -> {
                    // Flicker: skip every ~3rd frame based on pulse speed
                    val flicker = if ((fastPulse * 6f).toInt() % 3 == 0) 0f
                                  else 0.25f + pulse * 0.45f
                    if (flicker > 0f) drawStateGlow(ColorError, alpha = flicker)
                }
                is BrainState.Reconnecting -> {
                    // Slow amber pulse while reconnecting
                    drawStateGlow(Color(0xFFFFCC02), alpha = 0.18f + pulse * 0.22f)
                }
            }
        }

        // ── Layer 2: SVG brain icon ─────────────────────────────────────────
        key(blinkTrigger) {
            val blink by animateFloatAsState(
                targetValue   = 0f,
                animationSpec = keyframes {
                    durationMillis = 480
                    1f at 80  with LinearEasing
                    0f at 480 with FastOutSlowInEasing
                },
                label = "blinkInner",
            )

            Image(
                painter            = brainPainter,
                contentDescription = "Brain",
                modifier           = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f + envelope * 0.04f
                        scaleX = scale
                        scaleY = scale
                        // Blink = brief white flash by boosting alpha toward white via overlay
                        alpha = 1f // icon always opaque; flash done via colorFilter below
                    },
                colorFilter = ColorFilter.tint(
                    // Lerp icon color toward white during blink
                    lerp(iconTint, Color.White, blink)
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Glow draw functions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Single centered radial glow — used for Idle / Searching / Connecting / Error.
 * Layered twice (large outer + tight inner) so it looks soft and blurry.
 */
private fun DrawScope.drawStateGlow(color: Color, alpha: Float) {
    if (alpha <= 0f) return
    val cx = size.width  * 0.5f
    val cy = size.height * 0.5f

    // Outer soft halo
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha * 0.55f),
                color.copy(alpha = alpha * 0.15f),
                Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = size.minDimension * 0.65f,
        ),
        center = Offset(cx, cy),
        radius = size.minDimension * 0.65f,
    )

    // Inner tight core glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha * 0.80f),
                color.copy(alpha = alpha * 0.25f),
                Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = size.minDimension * 0.32f,
        ),
        center = Offset(cx, cy),
        radius = size.minDimension * 0.32f,
    )
}

/**
 * Per-band glows for the Live state.
 *
 * Each band has:
 *  - a distinct color
 *  - a distinct spatial offset (so they don't all pile on the center)
 *  - an outer halo + inner core, both sized by band power + envelope
 *
 * The offsets are arranged around an ellipse matching rough lobe positions:
 *   Delta  → center (background)
 *   Theta  → temporal (sides)
 *   Alpha  → occipital (bottom)
 *   Beta   → frontal (top)
 *   Gamma  → parietal (upper-center)
 */
private fun DrawScope.drawLiveGlows(
    bandPowers: Map<EegBand, Float>,
    envelope: Float,
) {
    data class GlowSpec(
        val band: EegBand,
        val cx: Float, // fractional center x
        val cy: Float, // fractional center y
        val outerR: Float, // outer radius as fraction of minDimension
        val innerR: Float, // inner radius as fraction of minDimension
    )

    val specs = listOf(
        GlowSpec(EegBand.Delta, 0.50f, 0.50f, 0.75f, 0.40f), // center background
        GlowSpec(EegBand.Theta, 0.50f, 0.62f, 0.55f, 0.28f), // temporal/lower
        GlowSpec(EegBand.Alpha, 0.50f, 0.78f, 0.52f, 0.26f), // occipital/bottom
        GlowSpec(EegBand.LowBeta, 0.50f, 0.22f, 0.52f, 0.26f), // frontal/top
        GlowSpec(EegBand.Gamma, 0.50f, 0.38f, 0.50f, 0.24f), // parietal/upper-center
    )

    specs.forEach { spec ->
        val power = bandPowers[spec.band] ?: 0f
        if (power < 0.03f) return@forEach

        val boost = 1f + envelope * 0.5f
        val alpha = (power * boost).coerceIn(0f, 0.85f)
        val color = BandGlowColors[spec.band] ?: Color.White

        val cx = size.width  * spec.cx
        val cy = size.height * spec.cy

        // Outer soft halo — large, very transparent
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha * 0.40f),
                    color.copy(alpha = alpha * 0.10f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = size.minDimension * spec.outerR,
            ),
            center = Offset(cx, cy),
            radius = size.minDimension * spec.outerR,
        )

        // Inner tight core — brighter, smaller
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha * 0.75f),
                    color.copy(alpha = alpha * 0.25f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = size.minDimension * spec.innerR,
            ),
            center = Offset(cx, cy),
            radius = size.minDimension * spec.innerR,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility
// ─────────────────────────────────────────────────────────────────────────────

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0A1A, widthDp = 300, heightDp = 300)
@Composable
private fun PreviewIdle() {
    NeurfocusdndTheme { BrainCanvas(state = BrainState.Idle, modifier = Modifier.fillMaxSize()) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A1A, widthDp = 300, heightDp = 300)
@Composable
private fun PreviewSearching() {
    NeurfocusdndTheme { BrainCanvas(state = BrainState.Searching, modifier = Modifier.fillMaxSize()) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A1A, widthDp = 300, heightDp = 300)
@Composable
private fun PreviewLive() {
    NeurfocusdndTheme {
        BrainCanvas(
            state = BrainState.Live(
                focus        = dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore(0.7f),
                bandPowers   = mapOf(
                    EegBand.Delta    to 0.25f,
                    EegBand.Theta    to 0.40f,
                    EegBand.Alpha    to 0.60f,
                    EegBand.LowBeta  to 0.55f,
                    EegBand.HighBeta to 0.30f,
                    EegBand.Gamma    to 0.35f,
                ),
                rawEnvelopeUv = 65f,
                debugStats = dev.neurofocus.neurfocus_dnd.brain.domain.EegDebugStats(
                    totalSamples = 1000, effectiveRateSps = 253f,
                    bleNotifyCount = 1000, seqGaps = 0, ignoredPayloads = 0,
                    lastRawCount = 127000, lastMicrovoltsCorrected = 48f,
                    windowRmsUv = 52f, windowSamples = 1265,
                    transportMode = "ascii", lastNotifyMs = System.currentTimeMillis(),
                ),
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

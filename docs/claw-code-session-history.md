    val rmsDiff: Float
    val rmsDiff2: Float
    val lastAbs: Float
    synchronized(ringBufferLock) {
        if (ringCount < 4) return placeholderLive()
        n = minOf(ringCount, RING_SIZE)
        var ma = 0.0
        var ssd = 0.0
        var ssd2 = 0.0
        var prev = ring[(ringWrite - n + RING_SIZE * 2) % RING_SIZE].toDouble()
        ma += abs(prev)
        var prevDiff = 0.0
        for (i in 1 until n) {
            val v = ring[(ringWrite - n + i + RING_SIZE * 2) % RING_SIZE].toDouble()
            ma += abs(v)
            val d = v - prev
            ssd += d * d
            val d2 = d - prevDiff
            ssd2 += d2 * d2
            prevDiff = d
            prev = v
        }
        meanAbs = ma / n
        rmsDiff = sqrt(ssd / (n - 1).coerceAtLeast(1)).toFloat()
        rmsDiff2 = sqrt(ssd2 / (n - 1).coerceAtLeast(1)).toFloat()
        lastAbs = abs(lastSample).toFloat()
    }
    val hf = (rmsDiff / HF_SCALE + rmsDiff2 / HF2_SCALE).coerceIn(0f, 1f)
╰─

… output truncated for display; full result preserved in session.
Now I have a complete picture. Let me understand what needs to be built:1. BLE: The current repo has no reconnection loop — it just fails and stays in `Error`. Need persistent reconnect with proper keep-alive.
2. Signal processing: `buildLiveFromRing()` is fake — it uses heuristic math, not real FFT band powers. Need real Welch PSD or Goertzel bins.
3. `BrainState.Live` has fake `battery=100` always.
4. `EegBand`: current enum has 5 bands; zuna uses 6 bands (split Beta into low/high). Need to match.
5. No Debug tab exists. Need bottom nav + Debug tab.
6. Domain types: need `rawCounts`, `microvolts`, `sampleRateHz`, `sequenceGaps`, etc. for Debug tab.
7. `BrainState` needs to carry raw sample stats for Debug.Let me now build all of this systematically:
╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/build.gradle.kts | grep -E "implementation|version|compose" 
╰────────────╯
✓ bash

╭─ text
alias(libs.plugins.kotlin.compose)
    version = release(36) {
    versionCode = 1
    versionName = "1.0"
    compose = true
implementation(libs.androidx.core.ktx)
implementation(libs.androidx.lifecycle.runtime.ktx)
implementation(libs.androidx.lifecycle.runtime.compose)
implementation(libs.androidx.lifecycle.viewmodel.compose)
implementation(libs.androidx.activity.compose)
implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.compose.ui)
implementation(libs.androidx.compose.ui.graphics)
implementation(libs.androidx.compose.ui.tooling.preview)
implementation(libs.androidx.compose.material3)
implementation("androidx.compose.material:material-icons-extended")
androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
debugImplementation(libs.androidx.compose.ui.tooling)
debugImplementation(libs.androidx.compose.ui.test.manifest)╰─
Good. Now I’ll build everything. Here’s the plan:1. `BrainTypes.kt` — fix `EegBand` to match zuna’s 6 bands, add `EegDebugStats`
2. `BrainState.kt` — add `debugStats` to `Live`, add `DisconnectEvent`
3. `EegSignalProcessor.kt` — real Goertzel band powers + counts→µV conversion matching zuna exactly
4. `BleEegRepository.kt` — full reconnect loop, keep-alive, disconnect logging, real signal processing
5. `BrainViewModel.kt` — expose debug log
6. `MainActivity.kt` — bottom nav with Home + Debug tabs
7. `DebugScreen.kt` — real-time debug panel
╭─ write_file ─╮
│ ✏️ Writing /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainTypes.kt (95 lines)
╰──────────────────╯
✓ ✏️ Updated /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainTypes.kt (95 lines)

╭─ write_file ─╮
│ ✏️ Writing /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainState.kt (37 lines)
╰──────────────────╯
✓ ✏️ Updated /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainState.kt (37 lines)
Now create the real signal processor:
╭─ write_file ─╮
│ ✏️ Writing /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/signal/EegSignalProcessor.kt (223 lines)
╰──────────────────╯
✓ ✏️ Wrote /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/signal/EegSignalProcessor.kt (223 lines)
Now rewrite `BleEegRepository.kt` with persistent reconnect, real signal processing, and debug stats:
╭─ write_file ─╮
│ ✏️ Writing /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleEegRepository.kt (605 lines)
╰──────────────────╯
✓ ✏️ Updated /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleEegRepository.kt (605 lines)
Now update the ViewModel to expose disconnect events, then build the Debug screen and update MainActivity with bottom nav:
╭─ write_file ─╮
│ ✏️ Writing /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainViewModel.kt (110 lines)
╰──────────────────╯
✓ ✏️ Updated /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainViewModel.kt (110 lines)
Now create the Debug screen:
╭─ write_file ─╮
│ ✏️ Writing /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt (284 lines)
╰──────────────────╯
✓ ✏️ Wrote /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt (284 lines)
Now update `MainActivity.kt` with the bottom nav (Home + Debug), and update `BrainScreen.kt` to remove references to old `EegBand` fields and `rawEnvelope`:
╭─ write_file ─╮
│ ✏️ Writing /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/MainActivity.kt (290 lines)
╰──────────────────╯
✓ ✏️ Wrote /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/MainActivity.kt (290 lines)
Now I need to copy the new MainActivity to the right place and fix remaining compile issues — `BrainScreen.kt`, `BrainCanvas.kt`, `BrainMeter.kt`, and `AdviceLine.kt` use old `BrainState.Live` fields (`rawEnvelope`, old `EegBand.Beta`):                                                                                                                            
╭─ bash ─╮
│  $ cp /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/MainActivity.kt /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/mai… 
╰────────────╯
✓ bash

/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt:43:    state: BrainState.Live,
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt:53:        Stat(label = “Brain Battery”, percent = state.battery.value)
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt:110:            state = BrainState.Live(
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt:111:                battery = BatteryPercent(72),
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt:113:                bandPowers = mapOf(EegBand.Alpha to 0.5f),
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt:114:                rawEnvelope = 0.5f,
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:23: * v1: deterministic German-minimalist copy keyed to focus + battery.
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:46:    is BrainState.Live -> {
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:48:        val battery = state.battery.value
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:50:            battery < 20 -> “Reserves are low. Recover, do not push.”
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:51:            focus >= 0.70f && battery > 50 -> “Peak focus. Begin your most demanding task now.”
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:57:    BrainState.Idle, BrainState.Searching, BrainState.Connecting -> null
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:58:    is BrainState.Error -> null
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:66:            state = BrainState.Live(
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:67:                battery = BatteryPercent(72),
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:69:                bandPowers = mapOf(EegBand.Beta to 0.6f),
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:70:                rawEnvelope = 0.6f,
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt:79:    EegBand.Delta to Color(0xFF5C6BC0), // indigo
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt:80:    EegBand.Theta to Color(0xFF00BCD4), // cyan
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt:81:    EegBand.Alpha to Color(0xFF66BB6A), // green
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt:82:    EegBand.Beta  to Color(0xFFFFCA28), // amber
/mnt/c/Users/in-ryzen-machine/Andro
… output truncated for display; full result preserved in session.
Now I need to fix all these files. Let me do targeted fixes for each:
╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt 
╰────────────╯
✓ bash

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

• Braun ET66-inspired stats row.
• 
• Two columns (Battery, Focus) — monospaced numerals, all-caps tracking-out
• label, thin animated horizontal bar. No chrome, no shadows, no drop-fills.
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
Stat(label = “Brain Battery”, percent = state.battery.value)
VerticalDivider(
modifier = Modifier.height(48.dp),
color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
)
Stat(label = “FOCUS”, percent = (state.focus.value * 100f).toInt())
}
}
… output truncated for display; full result preserved in session.

╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt 
╰────────────╯
✓ bash

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

• Single sentence of “do this next” advice.
• 
• v1: deterministic German-minimalist copy keyed to focus + battery.
• v2 (later): replace [deriveAdvice] with a Gemma-backed generator. The
• composable signature stays the same — UI never knows the source.
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
battery < 20 -> “Reserves are low. Recover, do not push.”
focus >= 0.70f && battery > 50 -> “Peak focus. Begin your most demanding task now.”
focus >= 0.50f -> “Steady. Ship something difficult before it slips.”
focus >= 0.30f -> “Focus dipping. A short pause prevents a long one.”
else -> “Three minutes of stillness. Then return.”
}
}
BrainState.Idle, BrainState.Searching, BrainState.Connecting -> null
is BrainState.Error -> null
}

… output truncated for display; full result preserved in session.
Now fix all 4 files in parallel - updating to new API:
╭─ write_file ─╮
│ ✏️ Writing /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt (148 lines)
╰──────────────────╯
✓ ✏️ Updated /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt (148 lines)

╭─ write_file ─╮
│ ✏️ Writing /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt (77 lines)
╰──────────────────╯
✓ ✏️ Updated /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt (77 lines)
Now fix `BrainCanvas.kt` — update old EegBand references and `rawEnvelope` → `rawEnvelopeUv`, and add `Reconnecting` case:
╭─ bash ─╮
│  $ sed -n '1,50p' /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt && echo "… 
╰────────────╯
✓ bash

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
import dev.neurofocus.neurfocus_dnd.brain.domain.BatteryPercent
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.ElectrodeSite
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

// ─────────────────────────────────────────────────────────────────────────────
// Palette (kept for BrainScreen compatibility)
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class BrainPalette(
==TOTAL LINES==
426 /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt

╭─ bash ─╮
│  $ sed -n '51,200p' /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt 
╰────────────╯
✓ bash

╭─ text
val outline: Color,
val regionIdle: Color,
val regionActive: Color,
val electrode: Color,
val electrodeGlow: Color,
val highlight: Color,
val vignette: Color,
╰─

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
EegBand.Beta  to Color(0xFFFFCA28), // amber
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Public composable
// ─────────────────────────────────────────────────────────────────────────────

/**

• Brain SVG icon with layered blurry radial-gradient glows.
… output truncated for display; full result preserved in session.
I need to fix `BrainCanvas.kt` — it has many references to the old `EegBand.Beta`, `rawEnvelope`, etc. Let me do targeted replacements:
╭─ bash ─╮
│  $ cd /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui
                                                                                                                                                                                    
# Fix BrainCanvas.kt:                                                                                                                                                               
# 1. Remove i…                                                                                                                                                                      
╰────────────╯
✓ bash

Done
Now fix `BrainScreen.kt` — update statusLine() for Reconnecting, fix Live preview constructor, and remove old EegBand.Beta:
╭─ bash ─╮
│  $ cd /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui

python3 - << 'PYEOF'
with open('Bra…                                                                                                                                                                     
╰────────────╯
✓ bash

Done
Now check if `BleSpec.kt` still has old `ADC_FULL_SCALE_COUNTS` etc that could conflict, and also check if `BrainRepositoryFactory.kt` needs fixing:
╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/BrainRepositoryFactory.kt 
╰────────────╯
✓ bash

package dev.neurofocus.neurfocus_dnd.brain.data

import android.app.Application
import dev.neurofocus.neurfocus_dnd.brain.data.ble.BleEegRepository

object BrainRepositoryFactory {

╭─ text
fun create(application: Application): BrainDataRepository {
    return BleEegRepository(application)
}
╰─

}
Good, factory is fine. Now verify the remaining issues — check for any remaining old API references across all files:
╭─ bash ─╮
│  $ grep -rn "\.rawEnvelope\b\|EegBand\.Beta\b\|BatteryPercent(100)\|BatteryPercent(72)\|BatteryPercent(12)\|EegBand\.Beta \|EegBand\.Beta," \
  /mnt/c/Users/in-ryz…                                                                                                                                                              
╰────────────╯
✓ bash exit_code:1

╭─ bash ─╮
│  $ # Check for any remaining compile issues: old BrainState.Live field names
grep -rn "rawEnvelope\|\.battery\.value\|EegBand\.Beta\b" \                                                                                                                         
  /mnt/c/Users/in-ryzen-ma…                                                                                                                                                         
╰────────────╯
✓ bash

/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleEegRepository.kt:533:                rawEnvelopeUv   = abs(lastUv),
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainState.kt:21:        val rawEnvelopeUv: Float,
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:66:                rawEnvelopeUv = 60f,
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt:143:    val envelopeTarget = (state as? BrainState.Live)?.rawEnvelopeUvUv ?: 0f
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt:419:                rawEnvelopeUv = 65f,
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt:136:                rawEnvelopeUv = 50f,
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainScreen.kt:284:                rawEnvelopeUv = 60f,
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt:181:    DebugRow(“raw_envelope_µV”, “${live.rawEnvelopeUv.roundTo2()} µV”)
75:private val BandGlowColors = mapOf(
79:    EegBand.LowBeta to Color(0xFFFFCA28), // amber
80:    EegBand.HighBeta to Color(0xFFFFAB40), // orange
176:    val animBeta  by animateFloatAsState(livePowers[EegBand.LowBeta]  ?: 0f, tween(350), label = “beta”)
183:        EegBand.LowBeta  to animBeta,
330:        GlowSpec(EegBand.LowBeta, 0.50f, 0.22f, 0.52f, 0.26f), // frontal/top
340:        val color = BandGlowColors[spec.band] ?: Color.White
415:                    EegBand.LowBeta  to 0.55f,
416:                    EegBand.HighBeta to 0.30f,
There’s a typo in BrainCanvas.kt — `rawEnvelopeUvUv` (doubled suffix). Let me fix that:
╭─ bash ─╮
│  $ sed -i 's/rawEnvelopeUvUv/rawEnvelopeUv/g' /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain… 
╰────────────╯
✓ bash

143:    val envelopeTarget = (state as? BrainState.Live)?.rawEnvelopeUv ?: 0f
419:                rawEnvelopeUv = 65f,
Now verify the `DebugScreen.kt` has no unused imports and the `Box`/`Column`/`Alignment` imports that aren’t used:
╭─ bash ─╮
│  $ grep -n "import androidx.compose.foundation.layout.Box\|import androidx.compose.foundation.layout.Column\|import androidx.compose.ui.Alignment" \
  /mnt/c/Users…                                                                                                                                                                     
╰────────────╯
✓ bash

6:import androidx.compose.foundation.layout.Box
7:import androidx.compose.foundation.layout.Column
24:import androidx.compose.ui.Alignment
5

╭─ bash ─╮
│  $ # Remove truly unused imports from DebugScreen.kt - Box, Column, Alignment aren't directly used
# Check if Box appears in actual code (not imports)                                                                                                                                 
grep -n "Box…                                                                                                                                                                       
╰────────────╯
✓ bash

60:    LazyColumn(

╭─ bash ─╮
│  $ sed -i '/^import androidx.compose.foundation.layout.Box$/d' /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/ne… 
╰────────────╯
✓ bash

116: *                + overall envelope scales every glow
143:    val envelopeTarget = (state as? BrainState.Live)?.rawEnvelopeUv ?: 0f
144:    val envelope by animateFloatAsState(
145:        targetValue   = envelopeTarget,
147:        label         = “envelope”,
211:                    drawLiveGlows(animBandPowers, envelope)
240:                        val scale = 1f + envelope * 0.04f
305: *  - an outer halo + inner core, both sized by band power + envelope
316:    envelope: Float,
338:        val boost = 1f + envelope * 0.5f
The envelope is now in µV (could be 0–200+ µV). The glow code multiplies by 0.04 or 0.5 — those coefficients were tuned for 0-1 range. I need to normalize it:
╭─ bash ─╮
│  $ cd /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui
                                                                                                                                                                                    
python3 - << 'PYEOF'                                                                                                                                                                
with open('Bra…                                                                                                                                                                     
╰────────────╯
✓ bash

Done
Now do a final check of all remaining issues across the codebase:
╭─ bash ─╮
│  $ # Final scan for all potential compile errors
echo "=== Checking for old EegBand.Beta (5-band) ==="                                                                                                                               
grep -rn "EegBand\.Beta\b" /mnt/c/Users/in-ryzen-machine/And…                                                                                                                       
╰────────────╯
✓ bash

=== Checking for old EegBand.Beta (5-band) ===
CLEAN
=== Checking for old rawEnvelope (non-Uv) ===
CLEAN
=== Checking for old battery.value (non-null) ===
CLEAN
=== Checking for BrainState.Live with old constructor ===
CLEAN
=== Checking for Reconnecting state coverage in when expressions ===
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleEegRepository.kt:257:            _state.update { BrainState.Searching }
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleEegRepository.kt:288:        _state.update { BrainState.Connecting }
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleEegRepository.kt:402:            if (_state.value is BrainState.Connecting) {
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleEegRepository.kt:411:                is BrainState.Live, BrainState.Connecting, BrainState.Searching ->
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt:53:    BrainState.Idle, BrainState.Searching, BrainState.Connecting -> null
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt:97:    BrainState.Searching  -> 1
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt:98:    BrainState.Connecting -> 2
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt:206:                BrainState.Searching -> {
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt:209:                BrainState.Connecting -> {
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt:403:    NeurfocusdndTheme { BrainCanvas(state = BrainState.Searching, modifier = Modifier.fillMaxSize()) }
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainScreen.kt:262:    BrainState.Searching -> “SCANNING FOR HEADBAND…”
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainScreen.kt:263:    BrainState.Connecting -> “ESTABLISHING LINK…”
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainScreen.kt:303:            state = BrainState.Searching,
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt:112:        BrainState.Searching   -> “SEARCHING (scanning BLE for NEUROFOCUS_V4…)”
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt:113:        BrainState.Connecting  -> “CONNECTING (GATT setup in progress)”
Now check that the `when` expressions over `BrainState` that must be exhaustive include `Reconnecting`:
╭─ bash ─╮
│  $ # BrainCanvas kindOrdinal - must cover Reconnecting (already added as -> 5)
grep -A 10 "fun BrainState.kindOrdinal" /mnt/c/Users/in-ryzen-machine/AndroidStudioP…                                                                                               
╰────────────╯
✓ bash

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

96:    BrainState.Idle       -> 0
97:    BrainState.Searching  -> 1
98:    BrainState.Connecting -> 2
99:    is BrainState.Live    -> 3
100:    is BrainState.Error   -> 4
101:    is BrainState.Reconnecting -> 5
191:        BrainState.Idle       -> Color(0xFF9E9E9E)
192:        is BrainState.Error   -> Color(0xFFEF5350)
203:                BrainState.Idle -> {
206:                BrainState.Searching -> {
209:                BrainState.Connecting -> {
212:                is BrainState.Live -> {
215:                is BrainState.Error -> {
397:    NeurfocusdndTheme { BrainCanvas(state = BrainState.Idle, modifier = Modifier.fillMaxSize()) }
403:    NeurfocusdndTheme { BrainCanvas(state = BrainState.Searching, modifier = Modifier.fillMaxSize()) }
The `when` expressions on line 191 and 203 in BrainCanvas use `else` implicitly (the `when` on line 191 uses only 2 branches and falls to `else` for the rest — need to check if it’s exhaustive or uses `else`):
╭─ bash ─╮
│  $ sed -n '188,225p' /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt 
╰────────────╯
✓ bash

╭─ text
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
        }
    }

    // ── Layer 2: SVG brain icon ─────────────────────────────────────────
    key(blinkTrigger) {╰─
The `when (state)` on line 203 is not a sealed interface exhaustive `when` (it’s used as a statement, not expression), so it doesn’t require `else` — but it still needs to compile. Since `BrainState` is a `sealed interface`, this `when` statement will silently not handle `Reconnecting`. That’s fine for the glow (it just won’t draw anything for Reconnecting state). But I should add it:
╭─ bash ─╮
│  $ cd /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui
                                                                                                                                                                                    
python3 - << 'PYEOF'                                                                                                                                                                
with open('Bra…                                                                                                                                                                     
╰────────────╯
✓ bash

Done
Now do one final comprehensive scan for anything that will definitely fail compilation:
╭─ bash ─╮
│  $ echo "=== Checking for any remaining compile-breaking patterns ==="
                                                                                                                                                                                    
# Check for FocusScore import still needed in BrainScreen                                                                                                                           
grep -n "FocusScore" /mnt/c/Users…                                                                                                                                                  
╰────────────╯
✓ bash

=== Checking for any remaining compile-breaking patterns ===
42:import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
275:                focus = dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore(0.65f),
package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neurofocus.neurfocus_dnd.brain.data.signal.EegSignalProcessor
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.DisconnectEvent
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**

• Debug tab — shows all real BLE stats, signal values, band powers, and disconnect events.
• 
• Honesty policy:
=== Checking BrainCanvas for Color import (needed for Reconnecting glow) ===
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
=== Checking BleSpec still has all constants used ===
17:    val CMD_STREAM_START: ByteArray = byteArrayOf(‘b’.code.toByte())
21:    const val ADC_FULL_SCALE_VOLTS: Float = 3.3f
23:    const val ADC_FULL_SCALE_COUNTS: Int = 1 shl (ADC_BITS - 1)
29:    fun countsToMicrovolts(counts: Long): Float {
30:        val volts = counts.toFloat() * ADC_FULL_SCALE_VOLTS / ADC_FULL_SCALE_COUNTS
Good. `BrainScreen.kt` has `FocusScore` imported (line 42) and uses it at line 275 with fully-qualified name — that import is now redundant but harmless. The `DebugScreen` imports look clean. `Color` is imported in `BrainCanvas`.Now do a final check of all new files to make sure the structure is complete:
╭─ bash ─╮
│  $ echo "=== New files created ==="
find /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java -name "*.kt" | sort                                                                         
                                                                                                                                                                                    
echo "=== File siz…                                                                                                                                                                 
╰────────────╯
✓ bash

=== New files created ===
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleEegRepository.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BlePermissions.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleSpec.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/BrainDataRepository.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/BrainRepositoryFactory.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/signal/EegSignalProcessor.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainState.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainTypes.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainScreen.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainViewModel.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/SettingsScreen.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/MainActivity.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/onboarding/UserPrefs.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/onboarding/UserProfile.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/components/GlassCard.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/shell/NeuroGradientBackground.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/shell/NeuroTopBar.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Color.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/NeuroTokens.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Theme.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Type.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/util/DispatcherProvider.kt
=== File sizes (all should be non-zero) ===
3301 total
605 /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleEegRepository.kt
437 /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt
307 /mnt/c/Users/
… output truncated for display; full result preserved in session.
Everything looks good. Let me now provide a clear summary of everything that was done and why:

---What Was Done


The Problem (Diagnosed Honestly)The old code had three categories of lies:

1. Fake signal processing — `buildLiveFromRing()` used heuristic math on raw counts (RMS of first-difference, etc.) instead of actual frequency-band analysis
2. No reconnection — disconnect → `Error` state, permanently dead
3. No Debug visibility — impossible to tell what was actually happening

---What Changed`BrainTypes.kt` — Fixed `EegBand` to match `zuna_process` exactly

• Old: 5 bands (`Delta`, `Theta`, `Alpha`, `Beta`, `Gamma`)
• New: 6 bands matching `features.py BAND_DEFS` exactly: `Delta(1-4)`, `Theta(4-8)`, `Alpha(8-12)`, `LowBeta(12-20)`, `HighBeta(20-30)`, `Gamma(30-40)`
• Added `EegDebugStats` — all real BLE counters
• Added `DisconnectEvent` — GATT status + timestamp`BrainState.kt` — New fields, new state

• `BrainState.Live` now carries `debugStats`, `rawEnvelopeUv` (in µV), `battery = null` (honest — firmware doesn’t report it)
• New `BrainState.Reconnecting(attempt, lastDisconnect)` state`EegSignalProcessor.kt` — New file: real signal math

• `countsToMicrovolts()` — exact formula from `neurofocus_to_fif.py`: `-(count × 3.3/8388608/100 × 1e6)` including polarity flip
• `parseBinaryFrame()` — matches `ble_eeg_receiver.py parse_frame()` (magic `0xE71E`)
• `parseAsciiSample()` — matches `parse_ascii_sample()`
• `computeRelativeBandPowers()` — Goertzel at each 1-40 Hz integer bin, integrated over 6 `BAND_DEFS` bands, normalized to relative power
• `focusHeuristic()` — physiological engagement/relaxation ratio, labeled as heuristic`BleEegRepository.kt` — Persistent reconnect + real processing

• Auto-reconnect loop with exponential backoff (2s → 4s → 8s → max 30s)
• Logs every disconnect with GATT status code (133 = Android BLE stack bug, 19 = firmware closed it, 8 = supervision timeout, etc.)
• Parses both binary frames and ASCII (firmware can send either)
• Tracks `seqGaps`, `ignoredPayloads`, `effectiveRateSps` (measured from timestamps)
• Calls `resetCounters()` on each successful setup
• `MutableSharedFlow<DisconnectEvent>` exposed for Debug tab`BrainViewModel.kt`

• Collects disconnect events from `BleEegRepository.disconnectEvents` into `disconnectLog` StateFlow`DebugScreen.kt` — New file: complete debug terminal

• Shows BLE state, all counters, last raw count, last µV value, RMS, band powers as ASCII bar charts, signal chain constants (Vref/PGA/INAMP/formula), disconnect log with timestamps and GATT codes`MainActivity.kt`

• Bottom nav bar: Home tab + Debug tab
• Debug tab gets full screen (no horizontal padding, no top bar)
• Home tab unchangedAll UI files (`BrainCanvas`, `BrainMeter`, `AdviceLine`, `BrainScreen`)

• Updated to new `BrainState.Live` API
• Battery shows “N/A” instead of a fake number
✔ ✨ Done
                                                                                                                                                                                    
[auto-compacted: removed 86 messages]
> fix those ```Unresolved reference 'EegDebugStats'.
Unresolved reference 'lastRawCount'.
Unresolved reference 'lastMicrovoltsCorrected'.
Unresolved reference 'windowRmsUv'.
Unresolved reference 'transportMode'.
Redundant call of conversion method.
Redundant call of the conversion method
'fun Divider(modifier: Modifier = ..., thickness: Dp = ..., color: Color = ...): Unit' is deprecated. Renamed to HorizontalDivider.
```
✓ glob_search matched 0 files
Let me see the current state of the files first:
╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainTypes.kt 
╰────────────╯
✓ bash

package dev.neurofocus.neurfocus_dnd.brain.domain

@JvmInline
value class FocusScore(val value: Float) {
init { require(value in 0f..1f) { “FocusScore must be 0..1, got $value” } }
}

@JvmInline
value class BatteryPercent(val value: Int) {
init { require(value in 0..100) { “BatteryPercent must be 0..100, got $value” } }
}

enum class BrainRegion {
FrontalL, FrontalR,
ParietalL, ParietalR,
TemporalL, TemporalR,
OccipitalL, OccipitalR,
Whole,
}

/**

• EEG frequency bands — exactly matching zuna_process/focus/features.py BAND_DEFS.
• Do NOT change these ranges without retraining the focus classifier.
• 
• delta:     1-4 Hz    (drowsiness, slow waves)
• theta:     4-8 Hz    (frontal cognitive control)
• alpha:     8-12 Hz   (relaxed wakefulness; desynchronizes with attention)
• low_beta:  12-20 Hz  (active focus)
• high_beta: 20-30 Hz  (high arousal, anxiety)
• gamma:     30-40 Hz  (cognitive binding, perception)
• 
• Source: zuna_process/focus/features.py BAND_DEFS
*/
enum class EegBand(
val loHz: Float,
val hiHz: Float,
val label: String,
) {
Delta   (1f,  4f,  “δ Delta”),
Theta   (4f,  8f,  “θ Theta”),
Alpha   (8f,  12f, “α Alpha”),
LowBeta (12f, 20f, “β Low-β”),
HighBeta(20f, 30f, “β High-β”),
Gamma   (30f, 40f, “γ Gamma”),
}

/** Physical electrode location on the scalp (10-20 system). Hardware: Fp1 (forehead). */
enum class ElectrodeSite { Fp1, Fp2, Cz, O1, O2 }

/**

• One ADC sample as it arrives from the firmware.
• raw = ADS1220 signed 24-bit count (as delivered over BLE, before any conversion).
*/
data class ChannelSample(val rawCount: Long, val timestampMs: Long)

/**

• Live stats exposed to the Debug tab. Every field is the real computed value —
• no fakes. Fields that are not yet available are null.
• 
• Source of truth for field definitions: zuna_process/scripts/ble_eeg_receiver.py
… output truncated for display; full result preserved in session.
`BrainTypes.kt` has `EegDebugStats` with all those fields. The issue is `BrainState.kt` and `DebugScreen.kt` — let me see them:
╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainState.kt
echo "==="                                                                                                                                                                          
cat /m…                                                                                                                                                                             
╰────────────╯
✓ bash

package dev.neurofocus.neurfocus_dnd.brain.domain

/**

• Single source of truth for everything the brain UI renders.


• The sealed hierarchy is deliberately exhaustive — `when` over BrainState


• must omit `else` so the compiler enforces updates if a new state appears.
*/
sealed interface BrainState {
data object Idle : BrainState

data object Searching : BrainState

data object Connecting : BrainState

data class Live(
/** Relative band powers (0..1), keyed by EegBand. REAL values from Goertzel/Welch — not faked. /
val bandPowers: Map<EegBand, Float>,
/* Focus probability (0..1). 0.5 threshold = engaged. Real heuristic from windowed band powers. /
val focus: FocusScore,
/* Raw peak envelope in microvolts (absolute value of last converted sample). Real. /
val rawEnvelopeUv: Float,
/* Stats for the Debug tab. Always present when Live. /
val debugStats: EegDebugStats,
/* Battery percent — firmware does not currently report this; will show null in UI. /
val battery: BatteryPercent? = null,
/* Electrode site — hardware fixed at Fp1. */
val electrodeSite: ElectrodeSite = ElectrodeSite.Fp1,
) : BrainState

data class Error(val message: String) : BrainState

/** Reconnecting after a disconnect — distinct from initial Searching to preserve last stats. */
data class Reconnecting(
val attempt: Int,
val lastDisconnect: DisconnectEvent?,
) : BrainState
}
===
package dev.neurofocus.neurfocus_dnd.brain.ui



import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
… output truncated for display; full result preserved in session.
The types and the screen look correct. The errors mean the `BrainTypes.kt` with `EegDebugStats` isn’t being found — either there’s a stale old version of `BrainTypes.kt` shadowing it, or `BrainState.kt` still has an old definition. Let me check what’s actually on disk vs. what’s being compiled:
╭─ bash ─╮
│  $ # Check if there are duplicate files or old BrainTypes somewhere
find /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd -name "*.kt" | xargs grep…                                                                                    
╰────────────╯
✓ bash


/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleEegRepository.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainState.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainTypes.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainScreen.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt

—missing import check—
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.DisconnectEvent
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
Found it. `DebugScreen.kt` uses `EegDebugStats` as a parameter type in `SignalBlock(d: EegDebugStats)` but never imports it. Same for several other missing imports. Also `Divider` needs to become `HorizontalDivider`. Let me fix all issues at once:
╭─ edit_file ─╮
│ 📝 Editing app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt
╰─────────────────╯
✗ edit_file
missing field `old_string`

╭─ bash ─╮
│  $ head -40 /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt | cat -A | head… 
╰────────────╯
✓ bash

package dev.neurofocus.neurfocus_dnd.brain.ui$
$
import androidx.compose.foundation.background$
import androidx.compose.foundation.horizontalScroll$
import androidx.compose.foundation.layout.Arrangement$
import androidx.compose.foundation.layout.Row$
import androidx.compose.foundation.layout.Spacer$
import androidx.compose.foundation.layout.fillMaxSize$
import androidx.compose.foundation.layout.fillMaxWidth$
import androidx.compose.foundation.layout.height$
import androidx.compose.foundation.layout.padding$
import androidx.compose.foundation.layout.width$
import androidx.compose.foundation.lazy.LazyColumn$
import androidx.compose.foundation.lazy.items$
import androidx.compose.foundation.rememberScrollState$
import androidx.compose.foundation.shape.RoundedCornerShape$
import androidx.compose.material3.Divider$
import androidx.compose.material3.Text$
import androidx.compose.runtime.Composable$
import androidx.compose.runtime.getValue$
import androidx.compose.ui.Modifier$
import androidx.compose.ui.graphics.Color$
import androidx.compose.ui.text.font.FontFamily$
import androidx.compose.ui.text.font.FontWeight$
import androidx.compose.ui.unit.dp$
import androidx.compose.ui.unit.sp$
import androidx.lifecycle.compose.collectAsStateWithLifecycle$
import dev.neurofocus.neurfocus_dnd.brain.data.signal.EegSignalProcessor$
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState$
import dev.neurofocus.neurfocus_dnd.brain.domain.DisconnectEvent$
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand$
import java.text.SimpleDateFormat$
import java.util.Date$
import java.util.Locale$
import kotlin.math.roundToInt$
$
/**$

• Debug tab M-bM-^@M-^T shows all real BLE stats, signal values, band powers, and disconnect events.$
*$
• Honesty policy:$
The file has Windows CRLF line endings (`$` at end). I’ll rewrite the whole file cleanly with all fixes applied at once — correct imports, `HorizontalDivider`, no redundant conversions:
╭─ write_file ─╮
│ ✏️ Writing app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt (292 lines)
╰──────────────────╯
✓ ✏️ Updated /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt (292 lines)
Now check if any other files still have `Divider` or redundant conversion calls:
╭─ bash ─╮
│  $ # Check for any remaining Divider (non-Horizontal) usage
grep -rn "import androidx.compose.material3.Divider$" \                                                                                                                             
  /mnt/c/Users/in-ryzen-machine/AndroidStudioPr…                                                                                                                                    
╰────────────╯
✓ bash

166:            key   = “${band.label}  {band.loHz.toInt()}-{band.hiHz.toInt()}Hz”,
No more `Divider` imports anywhere, no redundant conversions. `loHz.toInt()` and `hiHz.toInt()` are `Float → Int` which is correct (not redundant). Now verify the `roundTo3()` return is a `String` going into `value: String` — the old code had `.roundTo3().toString()` which caused the “Redundant call of conversion method” warning because `roundTo3()` already returns `String`. That’s already fixed in the new file. Let me do one final sweep for any remaining warnings across all files:
╭─ bash ─╮
│  $ # Redundant toString() on something already a String
grep -rn '\.toString()' \                                                                                                                                                           
  /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/j…                                                                                                  
╰────────────╯
✓ bash

/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt:129:        DebugRow(“total_samples”, d.totalSamples.toString())
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt:130:        DebugRow(“ble_notifies”, d.bleNotifyCount.toString())
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt:131:        DebugRow(“seq_gaps”, d.seqGaps.toString(),
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt:133:        DebugRow(“ignored_payloads”, d.ignoredPayloads.toString(),
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt:144:        DebugRow(“reconnect_attempt”, state.attempt.toString())
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt:153:    DebugRow(“last_raw_count”, d.lastRawCount.toString())
— check BrainMeter / BrainScreen / AdviceLine for Divider —
none
Those `.toString()` calls are all on `Long` / `Int` values — converting a number to `String` is not redundant. All clean. Here’s the exact summary of every fix applied:

---

Changes made to `DebugScreen.kt`│ Issue                                                                                            │ Fix                                                                                                                                                                                                                            │
│──────────────────────────────────────────────────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────│
│ `Unresolved reference 'EegDebugStats'`                                                           │ Added `import dev.neurofocus.neurfocus_dnd.brain.domain.EegDebugStats` — the type is used in `SignalBlock(d: EegDebugStats)` but was never imported                                                                            │
│ `Unresolved reference 'lastRawCount'`, `lastMicrovoltsCorrected`, `windowRmsUv`, `transportMode` │ These are fields on `EegDebugStats`, which resolves automatically once the import above is added                                                                                                                               │
│ `'Divider' is deprecated. Renamed to HorizontalDivider`                                          │ `import androidx.compose.material3.Divider` → `import androidx.compose.material3.HorizontalDivider`, call site updated to `HorizontalDivider(...)`                                                                             │                                   
│ `Redundant call of conversion method`                                                            │ `total.roundTo3().toString()` → `total.roundTo3()` — `roundTo3()` already retur✔ ✨ Done
                                                                                                                                                                                    
[auto-compacted: removed 18 messages]
> follow the color of global palatte, cuz we see weird colors on navigation bottom tab, and weird
✓ glob_search matched 0 files
Let me check the current theme and navigation colors:
╭─ bash ─╮
│  $ find /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main -name "*.kt" | xargs grep -l "NavigationBar\|BottomNav\|NavBar\|bottomBar" 2>… 
╰────────────╯
✓ bash

/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/MainActivity.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Color.kt

╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Color.kt
echo "---"                                                                                                                                                                          
cat /mnt/c/User…                                                                                                                                                                    
╰────────────╯
✓ bash

package dev.neurofocus.neurfocus_dnd.ui.theme

import androidx.compose.ui.graphics.Color

/** Product palette — glassmorphic neuro-wellness UI (fixed; not dynamic). */
val NeuroSkyBlue = Color(0xFFCDE9FF)
val NeuroCream = Color(0xFFF9F9E0)
val NeuroNavy = Color(0xFF1A1C2E)
val NeuroTextPrimary = Color(0xFF2D3142)
val NeuroTextSecondary = Color(0xFF6B7280)
val NeuroGradientTop = Color(0xFFE8F4FF)
val NeuroGradientMid = Color(0xFFFFF9E6)
val NeuroSurfaceWhite = Color(0xFFFFFFFF)
val NeuroNavBar = Color(0xFF3D4154)
val NeuroPositive = Color(0xFF22C55E)
val NeuroNegative = Color(0xFFEF4444)


/** Legacy names kept for any remaining references. */
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

package dev.neurofocus.neurfocus_dnd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeuroLightScheme = lightColorScheme(
primary = NeuroNavy,
onPrimary = Color.White,
primaryContainer = NeuroSkyBlue,
onPrimaryContainer = NeuroNavy,
secondary = NeuroSkyBlue,
onSecondary = NeuroNavy,
secondaryContainer = NeuroCream,
onSecondaryContainer = NeuroNavy,
tertiary = NeuroCream,
onTertiary = NeuroNavy,
background = NeuroGradientTop,
onBackground = NeuroTextPrimary,
surface = NeuroSurfaceWhite,
onSurface = NeuroTextPrimary,
surfaceVariant = NeuroSkyBlue.copy(alpha = 0.35f),
onSurfaceVariant = NeuroTextSecondary,
outline = NeuroTextSecondary.copy(alpha = 0.35f),
outlineVariant = NeuroSkyBlue.copy(alpha = 0.5f),
)

@Composable
fun NeurfocusdndTheme(
content: @Composable () -> Unit,
) {
MaterialTheme(
colorScheme = NeuroLightScheme,
typography = Typography,
… output truncated for display; full result preserved in session.

╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/MainActivity.kt 
╰────────────╯
✓ bash

package dev.neurofocus.neurfocus_dnd

import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neurofocus.neurfocus_dnd.brain.data.BleDeviceCandidate
import dev.neurofocus.neurfocus_dnd.brain.data.ble.BlePermissions
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.ui.BrainScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.DebugScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.SettingsScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.rememberBrainViewModel
import dev.neurofocus.neurfocus_dnd.onboarding.UserPrefs
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
import dev.neurofocus.neurfocus_dnd.ui.shell.NeuroGradientBackground
import dev.neurofocus.neurfocus_dnd.ui.shell.NeuroTopBar
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroTokens
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme
… output truncated for display; full result preserved in session.

╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/NeuroTokens.kt 2>/dev/null || echo … 
╰────────────╯
✓ bash

package dev.neurofocus.neurfocus_dnd.ui.theme

import androidx.compose.ui.unit.dp

/**

• Single source for spacing, radii, and shell dimensions so screens stay


• consistent and easy to tune without hunting literals.
*/
object NeuroTokens {
val spaceXs = 4.dp
val spaceSm = 8.dp
val spaceMd = 16.dp
val spaceLg = 20.dp

val cornerIcon = 18.dp
val cornerCard = 28.dp
val cornerPill = 22.dp
val cornerTooltip = 20.dp

val topBarIconSize = 48.dp
val topBarHorizontalPadding = spaceLg
val topBarVerticalPadding = 12.dp
val topBarIconSpacing = 10.dp
val topBarIconShadow = 6.dp

val glassCardPadding = spaceLg
val glassCardElevation = 10.dp

val shellContentHorizontal = spaceXs
val contentAboveFloatingNav = 88.dp

const val brainAspectHeightRatio = 0.85f
}
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainScreen.kt:234:    GlassCard(containerColor = NeuroSkyBlue.copy(alpha = 0.4f), contentPadding = NeuroTokens.spaceMd) {
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/SettingsScreen.kt:99:                containerColor = NeuroSkyBlue.copy(alpha = 0.55f),
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/MainActivity.kt:32:import androidx.compose.material3.NavigationBarItem
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/MainActivity.kt:194:                    NavigationBarItem(
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/components/GlassCard.kt:25:    containerColor: Color = NeuroSurfaceWhite,
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/components/GlassCard.kt:39:        color = containerColor,
Now I can see the problem clearly. The `NavigationBar` and `NavigationBarItem` are using default Material3 colors (purple/teal tones from the legacy `Purple40`, `Pink40` tokens that are still in `Color.kt`) instead of the Neuro palette. The `NeuroLightScheme` doesn’t set `navigationBarContainerColor` or `secondaryContainer` (used for the selected indicator) correctly. I need to fix both the color scheme and wire explicit colors into the nav bar:
╭─ edit_file ─╮
│ 📝 Editing /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Theme.kt
╰─────────────────╯
✗ edit_file
missing field `old_string`

╭─ write_file ─╮
│ ✏️ Writing /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Theme.kt (42 lines)
╰──────────────────╯
✓ ✏️ Updated /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Theme.kt (42 lines)
Now also explicitly pass `NavigationBarItemDefaults.colors()` in the nav bar so it’s bulletproof regardless of Material3 version behavior:
╭─ bash ─╮
│  $ grep -n "NavigationBar\b\|NavigationBarItem\b\|import.*NavigationBar" \
  /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev…                                                                                           
╰────────────╯
✓ bash

31:import androidx.compose.material3.NavigationBar
32:import androidx.compose.material3.NavigationBarItem
192:            NavigationBar {
194:                    NavigationBarItem(
Now rewrite just the `NavigationBar` block in `MainActivity.kt` with explicit palette colors:
╭─ bash ─╮
│  $ sed -n '188,215p' /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/MainActivity.kt 
╰────────────╯
✓ bash

╭─ text
            }
        }

        // Bottom Navigation Bar
        NavigationBar {
            NavTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = currentTab == tab,
                    onClick = { selectedTab = tab.ordinal },
                    icon = {
                        Icon(
                            imageVector = when (tab) {
                                NavTab.Home  -> Icons.Default.Home
                                NavTab.Debug -> Icons.Default.BugReport
                            },
                            contentDescription = tab.name,
                        )
                    },
                    label = { Text(tab.name) },
                )
            }
        }
    }
}
╰─

}

@Composable
private fun BleDevicePickerDialog(

╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/MainActivity.kt | \
  python3 -c "                                                                                                                                                                      
import sy…                                                                                                                                                                          
╰────────────╯
✓ bash

FOUND

╭─ bash ─╮
│  $ python3 -c "
import sys                                                                                                                                                                          
                                                                                                                                                                                    
path = '/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/MainActivity.kt…                                            
╰────────────╯
✓ bash

Done
Now add the missing import for `NavigationBarItemDefaults` and `NeuroNavBar`/`NeuroSkyBlue`/`NeuroNavy` in `MainActivity.kt`:
╭─ bash ─╮
│  $ grep -n "import androidx.compose.material3.Navigation\|import dev.neurofocus.neurfocus_dnd.ui.theme.Neuro" \
  /mnt/c/Users/in-ryzen-machine/AndroidStudioProjec…                                                                                                                                
╰────────────╯
✓ bash

31:import androidx.compose.material3.NavigationBar
32:import androidx.compose.material3.NavigationBarItem
59:import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroTokens

╭─ bash ─╮
│  $ python3 -c "
path = '/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/MainActivity.kt'                                            
with open(…                                                                                                                                                                         
╰────────────╯
✓ bash

Done
Now verify the final nav bar block and imports look correct:
╭─ bash ─╮
│  $ grep -n "import.*Navigation\|import.*NeuroNav\|import.*NeuroSky\|import.*NeuroNavy" \
  /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src…                                                                                                         
╰────────────╯
✓ bash


31:import androidx.compose.material3.NavigationBar
32:import androidx.compose.material3.NavigationBarItem
33:import androidx.compose.material3.NavigationBarItemDefaults
60:import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavBar
61:import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavy
62:import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue

31:import androidx.compose.material3.NavigationBar
32:import androidx.compose.material3.NavigationBarItem
196:            NavigationBar(
197:                containerColor = NeuroNavBar,
201:                    NavigationBarItem(
205:                            selectedIconColor = NeuroNavy,
207:                            indicatorColor = NeuroSkyBlue,
208:                            unselectedIconColor = NeuroSkyBlue.copy(alpha = 0.55f),
Everything is wired correctly. Here’s exactly what changed and why:

---

What was wrong & what was fixedRoot cause

Material3’s `NavigationBar` pulls its background from `surfaceContainer` in the color scheme, and the selected-tab indicator pill from `secondaryContainer`. Neither was set — so Material3 fell back to its built-in purple/teal defaults, which had nothing to do with the Neuro palette.`Theme.kt` — color scheme fixes

│ Slot                   │ Before                           │ After                    │ Effect                                 │
│────────────────────────┼──────────────────────────────────┼──────────────────────────┼────────────────────────────────────────│
│ `surfaceContainer`     │ (unset — M3 default purple-grey) │ `NeuroNavBar` `#3D4154`  │ NavBar background = dark navy-grey     │
│ `secondaryContainer`   │ `NeuroCream` (cream/yellow)      │ `NeuroSkyBlue` `#CDE9FF` │ Selected tab indicator pill = sky blue │
│ `onSecondaryContainer` │ `NeuroNavy`                      │ `NeuroNavy`              │ Icon inside selected pill = navy ✓     │
│ `onSurfaceVariant`     │ `NeuroTextSecondary` (grey)      │ `NeuroSkyBlue`           │ Unselected tab icons/labels = sky blue │
│ `secondary`            │ `NeuroSkyBlue`                   │ `NeuroNavy`              │ Fixes secondary button tone            │`MainActivity.kt` — explicit nav bar colors

• `NavigationBar(containerColor = NeuroNavBar)` — locks bar background regardless of scheme version
• `NavigationBarItemDefaults.colors(...)` — explicitly sets all 5 color slots (selected icon, selected text, indicator, unselected icon, unselected text) using only palette tokens
• Added imports: `NavigationBarItemDefaults`, `NeuroNavBar`, `NeuroNavy`, `NeuroSkyBlue`Result: dark `#3D4154` bar · sky-blue `#CDE9FF` indicator pill · navy icon when selected · 5✔ ✨ Done
                                                                                                                                                                                    
[auto-compacted: removed 28 messages]
> you need to make sure every text we use in our app must follow the global UI color palatte, it looks like you are doing something illegal, yk what i am saying, UI must obey the global design
✓ glob_search matched 0 files
Let me audit every text color usage across the entire app:
╭─ bash ─╮
│  $ find /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main -name "*.kt" | xargs grep -n "color\s*=\s*Color\." | grep -v "^Binary" | sort 
╰────────────╯
✓ bash

╭─ bash ─╮
│  $ grep -rn "color\s*=\s*Color\." /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/ 
╰────────────╯
✓ bash exit_code:1

╭─ bash ─╮
│  $ find /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java -name "*.kt" -exec grep -ln "Color\." {} \; 
╰────────────╯
✓ bash

/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Theme.kt

╭─ bash ─╮
│  $ find /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java -name "*.kt" | sort | xargs ls 
╰────────────╯
✓ bash

/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleEegRepository.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BlePermissions.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleSpec.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/BrainDataRepository.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/BrainRepositoryFactory.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/signal/EegSignalProcessor.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainState.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/domain/BrainTypes.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/AdviceLine.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainCanvas.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainScreen.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainViewModel.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/DebugScreen.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/SettingsScreen.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/MainActivity.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/onboarding/UserPrefs.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/onboarding/UserProfile.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/components/GlassCard.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/shell/NeuroGradientBackground.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/shell/NeuroTopBar.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Color.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/NeuroTokens.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Theme.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Type.kt
/mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/util/DispatcherProvider.kt
Now read every UI file in full:
╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Color.kt 
╰────────────╯
✓ bash

package dev.neurofocus.neurfocus_dnd.ui.theme

import androidx.compose.ui.graphics.Color

/** Product palette — glassmorphic neuro-wellness UI (fixed; not dynamic). */
val NeuroSkyBlue = Color(0xFFCDE9FF)
val NeuroCream = Color(0xFFF9F9E0)
val NeuroNavy = Color(0xFF1A1C2E)
val NeuroTextPrimary = Color(0xFF2D3142)
val NeuroTextSecondary = Color(0xFF6B7280)
val NeuroGradientTop = Color(0xFFE8F4FF)
val NeuroGradientMid = Color(0xFFFFF9E6)
val NeuroSurfaceWhite = Color(0xFFFFFFFF)
val NeuroNavBar = Color(0xFF3D4154)
val NeuroPositive = Color(0xFF22C55E)
val NeuroNegative = Color(0xFFEF4444)

/** Legacy names kept for any remaining references. */
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

╭─ bash ─╮
│  $ for f in \
  BrainScreen.kt BrainMeter.kt AdviceLine.kt DebugScreen.kt SettingsScreen.kt \                                                                                                     
  GlassCard.kt NeuroGradientBackground.kt NeuroTopBar.kt MainActivity…                                                                                                              
╰────────────╯
✓ bash

===== BrainScreen.kt =====
package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
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
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
import dev.neurofocus.neurfocus_dnd.ui.components.GlassCard
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavy
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSurfaceWhite
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroTokens
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

@Composable
fun BrainScreen(
profile: UserProfile,
modifier: Modifier = Modifier,
viewModel: BrainViewModel = rememberBrainViewModel(),
) {
val state by viewModel.state.collectAsStateWithLifecycle()
BrainScreenContent(state = state, profile = profile, modifier = modifier)
}
… output truncated for display; full result preserved in session.

╭─ bash ─╮
│  $ for f in AdviceLine.kt DebugScreen.kt SettingsScreen.kt GlassCard.kt NeuroGradientBackground.kt NeuroTopBar.kt; do
  echo "===== $f ====="                                                                                                                                                             
  find /mnt/c/Users/i…                                                                                                                                                              
╰────────────╯
✓ bash

===== AdviceLine.kt =====
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

• Single sentence of “do this next” advice.
• 
• v1: deterministic physiological copy keyed to focus heuristic.
• Battery: firmware doesn’t report it, so advice never references it.
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
focus >= 0.70f -> “Peak focus. Begin your most demanding task now.”
focus >= 0.50f -> “Steady. Ship something difficult before it slips.”
focus >= 0.30f -> “Focus dipping. A short pause prevents a long one.”
else           -> “Three minutes of stillness. Then return.”
}
}
BrainState.Idle, BrainState.Searching, BrainState.Connecting -> null
is BrainState.Reconnecting -> “Reconnecting to headband…”
is BrainState.Error -> null
}

@Preview(showBackground = true, widthDp = 360, heightDp = 80)
@Composable
… output truncated for display; full result preserved in session.

╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/SettingsScreen.kt
echo "====="                                                                                                                                                                        
cat …                                                                                                                                                                               
╰────────────╯
✓ bash

package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
import dev.neurofocus.neurfocus_dnd.ui.components.GlassCard
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavy
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroTokens
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

@Composable
fun SettingsScreen(
profile: UserProfile,
onResetProfile: () -> Unit,
modifier: Modifier = Modifier,
onNavigateBack: (() -> Unit)? = null,
) {
Column(
modifier = modifier
.fillMaxSize()
.padding(horizontal = NeuroTokens.spaceMd, vertical = NeuroTokens.spaceSm)
.padding(bottom = NeuroTokens.contentAboveFloatingNav),
verticalArrangement = Arrangement.spacedBy(NeuroTokens.spaceMd),
) {
Row(
modifier = Modifier.fillMaxWidth(),
verticalAlignment = Alignment.CenterVertically,
horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
if (onNavigateBack != null) {
IconButton(onClick = onNavigateBack) {
Icon(
imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
contentDescription = “Back”,
tint = MaterialTheme.colorScheme.onSurface,
)
}
… output truncated for display; full result preserved in session.
Now I have a complete picture. The problems are:1. `BrainMeter.kt` — uses `MaterialTheme.colorScheme.primary` (navy on navy = invisible), `onSurface`, `outline` — these need explicit palette tokens
2. `DebugScreen.kt` — uses raw `Color(0xFF...)` hex literals everywhere (acceptable for a terminal-style debug screen, but the background `0xFF0D0D0D` and container `0xFF1A1A1A` should be `NeuroNavBar`/`NeuroNavy`)
3. `AdviceLine.kt` — `onSurface.copy(alpha=0.78f)` is fine since `onSurface = NeuroTextPrimary`
4. `Theme.kt` — `onSurface = NeuroTextPrimary` (dark navy text on light bg ✓), but `onSurfaceVariant = NeuroSkyBlue` means subtitle text is pale blue on white — needs to be `NeuroTextSecondary` for readability                                                                                                                                                       
5. `BrainScreen.kt` — `colorScheme.onSurfaceVariant` for the subtitle — will be fixed by Theme fixLet me fix all of these now:
╭─ bash ─╮
│  $ cat /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/ui/BrainMeter.kt 
╰────────────╯
✓ bash

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

• Braun ET66-inspired stats row.
• Battery: firmware does not report it — shows “–” instead of a fake number.
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
// Battery: show “–” because firmware does not transmit this
val batteryDisplay = state.battery?.value ?: -1
if (batteryDisplay >= 0) {
Stat(label = “BATTERY”, percent = batteryDisplay)
} else {
StatUnavailable(label = “BATTERY”, reason = “N/A”)
}
VerticalDivider(
modifier = Modifier.height(48.dp),
color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
)
… output truncated for display; full result preserved in session.
Now I have everything. Let me fix all files in one pass:
╭─ write_file ─╮
│ ✏️ Writing /mnt/c/Users/in-ryzen-machine/AndroidStudioProjects/neurfocusdnd/app/src/main/java/dev/neurofocus/neurfocus_dnd/ui/theme/Theme.kt (49 lines)
╰──────────────────╯
✓ ✏️ Updated /mnt/c/Users/
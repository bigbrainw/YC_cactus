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
import androidx.compose.material3.HorizontalDivider
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
import dev.neurofocus.neurfocus_dnd.brain.domain.EegDebugStats
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavBar
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavy
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Debug terminal palette — semantic signal colors on the dark debug background ──────────
// Background uses the global NeuroNavy/NeuroNavBar tokens.
// Text signal colors (green/red/yellow/blue) are terminal conventions, not UI chrome.
private val DbgBackground  = NeuroNavy                    // #1A1C2E — dark navy
private val DbgSurface     = NeuroNavBar                  // #3D4154 — card/row surface
private val DbgTextKey     = NeuroSkyBlue.copy(alpha = 0.7f) // muted sky for key column
private val DbgTextValue   = Color(0xFFECEFF1)            // near-white for value column
private val DbgGood        = Color(0xFF00E676)            // green  — healthy / live
private val DbgWarn        = Color(0xFFFFD740)            // amber  — gaps / reconnect
private val DbgError       = Color(0xFFFF5252)            // red    — error / timeout
private val DbgIdle        = Color(0xFFB0BEC5)            // grey   — idle / searching
private val DbgHeader      = NeuroSkyBlue                 // section titles = sky blue
private val DbgDivider     = NeuroNavBar                  // divider line = nav bar grey

/**
 * Debug tab — shows all real BLE stats, signal values, band powers, and disconnect events.
 *
 * Honesty policy:
 *   - Every value displayed here comes directly from the real BLE stream.
 *   - If a value is unavailable, "--" is shown. No fake numbers.
 *   - Focus score is labeled "heuristic (Goertzel/band-ratio)" — not the trained LR classifier.
 *   - Battery: firmware does not transmit battery level → shown as "N/A (firmware)".
 */
@Composable
fun DebugScreen(
    viewModel: BrainViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val disconnectLog by viewModel.disconnectLog.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DbgBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            DebugSectionHeader("BLE STATE")
            BleStateBlock(state)
        }

        if (state is BrainState.Live) {
            val live = state as BrainState.Live
            item {
                Spacer(Modifier.height(4.dp))
                DebugSectionHeader("SIGNAL  (counts→µV, Vref=3.3V, PGA=1, INAMP=100, inverted)")
                SignalBlock(live.debugStats)
            }
            item {
                Spacer(Modifier.height(4.dp))
                DebugSectionHeader("BAND POWERS  (Goertzel relative, 1-40Hz, 6 bands = zuna BAND_DEFS)")
                BandPowerBlock(live.bandPowers)
            }
            item {
                Spacer(Modifier.height(4.dp))
                DebugSectionHeader("FOCUS HEURISTIC  (band-ratio, NOT trained LR classifier)")
                FocusBlock(live)
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            DebugSectionHeader("SIGNAL CHAIN CONSTANTS  (source: neurofocus_to_fif.py)")
            SignalChainConstantsBlock()
        }

        item {
            Spacer(Modifier.height(4.dp))
            DebugSectionHeader("DISCONNECT LOG  (last ${disconnectLog.size}, GATT status codes)")
            if (disconnectLog.isEmpty()) {
                DebugRow("events", "none yet")
            }
        }

        items(disconnectLog.reversed()) { event ->
            DisconnectEventRow(event)
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun BleStateBlock(state: BrainState) {
    val statusText = when (state) {
        BrainState.Idle        -> "IDLE"
        BrainState.Searching   -> "SEARCHING (scanning BLE for NEUROFOCUS_V4...)"
        BrainState.Connecting  -> "CONNECTING (GATT setup in progress)"
        is BrainState.Live     -> "LIVE ✓  transport=${state.debugStats.transportMode}"
        is BrainState.Reconnecting -> "RECONNECTING  attempt=${state.attempt}  last=${state.lastDisconnect?.message ?: "—"}"
        is BrainState.Error    -> "ERROR: ${state.message}"
    }
    DebugRow("state", statusText, valueColor = when (state) {
        is BrainState.Live         -> DbgGood
        is BrainState.Error        -> DbgError
        is BrainState.Reconnecting -> DbgWarn
        else                       -> DbgIdle
    })

    if (state is BrainState.Live) {
        val d = state.debugStats
        DebugRow("total_samples", d.totalSamples.toString())
        DebugRow("ble_notifies", d.bleNotifyCount.toString())
        DebugRow("seq_gaps", d.seqGaps.toString(),
            valueColor = if (d.seqGaps > 0) DbgWarn else null)
        DebugRow("ignored_payloads", d.ignoredPayloads.toString(),
            valueColor = if (d.ignoredPayloads > 0) DbgWarn else null)
        DebugRow("effective_rate",
            "${d.effectiveRateSps.roundTo1()}  SPS  (firmware ADC rate: 600 SPS, BLE ~253 SPS expected)")
        DebugRow("window_samples", "${d.windowSamples}  (target: ~1265 for 5s @ 253 SPS)")
        val ageMs = System.currentTimeMillis() - d.lastNotifyMs
        DebugRow("last_notify_ms_ago", "${ageMs}ms",
            valueColor = if (ageMs > 3000) DbgError else DbgGood)
    }

    if (state is BrainState.Reconnecting) {
        DebugRow("reconnect_attempt", state.attempt.toString())
        state.lastDisconnect?.let {
            DebugRow("last_gatt_status", "${it.gattStatus}  ${it.message}")
        }
    }
}

@Composable
private fun SignalBlock(d: EegDebugStats) {
    DebugRow("last_raw_count", d.lastRawCount.toString())
    DebugRow("last_µV_corrected", "${d.lastMicrovoltsCorrected.roundTo2()} µV")
    DebugRow("window_rms_µV", "${d.windowRmsUv.roundTo2()} µV")
    DebugRow("transport", d.transportMode)
}

@Composable
private fun BandPowerBlock(bandPowers: Map<EegBand, Float>) {
    EegBand.entries.forEach { band ->
        val power = bandPowers[band] ?: 0f
        val pct = (power * 100f).roundTo1()
        val bar = buildBar(power)
        DebugRow(
            key   = "${band.label}  ${band.loHz.toInt()}-${band.hiHz.toInt()}Hz",
            value = "$pct%  $bar",
            valueColor = bandColor(band),
        )
    }
    val total = bandPowers.values.sum()
    DebugRow(
        key = "sum (should=1.0)",
        value = total.roundTo3(),
        valueColor = if (abs(total - 1f) < 0.01f) DbgGood else DbgError,
    )
}

@Composable
private fun FocusBlock(live: BrainState.Live) {
    val f = live.focus.value
    val pct = (f * 100).roundToInt()
    val engaged = f >= 0.5f
    DebugRow(
        "p_engaged",
        "$pct%  ${if (engaged) ">> ENGAGED" else "  resting"}",
        valueColor = if (engaged) DbgGood else DbgIdle,
    )
    DebugRow("raw_envelope_µV", "${live.rawEnvelopeUv.roundTo2()} µV")
    DebugRow("battery", live.battery?.let { "${it.value}%" } ?: "N/A (firmware does not transmit battery)")
    DebugRow("electrode", live.electrodeSite.name)
}

@Composable
private fun SignalChainConstantsBlock() {
    DebugRow("Vref",       "${EegSignalProcessor.VREF_V} V  (firmware setVRefValue_V(3.3))")
    DebugRow("PGA gain",   "${EegSignalProcessor.PGA_GAIN}  (firmware setGain(ADS1220_GAIN_1))")
    DebugRow("INAMP gain", "${EegSignalProcessor.INAMP_GAIN}  (AD8422, R6=100Ω → 1+9900/100=100)")
    DebugRow("ADC bits",   "24  (ADS1220, full scale 2^23 = 8388608)")
    DebugRow("polarity",   "negated  (IN+ mastoid, IN- Fp1 → EEG convention)")
    DebugRow("formula",    "µV = -(count × 3.3/8388608/100 × 1e6)")
    DebugRow("bands",      "6  (delta θ α low-β high-β γ) matching features.py BAND_DEFS")
    DebugRow("window",     "5.0 s  (FIXED — matches realtime_focus.py WINDOW_S)")
    DebugRow("hop",        "2.5 s  (FIXED — matches realtime_focus.py HOP_S)")
}

@Composable
private fun DisconnectEventRow(event: DisconnectEvent) {
    val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    val time = fmt.format(Date(event.timestampMs))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DbgSurface, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "[$time] status=${event.gattStatus}  state=${event.newState}",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = DbgWarn,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = "  ${event.message}",
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        color = DbgIdle,
        modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun DebugSectionHeader(title: String) {
    Text(
        text = title,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = DbgHeader,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
    HorizontalDivider(color = DbgDivider, thickness = 1.dp)
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun DebugRow(key: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = key,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = DbgTextKey,
            modifier = Modifier.width(200.dp),
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = valueColor ?: DbgTextValue,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            maxLines = 2,
        )
    }
}

private fun buildBar(fraction: Float, width: Int = 20): String {
    val filled = (fraction * width).roundToInt().coerceIn(0, width)
    return "[" + "█".repeat(filled) + "░".repeat(width - filled) + "]"
}

private fun bandColor(band: EegBand): Color = when (band) {
    EegBand.Delta    -> NeuroSkyBlue.copy(alpha = 0.8f)
    EegBand.Theta    -> Color(0xFF80DEEA)
    EegBand.Alpha    -> Color(0xFFA5D6A7)
    EegBand.LowBeta  -> Color(0xFFFFCC02)
    EegBand.HighBeta -> Color(0xFFFFAB40)
    EegBand.Gamma    -> Color(0xFFFF8A65)
}

private fun Float.roundTo1(): String = "%.1f".format(this)
private fun Float.roundTo2(): String = "%.2f".format(this)
private fun Float.roundTo3(): String = "%.3f".format(this)

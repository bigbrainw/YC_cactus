package dev.neurofocus.neurfocus_dnd.brain.data

import dev.neurofocus.neurfocus_dnd.brain.domain.BatteryPercent
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.ElectrodeSite
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Deterministic synthetic EEG source for UI development without hardware.
 *
 * Emits a [BrainState.Live] every [updateIntervalMs] with band powers driven
 * by independent sine waves of different periods, so the brain visualization
 * has visible, smooth, non-trivial motion.
 *
 * NOT for production. Replace with a real BLE-backed implementation in Phase 4.
 */
class FakeEegRepository(
    private val dispatchers: DispatcherProvider,
    private val electrodeSite: ElectrodeSite = ElectrodeSite.Fp1,
    private val updateIntervalMs: Long = 100L,
) : BrainDataRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private var pumpJob: Job? = null
    private var streamStartedAtMs: Long = 0L

    private val _state = MutableStateFlow<BrainState>(BrainState.Idle)
    override val state: StateFlow<BrainState> = _state.asStateFlow()

    override suspend fun connect() {
        if (pumpJob?.isActive == true) return

        _state.update { BrainState.Searching }
        delay(SEARCH_DELAY_MS)
        _state.update { BrainState.Connecting }
        delay(CONNECT_DELAY_MS)

        streamStartedAtMs = System.currentTimeMillis()
        pumpJob = scope.launch { runEmitter() }
    }

    override suspend fun disconnect() {
        pumpJob?.cancel()
        pumpJob = null
        _state.update { BrainState.Idle }
    }

    override fun dispose() {
        pumpJob?.cancel()
        pumpJob = null
        scope.cancel()
    }

    private suspend fun runEmitter() {
        while (scope.isActive) {
            val tSec = (System.currentTimeMillis() - streamStartedAtMs) / 1_000f
            val bands = synthesizeBands(tSec)
            val focus = computeFocus(bands)
            val envelope = abs(sin(tSec * 2f * PI.toFloat() * RAW_ENVELOPE_HZ))

            _state.update {
                BrainState.Live(
                    battery = BatteryPercent(estimateBattery(tSec)),
                    focus = FocusScore(focus),
                    bandPowers = bands,
                    rawEnvelope = envelope,
                    electrodeSite = electrodeSite,
                )
            }
            delay(updateIntervalMs)
        }
    }

    private fun synthesizeBands(tSec: Float): Map<EegBand, Float> = mapOf(
        EegBand.Delta to wave(tSec, periodSec = 17f, phase = 0.0f, base = 0.30f, amp = 0.15f),
        EegBand.Theta to wave(tSec, periodSec = 11f, phase = 1.1f, base = 0.25f, amp = 0.20f),
        EegBand.Alpha to wave(tSec, periodSec = 7f,  phase = 2.3f, base = 0.45f, amp = 0.30f),
        EegBand.Beta  to wave(tSec, periodSec = 5f,  phase = 0.7f, base = 0.40f, amp = 0.30f),
        EegBand.Gamma to wave(tSec, periodSec = 3f,  phase = 1.9f, base = 0.20f, amp = 0.20f),
    )

    private fun wave(
        tSec: Float,
        periodSec: Float,
        phase: Float,
        base: Float,
        amp: Float,
    ): Float {
        val raw = base + amp * sin(2f * PI.toFloat() * tSec / periodSec + phase)
        return raw.coerceIn(0f, 1f)
    }

    /**
     * Beta + Gamma dominance over Theta is a common (rough) heuristic for
     * sustained attention. This is a demo formula, not a clinical metric.
     */
    private fun computeFocus(bands: Map<EegBand, Float>): Float {
        val beta = bands[EegBand.Beta] ?: 0f
        val gamma = bands[EegBand.Gamma] ?: 0f
        val theta = bands[EegBand.Theta] ?: 0f
        val numerator = beta + 0.5f * gamma
        val denominator = numerator + theta + EPSILON
        return (numerator / denominator).coerceIn(0f, 1f)
    }

    private fun estimateBattery(tSec: Float): Int {
        val drainPercent = (tSec / SECONDS_PER_BATTERY_PERCENT).toInt()
        return (100 - drainPercent).coerceIn(0, 100)
    }

    private companion object {
        const val SEARCH_DELAY_MS = 800L
        const val CONNECT_DELAY_MS = 600L
        const val RAW_ENVELOPE_HZ = 8f
        const val SECONDS_PER_BATTERY_PERCENT = 60f
        const val EPSILON = 1e-6f
    }
}

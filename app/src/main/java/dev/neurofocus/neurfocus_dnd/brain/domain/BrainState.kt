package dev.neurofocus.neurfocus_dnd.brain.domain

/**
 * Single source of truth for everything the brain UI renders.
 * The sealed hierarchy is deliberately exhaustive — `when` over BrainState
 * must omit `else` so the compiler enforces updates if a new state appears.
 */
sealed interface BrainState {
    data object Idle : BrainState

    data object Searching : BrainState

    data object Connecting : BrainState

    data class Live(
        /** Relative band powers (0..1), keyed by EegBand. REAL values from Goertzel/Welch — not faked. */
        val bandPowers: Map<EegBand, Float>,
        /** Focus probability (0..1). 0.5 threshold = engaged. Real heuristic from windowed band powers. */
        val focus: FocusScore,
        /** Raw peak envelope in microvolts (absolute value of last converted sample). Real. */
        val rawEnvelopeUv: Float,
        /** Stats for the Debug tab. Always present when Live. */
        val debugStats: EegDebugStats,
        /** Battery percent — firmware does not currently report this; will show null in UI. */
        val battery: BatteryPercent? = null,
        /** Electrode site — hardware fixed at Fp1. */
        val electrodeSite: ElectrodeSite = ElectrodeSite.Fp1,
    ) : BrainState

    data class Error(val message: String) : BrainState

    /** Reconnecting after a disconnect — distinct from initial Searching to preserve last stats. */
    data class Reconnecting(
        val attempt: Int,
        val lastDisconnect: DisconnectEvent?,
    ) : BrainState
}

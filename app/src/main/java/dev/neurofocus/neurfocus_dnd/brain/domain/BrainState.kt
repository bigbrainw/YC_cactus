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
        val battery: BatteryPercent,
        val focus: FocusScore,
        val bandPowers: Map<EegBand, Float>,
        val rawEnvelope: Float,
        val electrodeSite: ElectrodeSite,
    ) : BrainState

    data class Error(val message: String) : BrainState
}

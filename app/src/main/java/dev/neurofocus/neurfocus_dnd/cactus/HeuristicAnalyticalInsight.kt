package dev.neurofocus.neurfocus_dnd.cactus

import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand

/**
 * Deterministic single-sentence readout from relative band powers (Fp1 / single-channel proxy).
 * Not a medical interpretation — avoids claiming diagnosis.
 */
fun buildHeuristicAnalyticalInsight(live: BrainState.Live): String {
    val p = live.bandPowers
    fun v(b: EegBand) = p[b] ?: 0f
    val d = v(EegBand.Delta)
    val t = v(EegBand.Theta)
    val a = v(EegBand.Alpha)
    val lb = v(EegBand.LowBeta)
    val hb = v(EegBand.HighBeta)
    val g = v(EegBand.Gamma)
    val focus = live.focus.value
    val dominant = EegBand.entries.maxBy { v(it) }
    val shape = when {
        lb + hb > a + 0.15f -> "beta-leaning versus alpha"
        a > lb + hb + 0.1f -> "alpha-leaning (relaxed wake / idle bias)"
        t > d + 0.12f -> "theta-leaning versus delta"
        d > 0.22f -> "slow-wave/delta prominence (consider drowsiness or artifact)"
        g > 0.18f -> "gamma-leaning (active processing proxy)"
        else -> "mixed bands without a sharp dominant"
    }
    return "Fp1-style proxy looks $shape with ${dominant.label} strongest and focus heuristic ${(focus * 100).toInt()}% (not clinical advice)."
}

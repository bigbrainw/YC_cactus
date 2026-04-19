package dev.neurofocus.neurfocus_dnd.cactus

import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand

/**
 * Deterministic one-liner from relative band powers (Fp1 / single-channel proxy).
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
    val parts = mutableListOf<String>()
    parts += when {
        lb + hb > a + 0.15f -> "Frontal-motor band energy is elevated versus alpha."
        a > lb + hb + 0.1f -> "Posterior alpha is relatively strong — idling / relaxed wakefulness bias."
        t > d + 0.12f      -> "Theta is prominent versus delta — cognitive control / drowsiness axis worth watching."
        d > 0.22f          -> "Slow delta power is nontrivial — check for drowsiness or eye/artifact."
        g > 0.18f          -> "Gamma-band relative power is up — binding / active processing proxy at Fp1."
        else               -> "Band distribution is mixed; no single band dominates strongly."
    }
    parts += " Dominant relative band: ${dominant.label}. "
    parts += "Focus heuristic ${(focus * 100).toInt()}% (not the trained offline classifier). "
    if (!live.debugStats.transportMode.equals("ascii", ignoreCase = true)) {
        parts += "Transport ${live.debugStats.transportMode.uppercase()} @ ${live.debugStats.effectiveRateSps.toInt()} SPS."
    }
    return parts.joinToString("").trim()
}

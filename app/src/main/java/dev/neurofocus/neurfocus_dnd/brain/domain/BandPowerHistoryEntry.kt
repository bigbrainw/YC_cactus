package dev.neurofocus.neurfocus_dnd.brain.domain

/**
 * One row for debug heatmap / time series of relative band powers.
 */
data class BandPowerHistoryEntry(
    val timestampMs: Long,
    val bands: Map<EegBand, Float>,
)

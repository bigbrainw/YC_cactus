package dev.neurofocus.neurfocus_dnd.brain.domain

@JvmInline
value class FocusScore(val value: Float) {
    init { require(value in 0f..1f) { "FocusScore must be 0..1, got $value" } }
}

@JvmInline
value class BatteryPercent(val value: Int) {
    init { require(value in 0..100) { "BatteryPercent must be 0..100, got $value" } }
}

enum class BrainRegion {
    FrontalL, FrontalR,
    ParietalL, ParietalR,
    TemporalL, TemporalR,
    OccipitalL, OccipitalR,
    Whole,
}

/**
 * Standard EEG frequency bands. The Hz ranges follow common clinical convention
 * and define which raw signal frequencies contribute to each band's power.
 */
enum class EegBand(val rangeHz: ClosedFloatingPointRange<Float>) {
    Delta(0.5f..4f),
    Theta(4f..8f),
    Alpha(8f..13f),
    Beta(13f..30f),
    Gamma(30f..45f),
}

/**
 * Physical electrode location on the scalp (10-20 system subset).
 * Hardware decision — defaults to Fp1 for consumer headband form factors.
 */
enum class ElectrodeSite { Fp1, Fp2, Cz, O1, O2 }

/**
 * One ADC sample as it arrives from the firmware. The firmware streams a
 * signed 24-bit count; this wrapper holds the converted microvolt value
 * plus the wall-clock arrival time used by FFT windowing.
 */
data class ChannelSample(val microvolts: Float, val timestampMs: Long)

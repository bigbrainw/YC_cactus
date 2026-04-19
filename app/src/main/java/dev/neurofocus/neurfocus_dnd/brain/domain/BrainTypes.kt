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
 * EEG frequency bands — exactly matching zuna_process/focus/features.py BAND_DEFS.
 * Do NOT change these ranges without retraining the focus classifier.
 *
 *   delta:     1-4 Hz    (drowsiness, slow waves)
 *   theta:     4-8 Hz    (frontal cognitive control)
 *   alpha:     8-12 Hz   (relaxed wakefulness; desynchronizes with attention)
 *   low_beta:  12-20 Hz  (active focus)
 *   high_beta: 20-30 Hz  (high arousal, anxiety)
 *   gamma:     30-40 Hz  (cognitive binding, perception)
 *
 * Source: zuna_process/focus/features.py BAND_DEFS
 */
enum class EegBand(
    val loHz: Float,
    val hiHz: Float,
    val label: String,
) {
    Delta   (1f,  4f,  "δ Delta"),
    Theta   (4f,  8f,  "θ Theta"),
    Alpha   (8f,  12f, "α Alpha"),
    LowBeta (12f, 20f, "β Low-β"),
    HighBeta(20f, 30f, "β High-β"),
    Gamma   (30f, 40f, "γ Gamma"),
}

/** Physical electrode location on the scalp (10-20 system). Hardware: Fp1 (forehead). */
enum class ElectrodeSite { Fp1, Fp2, Cz, O1, O2 }

/**
 * One ADC sample as it arrives from the firmware.
 * raw = ADS1220 signed 24-bit count (as delivered over BLE, before any conversion).
 */
data class ChannelSample(val rawCount: Long, val timestampMs: Long)

/**
 * Live stats exposed to the Debug tab. Every field is the real computed value —
 * no fakes. Fields that are not yet available are null.
 *
 * Source of truth for field definitions: zuna_process/scripts/ble_eeg_receiver.py
 */
data class EegDebugStats(
    /** Total samples received since connect */
    val totalSamples: Long,
    /** Effective delivery rate calculated from inter-sample timestamps */
    val effectiveRateSps: Float,
    /** Number of BLE notification packets received */
    val bleNotifyCount: Long,
    /** Number of sequence-number gaps detected (binary framing only) */
    val seqGaps: Int,
    /** Number of payloads that could not be parsed */
    val ignoredPayloads: Int,
    /** Most recent raw ADS1220 count (signed 24-bit) */
    val lastRawCount: Long,
    /** Most recent converted value in microvolts (after gain correction + polarity flip) */
    val lastMicrovoltsCorrected: Float,
    /** RMS of the current analysis window in microvolts */
    val windowRmsUv: Float,
    /** Analysis window length in samples */
    val windowSamples: Int,
    /** Transport mode detected from data ("binary" | "ascii" | "none") */
    val transportMode: String,
    /** Timestamp of last successful BLE notification (System.currentTimeMillis) */
    val lastNotifyMs: Long,
)

/** A single logged disconnect event for the Debug tab. */
data class DisconnectEvent(
    val timestampMs: Long,
    /** Android GATT status code (0 = GATT_SUCCESS, 8 = GATT_CONN_TIMEOUT, 19 = GATT_CONN_TERMINATE_PEER_USER, etc.) */
    val gattStatus: Int,
    /** Android connection newState (BluetoothProfile.STATE_*) */
    val newState: Int,
    val message: String,
)

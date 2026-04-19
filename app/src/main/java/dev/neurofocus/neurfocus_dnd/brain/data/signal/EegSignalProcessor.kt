package dev.neurofocus.neurfocus_dnd.brain.data.signal

import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Real-time EEG signal processor — mirrors the zuna_process Python pipeline
 * (zuna_process/focus/features.py + neurofocus_to_fif.py) running on-device.
 *
 * Signal chain (matching firmware + zuna_process exactly):
 *
 *   ADS1220 raw count  (24-bit signed integer)
 *     → counts_to_volts()  [V_adc = count * Vref / 2^23]
 *     → electrode volts    [V_eeg = V_adc / INAMP_GAIN]
 *     → negate             [IN+ = mastoid, IN- = Fp1 → flip to EEG convention]
 *     → microvolts         [µV = V_eeg * 1e6]
 *     → rolling 5 s window at ~253 SPS ≈ 1265 samples
 *     → Goertzel band powers (1-40 Hz, 6 bands matching BAND_DEFS)
 *     → relative powers (divided by total 1-40 Hz power)
 *     → focus heuristic (beta + gamma dominance)
 *
 * Source verification:
 *   - Vref = 3.3 V : neurofocus_to_fif.py --vref 3.3
 *   - PGA_GAIN = 1  : neurofocus_to_fif.py --pga-gain 1.0
 *   - INAMP_GAIN = 100 : AD8422, R6=100Ω → G=1+9900/100=100
 *   - Invert = true : IN+ on mastoid, IN- on Fp → negate for EEG convention
 *   - Bands : features.py BAND_DEFS exactly
 *   - Window = 5.0 s, hop = 2.5 s : realtime_focus.py WINDOW_S / HOP_S (FIXED)
 */
object EegSignalProcessor {

    // --- Firmware-derived constants (do not change without firmware/hardware change) ---

    /** ADS1220 reference voltage (V). Source: firmware ads1220_driver.cpp setVRefValue_V(3.3) */
    const val VREF_V: Float = 3.3f

    /** ADS1220 PGA gain. Source: firmware setGain(ADS1220_GAIN_1) */
    const val PGA_GAIN: Float = 1.0f

    /** AD8422 in-amp gain. Source: hardware R6=100Ω → G=1+9.9kΩ/100=100. */
    const val INAMP_GAIN: Float = 100.0f

    /** 2^23 = 8388608. Full-scale count for 24-bit signed ADC. */
    private const val ADC_FULL_SCALE: Float = 8_388_608f

    /**
     * Convert ADS1220 raw 24-bit signed count to electrode voltage in microvolts.
     *
     * Formula (from neurofocus_to_fif.py):
     *   V_adc = count * (Vref / PGA_GAIN) / 2^23
     *   V_eeg = V_adc / INAMP_GAIN
     *   µV    = V_eeg * 1_000_000
     *   sign  = negate (IN+ mastoid, IN- Fp → flip to EEG convention)
     */
    fun countsToMicrovolts(rawCount: Long): Float {
        val vAdc = rawCount.toFloat() * (VREF_V / PGA_GAIN) / ADC_FULL_SCALE
        val vEeg = vAdc / INAMP_GAIN
        return -(vEeg * 1_000_000f)  // negate: mastoid as IN+
    }

    /**
     * Compute relative band powers from a window of microvolts using Goertzel algorithm.
     *
     * Goertzel is efficient for computing DFT power at specific frequencies without
     * a full FFT. We run it at the center frequency of each band and sum over the
     * band's frequency range at bin resolution.
     *
     * Returns: map of EegBand → relative power (0..1).
     * All values sum to 1.0 (relative to total 1-40 Hz power).
     * If the window is too short or all-zero, returns equal weights (0.167 each).
     *
     * Source: matches zuna_process/focus/features.py _band_power() + welch() logic,
     * adapted to real-time Goertzel instead of offline Welch (same frequency bins,
     * same band integration).
     */
    fun computeRelativeBandPowers(microvoltWindow: FloatArray, sampleRateHz: Float): Map<EegBand, Float> {
        val n = microvoltWindow.size
        if (n < 32 || sampleRateHz <= 0f) {
            return EegBand.entries.associateWith { 1f / EegBand.entries.size }
        }

        // Compute power spectral density using Goertzel at each integer Hz bin 1..40
        val freqResHz = sampleRateHz / n
        val binLo = 1  // 1 Hz
        val binHi = minOf(40, (n / 2))  // 40 Hz or Nyquist

        val binPowers = FloatArray(binHi + 1)
        for (freqHz in binLo..binHi) {
            binPowers[freqHz] = goertzelPower(microvoltWindow, freqHz.toFloat(), sampleRateHz)
        }

        // Integrate power over each band (trapezoid approximation)
        val bandAbsPowers = FloatArray(EegBand.entries.size)
        for ((i, band) in EegBand.entries.withIndex()) {
            val lo = band.loHz.toInt().coerceAtLeast(binLo)
            val hi = minOf(band.hiHz.toInt(), binHi)
            var sum = 0f
            for (f in lo..hi) {
                sum += binPowers[f]
            }
            bandAbsPowers[i] = sum.coerceAtLeast(0f)
        }

        // Total = sum of 1-40 Hz power
        val total = bandAbsPowers.sum()
        if (total <= 0f) {
            return EegBand.entries.associateWith { 1f / EegBand.entries.size }
        }

        return EegBand.entries.mapIndexed { i, band ->
            band to (bandAbsPowers[i] / total).coerceIn(0f, 1f)
        }.toMap()
    }

    /**
     * Goertzel algorithm: computes |DFT|^2 at a single target frequency.
     * Much cheaper than FFT when you only need a few bins.
     *
     * Returns power (magnitude squared) at targetHz.
     */
    private fun goertzelPower(samples: FloatArray, targetHz: Float, sampleRateHz: Float): Float {
        val n = samples.size
        val k = (n * targetHz / sampleRateHz + 0.5f).toInt()
        val omega = 2.0 * PI * k / n
        val coeff = (2.0 * cos(omega)).toFloat()
        var q0 = 0f
        var q1 = 0f
        var q2 = 0f
        for (x in samples) {
            q0 = coeff * q1 - q2 + x
            q2 = q1
            q1 = q0
        }
        val real = q1 - q2 * cos(omega).toFloat()
        val imag = (q2 * sin(omega)).toFloat()
        return real * real + imag * imag
    }

    /**
     * Compute RMS of a microvolts array.
     */
    fun rmsUv(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s.toDouble()
        return sqrt(sum / samples.size).toFloat()
    }

    /**
     * Focus heuristic from relative band powers.
     *
     * Mirrors the rough logic in realtime_focus.py:
     *   engaged ↑ = higher low_beta + high_beta + gamma
     *   engaged ↓ = higher delta + theta
     *
     * This is NOT the trained LR classifier (that requires training data).
     * This is a physiologically-grounded heuristic for on-device real-time use.
     * The Debug tab clearly labels it as "heuristic" not "model".
     *
     * Range: 0..1 where >0.5 = likely focused.
     */
    fun focusHeuristic(relativePowers: Map<EegBand, Float>): Float {
        val delta    = relativePowers[EegBand.Delta]    ?: 0f
        val theta    = relativePowers[EegBand.Theta]    ?: 0f
        val alpha    = relativePowers[EegBand.Alpha]    ?: 0f
        val lowBeta  = relativePowers[EegBand.LowBeta]  ?: 0f
        val highBeta = relativePowers[EegBand.HighBeta] ?: 0f
        val gamma    = relativePowers[EegBand.Gamma]    ?: 0f

        // Engagement score: high beta + gamma vs slow waves
        val engagementNumerator   = lowBeta * 0.4f + highBeta * 0.3f + gamma * 0.3f
        val relaxationNumerator   = delta   * 0.4f + theta   * 0.35f + alpha * 0.25f

        // Ratio: engagement / (engagement + relaxation), avoids division by zero
        val total = engagementNumerator + relaxationNumerator
        return if (total <= 0f) 0.5f else (engagementNumerator / total).coerceIn(0f, 1f)
    }

    /**
     * Parse binary frame from firmware.
     * Frame format: magic(2) + seq(2 LE uint16) + count(1) + samples(count * 4 LE int32)
     * Source: zuna_process/scripts/ble_eeg_receiver.py parse_frame()
     */
    fun parseBinaryFrame(data: ByteArray): Pair<Int, List<Long>>? {
        if (data.size < 5) return null
        if (data[0] != 0xE7.toByte() || data[1] != 0x1E.toByte()) return null
        val seq = (data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)
        val count = data[4].toInt() and 0xFF
        val need = 5 + count * 4
        if (data.size < need) return null
        val samples = (0 until count).map { i ->
            val off = 5 + i * 4
            val raw = (data[off].toInt() and 0xFF) or
                      ((data[off + 1].toInt() and 0xFF) shl 8) or
                      ((data[off + 2].toInt() and 0xFF) shl 16) or
                      ((data[off + 3].toInt() and 0xFF) shl 24)
            raw.toLong()  // int32 → Long (sign-extends properly)
        }
        return seq to samples
    }

    /**
     * Parse ASCII decimal sample from firmware.
     * Format: UTF-8 string containing a single signed integer, possibly with whitespace.
     * Source: zuna_process/scripts/ble_eeg_receiver.py parse_ascii_sample()
     */
    fun parseAsciiSample(data: ByteArray): Long? {
        return try {
            val s = String(data, Charsets.UTF_8).trim()
            if (s.isEmpty()) return null
            if (s[0] != '-' && !s[0].isDigit()) return null
            s.toLong()
        } catch (_: Exception) {
            null
        }
    }
}

// Extension to call private sin — Kotlin's kotlin.math.sin is fine here
private fun sin(x: Double): Double = kotlin.math.sin(x)

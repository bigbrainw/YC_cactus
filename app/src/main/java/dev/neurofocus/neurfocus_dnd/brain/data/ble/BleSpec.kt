package dev.neurofocus.neurfocus_dnd.brain.data.ble

/**
 * Single source of truth for the firmware contract.
 * Mirrors `firmware/v4/src/config.h` and `ble_manager.cpp`
 * as documented in `docs/CODEBASE_MEMORY.md`.
 *
 * Do NOT change these values without updating the firmware first.
 */
object BleSpec {
    const val DEVICE_NAME_PREFIX: String = "NEUROFOCUS_V4"

    const val SERVICE_UUID: String = "0338ff7c-6251-4029-a5d5-24e4fa856c8d"
    const val DATA_CHARACTERISTIC_UUID: String = "ad615f2b-cc93-4155-9e4d-f5f32cb9a2d7"
    const val COMMAND_CHARACTERISTIC_UUID: String = "b5e3d1c9-8a2f-4e7b-9c6d-1a3f5e7b9c2d"

    val CMD_STREAM_START: ByteArray = byteArrayOf('b'.code.toByte())
    val CMD_STREAM_STOP: ByteArray = byteArrayOf('s'.code.toByte())
    val CMD_RESET: ByteArray = byteArrayOf('v'.code.toByte())

    const val ADC_FULL_SCALE_VOLTS: Float = 3.3f
    const val ADC_BITS: Int = 24
    const val ADC_FULL_SCALE_COUNTS: Int = 1 shl (ADC_BITS - 1)

    const val SAMPLE_RATE_HZ: Int = 600
    const val SAMPLE_PERIOD_MICROS: Long = 1_667L

    /** Convert a raw signed 24-bit ADC count into microvolts. */
    fun countsToMicrovolts(counts: Long): Float {
        val volts = counts.toFloat() * ADC_FULL_SCALE_VOLTS / ADC_FULL_SCALE_COUNTS
        return volts * 1_000_000f
    }
}

package dev.neurofocus.neurfocus_dnd.cactus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * In-memory ring buffer of download / Cactus lifecycle lines for Debug UI.
 * Thread-safe for append from IO threads.
 */
object DownloadLogStore {

    private const val MAX_LINES = 400
    private val buf = ArrayDeque<String>(MAX_LINES)
    private val sampleLines = ArrayDeque<String>(80)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _samples = MutableStateFlow<List<String>>(emptyList())
    /** Short hex previews of downloaded chunks (first bytes of each read). */
    val chunkSamples: StateFlow<List<String>> = _samples.asStateFlow()

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun append(message: String) {
        val line = "[${fmt.format(Date())}] $message"
        if (buf.size >= MAX_LINES) buf.removeFirst()
        buf.addLast(line)
        _lines.value = buf.toList()
    }

    @Synchronized
    fun appendSample(label: String, bytes: ByteArray, previewLen: Int = 24) {
        val n = min(previewLen, bytes.size)
        val hex = bytes.copyOf(n).joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }
        val s = "$label (${bytes.size} B): $hex${if (bytes.size > n) "…" else ""}"
        if (sampleLines.size >= 80) sampleLines.removeFirst()
        sampleLines.addLast(s)
        _samples.value = sampleLines.toList()
    }

    @Synchronized
    fun clear() {
        buf.clear()
        sampleLines.clear()
        _lines.value = emptyList()
        _samples.value = emptyList()
    }
}

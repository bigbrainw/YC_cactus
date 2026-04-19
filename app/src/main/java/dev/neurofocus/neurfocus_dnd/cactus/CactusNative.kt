package dev.neurofocus.neurfocus_dnd.cactus

import org.json.JSONObject

/**
 * JNI bindings per [docs/cactus-computing-kotlin.md].
 * Ship **libcactus.so** under `app/src/main/jniLibs/arm64-v8a/`.
 */
object CactusNative {

    @Volatile
    private var loadAttempted = false

    @Volatile
    private var loadOk = false

    fun isLoaded(): Boolean {
        if (!loadAttempted) synchronized(this) {
            if (!loadAttempted) {
                loadAttempted = true
                loadOk = try {
                    System.loadLibrary("cactus")
                    true
                } catch (_: UnsatisfiedLinkError) {
                    false
                } catch (_: Throwable) {
                    false
                }
            }
        }
        return loadOk
    }

    fun init(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long {
        if (!isLoaded()) return 0L
        return cactusInit(modelPath, corpusDir, cacheIndex)
    }

    fun destroy(model: Long) {
        if (model == 0L || !isLoaded()) return
        cactusDestroy(model)
    }

    fun lastError(): String {
        if (!isLoaded()) return "libcactus.so not loaded"
        return cactusGetLastError()
    }

    /**
     * Blocking completion; returns assistant text or null if JNI/export missing or generation failed.
     * [responseUtf8] is written as UTF-8 JSON (per Cactus engine) or plain text, NUL-terminated when possible.
     */
    fun tryComplete(model: Long, messagesJson: String, optionsJson: String): String? {
        if (model == 0L || !isLoaded()) return null
        val buf = ByteArray(4096)
        return try {
            cactusComplete(model, messagesJson, optionsJson, buf)
            val raw = buf.decodeToString().substringBefore('\u0000').trim()
            if (raw.isEmpty()) null else extractCompletionText(raw)
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractCompletionText(raw: String): String? {
        return try {
            val o = JSONObject(raw)
            if (o.has("success") && !o.optBoolean("success", true)) return null
            o.optString("response", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            raw.takeIf { it.isNotBlank() }
        }
    }

    private external fun cactusInit(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long

    private external fun cactusDestroy(model: Long)

    private external fun cactusGetLastError(): String

    /** @return engine result code; 0 typically success — also inspect JSON in buffer. */
    private external fun cactusComplete(
        model: Long,
        messagesJson: String,
        optionsJson: String,
        responseUtf8: ByteArray,
    ): Int
}

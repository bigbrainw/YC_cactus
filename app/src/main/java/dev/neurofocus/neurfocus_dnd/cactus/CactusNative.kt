package dev.neurofocus.neurfocus_dnd.cactus

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

    private external fun cactusInit(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long

    private external fun cactusDestroy(model: Long)

    private external fun cactusGetLastError(): String
}

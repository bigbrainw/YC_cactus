package dev.neurofocus.neurfocus_dnd.cactus

import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Loads local Cactus weights (when [CactusNative] + file present), keeps a model handle,
 * and publishes one-sentence analytical copy via [CactusNative.tryComplete] when the JNI
 * export exists, otherwise [buildHeuristicAnalyticalInsight].
 */
class CactusInsightEngine(
    private val appScope: CoroutineScope,
    private val modelRepo: CactusModelRepository,
) {

    private val mutex = Mutex()
    @Volatile
    private var modelHandle: Long = 0L

    private val _insight = MutableStateFlow(InsightUiState.Idle)
    val insight: StateFlow<InsightUiState> = _insight.asStateFlow()

    private var refreshJob: Job? = null

    fun destroy() {
        refreshJob?.cancel()
        runBlocking {
            mutex.withLock {
                if (modelHandle != 0L) {
                    try {
                        CactusNative.destroy(modelHandle)
                    } catch (_: Throwable) {
                    }
                    modelHandle = 0L
                }
            }
        }
    }

    fun scheduleRefresh(live: BrainState.Live) {
        refreshJob?.cancel()
        refreshJob = appScope.launch(Dispatchers.Default) {
            _insight.value = _insight.value.copy(loading = true, error = null)
            val text = withContext(Dispatchers.IO) {
                runInference(live)
            }
            _insight.value = InsightUiState(
                text = text,
                loading = false,
                error = null,
                usedNativeModel = modelHandle != 0L,
            )
        }
    }

    fun setDisconnectedPlaceholder() {
        refreshJob?.cancel()
        _insight.value = InsightUiState.Idle
    }

    fun setError(message: String) {
        _insight.value = InsightUiState(
            text = firstSentenceOnly(
                "Add libcactus.so under jniLibs (arm64-v8a) and download the default Gemma 3 270M INT4 pack in Settings.",
            ),
            loading = false,
            error = message,
            usedNativeModel = false,
        )
    }

    private suspend fun runInference(live: BrainState.Live): String {
        val file = modelRepo.resolvedModelFile()
        if (file == null || !file.isFile || file.length() == 0L) {
            return firstSentenceOnly(
                buildHeuristicAnalyticalInsight(live) +
                    " Download the default Gemma 3 270M INT4 weights from Settings to run on-device Gemma.",
            )
        }
        mutex.withLock {
            if (modelHandle == 0L && CactusNative.isLoaded()) {
                val h = CactusNative.init(file.absolutePath, null, false)
                if (h == 0L) {
                    val err = runCatching { CactusNative.lastError() }.getOrElse { it.message ?: "init failed" }
                    DownloadLogStore.append("cactusInit failed: $err")
                    return firstSentenceOnly(
                        buildHeuristicAnalyticalInsight(live) + " (Cactus init failed: $err.)",
                    )
                }
                modelHandle = h
                DownloadLogStore.append("cactusInit ok handle=$h")
            } else if (modelHandle == 0L) {
                DownloadLogStore.append("libcactus.so not loaded — heuristic only")
            }
        }
        val nativeLine = mutex.withLock {
            if (modelHandle == 0L) null
            else {
                val msg = cactusAnalyticalMessagesJson(live)
                val opt = cactusAnalyticalOptionsJson()
                CactusNative.tryComplete(modelHandle, msg, opt).also { r ->
                    if (r != null) DownloadLogStore.append("cactusComplete ok len=${r.length}")
                    else DownloadLogStore.append("cactusComplete null or skipped")
                }
            }
        }
        val text = nativeLine?.let { firstSentenceOnly(it) }
        return if (!text.isNullOrBlank()) {
            text
        } else {
            firstSentenceOnly(buildHeuristicAnalyticalInsight(live))
        }
    }

    /** Call after a new model file is installed so the next refresh re-inits. */
    suspend fun resetModelForNewFile() {
        mutex.withLock {
            if (modelHandle != 0L) {
                try {
                    CactusNative.destroy(modelHandle)
                } catch (_: Throwable) {
                }
                modelHandle = 0L
            }
        }
    }
}

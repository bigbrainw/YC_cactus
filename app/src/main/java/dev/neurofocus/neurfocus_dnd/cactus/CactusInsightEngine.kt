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
 * and publishes short analytical copy. Until `cactusComplete` JNI is wired, copy is
 * [buildHeuristicAnalyticalInsight] while still exercising init/destroy when possible.
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
            text = "Add a model in Settings and place libcactus.so in jniLibs (arm64-v8a).",
            loading = false,
            error = message,
            usedNativeModel = false,
        )
    }

    private suspend fun runInference(live: BrainState.Live): String {
        val file = modelRepo.resolvedModelFile()
        if (file == null || !file.isFile || file.length() == 0L) {
            return buildHeuristicAnalyticalInsight(live) +
                " On-device model file missing — download in Settings."
        }
        mutex.withLock {
            if (modelHandle == 0L && CactusNative.isLoaded()) {
                val h = CactusNative.init(file.absolutePath, null, false)
                if (h == 0L) {
                    val err = runCatching { CactusNative.lastError() }.getOrElse { it.message ?: "init failed" }
                    DownloadLogStore.append("cactusInit failed: $err")
                    return buildHeuristicAnalyticalInsight(live) + " (cactusInit: $err)"
                }
                modelHandle = h
                DownloadLogStore.append("cactusInit ok handle=$h")
            } else if (modelHandle == 0L) {
                DownloadLogStore.append("libcactus.so not loaded — heuristic only")
            }
        }
        // Native completion not JNI-wrapped yet — still return heuristic for readable UI.
        return buildHeuristicAnalyticalInsight(live)
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

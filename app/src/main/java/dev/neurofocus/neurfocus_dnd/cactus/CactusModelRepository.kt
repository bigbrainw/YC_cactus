package dev.neurofocus.neurfocus_dnd.cactus

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient

sealed class DownloadUiState {
    data object Idle : DownloadUiState()
    data class Downloading(val bytesReceived: Long, val totalBytes: Long?) : DownloadUiState()
    data class Done(val path: String, val bytes: Long) : DownloadUiState()
    data class Error(val message: String) : DownloadUiState()
}

class CactusModelRepository(
    private val appContext: Context,
    private val prefs: CactusModelPrefs,
) {

    private val client = OkHttpClient.Builder().build()

    private val _downloadState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val downloadState: StateFlow<DownloadUiState> = _downloadState.asStateFlow()

    private val _modelGeneration = MutableStateFlow(0L)
    /** Bumps when a new model file is written — insight engine should drop its handle. */
    val modelGeneration: StateFlow<Long> = _modelGeneration.asStateFlow()

    fun modelDir(): File = File(appContext.filesDir, "cactus").also { it.mkdirs() }

    fun defaultModelFile(): File = File(modelDir(), MODEL_FILENAME)

    fun resolvedModelFile(): File? {
        val p = prefs.modelAbsolutePath ?: return null
        val f = File(p)
        return if (f.isFile) f else null
    }

    fun modelFileSizeBytes(): Long = resolvedModelFile()?.length() ?: 0L

    suspend fun downloadFromUrl(url: String) = withContext(Dispatchers.IO) {
        _downloadState.value = DownloadUiState.Downloading(0L, null)
        DownloadLogStore.append("GET $url")
        val target = defaultModelFile()
        val tmp = File(target.parentFile, "${target.name}.download")
        try {
            tmp.delete()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "HTTP ${response.code} ${response.message}"
                    DownloadLogStore.append("FAIL $msg")
                    prefs.lastDownloadOk = false
                    prefs.lastError = msg
                    _downloadState.value = DownloadUiState.Error(msg)
                    return@withContext
                }
                val body = response.body ?: throw IOException("empty body")
                val total = body.contentLength().takeIf { it > 0 }
                body.byteStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var received = 0L
                        while (true) {
                            val r = input.read(buf)
                            if (r == -1) break
                            out.write(buf, 0, r)
                            received += r
                            if (received <= 64 * 1024 * 3) {
                                DownloadLogStore.appendSample("chunk@${received}", buf.copyOf(r))
                            }
                            _downloadState.value = DownloadUiState.Downloading(received, total)
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("rename failed")
            prefs.modelAbsolutePath = target.absolutePath
            prefs.lastDownloadOk = true
            prefs.lastError = null
            DownloadLogStore.append("Saved ${target.absolutePath} (${target.length()} bytes)")
            _modelGeneration.update { System.currentTimeMillis() }
            _downloadState.value = DownloadUiState.Done(target.absolutePath, target.length())
        } catch (e: Throwable) {
            val msg = e.message ?: e.javaClass.simpleName
            prefs.lastDownloadOk = false
            prefs.lastError = msg
            DownloadLogStore.append("ERROR $msg")
            _downloadState.value = DownloadUiState.Error(msg)
            try {
                tmp.delete()
            } catch (_: Throwable) {
            }
        }
    }

    fun deleteLocalModel() {
        val f = resolvedModelFile() ?: defaultModelFile()
        try {
            if (f.exists()) f.delete()
        } catch (_: Throwable) {
        }
        prefs.modelAbsolutePath = null
        prefs.lastDownloadOk = false
        prefs.lastError = null
        _downloadState.value = DownloadUiState.Idle
        _modelGeneration.update { System.currentTimeMillis() }
        DownloadLogStore.append("Deleted local model")
    }

    companion object {
        /** Matches [R.string.cactus_default_model_download_url] artifact name. */
        const val MODEL_FILENAME = "gemma-3-270m-it-int4.zip"
    }
}

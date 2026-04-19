package dev.neurofocus.neurfocus_dnd.cactus

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persistence for on-device Cactus model file path and last download outcome.
 */
class CactusModelPrefs(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) {
            prefs.edit { putString(KEY_LAST_ERROR, value) }
        }

    var lastDownloadOk: Boolean
        get() = prefs.getBoolean(KEY_LAST_DOWNLOAD_OK, false)
        set(value) {
            prefs.edit { putBoolean(KEY_LAST_DOWNLOAD_OK, value) }
        }

    /** Last URL used for a successful or attempted download (user-editable in Settings). */
    var modelDownloadUrl: String?
        get() = prefs.getString(KEY_DOWNLOAD_URL, null)
        set(value) {
            prefs.edit { putString(KEY_DOWNLOAD_URL, value) }
        }

    /** Absolute path to model file on disk (set after successful download or manual install). */
    var modelAbsolutePath: String?
        get() = prefs.getString(KEY_MODEL_PATH, null)
        set(value) {
            prefs.edit { putString(KEY_MODEL_PATH, value) }
        }

    companion object {
        private const val PREFS_NAME = "neurofocus_cactus_model"
        private const val KEY_MODEL_PATH = "model_absolute_path"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_DOWNLOAD_OK = "last_download_ok"
        private const val KEY_DOWNLOAD_URL = "model_download_url"
    }
}

package dev.neurofocus.neurfocus_dnd.brain.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.neurofocus.neurfocus_dnd.NeuroApp
import dev.neurofocus.neurfocus_dnd.cactus.CactusModelRepository
import dev.neurofocus.neurfocus_dnd.cactus.DownloadUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CactusSettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repo: CactusModelRepository =
        (application as NeuroApp).cactusModelRepository

    val downloadState: StateFlow<DownloadUiState> = repo.downloadState

    fun startDownload(url: String) {
        viewModelScope.launch {
            repo.downloadFromUrl(url.trim())
        }
    }

    fun deleteModel() {
        repo.deleteLocalModel()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass != CactusSettingsViewModel::class.java) {
                        error("Unsupported ViewModel: ${modelClass.name}")
                    }
                    return CactusSettingsViewModel(application) as T
                }
            }
    }
}

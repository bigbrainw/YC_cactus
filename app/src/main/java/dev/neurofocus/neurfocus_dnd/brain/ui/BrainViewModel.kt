package dev.neurofocus.neurfocus_dnd.brain.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neurofocus.neurfocus_dnd.brain.data.BrainDataRepository
import dev.neurofocus.neurfocus_dnd.brain.data.BrainRepositoryFactory
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the [BrainDataRepository] for the current Activity scope and exposes
 * its state to the UI. Uses BLE when the device supports it; otherwise a
 * synthetic [dev.neurofocus.neurfocus_dnd.brain.data.FakeEegRepository].
 */
class BrainViewModel(
    private val repository: BrainDataRepository,
) : ViewModel() {

    val state: StateFlow<BrainState> = repository.state

    init {
        viewModelScope.launch { repository.connect() }
    }

    /** Call after runtime Bluetooth permissions are granted. */
    fun retryConnect() {
        viewModelScope.launch { repository.connect() }
    }

    override fun onCleared() {
        repository.dispose()
        super.onCleared()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass != BrainViewModel::class.java) {
                        error("Unsupported ViewModel: ${modelClass.name}")
                    }
                    return BrainViewModel(BrainRepositoryFactory.create(application)) as T
                }
            }
    }
}

@Composable
fun rememberBrainViewModel(): BrainViewModel {
    val app = LocalContext.current.applicationContext as Application
    val factory = remember(app) { BrainViewModel.factory(app) }
    return viewModel(factory = factory)
}

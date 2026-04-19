package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neurofocus.neurfocus_dnd.brain.data.BrainDataRepository
import dev.neurofocus.neurfocus_dnd.brain.data.FakeEegRepository
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.util.DefaultDispatcherProvider
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the [BrainDataRepository] for the current Activity scope and exposes
 * its state to the UI. Triggers connection on construction; tears down when
 * the ViewModel is cleared.
 *
 * Default constructor uses [FakeEegRepository] so the UI can render without
 * hardware. Phase 4 will replace the default with a BLE-backed implementation.
 */
class BrainViewModel(
    private val repository: BrainDataRepository = FakeEegRepository(DefaultDispatcherProvider()),
) : ViewModel() {

    val state: StateFlow<BrainState> = repository.state

    init {
        viewModelScope.launch { repository.connect() }
    }

    override fun onCleared() {
        super.onCleared()
        // shutdown is synchronous — viewModelScope is already cancelled by now,
        // so we can't launch a coroutine here for disconnect().
        (repository as? FakeEegRepository)?.shutdown()
    }
}

package dev.neurofocus.neurfocus_dnd.brain.data

import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the EEG data source. The UI never knows whether the
 * underlying implementation is fake, BLE, or a recorded fixture.
 *
 * Implementations must:
 *  - Expose state as [StateFlow] so collectors get the latest snapshot on subscribe.
 *  - Use [update] for atomic transitions (no `value = ...` for derived state).
 *  - Inject a [kotlinx.coroutines.CoroutineDispatcher] via constructor.
 */
interface BrainDataRepository {
    val state: StateFlow<BrainState>

    suspend fun connect()

    suspend fun disconnect()
}

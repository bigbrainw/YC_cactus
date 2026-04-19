package dev.neurofocus.neurfocus_dnd.brain.data

import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import kotlinx.coroutines.flow.StateFlow

data class BleDeviceCandidate(
    val displayName: String,
    val address: String,
)

/**
 * Abstraction over the EEG data source.
 *
 * Implementations must:
 *  - Expose state as [StateFlow] so collectors get the latest snapshot on subscribe.
 *  - Inject a [kotlinx.coroutines.CoroutineDispatcher] via constructor.
 */
interface BrainDataRepository {
    val state: StateFlow<BrainState>

    suspend fun connect()

    suspend fun disconnect()

    /** Synchronous teardown for [androidx.lifecycle.ViewModel.onCleared]. */
    fun dispose()

    suspend fun scanNeuroFocusDevices(): List<BleDeviceCandidate>

    /**
     * Connect to a device chosen from [scanNeuroFocusDevices].
     */
    suspend fun connectToDeviceAddress(address: String)
}

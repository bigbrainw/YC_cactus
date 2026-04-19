package dev.neurofocus.neurfocus_dnd.brain.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neurofocus.neurfocus_dnd.brain.data.BleDeviceCandidate
import dev.neurofocus.neurfocus_dnd.brain.data.BrainDataRepository
import dev.neurofocus.neurfocus_dnd.brain.data.BrainRepositoryFactory
import dev.neurofocus.neurfocus_dnd.brain.data.ble.BleEegRepository
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.DisconnectEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrainViewModel(
    private val repository: BrainDataRepository,
) : ViewModel() {

    val state: StateFlow<BrainState> = repository.state

    private val _blePickerOpen = MutableStateFlow(false)
    val blePickerOpen: StateFlow<Boolean> = _blePickerOpen.asStateFlow()

    private val _blePickerBusy = MutableStateFlow(false)
    val blePickerBusy: StateFlow<Boolean> = _blePickerBusy.asStateFlow()

    private val _blePickerDevices = MutableStateFlow<List<BleDeviceCandidate>>(emptyList())
    val blePickerDevices: StateFlow<List<BleDeviceCandidate>> = _blePickerDevices.asStateFlow()

    /** Disconnect event log — populated from BleEegRepository if available, otherwise empty. */
    private val _disconnectLog = MutableStateFlow<List<DisconnectEvent>>(emptyList())
    val disconnectLog: StateFlow<List<DisconnectEvent>> = _disconnectLog.asStateFlow()

    init {
        viewModelScope.launch { repository.connect() }

        // Collect disconnect events from the BLE repository if available
        val bleRepo = repository as? BleEegRepository
        if (bleRepo != null) {
            viewModelScope.launch {
                bleRepo.disconnectEvents.collect { event ->
                    _disconnectLog.value = (_disconnectLog.value + event).takeLast(100)
                }
            }
        }
    }

    fun retryConnect() {
        viewModelScope.launch { repository.connect() }
    }

    fun openBleDevicePicker() {
        _blePickerOpen.value = true
        refreshBleDeviceList()
    }

    fun dismissBleDevicePicker() {
        _blePickerOpen.value = false
    }

    fun refreshBleDeviceList() {
        viewModelScope.launch {
            _blePickerBusy.value = true
            try {
                _blePickerDevices.value = repository.scanNeuroFocusDevices()
            } finally {
                _blePickerBusy.value = false
            }
        }
    }

    fun connectBleDevice(address: String) {
        viewModelScope.launch {
            repository.connectToDeviceAddress(address)
            _blePickerOpen.value = false
        }
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

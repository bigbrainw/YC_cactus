package dev.neurofocus.neurfocus_dnd.brain.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neurofocus.neurfocus_dnd.NeuroApp
import dev.neurofocus.neurfocus_dnd.brain.data.BleDeviceCandidate
import dev.neurofocus.neurfocus_dnd.brain.data.BrainDataRepository
import dev.neurofocus.neurfocus_dnd.brain.data.BrainRepositoryFactory
import dev.neurofocus.neurfocus_dnd.brain.data.ble.BleEegRepository
import dev.neurofocus.neurfocus_dnd.brain.domain.BandPowerHistoryEntry
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.DisconnectEvent
import dev.neurofocus.neurfocus_dnd.cactus.CactusInsightEngine
import dev.neurofocus.neurfocus_dnd.cactus.InsightUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrainViewModel(
    private val repository: BrainDataRepository,
    private val neuroApp: NeuroApp,
) : ViewModel() {

    private val insightEngine = CactusInsightEngine(viewModelScope, neuroApp.cactusModelRepository)

    val state: StateFlow<BrainState> = repository.state

    val insight: StateFlow<InsightUiState> = insightEngine.insight

    private val _bandPowerHistory = MutableStateFlow<List<BandPowerHistoryEntry>>(emptyList())
    val bandPowerHistory: StateFlow<List<BandPowerHistoryEntry>> = _bandPowerHistory.asStateFlow()

    private val _blePickerOpen = MutableStateFlow(false)
    val blePickerOpen: StateFlow<Boolean> = _blePickerOpen.asStateFlow()

    private val _blePickerBusy = MutableStateFlow(false)
    val blePickerBusy: StateFlow<Boolean> = _blePickerBusy.asStateFlow()

    private val _blePickerDevices = MutableStateFlow<List<BleDeviceCandidate>>(emptyList())
    val blePickerDevices: StateFlow<List<BleDeviceCandidate>> = _blePickerDevices.asStateFlow()

    private val _disconnectLog = MutableStateFlow<List<DisconnectEvent>>(emptyList())
    val disconnectLog: StateFlow<List<DisconnectEvent>> = _disconnectLog.asStateFlow()

    private var lastInsightMs = 0L
    private var lastHistoryMs = 0L

    init {
        viewModelScope.launch { repository.connect() }

        val bleRepo = repository as? BleEegRepository
        if (bleRepo != null) {
            viewModelScope.launch {
                bleRepo.disconnectEvents.collect { event ->
                    _disconnectLog.value = (_disconnectLog.value + event).takeLast(100)
                }
            }
        }

        viewModelScope.launch {
            neuroApp.cactusModelRepository.modelGeneration.collect {
                if (it > 0L) {
                    insightEngine.resetModelForNewFile()
                }
            }
        }

        viewModelScope.launch {
            repository.state.collect { s ->
                val now = System.currentTimeMillis()
                when (s) {
                    is BrainState.Live -> {
                        if (now - lastInsightMs >= 2800L) {
                            lastInsightMs = now
                            insightEngine.scheduleRefresh(s)
                        }
                        if (now - lastHistoryMs >= 2000L) {
                            lastHistoryMs = now
                            val entry = BandPowerHistoryEntry(now, HashMap(s.bandPowers))
                            _bandPowerHistory.value =
                                (_bandPowerHistory.value + entry).takeLast(90)
                        }
                    }
                    else -> {
                        insightEngine.setDisconnectedPlaceholder()
                    }
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
        insightEngine.destroy()
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
                    val neuro = application as NeuroApp
                    return BrainViewModel(
                        BrainRepositoryFactory.create(application),
                        neuro,
                    ) as T
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

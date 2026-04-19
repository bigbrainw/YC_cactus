package dev.neurofocus.neurfocus_dnd.brain.data.ble

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.neurofocus.neurfocus_dnd.brain.data.BleDeviceCandidate
import dev.neurofocus.neurfocus_dnd.brain.data.BrainDataRepository
import dev.neurofocus.neurfocus_dnd.brain.domain.BatteryPercent
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.ElectrodeSite
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.util.DefaultDispatcherProvider
import dev.neurofocus.neurfocus_dnd.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * BLE central for NeuroFocus V4 firmware: scans for [BleSpec.DEVICE_NAME_PREFIX],
 * connects, enables NOTIFY on the data characteristic, parses **ASCII decimal**
 * samples (per firmware), and maps the stream into [BrainState.Live] for the UI.
 */
@SuppressLint("MissingPermission")
class BleEegRepository(
    private val app: Application,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : BrainDataRepository {

    constructor(application: Application) : this(
        application,
        (application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter,
        DefaultDispatcherProvider(),
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val ioMutex = Mutex()

    private val _state = MutableStateFlow<BrainState>(BrainState.Idle)
    override val state: StateFlow<BrainState> = _state.asStateFlow()

    @Volatile
    private var gatt: BluetoothGatt? = null

    private var connectJob: Job? = null

    private val ringBufferLock = Any()
    private val ring = LongArray(RING_SIZE)
    private var ringCount = 0
    private var ringWrite = 0
    private var lastSample: Long = 0
    private var updateTick = 0

    override suspend fun connect() {
        connectJob?.cancel()
        withTimeoutOrNull(CONNECT_TOTAL_TIMEOUT_MS) {
            connectJob = scope.launch(dispatchers.default) {
                ioMutex.withLock { runConnectLocked(forcedDevice = null) }
            }
            connectJob?.join()
        } ?: run {
            connectJob?.cancel()
            _state.update {
                BrainState.Error("Connection timed out. Move closer to the device and try again.")
            }
            teardownGatt()
        }
    }

    override suspend fun connectToDeviceAddress(address: String) {
        val device = try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (device == null) {
            _state.update { BrainState.Error("Invalid Bluetooth address.") }
            return
        }
        connectJob?.cancel()
        withTimeoutOrNull(CONNECT_TOTAL_TIMEOUT_MS) {
            connectJob = scope.launch(dispatchers.default) {
                ioMutex.withLock { runConnectLocked(forcedDevice = device) }
            }
            connectJob?.join()
        } ?: run {
            connectJob?.cancel()
            _state.update {
                BrainState.Error("Connection timed out. Move closer to the device and try again.")
            }
            teardownGatt()
        }
    }

    override suspend fun scanNeuroFocusDevices(): List<BleDeviceCandidate> =
        withContext(dispatchers.main) {
            if (!BlePermissions.hasAll(app)) return@withContext emptyList()
            val adapter = bluetoothAdapter ?: return@withContext emptyList()
            if (!adapter.isEnabled) return@withContext emptyList()
            val scanner = adapter.bluetoothLeScanner ?: return@withContext emptyList()
            suspendCancellableCoroutine { cont ->
                val resumed = AtomicBoolean(false)
                val byAddress = linkedMapOf<String, BleDeviceCandidate>()
                lateinit var scanCallback: ScanCallback
                val handler = Handler(Looper.getMainLooper())
                val finishRunnable = object : Runnable {
                    override fun run() {
                        val list = byAddress.values.toList().sortedBy { it.displayName.lowercase() }
                        if (!resumed.compareAndSet(false, true)) return
                        try {
                            scanner.stopScan(scanCallback)
                        } catch (_: Throwable) {
                        }
                        cont.resume(list)
                    }
                }
                fun finishEarlyEmpty() {
                    handler.removeCallbacks(finishRunnable)
                    if (!resumed.compareAndSet(false, true)) return
                    try {
                        scanner.stopScan(scanCallback)
                    } catch (_: Throwable) {
                    }
                    cont.resume(emptyList())
                }
                scanCallback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val name = result.device.name
                            ?: result.scanRecord?.deviceName
                            ?: return
                        if (!name.startsWith(BleSpec.DEVICE_NAME_PREFIX)) return
                        val d = result.device
                        byAddress[d.address] = BleDeviceCandidate(displayName = name, address = d.address)
                    }

                    override fun onBatchScanResults(results: MutableList<ScanResult>) {
                        for (r in results) {
                            onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, r)
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        finishEarlyEmpty()
                    }
                }
                cont.invokeOnCancellation {
                    handler.removeCallbacks(finishRunnable)
                    try {
                        scanner.stopScan(scanCallback)
                    } catch (_: Throwable) {
                    }
                }
                scanner.startScan(
                    null,
                    ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build(),
                    scanCallback,
                )
                handler.postDelayed(finishRunnable, PICKER_SCAN_MS)
            }
        }

    private suspend fun runConnectLocked(forcedDevice: BluetoothDevice?) {
        if (!BlePermissions.hasAll(app)) {
            _state.update {
                BrainState.Error("Bluetooth permission required. Grant it in Settings, then reopen the app.")
            }
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _state.update {
                BrainState.Error("Bluetooth is off or unavailable. Turn Bluetooth on and try again.")
            }
            return
        }

        teardownGatt()
        _state.update { BrainState.Searching }

        val device = if (forcedDevice != null) {
            forcedDevice
        } else {
            withContext(Dispatchers.Main) {
                withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        val scanner = adapter.bluetoothLeScanner
                        if (scanner == null) {
                            cont.resume(null)
                            return@suspendCancellableCoroutine
                        }
                        val callback = object : ScanCallback() {
                            private fun finish(dev: BluetoothDevice?) {
                                if (cont.isCompleted) return
                                try {
                                    scanner.stopScan(this)
                                } catch (_: Throwable) {
                                }
                                cont.resume(dev)
                            }

                            override fun onScanResult(callbackType: Int, result: ScanResult) {
                                val name = result.device.name
                                    ?: result.scanRecord?.deviceName
                                    ?: return
                                if (name.startsWith(BleSpec.DEVICE_NAME_PREFIX)) {
                                    finish(result.device)
                                }
                            }

                            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                                for (r in results) {
                                    onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, r)
                                }
                            }

                            override fun onScanFailed(errorCode: Int) {
                                finish(null)
                            }
                        }
                        cont.invokeOnCancellation {
                            try {
                                scanner.stopScan(callback)
                            } catch (_: Throwable) {
                            }
                        }
                        scanner.startScan(
                            null,
                            ScanSettings.Builder()
                                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                                .build(),
                            callback,
                        )
                    }
                }
            }
        }

        if (device == null) {
            _state.update {
                BrainState.Error("No NeuroFocus device found. Power the headband on and stay within a few metres.")
            }
            return
        }

        _state.update { BrainState.Connecting }

        val serviceUuid = uuid(BleSpec.SERVICE_UUID)
        val dataUuid = uuid(BleSpec.DATA_CHARACTERISTIC_UUID)
        val cmdUuid = uuid(BleSpec.COMMAND_CHARACTERISTIC_UUID)
        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        val connected = withContext(Dispatchers.Main) {
            withTimeoutOrNull(GATT_SETUP_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val resumed = AtomicBoolean(false)
                    fun tryResume(ok: Boolean) {
                        if (resumed.compareAndSet(false, true)) {
                            cont.resume(ok)
                        }
                    }

                    val callback = object : BluetoothGattCallback() {
                        private var dataChar: BluetoothGattCharacteristic? = null
                        private var cmdChar: BluetoothGattCharacteristic? = null
                        private var setupStep = 0

                        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                if (status != BluetoothGatt.GATT_SUCCESS) {
                                    tryResume(false)
                                    return
                                }
                                this@BleEegRepository.gatt = gatt
                                // Request high priority and larger MTU for the 600Hz stream
                                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                                gatt.requestMtu(MTU_REQUEST)
                                gatt.discoverServices()
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                tryResume(false)
                                failQuiet()
                            }
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                tryResume(false)
                                return
                            }
                            val service = gatt.getService(serviceUuid)
                            if (service == null) {
                                tryResume(false)
                                return
                            }
                            dataChar = service.getCharacteristic(dataUuid)
                            cmdChar = service.getCharacteristic(cmdUuid)
                            val dc = dataChar
                            if (dc == null) {
                                tryResume(false)
                                return
                            }
                            gatt.setCharacteristicNotification(dc, true)
                            val desc = dc.getDescriptor(cccdUuid)
                            if (desc == null) {
                                tryResume(false)
                                return
                            }
                            setupStep = 1
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                @Suppress("DEPRECATION")
                                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(desc)
                            }
                        }

                        override fun onDescriptorWrite(
                            gatt: BluetoothGatt,
                            descriptor: BluetoothGattDescriptor,
                            status: Int,
                        ) {
                            if (setupStep != 1) return
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                tryResume(false)
                                return
                            }
                            val cc = cmdChar
                            if (cc != null) {
                                setupStep = 2
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeCharacteristic(cc, BleSpec.CMD_STREAM_START, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                                } else {
                                    @Suppress("DEPRECATION")
                                    cc.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                    @Suppress("DEPRECATION")
                                    cc.value = BleSpec.CMD_STREAM_START
                                    @Suppress("DEPRECATION")
                                    gatt.writeCharacteristic(cc)
                                }
                            } else {
                                tryResume(true)
                            }
                        }

                        override fun onCharacteristicWrite(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            status: Int,
                        ) {
                            if (setupStep != 2) return
                            if (characteristic.uuid != cmdUuid) return
                            tryResume(status == BluetoothGatt.GATT_SUCCESS)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onCharacteristicChanged(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            value: ByteArray,
                        ) {
                            if (characteristic.uuid != dataUuid) return
                            val text = String(value, StandardCharsets.UTF_8).trim()
                            if (text.isEmpty()) return
                            for (token in TOKENS_REGEX.split(text)) {
                                if (token.isEmpty()) continue
                                token.toLongOrNull()?.let { ingestSample(it) }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onCharacteristicChanged(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                        ) {
                            @Suppress("DEPRECATION")
                            val payload = characteristic.value ?: return
                            onCharacteristicChanged(gatt, characteristic, payload)
                        }
                    }

                    val g = device.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE)
                    if (g == null) {
                        tryResume(false)
                        return@suspendCancellableCoroutine
                    }
                    this@BleEegRepository.gatt = g
                    cont.invokeOnCancellation {
                        try {
                            g.disconnect()
                            g.close()
                        } catch (_: Throwable) {
                        }
                    }
                }
            } ?: false
        }

        if (!connected) {
            _state.update {
                BrainState.Error("Could not connect to GATT services. Try again or power-cycle the headband.")
            }
            teardownGatt()
            return
        }

        // First live frame may arrive asynchronously; show live with neutral metrics until samples land.
        if (_state.value !is BrainState.Live) {
            _state.update { placeholderLive() }
        }
    }

    private fun ingestSample(sample: Long) {
        synchronized(ringBufferLock) {
            lastSample = sample
            ring[ringWrite] = sample
            ringWrite = (ringWrite + 1) % RING_SIZE
            if (ringCount < RING_SIZE) ringCount++
            updateTick++
        }
        val shouldPush = synchronized(ringBufferLock) {
            if (updateTick >= UPDATE_EVERY_N_SAMPLES) {
                updateTick = 0
                true
            } else {
                false
            }
        }
        if (shouldPush) {
            _state.update { buildLiveFromRing() }
        }
    }

    private fun buildLiveFromRing(): BrainState {
        val n: Int
        val meanAbs: Double
        val rmsDiff: Float
        val rmsDiff2: Float
        val lastAbs: Float
        synchronized(ringBufferLock) {
            if (ringCount < 4) return placeholderLive()
            n = minOf(ringCount, RING_SIZE)
            var ma = 0.0
            var ssd = 0.0
            var ssd2 = 0.0
            var prev = ring[(ringWrite - n + RING_SIZE * 2) % RING_SIZE].toDouble()
            ma += abs(prev)
            var prevDiff = 0.0
            for (i in 1 until n) {
                val v = ring[(ringWrite - n + i + RING_SIZE * 2) % RING_SIZE].toDouble()
                ma += abs(v)
                val d = v - prev
                ssd += d * d
                val d2 = d - prevDiff
                ssd2 += d2 * d2
                prevDiff = d
                prev = v
            }
            meanAbs = ma / n
            rmsDiff = sqrt(ssd / (n - 1).coerceAtLeast(1)).toFloat()
            rmsDiff2 = sqrt(ssd2 / (n - 1).coerceAtLeast(1)).toFloat()
            lastAbs = abs(lastSample).toFloat()
        }
        val hf = (rmsDiff / HF_SCALE + rmsDiff2 / HF2_SCALE).coerceIn(0f, 1f)
        val level = (meanAbs / ADC_SCALE.toDouble()).coerceIn(0.0, 1.0).toFloat()
        val focusValue = (hf * 0.65f + level * 0.35f).coerceIn(0f, 1f)
        val envelope = (lastAbs / ADC_SCALE).coerceIn(0f, 1f)

        val bands: Map<EegBand, Float> = mapOf(
            EegBand.Delta to (1f - hf * 0.9f).coerceIn(0.08f, 0.95f),
            EegBand.Theta to (0.55f + level * 0.25f - hf * 0.2f).coerceIn(0.08f, 0.95f),
            EegBand.Alpha to (0.45f + (1f - hf) * 0.35f).coerceIn(0.08f, 0.95f),
            EegBand.Beta to (0.25f + hf * 0.65f).coerceIn(0.08f, 0.95f),
            EegBand.Gamma to (0.15f + hf * 0.55f + rmsDiff2 * 0.15f).coerceIn(0.08f, 0.95f),
        )

        return BrainState.Live(
            battery = BatteryPercent(100),
            focus = FocusScore(focusValue),
            bandPowers = bands,
            rawEnvelope = envelope,
            electrodeSite = ElectrodeSite.Fp1,
        )
    }

    private fun placeholderLive(): BrainState.Live = BrainState.Live(
        battery = BatteryPercent(100),
        focus = FocusScore(0.35f),
        bandPowers = EegBand.entries.associateWith { 0.35f },
        rawEnvelope = 0.2f,
        electrodeSite = ElectrodeSite.Fp1,
    )

    override suspend fun disconnect() {
        connectJob?.cancel()
        ioMutex.withLock {
            teardownGatt()
            _state.update { BrainState.Idle }
        }
    }

    override fun dispose() {
        connectJob?.cancel()
        Handler(Looper.getMainLooper()).post {
            teardownGatt()
        }
        scope.cancel()
    }

    private fun teardownGatt() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Throwable) {
        }
        gatt = null
    }

    private fun failQuiet(message: String = "Device disconnected.") {
        _state.update { cur ->
            when (cur) {
                is BrainState.Live,
                BrainState.Connecting,
                BrainState.Searching,
                -> BrainState.Error(message)
                else -> cur
            }
        }
    }

    private companion object {
        val TOKENS_REGEX = Regex("\\s+")
        const val RING_SIZE = 256
        const val SCAN_TIMEOUT_MS = 45_000L
        const val PICKER_SCAN_MS = 10_000L
        const val GATT_SETUP_TIMEOUT_MS = 30_000L
        const val CONNECT_TOTAL_TIMEOUT_MS = 90_000L
        const val UPDATE_EVERY_N_SAMPLES = 24 // Optimized for 600Hz (approx 25 updates/sec)
        const val MTU_REQUEST = 512 // Max MTU for efficient ASCII packet handling
        const val HF_SCALE = 2.5e6f
        const val HF2_SCALE = 5.0e12f
        const val ADC_SCALE = 4_000_000f

        fun uuid(s: String): UUID = UUID.fromString(s)
    }
}

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
import android.util.Log
import dev.neurofocus.neurfocus_dnd.brain.data.BleDeviceCandidate
import dev.neurofocus.neurfocus_dnd.brain.data.BrainDataRepository
import dev.neurofocus.neurfocus_dnd.brain.data.signal.EegSignalProcessor
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.domain.DisconnectEvent
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.EegDebugStats
import dev.neurofocus.neurfocus_dnd.brain.domain.FocusScore
import dev.neurofocus.neurfocus_dnd.util.DefaultDispatcherProvider
import dev.neurofocus.neurfocus_dnd.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * BLE central for NeuroFocus V4 firmware.
 *
 * Key behaviours matching zuna_process:
 *   1. Parses both binary frames (magic 0xE71E) AND ASCII decimal samples — same logic
 *      as ble_eeg_receiver.py parse_frame() / parse_ascii_sample().
 *   2. Converts raw counts → µV using the exact formula from neurofocus_to_fif.py
 *      (Vref=3.3V, PGA=1, INAMP=100, negated for EEG convention).
 *   3. Computes real Goertzel band powers over a rolling 5s window — same 6 bands as
 *      features.py BAND_DEFS.
 *   4. Auto-reconnects indefinitely after any disconnect with exponential backoff.
 *   5. Logs every disconnect event with GATT status code and timestamp.
 *   6. Sends 'b' command after notification setup (BLE_USAGE.md).
 *   7. Requests CONNECTION_PRIORITY_HIGH + MTU 247 on connect.
 *
 * Disconnect investigation notes:
 *   - GATT status 8  = GATT_CONN_TIMEOUT (phone too far, or ESP32 supervision timeout)
 *   - GATT status 19 = GATT_CONN_TERMINATE_PEER_USER (firmware closed connection)
 *   - GATT status 133 = GATT_ERROR / BLE stack reset (Android BLE stack bug — need close()+new gatt)
 *   - GATT status 0 (disconnected) = clean disconnect
 *   All of these trigger reconnect. Status logged to disconnectEvents for Debug tab.
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

    private val _state = MutableStateFlow<BrainState>(BrainState.Idle)
    override val state: StateFlow<BrainState> = _state.asStateFlow()

    // Disconnect event log for Debug tab — capped at 100 entries
    private val _disconnectEvents = MutableSharedFlow<DisconnectEvent>(replay = 100, extraBufferCapacity = 100)
    val disconnectEvents: SharedFlow<DisconnectEvent> = _disconnectEvents.asSharedFlow()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var lastKnownDevice: BluetoothDevice? = null

    private var connectJob: Job? = null
    private var reconnectJob: Job? = null
    private val connectMutex = Mutex()

    // --- Rolling sample buffer (5 s window at ~300 SPS max = 1500 samples) ---
    private val bufLock = Any()
    /** Rolling window of µV values for Goertzel analysis */
    private val uvWindow = ArrayDeque<Float>(WINDOW_SAMPLES_MAX)
    /** Timestamps for effective rate calculation */
    private val tWindow = ArrayDeque<Long>(WINDOW_SAMPLES_MAX)

    // --- Counters (all real, no fakes) ---
    private val totalSamples    = AtomicLong(0)
    private val bleNotifyCount  = AtomicLong(0)
    private val seqGaps         = AtomicInteger(0)
    private val ignoredPayloads = AtomicInteger(0)
    private var lastSeq: Int?   = null
    private var transportMode   = "none"
    private val lastNotifyMs    = AtomicLong(0)

    // Update-throttle: push state every N samples
    private val samplesSinceUpdate = AtomicInteger(0)

    // Reconnect state
    private var reconnectAttempt = 0
    private var lastDisconnectEvent: DisconnectEvent? = null

    // --- Public API ---

    override suspend fun connect() {
        reconnectAttempt = 0
        startConnectLoop(forcedDevice = null)
    }

    override suspend fun connectToDeviceAddress(address: String) {
        val device = try { bluetoothAdapter?.getRemoteDevice(address) }
        catch (_: IllegalArgumentException) { null }
        if (device == null) {
            _state.update { BrainState.Error("Invalid address: $address") }
            return
        }
        lastKnownDevice = device
        reconnectAttempt = 0
        startConnectLoop(forcedDevice = device)
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
                lateinit var cb: ScanCallback
                val handler = Handler(Looper.getMainLooper())
                val finish = Runnable {
                    if (!resumed.compareAndSet(false, true)) return@Runnable
                    try { scanner.stopScan(cb) } catch (_: Throwable) {}
                    cont.resume(byAddress.values.toList().sortedBy { it.displayName.lowercase() })
                }
                cb = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val name = result.device.name ?: result.scanRecord?.deviceName ?: return
                        if (!name.startsWith(BleSpec.DEVICE_NAME_PREFIX, ignoreCase = true)) return
                        byAddress[result.device.address] = BleDeviceCandidate(name, result.device.address)
                    }
                    override fun onBatchScanResults(results: MutableList<ScanResult>) {
                        results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
                    }
                    override fun onScanFailed(errorCode: Int) {
                        if (!resumed.compareAndSet(false, true)) return
                        handler.removeCallbacks(finish)
                        try { scanner.stopScan(this) } catch (_: Throwable) {}
                        cont.resume(emptyList())
                    }
                }
                cont.invokeOnCancellation {
                    handler.removeCallbacks(finish)
                    try { scanner.stopScan(cb) } catch (_: Throwable) {}
                }
                scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
                handler.postDelayed(finish, PICKER_SCAN_MS)
            }
        }

    override suspend fun disconnect() {
        reconnectJob?.cancel()
        connectJob?.cancel()
        connectMutex.withLock {
            teardownGatt()
            _state.update { BrainState.Idle }
        }
    }

    override fun dispose() {
        reconnectJob?.cancel()
        connectJob?.cancel()
        Handler(Looper.getMainLooper()).post { teardownGatt() }
        scope.cancel()
    }

    // --- Connection loop with persistent auto-reconnect ---

    private fun startConnectLoop(forcedDevice: BluetoothDevice?) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = 0
            while (true) {
                connectMutex.withLock {
                    if (attempt > 0) {
                        _state.update {
                            BrainState.Reconnecting(attempt = attempt, lastDisconnect = lastDisconnectEvent)
                        }
                    }
                    doConnect(forcedDevice ?: lastKnownDevice)
                }
                // If we reach here without being Live → wait and retry
                val currentState = _state.value
                if (currentState is BrainState.Live) {
                    // Connected — wait until it disconnects (i.e. state changes away from Live)
                    while (_state.value is BrainState.Live) {
                        delay(500)
                    }
                }
                // Backoff before retry: 2s, 4s, 8s, max 30s
                attempt++
                reconnectAttempt = attempt
                val backoffMs = minOf(2_000L * (1L shl minOf(attempt - 1, 4)), 30_000L)
                Log.w(TAG, "Reconnect attempt $attempt in ${backoffMs}ms (state=${_state.value})")
                delay(backoffMs)
            }
        }
    }

    /** Single attempt to scan (if needed) and connect. Returns when the connection is fully set up or has failed. */
    private suspend fun doConnect(forcedDevice: BluetoothDevice?) {
        if (!BlePermissions.hasAll(app)) {
            _state.update { BrainState.Error("Bluetooth permission required. Grant in Settings.") }
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _state.update { BrainState.Error("Bluetooth is off.") }
            return
        }
        teardownGatt()

        val device: BluetoothDevice? = if (forcedDevice != null) {
            forcedDevice
        } else {
            _state.update { BrainState.Searching }
            withContext(Dispatchers.Main) {
                withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        val scanner = adapter.bluetoothLeScanner
                        if (scanner == null) { cont.resume(null); return@suspendCancellableCoroutine }
                        val cb = object : ScanCallback() {
                            fun finish(d: BluetoothDevice?) {
                                if (cont.isCompleted) return
                                try { scanner.stopScan(this) } catch (_: Throwable) {}
                                cont.resume(d)
                            }
                            override fun onScanResult(callbackType: Int, result: ScanResult) {
                                val name = result.device.name ?: result.scanRecord?.deviceName ?: return
                                if (name.startsWith(BleSpec.DEVICE_NAME_PREFIX, ignoreCase = true)) finish(result.device)
                            }
                            override fun onBatchScanResults(r: MutableList<ScanResult>) { r.forEach { onScanResult(0, it) } }
                            override fun onScanFailed(errorCode: Int) { finish(null) }
                        }
                        cont.invokeOnCancellation { try { scanner.stopScan(cb) } catch (_: Throwable) {} }
                        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
                    }
                }
            }
        }

        if (device == null) {
            _state.update { BrainState.Error("No NeuroFocus device found nearby.") }
            return
        }
        lastKnownDevice = device
        _state.update { BrainState.Connecting }

        val serviceUuid  = UUID.fromString(BleSpec.SERVICE_UUID)
        val dataUuid     = UUID.fromString(BleSpec.DATA_CHARACTERISTIC_UUID)
        val cmdUuid      = UUID.fromString(BleSpec.COMMAND_CHARACTERISTIC_UUID)
        val cccdUuid     = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        val connected = withContext(Dispatchers.Main) {
            withTimeoutOrNull(GATT_SETUP_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val resumed = AtomicBoolean(false)
                    fun tryResume(ok: Boolean) {
                        if (resumed.compareAndSet(false, true)) cont.resume(ok)
                    }

                    val callback = object : BluetoothGattCallback() {
                        private var dataChar: BluetoothGattCharacteristic? = null
                        private var cmdChar: BluetoothGattCharacteristic? = null
                        private var setupStep = 0

                        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                if (status != BluetoothGatt.GATT_SUCCESS) {
                                    logDisconnect(status, newState, "connect failed, GATT status=$status")
                                    tryResume(false); return
                                }
                                this@BleEegRepository.gatt = g
                                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                                g.requestMtu(MTU_REQUEST)
                                g.discoverServices()
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                logDisconnect(status, newState, gattStatusLabel(status))
                                tryResume(false)
                                handleDisconnect(status, newState)
                            }
                        }

                        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                            Log.d(TAG, "MTU changed to $mtu (status=$status)")
                        }

                        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                            if (status != BluetoothGatt.GATT_SUCCESS) { tryResume(false); return }
                            val svc = g.getService(serviceUuid) ?: run { tryResume(false); return }
                            dataChar = svc.getCharacteristic(dataUuid) ?: run { tryResume(false); return }
                            cmdChar  = svc.getCharacteristic(cmdUuid)
                            val dc = dataChar!!
                            g.setCharacteristicNotification(dc, true)
                            val desc = dc.getDescriptor(cccdUuid) ?: run { tryResume(false); return }
                            setupStep = 1
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                @Suppress("DEPRECATION") desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION") g.writeDescriptor(desc)
                            }
                        }

                        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                            if (setupStep != 1 || status != BluetoothGatt.GATT_SUCCESS) { tryResume(false); return }
                            val cc = cmdChar
                            if (cc != null) {
                                setupStep = 2
                                // Send 'b' to start streaming
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    g.writeCharacteristic(cc, BleSpec.CMD_STREAM_START, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                                } else {
                                    @Suppress("DEPRECATION") cc.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                    @Suppress("DEPRECATION") cc.value = BleSpec.CMD_STREAM_START
                                    @Suppress("DEPRECATION") g.writeCharacteristic(cc)
                                }
                            } else {
                                // No command characteristic — notify enabled anyway, try direct
                                resetCounters()
                                tryResume(true)
                            }
                        }

                        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                            if (setupStep == 2) {
                                Log.d(TAG, "Stream-start command write status=$status")
                                resetCounters()
                                tryResume(true)
                            }
                        }

                        // Android < 13 notification callback
                        @Suppress("DEPRECATION")
                        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                            if (characteristic.uuid == dataUuid) {
                                onNotifyData(characteristic.value ?: return)
                            }
                        }

                        // Android 13+ notification callback
                        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                            if (characteristic.uuid == dataUuid) onNotifyData(value)
                        }
                    }

                    val g = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        device.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE)
                    } else {
                        @Suppress("DEPRECATION") device.connectGatt(app, false, callback)
                    }
                    this@BleEegRepository.gatt = g
                    cont.invokeOnCancellation { g?.disconnect(); g?.close() }
                }
            }
        } ?: false

        if (!connected) {
            // Setup failed — state already updated by handleDisconnect or stays as Connecting
            if (_state.value is BrainState.Connecting) {
                _state.update { BrainState.Error("GATT setup failed — will retry.") }
            }
        }
    }

    private fun handleDisconnect(gattStatus: Int, newState: Int) {
        _state.update { cur ->
            when (cur) {
                is BrainState.Live, BrainState.Connecting, BrainState.Searching ->
                    BrainState.Reconnecting(attempt = reconnectAttempt, lastDisconnect = lastDisconnectEvent)
                else -> cur
            }
        }
    }

    private fun logDisconnect(gattStatus: Int, newState: Int, message: String) {
        val event = DisconnectEvent(
            timestampMs = System.currentTimeMillis(),
            gattStatus  = gattStatus,
            newState    = newState,
            message     = message,
        )
        lastDisconnectEvent = event
        Log.w(TAG, "DISCONNECT: status=$gattStatus newState=$newState $message")
        scope.launch { _disconnectEvents.emit(event) }
    }

    // --- Data ingestion ---

    private fun onNotifyData(data: ByteArray) {
        bleNotifyCount.incrementAndGet()
        lastNotifyMs.set(System.currentTimeMillis())

        // Try binary frame first (magic 0xE71E)
        val binary = EegSignalProcessor.parseBinaryFrame(data)
        if (binary != null) {
            val (seq, samples) = binary
            transportMode = "binary"
            val prevSeq = lastSeq
            if (prevSeq != null) {
                val expected = (prevSeq + 1) and 0xFFFF
                if (seq != expected) {
                    seqGaps.incrementAndGet()
                    Log.w(TAG, "Seq gap: expected $expected got $seq (gap=${(seq - expected) and 0xFFFF})")
                }
            }
            lastSeq = seq
            for (count in samples) ingestSample(count)
            return
        }

        // Try ASCII decimal
        val ascii = EegSignalProcessor.parseAsciiSample(data)
        if (ascii != null) {
            transportMode = "ascii"
            ingestSample(ascii)
            return
        }

        // Unparseable
        ignoredPayloads.incrementAndGet()
        Log.v(TAG, "Ignored payload (${data.size} bytes): ${data.take(8).map { it.toInt() and 0xFF }}")
    }

    private fun ingestSample(rawCount: Long) {
        val uv   = EegSignalProcessor.countsToMicrovolts(rawCount)
        val nowMs = System.currentTimeMillis()
        totalSamples.incrementAndGet()

        synchronized(bufLock) {
            uvWindow.addLast(uv)
            tWindow.addLast(nowMs)
            if (uvWindow.size > WINDOW_SAMPLES_MAX) {
                uvWindow.removeFirst()
                tWindow.removeFirst()
            }
        }

        val n = samplesSinceUpdate.incrementAndGet()
        if (n >= UPDATE_EVERY_N) {
            samplesSinceUpdate.set(0)
            pushLiveState(rawCount, uv)
        }
    }

    private fun pushLiveState(lastRawCount: Long, lastUv: Float) {
        // Snapshot the window
        val (windowUv, windowTs) = synchronized(bufLock) {
            uvWindow.toFloatArray() to tWindow.toLongArray()
        }

        val windowN   = windowUv.size
        val sampleRateHz = if (windowN >= 2 && windowTs.isNotEmpty()) {
            val dt = (windowTs.last() - windowTs.first()).coerceAtLeast(1L)
            windowN.toFloat() * 1000f / dt   // samples / seconds
        } else {
            0f
        }

        // Real Goertzel band powers
        val bandPowers: Map<EegBand, Float> = if (windowN >= MIN_WINDOW_FOR_ANALYSIS && sampleRateHz >= 50f) {
            EegSignalProcessor.computeRelativeBandPowers(windowUv, sampleRateHz)
        } else {
            // Not enough data yet — show all zeros explicitly (not fake values)
            EegBand.entries.associateWith { 0f }
        }

        val rmsUv    = EegSignalProcessor.rmsUv(windowUv)
        val focusVal = if (windowN >= MIN_WINDOW_FOR_ANALYSIS) {
            EegSignalProcessor.focusHeuristic(bandPowers)
        } else 0.5f

        val debugStats = EegDebugStats(
            totalSamples              = totalSamples.get(),
            effectiveRateSps          = sampleRateHz,
            bleNotifyCount            = bleNotifyCount.get(),
            seqGaps                   = seqGaps.get(),
            ignoredPayloads           = ignoredPayloads.get(),
            lastRawCount              = lastRawCount,
            lastMicrovoltsCorrected   = lastUv,
            windowRmsUv               = rmsUv,
            windowSamples             = windowN,
            transportMode             = transportMode,
            lastNotifyMs              = lastNotifyMs.get(),
        )

        _state.update {
            BrainState.Live(
                bandPowers      = bandPowers,
                focus           = FocusScore(focusVal),
                rawEnvelopeUv   = abs(lastUv),
                debugStats      = debugStats,
                battery         = null,  // Firmware does not report battery
                electrodeSite   = dev.neurofocus.neurfocus_dnd.brain.domain.ElectrodeSite.Fp1,
            )
        }
    }

    private fun resetCounters() {
        totalSamples.set(0)
        bleNotifyCount.set(0)
        seqGaps.set(0)
        ignoredPayloads.set(0)
        lastSeq = null
        transportMode = "none"
        samplesSinceUpdate.set(0)
        synchronized(bufLock) {
            uvWindow.clear()
            tWindow.clear()
        }
    }

    private fun teardownGatt() {
        try { gatt?.disconnect() } catch (_: Throwable) {}
        try { gatt?.close()      } catch (_: Throwable) {}
        gatt = null
    }

    // --- Helpers ---

    private fun gattStatusLabel(status: Int): String = when (status) {
        0    -> "clean disconnect"
        8    -> "GATT_CONN_TIMEOUT (device too far or supervision timeout)"
        19   -> "GATT_CONN_TERMINATE_PEER_USER (firmware closed connection)"
        22   -> "GATT_CONN_TERMINATE_LOCAL_HOST"
        34   -> "GATT_CONN_FAIL_ESTABLISH"
        133  -> "GATT_ERROR / BLE stack reset (Android bug, will reconnect)"
        257  -> "GATT_CONN_CANCEL"
        else -> "GATT status $status"
    }

    private fun ArrayDeque<Float>.toFloatArray(): FloatArray {
        val arr = FloatArray(size)
        var i = 0
        for (v in this) arr[i++] = v
        return arr
    }

    private fun ArrayDeque<Long>.toLongArray(): LongArray {
        val arr = LongArray(size)
        var i = 0
        for (v in this) arr[i++] = v
        return arr
    }

    companion object {
        private const val TAG = "BleEegRepo"

        /** Rolling window samples: 5 s at max 600 SPS = 3000, padded to 4096 */
        private const val WINDOW_SAMPLES_MAX = 4096

        /** Minimum window for Goertzel analysis (1 s at 50 SPS = 50 samples) */
        private const val MIN_WINDOW_FOR_ANALYSIS = 50

        /** Push state every N ingested samples (~25 Hz at 253 SPS) */
        private const val UPDATE_EVERY_N = 10

        private const val SCAN_TIMEOUT_MS       = 30_000L
        private const val PICKER_SCAN_MS        = 10_000L
        private const val GATT_SETUP_TIMEOUT_MS = 20_000L
        private const val MTU_REQUEST           = 247  // Match bleak exchange_mtu(247) in ble_eeg_receiver.py
    }
}

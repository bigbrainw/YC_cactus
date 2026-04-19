# NEUROFOCUS V4 FIRMWARE — COMPLETE CODEBASE MEMORY
> Author: AI analysis pass (2026-04-18)
> Purpose: A single document an LLM can load to understand every detail of this codebase without re-reading source files.

---

## 0. BIRD'S-EYE SUMMARY

This is an **Arduino + PlatformIO** firmware for a **Seeed XIAO ESP32-S3** that reads **EEG (brainwave) signals** from a **Texas Instruments ADS1220** 24-bit delta-sigma ADC and streams the raw ADC samples over **BLE (Bluetooth Low Energy)** — or over USB-Serial in a fallback mode — to a host application (e.g. a Web Bluetooth page).

The code is modular C++: six focused classes + one config header + one entry point + a power-management helper.

---

## 1. HARDWARE CONTEXT

| Component | Detail |
|-----------|--------|
| MCU | Seeed XIAO ESP32-S3 (Xtensa dual-core LX7, 240 MHz capable, runs at **80 MHz** by firmware choice) |
| ADC | Texas Instruments ADS1220 — 24-bit, delta-sigma, SPI interface |
| LED | One on-board LED wired to **GPIO 21** |
| USB | Native USB-CDC (HWCDC) at 115 200 baud for serial monitor / fallback commands |

### SPI wiring (hard-coded in `config.h`)
| Signal | ESP32-S3 GPIO |
|--------|--------------|
| SCLK | 36 |
| MISO | 37 |
| MOSI | 35 |
| CS (ADS1220 chip-select, active LOW) | 34 |
| DRDY (data-ready, active LOW) | 33 |

---

## 2. PROJECT LAYOUT

```
firmware/v4/
├── platformio.ini          ← build config, lib deps
├── sdkconfig.defaults      ← ESP-IDF Kconfig overrides (power management)
└── src/
    ├── config.h            ← ALL compile-time constants/flags
    ├── main.cpp            ← setup() + loop() entry point
    ├── ads1220_driver.h/.cpp  ← low-level ADS1220 SPI driver
    ├── eeg_streamer.h/.cpp    ← data-pump: ADC → BLE/Serial
    ├── ble_manager.h/.cpp     ← full BLE stack management
    ├── command_handler.h/.cpp ← unified command parser (Serial + BLE)
    ├── led_controller.h/.cpp  ← LED GPIO helper
    └── power_mgmt.h/.cpp      ← ESP32-S3 automatic light sleep
```

---

## 3. COMPILE-TIME CONFIGURATION (`config.h`)

Every tunable constant lives here. **Changing this file affects the whole build.**

```
ENABLE_BLE          = 1           ← Master switch: 1=BLE+Serial, 0=Serial-only
ADS1220_CS_PIN      = 34
ADS1220_DRDY_PIN    = 33
SPI_SCLK_PIN        = 36
SPI_MISO_PIN        = 37
SPI_MOSI_PIN        = 35
LED_PIN             = 21
CMD_STREAM_START    = 'b'
CMD_STREAM_STOP     = 's'
CMD_RESET           = 'v'
SERIAL_BAUD         = 115200
BLE_DEVICE_NAME     = "NEUROFOCUS_V4_01"   ← change suffix per board (_02, _03…)
BLE_ADV_INTERVAL_MIN_UNITS = 0x20  (= 20 × 0.625 ms = 12.5 ms)
BLE_ADV_INTERVAL_MAX_UNITS = 0x40  (= 64 × 0.625 ms = 40 ms)
```

**NOTE:** `DEEP_SLEEP_ENABLED` and `DEEP_SLEEP_SKIP_USB` are referenced in `main.cpp` but are **NOT defined in config.h**, meaning they are **currently undefined/disabled** — the deep-sleep block compiles away via `#if`. `DEEP_SLEEP_DURATION_US` and `BLE_ADVERTISE_WINDOW_MS` are also only referenced inside those disabled blocks. Deep sleep is wired up but dormant.

---

## 4. BUILD SYSTEM (`platformio.ini`)

```ini
platform  = espressif32
board     = seeed_xiao_esp32s3
framework = arduino
board_build.sdkconfig_defaults = sdkconfig.defaults   ← merges Kconfig power options
monitor_speed = 115200
upload_speed  = 115200
board_build.f_cpu = 80000000L    ← CPU locked at 80 MHz (not 240)
lib_deps:
  adafruit/Adafruit BusIO
  adafruit/Adafruit ADS1X15@^2.5.0   ← pulled in but NOT directly used in src/
  wollewald/ADS1220_WE@^1.0.18       ← the actual ADS1220 library used
```

The `Adafruit ADS1X15` library is a dependency of Adafruit BusIO and appears as a transitive dep; the firmware uses **ADS1220_WE** exclusively.

---

## 5. ESP-IDF POWER SETTINGS (`sdkconfig.defaults`)

These Kconfig flags are merged at compile time:

| Flag | Effect |
|------|--------|
| `CONFIG_PM_ENABLE=y` | Enables the ESP-IDF power-management API |
| `CONFIG_FREERTOS_USE_TICKLESS_IDLE=y` | FreeRTOS enters tickless idle → actual light sleep |
| `CONFIG_FREERTOS_IDLE_TIME_BEFORE_SLEEP=3` | 3 FreeRTOS ticks idle before entering light sleep |
| `CONFIG_BT_CTRL_MODEM_SLEEP=y` | BLE radio sleeps between advertising/connection events |
| `CONFIG_BT_CTRL_MODEM_SLEEP_MODE_1=y` | Mode 1 = ORIG (supported on S3 with main XTAL) |
| `CONFIG_BT_CTRL_LPCLK_SEL_MAIN_XTAL=y` | Uses main crystal (no external 32 kHz crystal needed) |
| `CONFIG_BT_CTRL_MAIN_XTAL_PU_DURING_LIGHT_SLEEP=y` | Keeps XTAL powered during light sleep so BLE timing stays accurate |

---

## 6. CLASS-BY-CLASS DEEP DIVE

---

### 6.1 `LedController` (led_controller.h/.cpp)

**Purpose:** Simple wrapper around a single GPIO LED.

**State:** `bool state` — tracks current on/off.

**Methods:**
- `init()` — sets `LED_PIN` (GPIO 21) as `OUTPUT`, calls `off()`.
- `on()` — `digitalWrite(HIGH)`, sets `state=true`.
- `off()` — `digitalWrite(LOW)`, sets `state=false`.
- `toggle()` — flips `state`, writes it.
- `blink(int times, int duration=250)` — loops `times` times: on → delay(duration) → off → delay(duration). **Blocking.** Used only during startup/error, not in the hot loop.

**Usage pattern in main:**
- 3 blinks after power init.
- 2 blinks when ready.
- Rapid 1-blink-100ms loop on fatal error (BLE init fail or ADS1220 init fail) — infinite hang.
- `on()` when streaming starts, `off()` when it stops.

---

### 6.2 `ADS1220Driver` (ads1220_driver.h/.cpp)

**Purpose:** Thin C++ wrapper around the `ADS1220_WE` library for the TI ADS1220 ADC.

**Internal member:** `ADS1220_WE ads` — instantiated with `(ADS1220_CS_PIN=34, ADS1220_DRDY_PIN=33)`.

#### 6.2.1 `initSPI()`
- Calls `SPI.begin(SCLK=36, MISO=37, MOSI=35, -1)` (CS managed manually, so -1).
- Sets `CS_PIN` to OUTPUT, drives it HIGH (deselected).
- Sets `DRDY_PIN` to INPUT_PULLUP.
- Waits 100 ms for hardware stabilization.

#### 6.2.2 `init()` — returns bool
1. Calls `initSPI()`.
2. Calls `reset()` then waits 200 ms.
3. Tries `ads.init()`. On failure, waits 500 ms, resets again, waits 500 ms, retries once more.
4. If second attempt fails, returns `false` → fatal error.
5. Calls `configure()`, waits 500 ms, returns `true`.

#### 6.2.3 `reset()`
Delegates to `ads.reset()` — sends the RESET SPI command to the chip.

#### 6.2.4 `powerDown()`
Delegates to `ads.powerDown()` — puts ADS1220 into 0.3 µA sleep mode.

#### 6.2.5 `wake()`
Calls `ads.start()` + `delay(1)` — 1 ms gives the oscillator ~100 µs to stabilize.

#### 6.2.6 `configure()` — the ADC settings (very important)
All settings written to the 4 configuration registers via SPI:

| Parameter | Value | Meaning |
|-----------|-------|---------|
| Input MUX | `ADS1220_MUX_0_AVSS` | AIN0 single-ended vs AVSS (ground). PGA auto-bypassed in single-ended. |
| Operating Mode | `ADS1220_NORMAL_MODE` | Standard power/noise balance (~415 µA at gain 1) |
| Gain | `ADS1220_GAIN_1` | 1 V/V — full-scale = ±VREF (±3.3V) |
| PGA bypass | `false` | PGA is **active** (not bypassed), provides gain |
| Data rate | `ADS1220_DR_LVL_5` | **600 SPS** in Normal Mode (1.67 ms/sample) |
| Voltage reference | `ADS1220_VREF_REFP0_REFN0` | External reference on REFP0/REFN0 pins |
| Reference value | `3.3V` | Tells library VREF = 3.3V for voltage calculations |
| FIR filter | `ADS1220_50HZ_60HZ` | Simultaneous 50 Hz + 60 Hz power-line rejection |
| Conversion mode | `ADS1220_CONTINUOUS` | ADC runs continuously, DRDY pulses on each sample |

**Key implication:** At 600 SPS, gain 1, VREF=3.3V:
- LSB = 3.3V / 8,388,608 ≈ 393.2 nV
- Noise ≈ 7.56 µVRMS (27.8 µVPP) — typical
- ENOB ≈ 16 bits
- Bandwidth: 0 to ~300 Hz (Nyquist)

#### 6.2.7 `isDataReady()` — returns bool
`digitalRead(DRDY_PIN) == LOW` — LOW means new data available.

#### 6.2.8 `readRawData()` — returns long
Calls `ads.getRawData()` — returns signed 24-bit integer (range -8,388,608 to +8,388,607) representing raw ADC counts. **Not converted to voltage here** — raw counts are streamed directly.

---

### 6.3 `EEGStreamer` (eeg_streamer.h/.cpp)

**Purpose:** The data pump — polls the ADC and dispatches samples to Serial and/or BLE.

**Members:**
- `ADS1220Driver& ads1220` — reference to the shared driver instance.
- `BLEManager* bleManager` — pointer (nullable) to BLE layer.
- `bool streaming = false` — streaming state flag.

**Methods:**

`start()`:
- Calls `ads1220.wake()` → wakes ADS1220 if it was powered down.
- Sets `streaming = true`.
- Prints "Stream started" to Serial.

`stop()`:
- Sets `streaming = false`.
- Calls `ads1220.powerDown()` → puts ADS1220 to sleep.
- Prints "Stream stopped" to Serial.

`update()` — called every loop iteration:
```
if (streaming AND ads1220.isDataReady()) {
    data = ads1220.readRawData()   // signed 24-bit long
    Serial.println(data)           // always printed to Serial
    if (bleManager != null AND bleManager.isConnected()) {
        bleManager.sendData(data)  // also sent via BLE notify
    }
}
```
- **Always outputs to Serial**, regardless of BLE state.
- BLE output is additive, only when connected.

`isStreaming()` — returns `bool streaming`.

---

### 6.4 `BLEManager` (ble_manager.h/.cpp)

**Purpose:** Full BLE GATT server — advertises, handles connections, sends data via notify, receives commands via write.

**Static singleton pattern:** `static BLEManager* instance` — set to `this` in constructor. Needed because BLE callbacks are static methods that must access instance state.

#### 6.4.1 UUIDs

| UUID | Role |
|------|------|
| Service: `0338ff7c-6251-4029-a5d5-24e4fa856c8d` | Custom EEG service |
| Data characteristic: `ad615f2b-cc93-4155-9e4d-f5f32cb9a2d7` | READ + NOTIFY — streams EEG data to client |
| Command characteristic: `b5e3d1c9-8a2f-4e7b-9c6d-1a3f5e7b9c2d` | WRITE + WRITE_NR — receives commands from client |

#### 6.4.2 `init()` — returns bool
1. `BLEDevice::init(DEVICE_NAME)` — "NEUROFOCUS_V4_01".
2. Creates `BLEServer`, attaches `ServerCallbacks`.
3. Creates service with the service UUID.
4. Creates **data characteristic** (READ+NOTIFY), adds `BLE2902` descriptor (Client Characteristic Configuration Descriptor — required to enable notifications).
5. Creates **command characteristic** (WRITE+WRITE_NR), attaches `CommandCallbacks`.
6. Starts the service.
7. Configures advertising: adds service UUID, enables scan response, sets intervals (0x20–0x40 = 12.5–40 ms).
8. Starts advertising.
9. Returns `true` always (no failure path in current code).

#### 6.4.3 State variables
```
volatile bool deviceConnected    ← set by BLE interrupt callbacks
bool oldDeviceConnected          ← previous-loop snapshot, for edge detection
volatile bool commandAvailable   ← flag: new command arrived via BLE write
volatile char lastCommand        ← the actual command byte
```

#### 6.4.4 `update()` — called every loop
Edge detection:
- If `!deviceConnected && oldDeviceConnected` → just disconnected: syncs `oldDeviceConnected = deviceConnected`.
- If `deviceConnected && !oldDeviceConnected` → just connected: prints "BLE: Client connected", syncs `oldDeviceConnected`.

`wasJustConnected()` = `deviceConnected && !oldDeviceConnected` — read **before** `update()` is called in main.cpp (important ordering).
`wasJustDisconnected()` = `!deviceConnected && oldDeviceConnected` — same.

#### 6.4.5 `sendData(long value)`
```
if (!deviceConnected || pDataCharacteristic == null) return;
dataString = String(value)          // converts long to ASCII string
pDataCharacteristic->setValue(dataString.c_str())
pDataCharacteristic->notify()       // pushes to subscribed clients
```
**Data format: ASCII decimal string** (e.g., "1234567"), not binary. This is the raw 24-bit ADC count.

#### 6.4.6 `getCommand()` — returns char
Clears `commandAvailable = false`, returns `lastCommand`.

#### 6.4.7 `stopAdvertising()`
Calls `BLEDevice::stopAdvertising()` — used only in the (currently disabled) deep-sleep path.

#### 6.4.8 Callbacks

`ServerCallbacks` (inner class, inherits `BLEServerCallbacks`):
- `onConnect(server)` → calls `BLEManager::onConnect(server)` → sets `instance->deviceConnected = true`.
- `onConnect(server, param)` → no-op (connection parameter update was removed — it was breaking Web Bluetooth).
- `onDisconnect(server)` → sets `instance->deviceConnected = false`.
- `onDisconnect(server, param)` → sets `instance->deviceConnected = false` + **immediately restarts advertising** via `pServer->getAdvertising()->start()`.

`CommandCallbacks` (inner class, inherits `BLECharacteristicCallbacks`):
- `onWrite(characteristic)` → reads first byte of written value, stores in `instance->lastCommand`, sets `instance->commandAvailable = true`.

---

### 6.5 `CommandHandler` (command_handler.h/.cpp)

**Purpose:** Unified command dispatcher — checks both Serial and BLE for incoming bytes and maps them to typed Command enums.

**Enum `Command`:** `NONE`, `STREAM_START`, `STREAM_STOP`, `RESET`.

**`process()`** — returns `Command`:
1. If `Serial.available()` → read char, call `processChar(c)`.
2. Else if `bleManager != null && bleManager->hasCommand()` → read char via `bleManager->getCommand()`, call `processChar(c)`.
3. Returns `NONE` if nothing available.

**`processChar(char c)`:**
```
'b' → STREAM_START
's' → STREAM_STOP
'v' → RESET
anything else → NONE
```

---

### 6.6 `PowerMgmt` (power_mgmt.h/.cpp)

**Purpose:** Configures ESP32-S3 automatic light sleep.

**`initPowerManagement()`:**
```c
esp_pm_config_esp32s3_t cfg = {
    .max_freq_mhz   = 80,    // never scale above 80 MHz
    .min_freq_mhz   = 80,    // never scale below 80 MHz (locked)
    .light_sleep_enable = true,  // allow tickless idle → light sleep
};
esp_pm_configure(&cfg);
```
- If `ESP_ERR_NOT_SUPPORTED` → prints warning (means `CONFIG_PM_ENABLE` wasn't compiled in).
- If `ESP_OK` → prints "Power: auto light sleep enabled @ 80 MHz".
- CPU is **frequency-locked at 80 MHz** (min=max=80), but light sleep is enabled for idle windows.

Combined with `sdkconfig.defaults` settings, when FreeRTOS has been idle for ≥3 ticks, the CPU enters light sleep, the BLE modem also sleeps between events.

---

## 7. `main.cpp` — FULL EXECUTION FLOW

### 7.1 Global object instantiation (before `setup()`)

```cpp
LedController led;
ADS1220Driver ads1220;
BLEManager bleManager;                      // only if ENABLE_BLE=1
CommandHandler cmdHandler(&bleManager);     // only if ENABLE_BLE=1
EEGStreamer streamer(ads1220, &bleManager); // only if ENABLE_BLE=1
// Serial-only fallback: cmdHandler(nullptr), streamer(ads1220, nullptr)
```

`static unsigned long advertiseStartTime = 0;` — only compiled if `ENABLE_BLE && DEEP_SLEEP_ENABLED` (currently disabled).

### 7.2 `setup()` — runs once

```
Serial.begin(115200)
delay(1000)                        // wait for USB-CDC to enumerate
print "EEG Interface - Initializing..."
led.init()                         // GPIO 21 OUTPUT, LED off
initPowerManagement()              // configure auto light sleep @ 80 MHz
led.blink(3)                       // 3 slow blinks = power-on signal

[ENABLE_BLE block]
bleManager.init()                  // init BLE, start advertising "NEUROFOCUS_V4_01"
  → on failure: infinite blink(1,100ms) = FATAL

ads1220.init()                     // SPI init + ADS1220 configure
  → on failure: infinite blink(1,100ms) = FATAL

print "Ready. Commands: 'b'=start, 's'=stop, 'v'=reset"
led.blink(2)                       // 2 blinks = ready signal

[ENABLE_BLE && DEEP_SLEEP_ENABLED]
advertiseStartTime = millis()      // (currently compiled away)
```

### 7.3 `loop()` — runs continuously

```
[ENABLE_BLE block]
1. justConnected    = bleManager.wasJustConnected()
   justDisconnected = bleManager.wasJustDisconnected()
   ↑ These MUST be read BEFORE bleManager.update() which syncs oldDeviceConnected

2. bleManager.update()
   → logs "BLE: Client connected" on rising edge
   → syncs oldDeviceConnected on both edges

3. if (justConnected):
       streamer.start()    // wake ADC + set streaming=true
       led.on()

4. if (justDisconnected):
       streamer.stop()     // set streaming=false + powerDown ADC
       led.off()
       [DEEP_SLEEP: reset advertiseStartTime]

5. [DEEP_SLEEP block — CURRENTLY DISABLED]
   if (!connected && !streaming && timeout expired):
       streamer.stop()
       bleManager.stopAdvertising()
       ads1220.powerDown()
       esp_sleep_enable_timer_wakeup(DEEP_SLEEP_DURATION_US)
       esp_deep_sleep_start()        // does not return

[ALL MODES]
6. cmd = cmdHandler.process()
   switch(cmd):
     STREAM_START → streamer.start() + led.on()
     STREAM_STOP  → streamer.stop()  + led.off()
     RESET        → streamer.stop() + led.off() + ads1220.init() (re-init)
     NONE         → (nothing)

7. streamer.update()
   → if streaming AND DRDY LOW:
       data = ads1220.readRawData()
       Serial.println(data)
       if BLE connected: bleManager.sendData(data)

8. if (streaming):  delayMicroseconds(100)   // ~100 µs — very tight poll loop
   else:            delay(1)                  // 1 ms — lets FreeRTOS light sleep
```

---

## 8. DATA FLOW DIAGRAM

```
ADS1220 (SPI)
    │  DRDY pin goes LOW (every 1.67 ms at 600 SPS)
    │
    ▼
ADS1220Driver::readRawData()
    │  returns signed 24-bit long (raw ADC counts)
    │
    ▼
EEGStreamer::update()
    ├──► Serial.println(data)          ← always, ASCII decimal
    │
    └──► BLEManager::sendData(data)    ← only if BLE connected
              │  String(data) → setValue() → notify()
              ▼
         BLE client (Web Bluetooth / phone app)
             receives ASCII decimal string via NOTIFY on
             characteristic ad615f2b-cc93-4155-9e4d-f5f32cb9a2d7
```

---

## 9. COMMAND FLOW DIAGRAM

```
[Serial port]          [BLE Client write]
    │                        │
    │                        ▼
    │              BLEManager::CommandCallbacks::onWrite()
    │                        │  stores byte in lastCommand
    │                        │  sets commandAvailable=true
    │                        │
    ▼                        ▼
CommandHandler::process()    ← checks both sources each loop
    │
    ▼
processChar('b'/'s'/'v')
    │
    ▼
main.cpp switch(cmd):
  'b' → EEGStreamer::start()
  's' → EEGStreamer::stop()
  'v' → ads1220.init() (reset+reconfigure)
```

---

## 10. BLE STATE MACHINE

```
POWER ON
    │
    ▼
advertising ("NEUROFOCUS_V4_01", service UUID, 12.5–40 ms intervals)
    │
    │ client connects
    ▼
CONNECTED
    │  auto-start streaming (main.cpp justConnected)
    │
    │ streaming: DRDY → readRaw → notify to client (600 SPS)
    │
    │ client writes command → CommandHandler processes
    │
    │ client disconnects (or drops)
    ▼
DISCONNECTED + stop streaming
    │
    │ onDisconnect(param) → immediately restart advertising
    ▼
advertising  (back to top)
```

---

## 11. ADS1220 CONFIGURATION SUMMARY (ACTIVE SETTINGS)

| Register field | Setting | Value |
|----------------|---------|-------|
| MUX | AIN0 single-ended vs AVSS | Single channel EEG input |
| Operating mode | Normal | ~415 µA power |
| Gain | 1 | Full-scale ±3.3V |
| PGA bypass | false (PGA active) | PGA enabled |
| Data rate | Level 5 | 600 SPS = 1.67 ms/sample |
| Voltage reference | External REFP0/REFN0 | 3.3V external ref |
| FIR filter | 50+60 Hz | Power-line rejection |
| Conversion mode | Continuous | Always converting |
| SPI mode | Mode 1 (CPOL=0, CPHA=1) | 4 MHz max |
| Reference value | 3.3V | LSB ≈ 393.2 nV |

**Effective resolution at these settings:** ~16 bits ENOB, noise ≈ 7.56 µVRMS.

---

## 12. POWER MANAGEMENT ARCHITECTURE

Three layers work together:

1. **CPU frequency lock** (`platformio.ini` + `power_mgmt.cpp`): CPU fixed at 80 MHz (not 240 MHz default). Reduces dynamic power ≈3× vs 240 MHz.

2. **Automatic light sleep** (`power_mgmt.cpp` + `sdkconfig.defaults`):
   - FreeRTOS `tickless idle` → CPU enters light sleep after 3 idle ticks.
   - `delay(1)` in the non-streaming loop gives FreeRTOS idle time to trigger light sleep.
   - While streaming, `delayMicroseconds(100)` is too short for light sleep → CPU stays awake (needed for tight DRDY polling).

3. **BLE modem sleep** (`sdkconfig.defaults`):
   - BLE controller sleeps between advertising/connection events.
   - Uses main XTAL as LP clock (no external 32 kHz crystal needed on XIAO).
   - XTAL stays powered during light sleep for BLE timing accuracy.

4. **ADS1220 power-down** (`eeg_streamer.cpp`):
   - ADC is powered down when streaming stops.
   - ADC is woken when streaming starts.
   - In stream mode: 415 µA; powered down: 0.3 µA typical.

5. **Deep sleep** (`main.cpp` — **CURRENTLY DISABLED**):
   - Code exists but `DEEP_SLEEP_ENABLED` is not defined → entire block compiles away.
   - When enabled: after `BLE_ADVERTISE_WINDOW_MS` ms of no connection, system calls `esp_deep_sleep_start()` with a timer wakeup for `DEEP_SLEEP_DURATION_US`.

---

## 13. CRITICAL DETAILS AND GOTCHAS

1. **`wasJustConnected()` / `wasJustDisconnected()` must be called BEFORE `update()`** in main.cpp. The update() call syncs `oldDeviceConnected`, which would cause these to always return false if called after.

2. **BLE data is ASCII decimal string**, not binary packed. The raw 24-bit ADC value (e.g., `1234567`) is converted to a string before sending via `String(value)`. Clients must parse this string to get the integer.

3. **`onConnect(server, param)` is a no-op** by design. A previous `updateConnParams()` call was removed because it broke Web Bluetooth data transfer. The comment acknowledges that the default ~29s supervision timeout may still cause periodic disconnects.

4. **`onDisconnect(server, param)` restarts advertising immediately** — this is the reconnect mechanism. `onDisconnect(server)` (without param) does NOT restart advertising.

5. **Deep sleep is wired but dormant** — `DEEP_SLEEP_ENABLED` is not defined in `config.h`. All deep-sleep code is inside `#if ENABLE_BLE && DEEP_SLEEP_ENABLED` guards and does not exist in the current binary.

6. **`shouldSkipDeepSleep()` always returns `false`** — the VBUS detection logic for skipping sleep when USB-powered is a stub comment. ESP32-S3's internal USB PHY has no VBUS sense; external GPIO would be needed.

7. **FIR filter at 600 SPS**: The datasheet only specifies 105 dB NMRR for 50/60 Hz at 20 SPS. At 600 SPS the filter's effectiveness is unspecified. Current config uses it anyway.

8. **Single-ended vs differential**: MUX is set to `ADS1220_MUX_0_AVSS` (single-ended). For true EEG, differential mode (e.g., `ADS1220_MUX_0_1`) would give better CMRR. This may be a hardware design constraint.

9. **Gain=1 with PGA active in single-ended mode**: Normally single-ended mode requires PGA bypass. The library handles this internally (`bypassPGA(false)` may be overridden by the library when single-ended MUX is set). Watch for this if changing MUX config.

10. **`ads.init()` in ADS1220_WE** returns a bool. The driver retries once on failure, adds delays between attempts.

11. **BLE advertising interval** (12.5–40 ms) is tuned for Web Bluetooth reliability. Wider intervals save power but can cause "unknown device" or missed scans on some hosts.

12. **`Adafruit ADS1X15`** appears in `lib_deps` but is **not used** by any source file. It's a transitive dependency that got committed. It adds to build size but doesn't affect functionality.

---

## 14. OBJECT OWNERSHIP MAP

```
main.cpp owns (stack-allocated globals):
  led         (LedController)
  ads1220     (ADS1220Driver)
  bleManager  (BLEManager)       ← contains ADS1220_WE as member
  cmdHandler  (CommandHandler)   ← holds ptr to bleManager
  streamer    (EEGStreamer)       ← holds ref to ads1220, ptr to bleManager

BLEManager internals:
  pServer              (BLEServer*)       heap, created in init()
  pDataCharacteristic  (BLECharacteristic*) heap
  pCmdCharacteristic   (BLECharacteristic*) heap
  instance             (static BLEManager*) = this, for callback access
```

---

## 15. FILE SIZE REFERENCE

| File | Lines |
|------|-------|
| ads1220_driver.cpp | 681 (mostly inline documentation/comments) |
| ads1220_driver.h | 25 |
| ble_manager.cpp | 116 |
| ble_manager.h | 80 |
| command_handler.cpp | 32 |
| command_handler.h | 27 |
| config.h | 34 |
| eeg_streamer.cpp | 28 |
| eeg_streamer.h | 25 |
| led_controller.cpp | 31 |
| led_controller.h | 20 |
| main.cpp | 170 |
| power_mgmt.cpp | 22 |
| power_mgmt.h | 7 |

---

## 16. THINGS TO KNOW WHEN MODIFYING CODE

- **To add a new command**: Add a `case 'X':` in `CommandHandler::processChar()`, add a new `Command` enum value, handle it in `main.cpp`'s switch.
- **To change sample rate**: Change `ads.setDataRate(ADS1220_DR_LVL_X)` in `ADS1220Driver::configure()`.
- **To add a second channel**: Change MUX and consider multiplexing — ADS1220 has one ADC, so multi-channel requires sequential MUX switching.
- **To enable deep sleep**: Define `DEEP_SLEEP_ENABLED`, `DEEP_SLEEP_DURATION_US`, and `BLE_ADVERTISE_WINDOW_MS` in `config.h`.
- **To rename the BLE device**: Change `BLE_DEVICE_NAME` in `config.h`. The comment says to change suffix per board (_02, _03, etc.).
- **To add a second board**: Only `BLE_DEVICE_NAME` needs changing — all other config is hardware-generic.
- **To use Serial-only mode**: Set `ENABLE_BLE 0` in `config.h`. All BLE code compiles away.
- **To change data format from ASCII to binary**: Modify `BLEManager::sendData()` — replace `String(value)` + `setValue(c_str())` with a direct byte array `setValue((uint8_t*)&value, 4)`.

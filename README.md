# neurfocus-dnd

Android app (Kotlin + Jetpack Compose) for the NeuroFocus EEG headband. Reads brain activity over BLE from a `NEUROFOCUS_V4_xx` device and visualizes it as a real-time, top-down brain map.

> **Hardware contract** (firmware side) is documented in [`docs/CODEBASE_MEMORY.md`](docs/CODEBASE_MEMORY.md). UUIDs and data format live in `app/src/main/java/dev/neurofocus/neurfocus_dnd/brain/data/ble/BleSpec.kt`.

---

## Requirements

| What | Version | Why |
|---|---|---|
| **JDK** | 21 | Gradle daemon. The build file declares it; if missing, Gradle auto-downloads via Foojay. |
| **Android SDK Platform** | API 36 | `compileSdk` |
| **Android device / emulator** | API 24+ (Android 7.0) | `minSdk` |
| **Gradle** | 9.3.1 | Pinned via wrapper — you don't install it manually |
| **Kotlin** | 2.2.10 | Pulled by the build |
| **AGP** | 9.1.1 | Pulled by the build |

You do **not** need to install Gradle, Kotlin, or AGP yourself. The Gradle wrapper (`gradlew` / `gradlew.bat`) handles them.

---

## Path A — Android Studio (the no-brainer way)

1. **Install Android Studio** (Hedgehog or newer).  
   Download from <https://developer.android.com/studio>.

2. **First launch** — let the setup wizard install the default SDK.

3. **Add SDK Platform 36** if missing:  
   `Tools → SDK Manager → SDK Platforms → Android 16 (API 36)` → Apply.

4. **Open the project**:  
   `File → Open…` → select the project root folder (the one containing `settings.gradle.kts`).

5. **Wait for Gradle sync** — bottom status bar shows progress. First sync downloads ~500 MB of dependencies and may take 5–15 minutes.

6. **Run**:
   - Plug in an Android phone (USB debugging on) **or** start an emulator from `Device Manager`.
   - Click the green ▶ Run button (or `Shift + F10`).

That's it. The app installs and launches.

### See the brain visualization without running

Open `BrainCanvas.kt` and look at the right pane:

- `BrainCanvasIdlePreview` — gray idle state
- `BrainCanvasStaticLivePreview` — high focus snapshot
- `BrainCanvasWarmPreview` — low battery / Night Shift mood
- `BrainCanvasAnimatedPreview` — click ▶ in the preview tab to see it pulse live

No device or emulator needed for previews.

---

## Path B — VS Code

VS Code can build, test, and edit, but **cannot run the app on a device** (no emulator UI, no run-config integration). Use it for code editing; use Android Studio or `adb` to run.

1. **Install JDK 21** — <https://adoptium.net/temurin/releases/?version=21>  
   On Windows, set `JAVA_HOME` to its install dir and add `%JAVA_HOME%\bin` to `PATH`.

2. **Install Android SDK command-line tools** — <https://developer.android.com/studio#command-line-tools-only>  
   Unzip into a folder, e.g. `C:\android-sdk\cmdline-tools\latest\`. Set:
   - `ANDROID_HOME` = `C:\android-sdk`
   - Add `%ANDROID_HOME%\platform-tools` and `%ANDROID_HOME%\cmdline-tools\latest\bin` to `PATH`.

3. **Install required SDK packages**:
   ```bash
   sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
   sdkmanager --licenses
   ```

4. **Install VS Code extensions**:
   - **Kotlin** by `mathiasfrohlich`
   - **Gradle for Java** by `vscjava`
   - **Android iOS Emulator** (optional, if you want emulator launch from VS Code)

5. **Open the folder** in VS Code. The Gradle extension will detect `settings.gradle.kts` and offer to import.

6. **Build / install**:
   ```bash
   ./gradlew assembleDebug                                 # build APK
   ./gradlew installDebug                                  # install on connected device
   adb shell am start -n dev.neurofocus.neurfocus_dnd/.MainActivity   # launch
   ```

   On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.

---

## Path C — Command line only

Useful for CI or quick checks. From the project root:

| Command | What it does |
|---|---|
| `./gradlew tasks` | List every available task |
| `./gradlew assembleDebug` | Build a debug APK to `app/build/outputs/apk/debug/` |
| `./gradlew installDebug` | Install on a connected device (`adb devices` to verify) |
| `./gradlew lint` | Run Android Lint checks |
| `./gradlew test` | Run unit tests |
| `./gradlew clean` | Wipe build outputs |

The first run downloads Gradle 9.3.1 to your user home (`~/.gradle/wrapper/dists/`). Subsequent runs reuse it.

---

## Project layout

```
neurfocusdnd/
├── app/
│   ├── build.gradle.kts                # module build script
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/dev/neurofocus/neurfocus_dnd/
│           ├── MainActivity.kt         # entry point + nav scaffold
│           ├── ui/theme/               # NeurfocusdndTheme (Material3)
│           ├── brain/
│           │   ├── domain/             # BrainState, BrainRegion, EegBand, …
│           │   ├── data/               # BrainDataRepository, FakeEegRepository
│           │   │   └── ble/            # BleSpec — firmware contract
│           │   └── ui/                 # BrainCanvas + previews
│           └── util/                   # DispatcherProvider
├── gradle/
│   ├── libs.versions.toml              # version catalog — single source of truth for deps
│   └── wrapper/                        # pinned Gradle 9.3.1
├── docs/
│   └── CODEBASE_MEMORY.md              # firmware reference (read before BLE work)
├── .cursor/rules/                      # AI agent rules — don't edit casually
├── settings.gradle.kts
├── build.gradle.kts                    # top-level build script
├── gradle.properties
└── local.properties                    # auto-generated; sets sdk.dir — do NOT commit
```

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `SDK location not found` | Set `sdk.dir` in `local.properties`, or set the `ANDROID_HOME` environment variable. |
| Gradle sync hangs forever | Kill the daemon: `./gradlew --stop`, then re-sync. |
| `Could not find tools.jar` / wrong JDK | Confirm `JAVA_HOME` points to a JDK 21 install, not a JRE. |
| `INSTALL_FAILED_USER_RESTRICTED` on device | Enable "USB debugging" and "Install via USB" in Developer Options. |
| Compose Preview "No render" | `Build → Clean Project`, then rebuild. Previews need at least one successful build. |
| Emulator slow | Use a hardware-accelerated AVD (Intel HAXM on x86 hosts, or the Android Emulator Hypervisor Driver). |
| `adb: device unauthorized` | Unplug, replug, accept the RSA key prompt on the phone. |

---

## What's wired up so far

- ✅ Compose theme + nav scaffold (`MainActivity`)
- ✅ Domain types: `BrainState`, `BrainRegion`, `EegBand`, `ElectrodeSite`
- ✅ `FakeEegRepository` — synthetic EEG generator for UI development
- ✅ `BrainCanvas` composable — top-down brain with regional wash + electrode dot + previews
- ⏳ `BrainViewModel` + `BrainScreen` (next phase)
- ⏳ Real BLE `BrainDataRepository` (Phase 4 — Nordic library to be added then)
- ⏳ Gemma LLM advice line (later)

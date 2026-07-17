# E-Ink Dashboard

Always-on personal dashboard for a **Meebook M103** e-ink tablet: Google
Calendar, Todoist and weather on a grayscale, low-refresh panel.

This repository currently contains the **project foundation** (task T01): a
minimal, buildable single-activity Compose app plus the fixed architecture and
toolchain. Product modules (Calendar, Todoist, Weather, the dashboard shell) are
added by later tasks — see [`AGENT_EXECUTION_PLAN.md`](AGENT_EXECUTION_PLAN.md).

## Target device (confirmed — see `DEVICE_AUDIT.md`)

| | |
|---|---|
| Model | Meebook M103 (Haoqing, Rockchip RK3566) |
| OS | Android 11 / API 30 |
| ABI | arm64-v8a (64-bit userspace); app is pure Kotlin, no native code |
| Screen | 1404×1872 px @ 240 dpi (~936×1248 dp), grayscale e-ink |

## Toolchain

Pinned for reproducibility (see [`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md)):

- **JDK 17** (AGP 8.6 requires JDK 17)
- Gradle 8.9 (via the wrapper), Android Gradle Plugin 8.6.1
- Kotlin 1.9.24, Compose Compiler 1.5.14, Compose BOM 2024.06.00
- compileSdk / targetSdk 34, **minSdk 30**, build-tools 34.0.0

### One-time setup

```bash
# JDK 17 (macOS example)
brew install openjdk@17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17

# Android SDK command-line tools + required packages
brew install --cask android-commandlinetools
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
yes | sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses
sdkmanager --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

The SDK path is read from `local.properties` (`sdk.dir=...`, **not** committed)
or from `$ANDROID_SDK_ROOT` / `$ANDROID_HOME`.

## Build & test

```bash
# Full local gate (unit tests + debug APK) — resolves JDK 17 and the SDK for you
./scripts/check.sh

# Or individually:
./gradlew test            # JVM unit tests (Robolectric where needed)
./gradlew assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk
```

Run Gradle with JDK 17. If your default `java` is a different version, either set
`JAVA_HOME` to a JDK 17 install (as above) or use `./scripts/check.sh`, which
resolves JDK 17 automatically.

## Install on the device (smoke test)

```bash
adb devices                                    # device must be authorized
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.eink.dashboard.debug -c android.intent.category.LAUNCHER 1
```

The debug application id is `com.eink.dashboard.debug` (a `.debug` suffix keeps
it installable alongside a future release build).

## Project layout

```
app/src/main/java/com/eink/dashboard/
  core/         # shared foundation — DeviceProfile (T00 constants), e-ink flag (T07)
  dashboard/    # shell, layout, refresh coordinator            (T02)
  modules/      # calendar (T03), todoist (T04), weather/system (T05)
  settings/     # DataStore settings + Settings screen          (T02)
  diagnostics/  # Diagnostics screen                            (T02)
docs/adr/       # architecture decision records
scripts/        # check.sh (CI gate), device_audit.sh (T00)
reports/        # per-task completion reports
```

Packages beyond `core.DeviceProfile` are intentionally empty in T01 — they fix
names and ownership boundaries only. See the ADR for the module-registry design.

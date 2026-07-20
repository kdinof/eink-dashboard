# E-Ink Dashboard

Native, foreground-only personal dashboard for a **Meebook M103** e-ink tablet.
It combines Google Calendar, Todoist, TaskForge/Obsidian tasks, weather, a minute clock and battery status
in a grayscale interface designed for low refresh rates and minimal ghosting.

Calendar, tasks and weather data are rendered and cached on the device. The app
does not perform background refreshes while closed. While the Activity is visible,
it serves an authenticated settings panel on the reader's current Wi-Fi address.

## Features

- Google Calendar through Android `CalendarContract` or the read-only Google Calendar API
- Phone-based Google OAuth through a minimal token broker; calendar data bypasses the broker
- Todoist Today / Upcoming views and task completion
- TaskForge Markdown views with safe local completion through Android's document picker
- Open-Meteo current conditions, daily summary and seven-day forecast
- Minute clock and live battery/charging status
- Configurable block visibility, orientation and calendar range
- Offline cache and stale-data states
- E-ink-safe grayscale UI with foreground-only refresh scheduling
- Todoist token encryption with Android Keystore (AES-256-GCM)
- Local Wi-Fi web settings panel with QR + one-time PIN pairing

Runtime debug builds contain only the six real product modules; demo blocks
are retained solely as test fixtures and are never registered in the app.

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

### Enable phone-based Google Calendar sign-in

Deploy the broker described in [`oauth-worker/README.md`](oauth-worker/README.md),
then bake its public HTTPS origin into the APK:

```bash
./gradlew assembleDebug \
  -PEINK_GOOGLE_BROKER_URL=https://calendar-auth.example.com
```

Without this property the rest of the dashboard works normally, but the web panel
shows that Google OAuth is not configured. Do not put the Google client secret in
Gradle properties or the APK; it belongs only in the Worker secret store.

## Install on the device (smoke test)

```bash
adb devices                                    # device must be authorized
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.eink.dashboard.debug -c android.intent.category.LAUNCHER 1
```

The debug application id is `com.eink.dashboard.debug` (a `.debug` suffix keeps
it installable alongside a release build).

## Project layout

```
app/src/main/java/com/eink/dashboard/
  core/         # shared foundation — DeviceProfile (T00 constants), e-ink flag (T07)
  dashboard/    # shell, layout, refresh coordinator            (T02)
  modules/      # calendar, todoist, taskforge, weather/system
  settings/     # DataStore settings + Settings screen          (T02)
  diagnostics/  # Diagnostics screen                            (T02)
docs/adr/       # architecture decision records
scripts/        # check.sh (CI gate), device_audit.sh (T00)
reports/        # per-task completion reports
```

See the ADRs in [`docs/adr/`](docs/adr/) for the module-registry architecture,
refresh policy and Todoist security decisions. Detailed implementation and
physical-device verification reports are available in [`reports/`](reports/).

## Privacy and credentials

- Never commit `local.properties`, Todoist tokens, signing keys or raw device
  audit dumps; the relevant paths are ignored by Git.
- Enter the Todoist personal token only in the app. Plaintext tokens are not
  stored in Room, DataStore or logs.
- TaskForge uses a persisted Android document URI and a private last-good snapshot.
  The Markdown file remains authoritative; the dashboard never uploads it.
- Remote setup uses unencrypted HTTP and is intended only for a trusted, encrypted
  home Wi-Fi network. Browser bearer tokens are stored only as hashes on the reader.
- Calendar access is read-only; the app does not request `WRITE_CALENDAR`.

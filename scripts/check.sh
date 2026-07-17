#!/usr/bin/env bash
#
# check.sh — local CI gate for the E-Ink Dashboard.
#
# Runs the mandatory foundation checks (T01): unit tests + debug assembly.
# Safe to run repeatedly. No secrets, no device required.
#
# Requirements:
#   - JDK 17 (AGP 8.6 requires exactly JDK 17). Override with JAVA_HOME.
#   - Android SDK with platform-34 + build-tools;34.0.0. Resolved from, in order:
#       $ANDROID_SDK_ROOT, $ANDROID_HOME, or local.properties (sdk.dir=...).
#
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

# ---- Resolve a JDK 17 ---------------------------------------------------------
if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -x /usr/libexec/java_home ]] && /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  elif [[ -x /opt/homebrew/opt/openjdk@17/bin/java ]]; then
    JAVA_HOME="/opt/homebrew/opt/openjdk@17"
  elif [[ -x /usr/lib/jvm/java-17-openjdk-amd64/bin/java ]]; then
    JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
  fi
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "ERROR: JDK 17 not found. Set JAVA_HOME to a JDK 17 install." >&2
  exit 1
fi
export JAVA_HOME
echo "==> JAVA_HOME=${JAVA_HOME}"
"${JAVA_HOME}/bin/java" -version

# ---- Resolve the Android SDK --------------------------------------------------
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "${SDK}" && -f "${ROOT}/local.properties" ]]; then
  SDK="$(grep -E '^sdk\.dir=' "${ROOT}/local.properties" | head -1 | cut -d= -f2- || true)"
fi
if [[ -z "${SDK}" || ! -d "${SDK}/platforms" ]]; then
  echo "ERROR: Android SDK not found. Set ANDROID_SDK_ROOT or local.properties sdk.dir." >&2
  exit 1
fi
export ANDROID_SDK_ROOT="${SDK}"
export ANDROID_HOME="${SDK}"
echo "==> ANDROID_SDK_ROOT=${SDK}"

# ---- Run the checks -----------------------------------------------------------
echo "==> ./gradlew test assembleDebug"
./gradlew --no-daemon test assembleDebug "$@"

echo "==> OK: unit tests passed and debug APK assembled."
echo "    APK: app/build/outputs/apk/debug/app-debug.apk"

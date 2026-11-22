#!/usr/bin/env bash
set -euo pipefail

emulator_log="${1:?Provide the emulator log path}"
acceleration="${2:-on}"
api_level="${3:-36}"
system_image="system-images;android-$api_level;aosp_atd;x86_64"
test_results=app/build/outputs/androidTest-results/connected/debug
avd_name=scancode-test
emulator_port=5554
boot_timeout=300
if [[ "$acceleration" == off ]]; then
  export ANDROID_I_WANT_MY_TCG=yes
  boot_timeout=1200
fi

if [[ ! -x "$ANDROID_HOME/emulator/emulator" ||
      ! -f "$ANDROID_HOME/system-images/android-$api_level/aosp_atd/x86_64/package.xml" ]]; then
  sdkmanager --install emulator platform-tools "$system_image"
fi
mkdir -p "$(dirname "$emulator_log")"
export ANDROID_AVD_HOME
ANDROID_AVD_HOME=$(mktemp -d)
emulator_pid=
cleanup() {
  if [[ -n "$emulator_pid" ]]; then
    kill "$emulator_pid" 2>/dev/null || true
    wait "$emulator_pid" 2>/dev/null || true
  fi
  rm -rf -- "$ANDROID_AVD_HOME"
}
trap cleanup EXIT
export ANDROID_SERIAL="emulator-$emulator_port"

avdmanager create avd --force --name "$avd_name" --package "$system_image" <<< 'no'
"$ANDROID_HOME/emulator/emulator" -avd "$avd_name" -port "$emulator_port" -accel "$acceleration" \
  -no-window -gpu swiftshader -no-snapshot -noaudio -no-boot-anim \
  -camera-front none -camera-back none > "$emulator_log" 2>&1 &
emulator_pid=$!

echo "Waiting for the Android API $api_level emulator to boot (acceleration: $acceleration)."
if ! timeout "$boot_timeout" bash -c '
  until [[ $(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r") == 1 ]] &&
      adb shell service check package 2>/dev/null | grep -q ": found" &&
      adb shell service check activity 2>/dev/null | grep -q ": found"; do
    if ! kill -0 "$1" 2>/dev/null; then
      echo "The Android emulator exited before boot completed." >&2
      exit 1
    fi
    sleep 2
  done
' _ "$emulator_pid"; then
  tail -n 60 "$emulator_log" >&2
  exit 1
fi

adb shell input keyevent 82
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

test_started="$ANDROID_AVD_HOME/test-started"
touch "$test_started"
bash ./gradlew --no-daemon :app:connectedDebugAndroidTest

# Gradle can finish successfully after an APK installation error without running any tests.
if [[ ! -d "$test_results" ]] || ! find "$test_results" -name 'TEST-*.xml' -newer "$test_started" \
    -exec grep -lE '<testcase[[:space:]]' {} + | grep .; then
  echo "The Android run produced no test cases. See $emulator_log for emulator diagnostics." >&2
  exit 1
fi

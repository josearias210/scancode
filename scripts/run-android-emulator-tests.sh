#!/usr/bin/env bash
set -euo pipefail

emulator_log="${1:?Provide the emulator log path}"
acceleration="${2:-on}"
system_image='system-images;android-36;google_apis;x86_64'
avd_name=scancode-test
emulator_port=5554
boot_timeout=300
if [[ "$acceleration" == off ]]; then
  export ANDROID_I_WANT_MY_TCG=yes
  boot_timeout=1200
fi

if [[ ! -x "$ANDROID_HOME/emulator/emulator" ||
      ! -f "$ANDROID_HOME/system-images/android-36/google_apis/x86_64/package.xml" ]]; then
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

echo "Waiting for the Android emulator to boot (acceleration: $acceleration)."
if ! timeout "$boot_timeout" bash -c '
  while [[ $(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r") != 1 ]]; do
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
bash ./gradlew --no-daemon :app:connectedDebugAndroidTest

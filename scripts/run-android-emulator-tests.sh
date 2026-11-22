#!/usr/bin/env bash
set -euo pipefail

emulator_log="${1:?Provide the emulator log path}"
system_image='system-images;android-36;google_apis;x86_64'
avd_name=scancode-ci
export ANDROID_SERIAL=emulator-5554

sdkmanager --install emulator platform-tools "$system_image"
avdmanager create avd --force --name "$avd_name" --package "$system_image" <<< 'no'
"$ANDROID_HOME/emulator/emulator" -avd "$avd_name" -port 5554 \
  -no-window -gpu swiftshader -no-snapshot -noaudio -no-boot-anim \
  -camera-front none -camera-back none > "$emulator_log" 2>&1 &
emulator_pid=$!
trap 'kill "$emulator_pid" 2>/dev/null || true' EXIT

timeout 300 bash -c '
  while [[ $(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r") != 1 ]]; do
    if ! kill -0 "$1" 2>/dev/null; then
      echo "The Android emulator exited before boot completed." >&2
      exit 1
    fi
    sleep 2
  done
' _ "$emulator_pid"

adb shell input keyevent 82
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
bash ./gradlew --no-daemon :app:connectedDebugAndroidTest

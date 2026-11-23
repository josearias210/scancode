#!/usr/bin/env bash
set -euo pipefail

emulator_log="${1:?Provide the emulator log path}"
acceleration="${2:-on}"
api_level="${3:-36}"
distribution_apk="${4:-}"
system_image="system-images;android-$api_level;aosp_atd;x86_64"
test_results=app/build/outputs/androidTest-results/connected/debug
test_artifacts=app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected
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
# Gradle's device discovery needs a response to getprop within two seconds.
if ! timeout "$boot_timeout" bash -c '
  until properties=$(timeout 2 adb shell getprop 2>/dev/null) &&
      grep -Fq "[sys.boot_completed]: [1]" <<< "$properties" &&
      grep -Fq "[ro.build.version.sdk]: [$2]" <<< "$properties" &&
      adb shell service check package 2>/dev/null | grep -q ": found" &&
      adb shell service check activity 2>/dev/null | grep -q ": found"; do
    if ! kill -0 "$1" 2>/dev/null; then
      echo "The Android emulator exited before boot completed." >&2
      exit 1
    fi
    sleep 2
  done
' _ "$emulator_pid" "$api_level"; then
  tail -n 60 "$emulator_log" >&2
  exit 1
fi

adb shell input keyevent 82
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

if [[ -n "$distribution_apk" ]]; then
  adb install "$distribution_apk"
  launch=$(adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
    -p com.scancode.app | tr -d '\r')
  printf '%s\n' "$launch"
  grep -Fxq 'Status: ok' <<< "$launch"
  if ! timeout 45 bash -c '
    until adb shell uiautomator dump /data/local/tmp/scancode-home.xml >/dev/null &&
        screen=$(adb shell cat /data/local/tmp/scancode-home.xml) &&
        [[ "$screen" == *"resource-id=\"com.scancode.app:id/scanTitle\""* &&
           "$screen" == *"resource-id=\"com.scancode.app:id/historyButton\""* ]]; do
      sleep 1
    done
  '; then
    echo "The signed APK did not show the ScanCode home screen." >&2
    adb logcat -b crash -d >&2
    exit 1
  fi
  echo "The signed distribution APK installed and showed the ScanCode home screen."
  exit 0
fi

test_started="$ANDROID_AVD_HOME/test-started"
touch "$test_started"
gradle_log="$ANDROID_AVD_HOME/instrumentation.log"
for attempt in 1 2 3; do
  if bash ./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest 2>&1 | tee "$gradle_log"; then
    break
  fi
  if [[ "$acceleration" != off || "$attempt" == 3 ]] ||
      ! grep -Fq 'Unknown API Level' "$gradle_log" ||
      ! grep -Fq '0 of which were compatible.' "$gradle_log"; then
    exit 1
  fi
  echo "Gradle could not identify the software emulator. Retrying after Android settles."
  sleep 15
done

# Gradle can finish successfully after an APK installation error without running any tests.
if [[ ! -d "$test_results" ]] || ! find "$test_results" -name 'TEST-*.xml' -newer "$test_started" \
    -exec grep -lE '<testcase[[:space:]]' {} + | grep .; then
  echo "The Android run produced no test cases. See $emulator_log for emulator diagnostics." >&2
  exit 1
fi

if [[ ! -d "$test_artifacts" ]] || ! find "$test_artifacts" -type f -name 'home-screen.png' \
    -newer "$test_started" -size +0c -print | grep .; then
  echo "The Android run did not export a new home-screen.png." >&2
  exit 1
fi

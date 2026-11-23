#!/usr/bin/env bash
set -euo pipefail

download="${1:?Provide the downloadable APK path}"
build_tools="$ANDROID_HOME/build-tools/37.0.0"

cp -- app/build/outputs/apk/debug/app-debug.apk "$download"
"$build_tools/zipalign" -c -P 16 4 "$download"
"$build_tools/apksigner" verify --verbose --print-certs "$download"
sha256sum "$download"

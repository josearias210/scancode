#!/usr/bin/env bash
set -euo pipefail

unsigned_apk="${1:?Provide the unsigned release APK}"
signed_apk="${2:?Provide the output APK path}"
build_tools="$ANDROID_HOME/build-tools/37.0.0"
: "${APK_SIGNING_KEYSTORE_BASE64:?Configure the distribution keystore in GitHub Actions}"
: "${APK_SIGNING_PASSWORD:?Configure the distribution keystore password in GitHub Actions}"
: "${APK_SIGNING_CERT_SHA256:?Configure the expected signing certificate fingerprint}"

umask 077
signing_dir=$(mktemp -d)
trap 'rm -f -- "$signing_dir/key.p12" "$signing_dir/aligned.apk"; rmdir -- "$signing_dir"' EXIT
base64 --decode <<< "$APK_SIGNING_KEYSTORE_BASE64" > "$signing_dir/key.p12"
unset APK_SIGNING_KEYSTORE_BASE64

"$build_tools/zipalign" -P 16 -f 4 "$unsigned_apk" "$signing_dir/aligned.apk"
"$build_tools/apksigner" sign --ks "$signing_dir/key.p12" --ks-type PKCS12 \
  --ks-pass env:APK_SIGNING_PASSWORD --key-pass env:APK_SIGNING_PASSWORD \
  --out "$signed_apk" "$signing_dir/aligned.apk"
unset APK_SIGNING_PASSWORD

"$build_tools/zipalign" -c -P 16 4 "$signed_apk"
certificate=$("$build_tools/apksigner" verify --verbose --print-certs "$signed_apk")
printf '%s\n' "$certificate"
if ! grep -Fiq "certificate SHA-256 digest: $APK_SIGNING_CERT_SHA256" <<< "$certificate"; then
  echo "The APK is signed with a different distribution certificate." >&2
  exit 1
fi
badging=$("$build_tools/aapt" dump badging "$signed_apk")
if grep -Fxq 'application-debuggable' <<< "$badging"; then
  echo "The distribution APK must use the release build." >&2
  exit 1
fi
sha256sum "$signed_apk"

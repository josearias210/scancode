#!/usr/bin/env bash
set -euo pipefail

apk="${1:?Provide the debug APK path}"
bundle="${2:?Provide the Android App Bundle path}"

"$ANDROID_HOME/build-tools/37.0.0/zipalign" -c -P 16 4 "$apk"

tmp_dir=$(mktemp -d)
trap 'rm -r "$tmp_dir"' EXIT
unzip -q "$bundle" 'base/lib/arm64-v8a/*.so' 'base/lib/x86_64/*.so' -d "$tmp_dir"

for library in "$tmp_dir"/base/lib/{arm64-v8a,x86_64}/*.so; do
    alignments=$(readelf -lW "$library" | awk '$1 == "LOAD" {print $NF}')
    if [[ -z "$alignments" ]]; then
        printf 'No LOAD segments in %s\n' "$library" >&2
        exit 1
    fi
    while IFS= read -r alignment; do
        if (( alignment < 16384 )); then
            printf 'Native library requires 16 KiB LOAD alignment: %s (%s)\n' "$library" "$alignment" >&2
            exit 1
        fi
    done <<< "$alignments"
    printf 'Verified %s\n' "${library#"$tmp_dir/base/lib/"}"
done

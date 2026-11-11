#!/usr/bin/env bash
# Verify CI-created universal Android APK metadata before offering it to device testers.
# Usage: bash scripts/verify_universal_apk.sh <apk> [output-dir]
set -euo pipefail
APK="${1:?APK path is required}"
OUT="${2:-$(dirname "$APK")}"
test -s "$APK" || { echo "::error::Universal APK is missing or empty"; exit 1; }
command -v unzip >/dev/null || { echo "::error::unzip required"; exit 1; }
mkdir -p "$OUT"

# Downloaded architecture-specific APKs are NOT interchangeable. A universal
# package must carry all three supported Flutter engine architectures.
ENTRIES="$(unzip -Z1 "$APK")"
for abi in armeabi-v7a arm64-v8a x86_64; do
  if ! grep -Fxq "lib/$abi/libflutter.so" <<<"$ENTRIES"; then
    echo "::error::Universal APK lacks Flutter engine ABI: $abi"
    exit 1
  fi
done
AAPT=""
if command -v aapt >/dev/null 2>&1; then
  AAPT="$(command -v aapt)"
else
  SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -n "$SDK_ROOT" ]]; then
    for candidate in "$SDK_ROOT"/build-tools/*/aapt; do
      if [[ -x "$candidate" ]]; then AAPT="$candidate"; fi
    done
  fi
fi
if [[ -z "$AAPT" ]]; then
  echo "::error::Android aapt required for SDK/package verification"
  exit 1
fi
BADGING="$("$AAPT" dump badging "$APK")"
{
  echo 'Adaptive Landscape - UNIVERSAL QA APK (NOT PLAY-SIGNED)'
  echo 'Architecture support: armeabi-v7a arm64-v8a x86_64'
  echo 'Deployment: internal/physical QA only; no Play signing guarantee'
  printf '%s\n' "$BADGING" | grep -E '^(package:|sdkVersion:|targetSdkVersion:|native-code:)' || true
  echo 'Physical install: NOT VERIFIED - package built and inspected only'
  echo 'Install failure hints: NO_MATCHING_ABIS=wrong APK or device ABI; OLDER_SDK=Android too old; UPDATE_INCOMPATIBLE=signing mismatch; VERSION_DOWNGRADE=versionCode too low.'
} > "$OUT/APK_COMPATIBILITY.txt"
sha256sum "$APK" | sed "s#  $APK#  $(basename "$APK")#" > "$OUT/SHA256SUMS-universal.txt"
echo 'PASS: Android universal APK contains ARMv7 + ARM64 + x86_64 Flutter engine.'
cat "$OUT/APK_COMPATIBILITY.txt"

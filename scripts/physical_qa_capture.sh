#!/usr/bin/env bash
# Physical-device data collection is EXPLICIT and LOCAL ONLY.
# Usage: bash scripts/physical_qa_capture.sh [apk-path] [output-dir]
set -euo pipefail
APK="${1:-}"
OUT="${2:-physical_qa_local}"
if ! command -v adb >/dev/null 2>&1; then
  echo 'ERROR: adb is missing. Install Android platform-tools.' >&2
  exit 2
fi
if [ "$(adb get-state 2>/dev/null)" != device ]; then
  echo 'ERROR: connect and authorize exactly one physical Android device.' >&2
  exit 2
fi
mkdir -p "$OUT"
REPORT="$OUT/qa_snapshot.txt"
{
  echo 'Adaptive Landscape physical QA — unverified until human signs results'
  echo "Captured UTC: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo 'Privacy: no screenshots/logcat/device serial/app inventory captured'
  echo "Android release: $(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "Android API: $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "Manufacturer: $(adb shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "Model: $(adb shell getprop ro.product.model | tr -d '\r')"
  echo "Battery: $(adb shell dumpsys battery | grep -E '^[[:space:]]*(level|temperature|status):' | tr -d '\r' || true)"
  if [ -n "$APK" ]; then
    if [ ! -f "$APK" ]; then echo "APK not found: $APK" >&2; exit 3; fi
    echo "APK SHA256: $(sha256sum "$APK" | cut -d ' ' -f 1)"
    echo "APK filename: $(basename "$APK")"
  else
    echo 'APK SHA256: NOT PROVIDED (required before certifying)'
  fi
  echo 'Screenshot, video, FPS, power, launcher and subjective quality: MANUAL QA REQUIRED'
} > "$REPORT"
# Memory report is limited to the app package and stays on the computer.
adb shell dumpsys meminfo it.riccardopinato.adaptive_landscape > "$OUT/app_meminfo.txt" || true
echo "Local QA evidence: $OUT"
echo 'Inspect files before sharing. Do not post logs, APK keys or device serial numbers.'

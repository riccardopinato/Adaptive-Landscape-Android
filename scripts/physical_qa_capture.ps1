# Physical QA evidence is collected ONLY after the user explicitly runs this script.
# Usage: powershell -ExecutionPolicy Bypass -File .\scripts\physical_qa_capture.ps1 -ApkPath "C:\path\to\app.apk"
param(
  [string]$ApkPath = "",
  [string]$OutputDir = ".\physical_qa_local"
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
  throw 'adb is required (Android platform-tools).'
}
$deviceState = (& adb get-state 2>$null | Out-String).Trim()
if ($deviceState -ne 'device') {
  throw 'Connect and authorize exactly one physical Android device.'
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$report = New-Object System.Collections.Generic.List[string]
$report.Add('Adaptive Landscape physical QA - unverified until human signs results')
$report.Add('Captured UTC: ' + [DateTime]::UtcNow.ToString('o'))
$report.Add('Privacy: no screenshots, logcat, device serial or app inventory captured')
foreach ($item in @(
    @('Android release', 'ro.build.version.release'),
    @('Android API', 'ro.build.version.sdk'),
    @('Manufacturer', 'ro.product.manufacturer'),
    @('Model', 'ro.product.model')
)) {
  $value = (& adb shell getprop $item[1] | Out-String).Trim()
  $report.Add($item[0] + ': ' + $value)
}
$battery = & adb shell dumpsys battery
$report.Add('Battery: ' + (($battery | Where-Object {
  $_ -match '^\s*(level|temperature|status):'
}) -join '; ').Trim())
if ($ApkPath -ne '') {
  if (-not (Test-Path -Path $ApkPath -PathType Leaf)) {
    throw "APK not found: $ApkPath"
  }
  $hash = (Get-FileHash -Path $ApkPath -Algorithm SHA256).Hash
  $report.Add('APK SHA256: ' + $hash)
  $report.Add('APK filename: ' + (Split-Path -Path $ApkPath -Leaf))
} else {
  $report.Add('APK SHA256: NOT PROVIDED (required for certification)')
}
$report.Add('Screenshots, video, FPS, heat, launcher and subjective quality: MANUAL QA REQUIRED')
$report | Set-Content -Encoding UTF8 -Path (Join-Path $OutputDir 'qa_snapshot.txt')
& adb shell dumpsys meminfo it.riccardopinato.adaptive_landscape |
  Set-Content -Encoding UTF8 -Path (Join-Path $OutputDir 'app_meminfo.txt')
Write-Host "Local-only QA files created in $OutputDir"
Write-Host 'Review files before sharing. Never upload APK private keys or device serials.'

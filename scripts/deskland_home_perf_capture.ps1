# Local, opt-in Windows ADB benchmark. No install, root, screen recording,
# screenshots, logcat, device serial or upload. Keep output outside Git.
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("baseline","candidate")]
    [string]$Label,
    [ValidateRange(30,240)]
    [int]$RefreshHz = 60,
    [string]$ApkPath = "",
    [string]$OutputDirectory = "physical_qa_local"
)
$ErrorActionPreference = "Stop"
$Package = "it.riccardopinato.adaptive_landscape"
$adb = (Get-Command adb -ErrorAction Stop).Source
$connected = @(& $adb devices | Where-Object { $_ -match '^\S+\s+device\s*$' })
if ($connected.Count -ne 1) {
    throw "Connect exactly one authorized Android device by USB. No serial is recorded."
}
$installed = @(& $adb shell pm path $Package 2>&1)
if ($LASTEXITCODE -ne 0 -or -not ($installed -match 'package:')) {
    throw "Deskland is not installed on the connected Android device."
}
$apkHash = "not_supplied"
if ($ApkPath) {
    if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
        throw "APK not found: $ApkPath"
    }
    $apkHash = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$prefix = Join-Path $OutputDirectory ("deskland-$Label-$stamp")
Write-Host "Deskland / $Label — local measurement only"
Write-Host "Save the exact APK hash and use the same refresh rate for both runs."
Write-Host "This script will NOT install, uninstall, replace the default Home, or change any app data."
Write-Host "Close overlays, use identical settings and battery state for both versions."
Read-Host "Press Enter to reset Android gfxinfo counters before the interactions"
& $adb shell dumpsys gfxinfo $Package reset | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Unable to reset gfxinfo" }
Write-Host "Perform: 15 Home returns, 15 Home page swipes, 10 app drawer openings,"
Write-Host "and 10 icon drags. Avoid leaving the Home while idle for long periods."
Read-Host "Press Enter when finished"
$stats = @(& $adb shell dumpsys gfxinfo $Package framestats)
if ($LASTEXITCODE -ne 0 -or -not ($stats -match "---PROFILEDATA---")) {
    throw "No Android gfxinfo framestats. Do not invent a PASS or timing result."
}
$stats | Set-Content -LiteralPath ($prefix + ".gfxinfo.txt") -Encoding UTF8

# Parse only documented CSV fields; all values are timestamps in nanoseconds.
# Skip frames with nonzero Flags and app-switch pauses over 1000ms.
$activeProfile = $false
$headers = @()
$measured = New-Object System.Collections.Generic.List[double]
foreach ($line in $stats) {
    if ($line -match '---PROFILEDATA---') {
        $activeProfile = -not $activeProfile
        continue
    }
    if (-not $activeProfile) { continue }
    if ($line -match '^Flags,') {
        $headers = $line.Trim().Split(',')
        continue
    }
    if (-not $headers.Count) { continue }
    $values = $line.Trim().Split(',')
    $idxFlags = [array]::IndexOf($headers, "Flags")
    $idxStart = [array]::IndexOf($headers, "IntendedVsync")
    $idxEnd = [array]::IndexOf($headers, "FrameCompleted")
    if ($idxFlags -lt 0 -or $idxStart -lt 0 -or $idxEnd -lt 0) { continue }
    if ($values.Count -le [Math]::Max($idxStart,$idxEnd)) { continue }
    try {
        if ([long]$values[$idxFlags] -ne 0) { continue }
        $duration = ([double][long]$values[$idxEnd] -
                     [double][long]$values[$idxStart]) / 1000000.0
        if ($duration -ge 1 -and $duration -le 1000) { $measured.Add($duration) }
    } catch { continue }
}
if ($measured.Count -eq 0) {
    throw "No valid frame rows; raw gfxinfo was saved for manual inspection."
}
$ordered = @($measured | Sort-Object)
$p50 = [Math]::Round($ordered[[Math]::Max(0,[int][Math]::Ceiling($ordered.Count*0.5)-1)],2)
$p95 = [Math]::Round($ordered[[Math]::Max(0,[int][Math]::Ceiling($ordered.Count*0.95)-1)],2)
$budget = 1000.0 / $RefreshHz
$janky = @($measured | Where-Object { $_ -gt $budget*1.5 }).Count
$veryJanky = @($measured | Where-Object { $_ -gt $budget*2.5 }).Count
$summary = [ordered]@{
    kind = "gfxinfo_framestats" # App UI frames, not wallpaper SurfaceFlinger frames
    label = $Label
    package = $Package
    refresh_hz_user_supplied = $RefreshHz
    apk_sha256 = $apkHash
    captured_local_at = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssK")
    frame_count = $measured.Count
    frame_duration_p50_ms = $p50
    frame_duration_p95_ms = $p95
    slow_frames_over_1_5x = $janky
    severe_frames_over_2_5x = $veryJanky
    slow_frame_ratio = [Math]::Round($janky / $measured.Count,4)
    note = "Local ADB aggregate. Device-specific conditions matter; no UX PASS implied."
}
$summary | ConvertTo-Json -Depth 4 |
    Set-Content -LiteralPath ($prefix + ".summary.json") -Encoding UTF8
Write-Host ("Frames: {0}; p50={1}ms; p95={2}ms; >1.5x frame budget={3:P1}" -f
    $measured.Count,$p50,$p95,($janky / $measured.Count))
Write-Host "Local evidence: $prefix.summary.json and $prefix.gfxinfo.txt"
Write-Host "Do not share the raw trace unless you choose to; both files remain local."

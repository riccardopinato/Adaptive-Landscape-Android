# From this kit directory: powershell -ExecutionPolicy Bypass -File .\BOOTSTRAP_WINDOWS.ps1
# Requires Flutter SDK, Android SDK, JDK and adb configured on the PC.
param([string]$Destination = (Join-Path $PSScriptRoot "generated_app"))
$ErrorActionPreference = 'Stop'
if (Test-Path $Destination) { throw "The destination already exists. Refusing to overwrite: $Destination" }
flutter --version
flutter create --platforms=android,web --template=app --org it.riccardopinato --project-name adaptive_landscape $Destination
if ($LASTEXITCODE -ne 0) { throw 'flutter create failed' }
$source = $PSScriptRoot
$paths = @('pubspec.yaml', 'l10n.yaml', 'lib', 'test', 'shaders', 'android/app/src/main/AndroidManifest.xml', 'android/app/src/main/res/xml', 'android/app/src/main/res/values/strings.xml', 'android/app/src/main/res/drawable/ic_landscape.xml', 'android/app/src/main/kotlin/it/riccardopinato/adaptivelandscape')
foreach ($rel in $paths) {
  $from = Join-Path $source $rel
  $to = Join-Path $Destination $rel
  if (Test-Path $from -PathType Container) {
    New-Item -Force -ItemType Directory -Path $to | Out-Null
    Copy-Item -Path (Join-Path $from '*') -Destination $to -Recurse -Force
  } else {
    New-Item -Force -ItemType Directory -Path (Split-Path $to) | Out-Null
    Copy-Item -Path $from -Destination $to -Force
  }
}
Remove-Item -Path (Join-Path $Destination "test/widget_test.dart") -ErrorAction SilentlyContinue
Push-Location $Destination
try {
  flutter pub get
  if ($LASTEXITCODE -ne 0) { throw 'pub get failed' }
  flutter gen-l10n
  if ($LASTEXITCODE -ne 0) { throw 'l10n failed' }
  flutter analyze
  if ($LASTEXITCODE -ne 0) { throw 'analyze failed' }
  flutter test
  if ($LASTEXITCODE -ne 0) { throw 'tests failed' }
  flutter build apk --debug --dart-define=BUILD_SOURCE_SHA=manual-windows
  if ($LASTEXITCODE -ne 0) { throw 'debug APK build failed' }
  Write-Host 'Debug APK: build/app/outputs/flutter-apk/app-debug.apk'
} finally { Pop-Location }

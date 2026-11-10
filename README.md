# Adaptive Landscape — Android MVP v0.1.0 (source repository)

Original Flutter control app + Android `WallpaperService` rendering engine. Inspired by the *product concept* of BLEZZ0102/Deskland, **not a copy of its GPL code or assets**. Deskland GPL-3.0 does not extend to independently implemented original code.

## What exists in this kit
- Flutter editor UI, 2 first-party vector landscapes, basic animation, 5×8 manual icon-position calibration.
- Android native `WallpaperService` drawing wallpaper independently of a Flutter UI activity (20fps only while visible; 8fps in battery saver).
- Native MethodChannel settings bridge and Android live wallpaper picker.
- Six-locale ARB foundation (EN/IT/ES/FR/DE/PT), pure Dart field tests, no sign-in, API, ads, subscriptions or user data upload.
- No third-party Deskland graphics, WebView2, Windows host, or original JS implementation.

## Required to run
This repository holds a **deterministic source overlay** (the native Flutter/Gradle scaffold is created by the bootstrap script and by GitHub Actions), not a compiled APK. `flutter`/Android SDK are not installed in the author's execution environment, so compile/runtime verification is **NOT VERIFIED**. On a computer with Android SDK, JDK and Flutter 3.41.4 (as selected by GitHub Actions; verify your local toolchain):

1. Extract this kit preserving the directory tree.
2. On Windows: `powershell -ExecutionPolicy Bypass -File .\BOOTSTRAP_WINDOWS.ps1`.
3. On Linux/macOS: `bash BOOTSTRAP_LINUX_MAC.sh`.
4. The generated project is `generated_app`. It refuses to overwrite an existing destination.
5. Debug APK path once build passes: `generated_app/build/app/outputs/flutter-apk/app-debug.apk`.
6. Install via `flutter install` in generated app or `adb install -r ...`.
7. Open Adaptive Landscape; select Meadow/Beach; tap approximate occupied cells; tap **Set live wallpaper**; choose Home.

`flutter create` supplies the Flutter/Gradle/SDK wrapper and device integration files appropriate to that installed toolchain. This source pack deliberately avoids committing guessed Gradle wrapper binaries or a fake signed APK. For repository CI, add a development workflow to the dedicated app repository after creation and lock dependency versions.

## Limitations / non-claims
- Does NOT discover the real Android launcher icon positions. Manually calibrated geometry is approximate, especially on third-party launchers, tablets, landscape orientation, widgets and multiple Home pages.
- Android wallpaper service cannot move real icons; it only changes the picture behind them.
- Native graphics are an original *two-scene MVP*, not a pixel-perfect port of Deskland's Beach and Meadow (different rendering stack/assets).
- Preview may differ from real native renderer in details. Physical device tests required.
- No production store signing, internal Play test, generated artifact, physical QA or donor E3 certification.

## Privacy / permissions
- No AccessibilityService, MediaProjection, READ_* permissions, overlay or Internet permission.
- Only `BIND_WALLPAPER` on a service, used by Android's wallpaper framework.
- The local icon occupancy grid stores **positions only**, not app names or real screenshots.

## Licensing
- Code in this kit is a fresh implementation. No open-source license is automatically granted by this draft; choose a license/publication policy explicitly when creating the dedicated repository.
- Upstream Deskland is GPL-3.0. Neither the Deskland code nor its artwork is incorporated.
- Do not use 'Deskland' as commercial name/branding.

## Factory baseline
- `riccardopinato/App-Factory-Core@d4ed1185d36d419228fba6b57a979cde308bd321`
- `CURRENT.yaml` master v27, Golden Index v29, registry v2.
- Reused **procedures/contracts**, not third-party code: App Bootstrap (draft), Golden Localization (copy-ready), Theme M3 (copy-ready), Android Native Reliability (draft), CI/Release (copy-ready), OSS Reuse (draft), Single Donor Admission (copy-ready), Provenance Ledger (draft), Release Reality (draft).
- See `docs/REUSE_PROVENANCE.md`, `docs/ROADMAP.md`.

## Repository CI and testing links

- [GitHub Actions — Android debug APK artifact](https://github.com/riccardopinato/Adaptive-Landscape-Android/actions/workflows/development.yml): select a successful run, then the `adaptive-landscape-android-debug` artifact.
- [Web preview — expected GitHub Pages location](https://riccardopinato.github.io/Adaptive-Landscape-Android/): available **only after the Pages job succeeds**. The Web preview shows controls and animation but CANNOT set an Android wallpaper.
- `main` includes a reproducible bootstrap; `generated_app/` is intentionally ignored and created by CI.

Read [test instructions](docs/RELEASE_TESTING.md) before assigning any device/Play certification.

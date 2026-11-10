# Adaptive Landscape Android

Original **Flutter + native Android Live Wallpaper** project. This is a reproducible **source overlay**: GitHub Actions bootstraps a full Flutter/Gradle project, runs Dart tests and Android build, and publishes a Flutter Web editor preview.

- Web demo: https://riccardopinato.github.io/Adaptive-Landscape-Android/
- APK build artifacts: https://github.com/riccardopinato/Adaptive-Landscape-Android/actions/workflows/development.yml
- Roadmap and evidence: [docs/ROADMAP.md](docs/ROADMAP.md)
- Android release safety: [docs/RELEASE_PLAY_CHECKLIST.md](docs/RELEASE_PLAY_CHECKLIST.md)
- Physical QA matrix: [docs/QA_MATRIX.md](docs/QA_MATRIX.md)
- Privacy (IT): [docs/PRIVACY_IT.md](docs/PRIVACY_IT.md)
- Normalized scene contract: [docs/SCENE_CONTRACT.md](docs/SCENE_CONTRACT.md)

## Available features (source implementation, not yet device-certified)

- Meadow vector scene and **Beach v0.3 overhead procedural sand/water coastline**, no sky, sun, clouds, horizon or decorative Beach objects. Manual night variants and Original/Sunset/Mint palettes.
- Flutter responsive live preview, motion control, reduced motion, Android wallpaper picker.
- Variable Home icon grid (3–8 columns, 3–12 rows), tap-to-mark selected cells.
- Drag/move/resize local widget exclusion zones. Occupancy influences cloud/hill/wave position and suppresses small objects.
- Multiple on-device/browser profiles, import/export single profile as JSON (clipboard + paste).
- Schema-2 settings bridge from Flutter to Kotlin; Android `WallpaperService` reads the same normalized geometry.
- Local-only persistence with `shared_preferences` plus Android preferences; 6 locales (EN/IT/ES/FR/DE/PT).
- Android 20fps max while visible, 8fps in power saver, no frame loop for reduced-motion/static scene, no animation while invisible.

**Important limitations:** A generic Android wallpaper cannot read positions of launcher icons or move real icons. The user calibrates manually. Both engines are independent implementations of the same contract; device-specific fidelity is not certified. Web preview is not an Android live wallpaper.

## Source build

Requires Flutter **3.41.4**, Android SDK, and **JDK 17**. Run `bash BOOTSTRAP_LINUX_MAC.sh generated_app` (Linux/macOS) or `powershell -ExecutionPolicy Bypass -File .\BOOTSTRAP_WINDOWS.ps1` on Windows. Both refuse to overwrite a prior generated project.

The bootstrap produces `generated_app/build/app/outputs/flutter-apk/app-debug.apk`. GitHub Actions also publishes internal optimized per-ABI release-mode APK artifacts: **these are not Store-signed** and must never be represented as ready for Google Play. CI Web deploy is on successful `main`.

No accounts, external API, analytics, advertising, camera, launcher-scraping, screenshot capture or app-name collection.

## Intellectual property and provenance

Deskland `BLEZZ0102/Deskland@9818b577402ff476e1b59da07970c56472713126` (GPL-3.0) is a **concept/UX reference only**. Its code, art, assets and logo were not imported. This repository's original code and branding must be reviewed for a separate commercialization license policy before sale. No Golden/Donor certification is implied by a passing consumer app CI.

Factory baseline: `riccardopinato/App-Factory-Core@d4ed1185d36d419228fba6b57a979cde308bd321` (Master v27, Golden Index v29, registry v2).

## Evidence principles

CI compilation and Web confirmation do not establish OEM battery, launcher layout, crash-free wallpaper lifecycle, install/update behavior or Play readiness. The Android physical test is deliberately deferred until the user resumes it. Track every physical result by exact APK SHA and device.

## Beach visual correction — v0.3.0

The user confirmed the Web v0.2 base but specified that Beach should be an aerial shoreline with sand forming around icon/zone occupancy and animated translucent blue/turquoise water. The previous sky/nuvole/sole composition was incorrect for Beach. Source-specific technical and license notes: [docs/BEACH_VISUAL_REFERENCE.md](docs/BEACH_VISUAL_REFERENCE.md).

The original procedural shoreline model is independently authored in Dart and Kotlin. It uses compact mesh shading on Web and cached low-resolution raster shading in Android. Neither implementation copies Deskland shaders or licensed artworks. The **new Web visual needs user acceptance**; Android runtime/frame rate and visual parity remain untested.

## Beach v0.3.2 — sea-only when empty, high-density Web GPU renderer

User-approved *direction*, not user-certified pixels: Beach contains **only animated sea** until the user marks app icons, folders, widgets or bottom-dock icons. Each occupied region grows a soft island of sand; adjacent regions join. The **3/4 fixed dock icons are set manually** in the editor; no wallpaper can universally inspect launcher icon positions. With all markers cleared (including dock), the shore threshold is never crossed anywhere.

For Web/high-density displays, `shaders/beach_ocean.frag` computes every output pixel using CanvasKit's GPU fragment shader. A cached 512×1024 **occupancy-only mask** is sampled smoothly; it is **not** a stretched 512px water bitmap. A shader-capable 4K framebuffer therefore gets 4K-shaded water without visible mesh triangles. Actual output resolution depends on window size/DPR and the browser GPU. Unsupported shader backends fall back to the former tessellated mesh (reduced quality), which is flagged as fallback. The Android Kotlin raster currently remains lower resolution to limit battery consumption and needs physical performance QA before raising its quality. No claim of 4K physical Android wallpaper.

Controls: guide overlay toggle for unoccluded ocean preview; 0/3/4 dock icons; all themes preserved. Unit tests cover empty ocean, beach around occupancy, dock presets, and texture vs CPU influence. More info: [docs/BEACH_VISUAL_REFERENCE.md](docs/BEACH_VISUAL_REFERENCE.md).

## Beach v0.3.3: correction after failed visual acceptance

The user reported two failures in v0.3.2: the beach still appeared when they expected only sea, and the water still looked artificial. **The first is a reproducible onboarding issue**: new editor/store defaults remained Meadow and editing overlays were visible, while existing browser profiles persisted. Fresh profiles now start with Beach and hidden guides, preserving existing profiles. The explicit **Ocean only · clear layout** action clears icons, folders, widgets and dock in the **current** profile (other saved profiles are preserved), with a visible empty/occupied status.

The Web editor now exposes the **actual renderer state**: GPU active, loading or fallback. When a browser cannot use Flutter's fragment shader, the Canvas fallback is announced rather than silently described as 4K. A large panoramic preview allows inspecting the water across the screen; icon positions are still calibrated on the phone-aspect editor. The ocean shader now uses smoother multiscale reflected-light patterns, more restrained turquoise gradients, finer foam and damp-sand transitions, with no static beach in an empty layout. As before, generated shader shading at display resolution does **not** guarantee photographic realism or a 4K physical device output.

**Verification required:** CI build/tests are source-level evidence; runtime GPU availability and water quality require review in the user's browser. The real Android Kotlin renderer is a separate, lower-resolution implementation with deferred device tests. The Deskland GPL project and its screenshot remain visual references only; no code or image pixels copied.

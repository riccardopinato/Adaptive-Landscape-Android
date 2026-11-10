# Android native wallpaper incident — v0.4.0 FAIL; v0.4.1 remediation

## Evidence

User installed the provided v0.4 ARM64 APK on a physical Android smartphone and described:
- Sea visibly pixelated/washed out rather than 4K, with a strong unnatural contrast outline at the sand edge.
- Animation nearly imperceptible and jagged.
- Sand only appears at 3 manually configured fixed dock apps. Other Home apps, folders and widgets do not create sand automatically.

Do **not** reinterpret the report as a successful Android real-device QA. Exact device/Android/launcher/APK SHA were not captured.

## Confirmed source causes

- Native source `CoastalBeachRenderer.ensureGeometry` scales to `360 / max(width,height)` and calls `drawBitmap(surface,...fullScreenRect)`. This is genuinely **upscaled 360px total dimension**, not pixel-resolution native drawing, and independent of the Web Flutter GPU shader in `shaders/beach_ocean.frag`.
- Android `WallpaperService.Engine` does not receive launcher icon/widget bounds; its saved `config_v2` contains **only user-marked grid cells and zones**. The optional bottom dock preset creates those 3 sand patches, hence the reported behavior. This is not a missed bug in the influence function: the data is absent.

## Repair included in proposed PR

1. `GpuOceanRenderer.kt`: true native EGL14/GLES20 fragment renderer on the wallpaper surface, with per-frame moving ocean caustics, color/bathymetry and feathered shore; no rendering a 360px water bitmap first. OpenGL context/surface torn down on lifecycle change; 20fps visible/8fps battery saver scheduling retained; Canvas fallback if EGL fails.
2. Native config parses existing optional `renderQuality` (Balanced/High/Ultra), preserves schema2. Exposes `render_backend_v1` for troubleshooting (`gpu_native` vs `cpu_360px_fallback`).
3. Screenshot-aided manual calibration: the **user** makes a screenshot of their Home, voluntarily picks it via system `ACTION_OPEN_DOCUMENT`; Flutter displays it over the editor grid at temporary 72% opacity. User taps icon/folder cells or inserts widget zones, explicitly confirms in-app. Screenshot bytes remain memory-only and never enter prefs, JSON profiles, files, telemetry or network.
4. No AccessibilityService, media-projection, system overlay or access to installed apps. Privacy and Play permissions unchanged.

## What is NOT solved

- Native **photographic 4K** is NOT delivered by a resolution-aware shader alone; a sufficiently detailed original or separately licensed texture would still need integration with authored motion and physical tests.
- Automatic live icon/widget tracking is NOT delivered. Android `WallpaperService` does not expose it. Accessibility can sometimes inspect launcher view hierarchies but varies by OEM, is sensitive, needs explicit user permission, Play disclosure and separate approval. Do not conflate this manual-screenshot solution with tracking.
- Calibration geometry may not match actual wallpaper cropping, status/navigation bars and launcher pages. Requires on-device adjustment/QA.
- EGL2 feature, OEM lifecycle, actual frame pacing, thermal/battery need real testing. A passing GitHub build only proves compilation.

## Exit gate

Build a **separate test APK from the PR**, send it without automatic merge, record user result and device/launcher if available. If user reports `cpu_360px_fallback`, investigate GPU driver/errors first. Only merge after physical behavior acceptance; commercial/Play gate remains open.

## Android policy basis

- `WallpaperService.Engine` Android API reference: https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine
- `AccessibilityService` active-window UI hierarchy: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Google Play Accessiblity API consent: https://support.google.com/googleplay/android-developer/answer/10964491

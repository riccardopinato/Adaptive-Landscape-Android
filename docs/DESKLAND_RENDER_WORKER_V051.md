# Deskland Android v0.5.1 — render-worker and real coastline performance

Factory baseline: `riccardopinato/App-Factory-Core@c5adc147046beb7024629466d10eeb799c3c2619`.
Source baseline: Draft PR #29 `02496ce8541af8dd327e2d898931ea6fd83b6f58`.
Implementation: opt-in Deskland Home only; no access to external launcher coordinates, no new permissions, and no public release.

## Verified design constraints / current research

Official Android `WallpaperService.Engine` callbacks including visibility and surface lifecycle are delivered to the app's main thread:
https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine

Android's rendering pattern recommends a **dedicated thread** for EGL/Canvas drawing so touch and launcher UI are not blocked:
https://developer.android.com/reference/android/opengl/GLSurfaceView
(Reference for threading principles; this is a direct EGL engine, not a GLSurfaceView.)

The actual renderer is `android/app/src/main/kotlin/it/riccardopinato/adaptivelandscape/GpuOceanRenderer.kt`.
The Android 35 Home emulator suite does not yet apply the live wallpaper or prove sand pixels.

## Implemented source changes

1. Each WallpaperService Engine owns a `HandlerThread` and `renderHandler`.
   All calls to `GpuOceanRenderer.render/resetSurface/dispose` and Canvas
   drawing move from Engine callbacks to the worker's serial message queue.
2. An Engine's visibility, surface readiness and destruction are respected
   before scheduling/continuing work. Hiding the wallpaper removes pending
   frames; destroying it queues GLES cleanup on the same render thread and
   requests `quitSafely()`.
3. SharedPreferences and role/source changes invalidate renderer caches
   through the worker queue. No worker draws directly from the Android UI
   callback. Power saver and Balanced/High/Ultra FPS targets are unchanged.
4. GLES2 allocations for previous/current 160×320 RGBA occupancy textures
   occur once per EGL context. Subsequent coastline changes use
   `glTexSubImage2D` and **one reusable direct upload buffer** instead of
   `glTexImage2D` allocations and a fresh direct buffer per change.
5. Existing bounded obstacle-mask stamping is preserved (already implemented
   in previous PRs; do not misattribute it as new in v0.5.1). The temporal
   coast morph still starts from the previously displayed blended mask.
6. Flutter package version is bumped to `0.5.1+16` to distinguish the
   exact build from the prior `0.4.7+14` experimental candidate.
   Signing compatibility across previous internal APKs is still UNKNOWN.

## Architecture invariants

- Never import or copy the original Windows GPL-3.0 Deskland assets/code.
- Home role and live wallpaper remain separate **user consent** flows.
- Never overwrite manually saved `config_v2` layouts or photo files.
- No background render loop when the wallpaper is hidden.
- No app-label/screenshot telemetry or network access.
- Multiple wallpaper engines/previews each own independent renderer thread
  and GLES resources; no shared static GPU state.
- A worker-thread graphics exception must not be mistaken for a verified
  physical device FAIL/PASS without log evidence.

## Exact-head validation gates

| Gate | Expected evidence |
|---|---|
| Static source/privacy/ARB validator | `python3 scripts/validate_repository.py` PASS |
| Flutter source/Android compilation | `bash BOOTSTRAP_LINUX_MAC.sh generated_app` PASS |
| ARM64 release APK, signature & SHA | CI artifact `Deskland-M1-ARM64-optimized-APK-NOT-PLAY-SIGNED` |
| Android 35 app/launcher smoke | Home, drag, folders/dock, package lifecycle, role rollback PASS |
| Actual wallpaper EGL + sand pixels | **NOT COVERED** by Home smoke; requires explicit wallpaper activation and device visual test |
| UI thread improvement | Frame pacing/jank before/after (Perfetto / Android FrameTimingMetric where applicable); **NOT MEASURED** |
| Realism | Original photo optional, believable coast/water motion; **NOT USER APPROVED** |
| Redmi/Samsung/Pixel physical QA | **NOT RUN**; ensure both launcher+wallpaper chosen with explicit system prompts |
| Install/update in place | **NOT RUN**; internal signing may not match old installs |

## Why photorealism is still not certified

The existing procedural GLSL ocean has full-resolution shading, but that
does not make it a true detailed ocean photo. The photo-first path is
implemented and can load a **user-selected legitimate overhead sea image**
offline. A new preinstalled photo needs independent provenance, license,
asset-size and actual visual QA; a low-resolution upscaled/AI-looking texture
must not be represented as true 4K photographic detail.

Do not mark this feature production-ready or merge upstream Draft PRs until
actual device tests prove correct geometry, authentic visual quality, no
launcher regressions and battery/thermal behavior.

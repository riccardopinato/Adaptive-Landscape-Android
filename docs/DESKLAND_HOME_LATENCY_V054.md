# Deskland v0.5.4 — Home interaction latency (Android)

Factory baseline: `riccardopinato/App-Factory-Core@c5adc147046beb7024629466d10eeb799c3c2619`, CURRENT v27, Golden Index v29.
Parent screenshot-layout import: PR #35 (`de98d642ffb6e9aef408584740e535db60cfb9c7`).

## User-facing problem

Physical feedback: the Deskland launcher appears to slow down the Android Home. This is user-observed, NOT yet an instrumented Xiaomi FPS/latency measurement. Do not claim exact root cause or numeric improvement absent identical-artifact device measurements.

## Source-level bottlenecks and bounded repairs

1. `AdaptiveHomeActivity.renderHome()` removed **all** views (including AppWidgetHost views) whenever the Home page needed updating. It now reconciles visible app/folder/dock tiles and live widget hosts, reusing existing views. Unchanged layout signatures skip repeated Home rebuilds entirely. Changes to an icon package invalidate cached artwork/signatures.
2. `renderHomeNextFrame()` coalesces duplicate frame callbacks. Touch drag and interrupted gestures force final snap-to-cell cleanup. The wallpaper state is published less frequently during a drag (180ms vs 90ms), with a final Home update.
3. Installed app icons are cached as independent Drawable.ConstantState instances; no network, new permission, cloud SDK or image recording.
4. The wallpaper render thread no longer has display-level CPU priority above the main Android UI thread. Frame requests are coalesced and constrained by the last frame start time; repeated offset callbacks do not force unrelated Beach renders.
5. During an active Home drag, the wallpaper is capped around 8fps while leaving touch/UI handling responsive. A bounded monotonic deadline prevents stale performance mode after a process crash; normal scene quality is restored after the gesture.

The effects on actual FPS, launch latency, thermal throttling and battery usage are **NOT VERIFIED** until physical comparison. Background sea animation may appear less fluid specifically *during* icon drags by design; UI responsiveness takes precedence.

## Automated gates

- Flutter analyze/test and Kotlin/Gradle Android build at exact new PR head.
- Android API 35 emulator: app launch, default Home role, installed app icons, page swipes, drawer, long press, folder drag, local screenshot matcher, curated import/undo, process restart, Home role restoration.
- New debug-only `deskland_qa_home_perf_v1` verifies: repeated identical Home redraw calls do not rebuild visible layout; dock view remains the same object across page changes where dock is populated.
- Static validator asserts no entire Home view-tree removal, widget host reuse, coalesced frame scheduling and drag GPU budget.
- All test-only state remains inside disposable emulator; no screenshots/photos exported.

## Required physical Redmi/Xiaomi acceptance

Use the exact same artifact bytes/hash across all retests. Before upgrading, ensure the existing layout can be recovered and verify signing continuity; do not uninstall a populated launcher to force a test upgrade.

Record device model, Android/HyperOS version, package versionCode, signature SHA-256, APK SHA-256, and these 5 cases:

1. **Baseline**: 15 Home-return gestures, 15 horizontal page changes, and 10 drawer opens. Record FPS/jank or frame-time percentiles from Android `dumpsys gfxinfo`/Perfetto, and perceived responsiveness.
2. **Drag**: 10 long-press/icon moves, verify no accidental launch, icon follows finger, sand catches up smoothly after release. Compare jank and UI touch delay against previous exact APK.
3. **Widgets**: add/reposition/rescale real widgets and verify widgets keep content without refresh/reset on unrelated Home updates.
4. **Lifecycle**: Home->app->Home, screen off/on, process kill/restart, system launcher switch and back, offline, battery saver.
5. **Stability**: 10+ minutes of Home interaction, no ANR/crashes, acceptable thermal/battery impact, screenshot import/undo intact.

A PASS on CI/emulator never becomes a Xiaomi physical PASS. PR remains Draft until hardware/UX and artifact/update gates pass.

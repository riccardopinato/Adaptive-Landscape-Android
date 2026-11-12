# Deskland Android v0.5.5 — Home fluidity, local diagnostics, adaptive rendering

Factory baseline: `riccardopinato/App-Factory-Core@c5adc147046beb7024629466d10eeb799c3c2619` (CURRENT v27, Golden Index v29).
Parent: Draft PR #36 (`040d519d76dbca3bd1e766b5a15575db0c801355`), v0.5.4+19.
This work is intentionally stacked, not merged to main.

## Problem and evidence boundary

User feedback: Deskland feels like it slows the entire Home. This is a real usability issue but **not** an instrumented FPS/ANR diagnosis. The previous version passed APK compilation and emulator functional smoke tests, which do not measure Snapdragon/MediaTek GPU load, Xiaomi/HyperOS scheduler behavior, or perceived responsiveness on physical hardware.

## Implemented in source — v0.5.5+20

1. **Home warm-return:** installed-app enumeration through LauncherApps no longer runs unconditionally on every Activity.onResume. The catalog is loaded on first Home resume or if the package-change callback was unavailable. Registered LauncherApps callbacks refresh app packages when changed. No hidden polling or permissions.
2. **Touch thread:** normalized icon/widget zones are still posted from real Views; conversion to JSON + preferences mutation is deferred off the MotionEvent handler path. The unchanged manual editor profile JSON is cached in the Activity and invalidated if `config_v2` changes.
3. **First content timing:** time to first populated Home frame is written locally as `auto_home_first_populated_frame_ms_v1` (source lifecycle estimate, not an OS-certified startup benchmark).
4. **Home UI frame pacing:** `HomeFrameMonitor` samples Choreographer frame intervals only while the Home is resumed; records a small last-512-sample ring buffer, counts intervals above 1.5x and 2.5x the expected device refresh interval, writes one aggregate `auto_home_perf_last_session_v1` on pause. Includes current refresh Hz, median/p95 interval in milliseconds, sample counts, warm-resume/catalog work duration, no-op rebuild counters. Does NOT collect app names, screenshot content, device identifiers, behavioral histories or network telemetry.
5. **Adaptive GPU relief:** sustained main-thread frame interval degradation triggers a 5-second, local, expiry-limited `auto_home_jank_budget_until_v1` marker, rate-limited to one write every 3 seconds. Only when Deskland owns the default Home does the wallpaper temporarily cap itself around 10fps. The existing interaction/battery-save limit remains ~8fps. User-selected rendering tier is never overwritten; normal frame pacing resumes automatically.
6. **GPU mask cache:** unchanged WallpaperState on Home reactivation no longer forces costly mask invalidation. Only geometry-bearing state changes regenerate occupancy textures; palette/night/motion/foam etc. update GL uniforms without recomputing land masks. No new third-party dependency.
7. **Optional ADB bench:** `scripts/deskland_home_perf_capture.ps1` resets Android gfxinfo counters before a **human-performed** repeatable set of interactions; saves anonymized local frame-duration aggregate plus raw app-targeted gfxinfo to ignored `physical_qa_local/`. It does not connect to a phone on behalf of the assistant, install anything, alter the default Home or upload files.

## Automated gates

- GitHub Actions exact-head validation of six locales, Flutter analyze/test, Android Gradle/debug and optimized ARM64 release compilation/signature/ABI check.
- Disposable Android API 35 emulator: native Home role, page/drawer/drag/folder gestures, screenshot import + undo, layout survival, process restart, and new `deskland_qa_frame_monitor_v1` deterministic timing-policy check.
- This emulator contract tests wiring and availability, not actual performance improvement.
- Emulator evidence must show one exact source SHA and all functional tests PASS; otherwise fix in the Draft PR.

## Physical acceptance criteria (unverified until collected)

On the **same Xiaomi/Redmi**, compare v0.5.4 and v0.5.5 with the same refresh Hz, identical wallpaper/profile/photo/quality, battery saver setting, screen brightness and launcher layouts.

- Record APK SHA-256 and signing certificate for each version. Never uninstall a populated launcher or bypass signing mismatch to force an update.
- 3 comparable sessions per version, each with 15 Home returns, 15 page swipes, 10 app drawer opens and 10 icon drags. Sample UI `dumpsys gfxinfo ... framestats`; capture p50/p95 frame duration and slow-frame ratio with the included PowerShell script. Evaluate median across sessions, not one short sample.
- **Go target, not a measured claim:** p95 frame duration and slow-frame proportion must not regress, target >=20% reduction in slow-frame ratio when baseline has measurable jank. No Android ANR/crash or loss of installed app shortcuts/widgets/dock/folder state.
- Separate SurfaceFlinger/Perfetto GPU trace and wallpaper FPS/battery/thermal observations. Choreographer + gfxinfo Home UI data alone cannot prove sea rendering FPS, perceived latency, dropped GPU frames or battery drain.
- Verify recovery after 30-minute Home activity, battery saver, screen-off/on, system launcher switch, process kill/restart, screenshot import/undo, real widget refresh and cold/warm resume.
- If visual sea becomes choppy or delayed too long during interaction, adjust adaptive thresholds based on physical measurements before user acceptance.

## Windows measurement (only after verifying install/signing safety)

```powershell
.\scripts\deskland_home_perf_capture.ps1 -Label baseline -RefreshHz 120 -ApkPath ".\old.apk"
.\scripts\deskland_home_perf_capture.ps1 -Label candidate -RefreshHz 120 -ApkPath ".\new.apk"
```

Replace 120 with the actual device refresh rate and the paths with genuine exact APK files. ADB must be installed and authorized by the user. Output stays in `physical_qa_local/` and must not be committed. Run 3 sessions for each version; filename includes date/time.

## Release gate

This candidate remains **CODED** until exact-source CI PASS. Even after CI PASS, physical responsiveness/visual/battery, preserved data on upgrade, stable signing, main branch dependency chain and review approval remain open. No merge, Play claim or hardware certification based on source and emulator tests.

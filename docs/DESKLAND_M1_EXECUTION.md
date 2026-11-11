# Deskland Android — Maxi-step 1 runtime handoff

Factory baseline: ee72dbb1b0de2cd71bc4aac42c350853c6915159 (Master v27 / Index v29).
Frozen candidate: Draft PR #26 at 6f5d1fb60b6b8fb2e35c3c396f11aed198cb08fd.
Source CI: https://github.com/riccardopinato/Adaptive-Landscape-Android/actions/runs/42947060839 (PASS).
Existing debug APK artifact: adaptive-landscape-android-debug, ID 13822384905. Signing is internal/debug only; no Play certification.

## Emulator gate (not physical QA)
The workflow builds a fresh debug APK from the QA branch using pinned Flutter and source, then, records SHA-256, installs it on a disposable Android 35 emulator, temporarily assigns the Home role using shell test privileges, observes actual Home UI and source-app preferences, checks empty page, add/remove/move app icons, process restart, then restores the original Home role. Evidence contains only named checks and hashes. It refuses to run on real devices.
It is a non-production test. It does not bypass actual user consent to change wallpaper, modify user phone settings, capture photos, or copy GPL assets.

## Physical follow-up (NOT RUN)
1. On a spare phone, record package, exact APK SHA-256, signing certificate, OS/API and renderer backend. Export existing profiles first; debug-signing differences can prevent update-in-place.
2. Open editor, set Beach, clear manual icon occupancy. Explicitly select Deskland live wallpaper in Android dialog and separately select Deskland Home with the system role picker.
3. Verify empty Home is only ocean; place one app and observe sand; long-press move and check continuous coastline; remove app and check sea returns. Repeat at motion=0.
4. Check page changes, app launch/back, widgets, force-stop/restart/reboot and restoration of original OEM launcher without losing its layout.
5. Inspect genuine native photo texture at 0/3/6/9/12 seconds and battery/thermal. User must approve visual quality.
6. Record PASS/FAIL/NOT RUN per scenario with exact APK and device evidence. Main merge/Play remain NO-GO until all relevant gates pass.

| Gate | Initial status |
|---|---|
| Flutter/Android source CI for PR #26 | PASS |
| Android emulator runtime | NOT RUN until workflow concludes |
| Human live wallpaper & launcher consent | NOT RUN |
| GPU visual realism, motion=0, OEM, battery | NOT RUN |
| Production merge / signed release | BLOCKED |

This experimental PR adds a bounded native Home layout-update correction and QA harness; it does not change Flutter, Android wallpaper renderer, config schema, or main.

## Observed runtime defect and bounded correction
Earlier emulator runs installed original PR #26 APK. They passed Home role selection, 6 visible icon zones, and empty page with zero zones; when returning to page 1 the UI and stored page indicated 1/3 but the published coastline count did not recover. Cause in source: `renderHome` publishes before new child tiles have measured bounds, and existing global layout callback reacts only to canvas size changes. This PR uses child `addOnLayoutChangeListener` on icons and widgets so final bounds cause a debounced republish. The fix MUST be verified against a freshly compiled APK from this branch. CI green does not certify physical reality.

## M1 incremental QA — stationary long-press guard and reproducible artifact
- A source-level defect was found in `AdaptiveHomeActivity.attachTouch`: `ACTION_UP` could call `performClick()` after an armed long press without movement. This must not start an application, so the app-launch condition is now `!armed && !cancelledByGesture`.
- Disposable emulator test now verifies a stationary 900ms long-press does not navigate away before the one-process `input touchscreen draganddrop` trial. This distinguishes a click leak from a true drag failure.
- The workflow now checks out the exact PR head commit rather than the synthetic merge commit and uploads its matching debug APK (`deskland-m1-qa-debug-NOT-PLAY-SIGNED`) for human testing, with SHA-256 in job logs and JSON. APK artifact is unverified until CI actually completes.
- If CI fails, read the `SAFE_DIAG` role holder, foreground activity, process-alive and zone-count fields. Never label a failed drag as PASS.

## Diagnostics: drag may terminate Home process
Run 43013339785 passed stationary long press but failed the following drag: role still held by Deskland while `pidof` returned empty and the foreground switched to Pixel Launcher. The QA harness must now collect AndroidRuntime/DEBUG/ActivityManager crash and process-exit evidence from the **disposable emulator only**; do not infer root cause without a trace.
For this test-only change, the CI reuses artifact `deskland-m1-qa-debug-NOT-PLAY-SIGNED` produced by 43013339785 (APK SHA256 `598230629bbdd3638ee9dc017f1a0416f4630f50827f4136cc043ae3b95fe074`) and rejects any source-native blob mismatch. Full rebuild is mandatory again after the next product/native change. This is diagnostic reuse, not a release certification.

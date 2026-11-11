# Deskland Android — Maxi-step 1 runtime handoff

Factory baseline: ee72dbb1b0de2cd71bc4aac42c350853c6915159 (Master v27 / Index v29).
Frozen candidate: Draft PR #26 at 6f5d1fb60b6b8fb2e35c3c396f11aed198cb08fd.
Source CI: https://github.com/riccardopinato/Adaptive-Landscape-Android/actions/runs/42947060839 (PASS).
Existing debug APK artifact: adaptive-landscape-android-debug, ID 13822384905. Signing is internal/debug only; no Play certification.

## Emulator gate (not physical QA)
The workflow downloads the existing APK without recompilation, records SHA-256, installs it on a disposable Android 35 emulator, temporarily assigns the Home role using shell test privileges, observes actual Home UI and source-app preferences, checks empty page, add/remove/move app icons, process restart, then restores the original Home role. Evidence contains only named checks and hashes. It refuses to run on real devices.
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

This QA-only PR targets the experimental widget branch; it does not change Flutter, Android renderer, product schema or main.

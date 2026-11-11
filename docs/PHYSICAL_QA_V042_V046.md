# Physical QA protocol — v0.4.2 → v0.4.6 (PENDING; zero device passes)

## Purpose and release boundary

This protocol accepts source/test readiness; it **does not** certify a visual
result from Flutter Web, GitHub Actions or the AI assistant. The latest
stacked QA branch depends, in order, on PR #13 → #16 → #17 → #18 → #19 → #20
→ the prephysical QA PR. None is automatically mergeable or authorized for
Google Play merely because a build succeeds.

**Never claim physical PASS before the user installs the exact APK and
documents their observation.** Do not merge the stacked PRs into main until
the visual result is explicitly accepted and the preceding PR gates pass.

## Preflight: artifact and signature safety

1. From the newest PR's exact-head successful GitHub Actions run download:
   `adaptive-landscape-android-debug`, or
   `adaptive-landscape-internal-optimized-NOT-PLAY-SIGNED` and its
   architecture-compatible APK.
2. Record PR number, branch, **head SHA**, Actions run ID and CI source merge
   SHA (if displayed), package and `versionCode` (`0.4.6+13` for this QA
   series), APK SHA256 and signing certificate SHA256. The editor's voluntary
   QA snapshot shows the *build* SHA if embedded; on PR jobs it can be the
   synthetic merge SHA, not necessarily the branch HEAD.
3. **Do not uninstall an existing app without first exporting your profile**
   if you value its data. CI-generated internal/debug APKs may be signed with
   ephemeral debug keys, so an upgrade-in-place can fail with
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; the higher versionCode alone does
   NOT fix a signing mismatch. Never force an uninstall without user decision.
4. Keep one chosen source image locally, visually confirm it is actual
   overhead open water (no sky, horizon, permanent sand or distracting foam).
   The binary must not be derived from the GPL Deskland repository.
5. Record device manufacturer/model, Android version, launcher name/version
   **only if voluntarily supplied**, battery saver status, date and time.
   Never collect app names, sensitive device identifiers or unrequested logs.

## Optional local evidence collection

**Android USB debugging / adb must be authorized by the user.** A script runs
locally on the user's own computer and does not upload content:

- Windows PowerShell:
  `powershell -ExecutionPolicy Bypass -File .\scripts\physical_qa_capture.ps1 -ApkPath ".\app-arm64-v8a-release.apk"`
- Linux/macOS:
  `bash scripts/physical_qa_capture.sh ./app-arm64-v8a-release.apk`

Both record the APK SHA256, OS/model and battery state, and app-scoped memory
info in `physical_qa_local/`. No screenshots, logcat or device serial are
captured. Inspect outputs before manually sharing.

**The physical test itself is NOT automated** and cannot be performed by a
GitHub CI runner without a real device and human review.

## Mandatory runbook

### 1. Basic runtime + privacy

- Install the exact QA APK; record any signature/update error.
- Launch editor and choose Beach, set high/ultra, disable manual icon markings.
- With no icon, zone or dock occupancy, actual Home wallpaper must show
  **entirely sea, no generated sand**.
- Import an authentic real-water image in Android, activate wallpaper,
  reopen editor, confirm `gpu_photo_native`. If it reports
  `gpu_native` or `cpu_360px_fallback`, visual acceptance is blocked.
- Confirm no Internet, media library, accessibility or overlay permission
  request; image remains local, optional, removable.
- Test invalid/corrupted/oversize/undersize photo; previous valid photo must
  survive failed import. Test screenshot calibration independently, and
  confirm its selected image is never persisted.

### 2. Visual quality (primary blocking gate)

- Compare native 0, 3, 6, 9 and 12 second frames via live observation (do
  not assume identical loop times when user changes motion speed).
- Ocean must look **photographed from overhead**, remain recognizable as
  genuine texture; avoid high-contrast fake caustic bands or uniform bright
  outline. Motion must be restrained, fluid and continuous.
- Start refraction 0.55, foam 0.45; adjust slowly. Compare Balanced/High/Ultra
  and Refraction 0/0.5/1, Foam 0/0.5/1.
- User is sole final judge of aesthetic acceptance. Mark `FAIL` if the
  water still resembles abstract noise, **even if CI and GPU status PASS**.

### 3. Home geometry

- Open temporary screenshot calibration and zoom 1/3/5×.
- Manually mark 3–5 real Home icon positions on the grid. Set top offset,
  vertical span and side inset to match actual launcher cells.
- Add a rectangular widget and the real dock count; verify sand appears
  only beside marked cells, including near screen edges and bottom dock.
- Test portrait/tall phone, gesture navigation bar/status bar and
  scrolling between Home pages. No automatic launcher icon detection.
- Switch profile, close, reopen and reboot: profile shapes/margins persist.

### 4. Memory, animation and battery

- Run at least 30 minutes on a real device per quality tier if feasible.
- Record observed / traced FPS, frame-time variability, app memory, total
  battery % delta, device temperature and ambient/battery-saver state.
  Do **not** treat target delays (83/63/50ms; power save >=125ms)
  as measured FPS. Thermal/battery values vary by device and background load.
- Open another app / lock screen for five minutes; verify wallpaper does not
  continue animating while invisible; verify it resumes cleanly.
- Replace photos, switch quality presets repeatedly, rotate and lock/unlock;
  verify no ANR, EGL crash, memory runaway, stale texture or blank wallpaper.

### 5. Usability and compatibility

- IT/EN/ES/FR/DE/PT language switches, large font, TalkBack, a small phone,
  tall phone and tablet where available.
- At minimum one Redmi/Xiaomi or user-owned Android first; additional
  Samsung/Pixel OEM testing is required before broad compatibility claim.
- Test offline/airplane mode, low-battery mode and fresh install.
- Test upgrade-in-place **only using the same signing certificate**.

## Evidence log to complete after each physical attempt

| Field | Value |
|---|---|
| Test date / tester | PENDING |
| PR / exact head SHA | PENDING |
| Actions run ID / build SHA | PENDING |
| APK SHA256 / signing certificate | PENDING |
| Device model / OS / launcher | PENDING |
| Photo provenance / photo aspect ratio | PENDING |
| Renderer backend | PENDING |
| Visual realism PASS/FAIL | PENDING |
| Animation loop PASS/FAIL | PENDING |
| Home grid/dock alignment PASS/FAIL | PENDING |
| Battery / FPS / thermal / memory | PENDING |
| A11Y / locale / privacy checks | PENDING |
| Upgrade / signature compatibility | PENDING |
| Final user aesthetic approval | NOT PROVIDED |
| Merge permitted | NO |

### Release decision

The build is ready **for physical testing** only once all relevant GitHub
checks succeed and a traceable artifact is available. Physical and aesthetic
checks then determine PASS/FAIL. Only after approvals and branch dependencies
resolve can the normal App Factory protected-branch PR/merge process proceed.
**Google Play signing, store Data Safety and AAB certification remain separate.**

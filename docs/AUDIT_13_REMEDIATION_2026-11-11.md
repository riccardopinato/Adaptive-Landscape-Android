# Deskland Android — 13-point NO-GO audit and remediation ledger

Baseline: `App-Factory-Core@ee72dbb1b0de2cd71bc4aac42c350853c6915159` (Master v27, Golden Index v29).
Repository: `riccardopinato/Adaptive-Landscape-Android`.
Base experimental PR: #23 / `44eb6a91847bbdb392299daac72407eca645bf11`.
Source implementation PR: #24, stacked onto #23. **No production merge, no physical certification.**

**Terminology:** SOURCE FIX = code changed, CI pending; PARTIAL = some cases not solved; BLOCKED = important functionality missing; DEVICE GATE = physical Android evidence required. Never promote SOURCE FIX to CLOSED until the required checks and physical tests are recorded.

| ID | Priority | Evidence from audit | Status on PR #24 | Remaining gate |
|---|---|---|---|---|
| AUD-001 | P0 | No complete searchable app drawer | SOURCE FIX, PARTIAL | Test multi-profile search, 100+ apps, app opening and accessibility |
| AUD-002 | P0 | No widget host, folders, shortcuts or multiple Home pages | BLOCKED | AppWidgetHost binding/picker/resize; folder persistence; pinned shortcuts; pagination and widget occupancy geometry |
| AUD-003 | P0 | Frame hitch/stall possible during drag and mask recalculation | DEVICE GATE | Move computational mask generation off UI/render dispatch, frame-time/jank traces, throttle and cancellation benchmarks |
| AUD-004 | P0 | Beach live wallpaper not applied automatically by choosing Home | BLOCKED BY ANDROID CONSENT | Guide user through ACTION_CHANGE_LIVE_WALLPAPER; inspect selected component; never silently replace user wallpaper |
| AUD-005 | P0 | Home not yet usable as an everyday launcher; some app launches unreliable | SOURCE FIX, PARTIAL | Correct user handle used; device test deep links/locked/disabled apps, start failures, Home/recents/navigation |
| AUD-006 | P1 | Brief swipes accidentally drag icons | SOURCE FIX | Long-press gesture and haptic; physical swipes/rapid drag/accessibility tests |
| AUD-007 | P1 | Old coastline remains when motion=0 | SOURCE FIX | Force target mask mix for staticScene; physical screenshot after add/remove/drag while motion=0 |
| AUD-008 | P1 | Returning to default Xiaomi/Samsung/other launcher is obscure | SOURCE FIX | Visible Change Home action; physical rollback with preserved original launcher layout |
| AUD-009 | P1 | Installed/removed applications not tracked until restart | SOURCE FIX | LauncherApps callback; verify package add/remove/change, locked profile and activity recreation |
| AUD-010 | P1 | Work, private, hidden and secondary profiles not fully supported | PARTIAL | Profile enumeration for supported Android versions; correct launch handle; separate persistence identities, user-visible restrictions, private-space policy |
| AUD-011 | P1 | Auto Home forcibly sets Beach and overrides chosen Meadow | SOURCE FIX | Respect base scene preference; verify Meadow reacts without corrupting manual config |
| AUD-012 | P1 | Normalized icon coordinates differ across OEM, tablets and navigation insets | DEVICE GATE | Verify system bars, gesture nav, cutouts, density, rotation, tablets, multi-window; exact pixel bounds |
| AUD-013 | P1 | Icon tiles missing sufficient keyboard/TalkBack activation semantics | SOURCE FIX, PARTIAL | Focusable/clickable icon semantics added; audit all Home controls, 48dp touch targets and TalkBack traversal |

## Review sequence / dependencies

1. **Source stability:** exact HEAD Flutter/Android CI, Kotlin compile and regression checks. PR #24 is Draft until these pass.
2. **Critical launcher parity:** complete AUD-002 and AUD-004 before making Home the default on a daily-driver device. Keep the manual wallpaper mode safe.
3. **Performance:** profile and fix AUD-003 without blindly increasing FPS or running expensive GPU tasks on UI callbacks.
4. **Cross-device:** resolve AUD-012 and AUD-013 with physical Android captures and assistive technology.
5. **Final acceptance:** clean install/update, wallpaper selection, Home role/rollback, empty ocean, app/folder/widget/add-remove/move, 0-motion, profiles, app drawer, process kill/reboot, battery/thermal, signed artifact identity and privacy review.

## Original Windows installer: static forensic fingerprint

User-supplied file: `Deskland-Setup.exe`, 273,003,382 bytes (~260.36 MiB).
SHA-256: `6ef9f1ff5a455f9ae5b26b9d821eef37fb568312fc589393ee45954c4a5ce48b`.
Static PE/NSIS inspection: **32-bit NSIS installer bootstrap (Nullsoft Install System v3.13)**; PE version resource describes `Deskland Website Installer`, product `Deskland`, version **0.1.31**, copyright 2026 Deskland authors; manifest execution level `asInvoker`.
The installer was **not executed**. The archive payload was **not extracted** in this environment; the evidence above is the wrapper's metadata, not a validated inventory of its embedded app files or licensed assets. No APK, Android-compatible assets or Windows-to-Android binary conversion is implied.

Official open-source reference: `BLEZZ0102/Deskland@9818b577402ff476e1b59da07970c56472713126` (GPL-3.0).
In the public Preview source, `host/DesktopHost.cs` reads real Windows desktop icon coordinates at intervals and supplies the changing geometry to its WebView2 wallpaper; the host pauses animations behind full-screen/maximized windows. `README.md` identifies Beach/Meadow as free Preview themes and other themes as previews only. The uploaded **Website** installer version must not be presumed to contain the same rights or source as this GPL Preview.

## Exact functional adaptation constraints

- Android `WallpaperService` cannot read third-party OEM launcher icon rectangles. The optional user-selected Deskland Home owns its icon views, and must host widgets to read their rectangles.
- Selecting the Home role and changing live wallpaper are distinct OS/user-consent actions; automatic silent takeover is not allowed.
- An empty Deskland Home must have zero occupied zones, resulting in sea only. App/folder/widget tiles must generate sand using their actual visible bounds. Moves should morph continuously; removal should erase coastline.
- Do not import upstream GPL source or art into this independently licensed code without an explicit project-wide licensing decision. Do not assume paid Website-edition artwork is free to redistribute.
- No AccessibilityService, QUERY_ALL_PACKAGES, auto screenshot capture or background collection of other launchers' UI is authorized.
- All physical gates are **NOT RUN** until linked to a source SHA, APK SHA-256, model/OS/launcher and observed outcomes.

## Required handoff

The source fixes are **not** a claim that all 13 audit points have been resolved.
The list must remain open until blockers (AUD-002, AUD-003, AUD-004, AUD-012) are implemented and independently verified. The GitHub hourly monitor checks commit/CI activity only, not real physical device behavior or interrupted ChatGPT sessions.

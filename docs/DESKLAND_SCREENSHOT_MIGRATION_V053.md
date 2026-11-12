# Deskland v0.5.3 — Layout migration from user-selected screenshot

Factory baseline: `riccardopinato/App-Factory-Core@c5adc147046beb7024629466d10eeb799c3c2619` (CURRENT v27, Golden index v29).
Parent PR #34: `c4df519c283d883163bc59f48c4940226f4f7843`.
Source implementation: Kotlin native Home, `AdaptiveHomeActivity.kt` and `HomeScreenshotMatcher.kt`.

## Product truth

Original Deskland Android concept was rejected because it required the owner to rebuild all apps by hand and displayed setup buttons over the ocean. Clean Home v0.5.2 fixed chrome but **not** layout migration. A conventional unprivileged Android wallpaper or launcher cannot query another launcher’s privately stored pages, icon coordinates, folders, or widget state. Android `LauncherApps.getActivityList` only retrieves launchable activities:
https://developer.android.com/reference/android/content/pm/LauncherApps

In v0.5.3 a voluntary screenshot provides a *visual approximation* of a previous Home layout, not private OEM data. No AccessibilityService, screen-recording permission, QUERY_ALL_PACKAGES or general photo-library access is added.

## Implemented

- First activation prompts once for migration, before separate Android wallpaper consent.
- Subsequent Home blank-space long press > «Import previous Home screenshot».
- `ACTION_OPEN_DOCUMENT` image selector, one selected content Uri at a time.
- Bitmap bound and downsampled off Android UI thread; no background screen capture.
- Icon comparison against `LauncherApps` drawable bitmaps using bounded, local
  pixel sampling; no remote OCR, network, AI package or extra ML model.
- Matches require a conservative threshold; unknown/tinted icons are ignored.
  The source screenshot can use a 4/5/6-column OEM Home; detected positions
  are normalized into Deskland's existing 5×8 grid and 4-slot dock. Layout
  reconstruction is therefore **approximate**, not pixel-exact.
- On-device annotated preview with ring labels, candidate names and
  confidence percentages. Two-step explicit confirmation; cancel = no write.
- On a previously automatically seeded Home, accepted image matches can
  replace the old alphabetic auto-fill globally. On a curated Deskland Home,
  only selected page icons and duplicated icons on other pages change.
- Existing folders, their membership, widgets and manual wallpaper profile
  `config_v2` are left intact. Conflicts with widget/folder cells are skipped.
- When the page was manually curated, unmatched shortcuts on that same page
  are preserved and proposed icon matches cannot overwrite a different
  manually placed shortcut. A curated dock keeps its existing order, adding
  only recognized entries that fit into free slots.
- Manual drag, folder, dock and other explicit Home edits atomically clear the
  auto-arranged marker; only first-time automatic population retains it.
- Returning from first-run screenshot selection now continues to the separate
  Android wallpaper permission step after preview dismissal, cancellation or
  decode failure, rather than abandoning the setup path.
- One atomic `SharedPreferences.Editor` changes all affected layout sections
  and writes a full pre-import recovery snapshot, including folder/dock data.
  «Undo last screenshot import» restores it without touching other launchers.
- All messages in EN, IT, ES, FR, DE and PT.
- Debug-only synthetic screenshot matcher test is RAM-only, never a physical
  screenshot. CI verifies exact build source + Android 35 emulator.
- Package version `0.5.3+18`.

## Known unsupported cases

- Direct import of Xiaomi/HyperOS, One UI, Pixel or other OEM private launcher
  database; do not claim that the old Home is copied automatically.
- Recognition of themed/user-customized icons, hidden apps, widgets, folders
  and nested folders from a screenshot: NOT RELIABLE; ignored or left to review.
- Dynamic Android widget host bind/restore on external OEM layouts.
- Screenshots showing a different DPI, wallpaper, icon sizes or icon pack:
  icon matcher may find zero/partial/wrong results.
- More than 3 Deskland pages or 4 dock entries, or cells occupied by current
  widgets/folders: results are skipped, not forcibly overwritten.
- Cloud/sync between launchers: none.
- Photorealistic water/sea: unaffected and not yet approved.

## Evidence matrix

| Gate | Status until proven |
|---|---|
| Kotlin + Flutter APK build | PENDING |
| Source and 6 native locales | PENDING |
| Local synthetic icon matcher on Android emulator | CI PASS on previous SHA; latest changes require rerun |
| Screenshot file picker / review/confirm with actual user image | NOT VERIFIED |
| Exact OEM screenshot positions | NOT POSSIBLE as a general guarantee |
| Undo, crash/restart, widgets unaffected | STATIC GUARDED; PHYSICAL NOT VERIFIED |
| Android 35 Home smoke on new source | CI PASS on previous SHA; latest changes require rerun |
| Physical Xiaomi/Redmi install + layout import | NOT VERIFIED |
| Actual sea/sand photo realism | NOT VERIFIED |
| Signed APK URL/HTTP download and update in-place | NOT VERIFIED |

## Research pointers

- Android LauncherApps API (launchable app list, not OEM layout):
  https://developer.android.com/reference/android/content/pm/LauncherApps
- Android Storage Access Framework and ACTION_OPEN_DOCUMENT (explicit selection):
  https://developer.android.com/training/data-storage/shared/documents-files
- Lawnchair docs for importing Nova .novabackup, **different format** from Xiaomi:
  https://github.com/LawnchairLauncher/docs/blob/main/help/core-features/backup-restore-and-reset.md

Nova source format adapters may be considered in a future PR with inspected
source corpus and offline-only compatibility. Do not claim Nova backup import
has shipped here. Physical/user visual approval remains mandatory.

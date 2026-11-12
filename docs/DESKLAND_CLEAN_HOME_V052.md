# Deskland v0.5.2 — Clean Home, install-aware first-run

Factory baseline (reuse of one resolved session baseline):
`riccardopinato/App-Factory-Core@c5adc147046beb7024629466d10eeb799c3c2619`.
Parent: Draft PR #33 `b0fe22f7fdaba0b2a51a2c9ca6f081ddaa6f738d`.
Scope: first approved roadmap maxi-step and a non-destructive fallback from maxi-step two.

## Reason for product correction

The owner's physical screenshot of Deskland v0.5.1 shows a fake-looking
technical Home with a large permanent wallpaper setup banner, page text,
top management buttons and an entire strip of bottom controls, plus a
single manually curated app. The screenshot is direct UX FAIL evidence.
A wallpaper-only process cannot read another launcher app's private icon
coordinates. This constraint remains true for Xiaomi/HyperOS/MIUI, Pixel
and Samsung unless the launcher explicitly exports/imports its layout.

## Implemented in source

- Ordinary Home has NO text chrome, technical warning banner, settings,
  widget or Home chooser buttons. Uses small passive page dots, a compact
  unlabeled drawer handle, native app icons and hosted widgets.
- Swipe LEFT/RIGHT on blank wallpaper changes Home page.
  Swipe UP opens the complete installed-app drawer, preserving search.
  Long-press EMPTY wallpaper opens a separate local management menu.
  Long-press an ICON continues to activate icon drag/drop without
  opening the menu.
- On a completely new Deskland layout, eligible installed launchable
  applications are placed locally in available Home cells across the
  existing 3 pages (maximum 120 cells, minus widgets). Additional
  installed apps always remain accessible from the complete app drawer.
  This seed is NOT an import of OEM positions.
- When updating an existing Deskland Home, all pinned icon cells, folder
  membership, widget placements and dock entries are left unchanged.
  A one-time opt-in dialog offers to FILL ONLY EMPTY CELLS from installed
  applications. The same operation is available later via a long press
  on the blank wallpaper. Never reset a user-authored layout silently.
- If Deskland wallpaper is not selected, one-time guided system
  wallpaper consent is offered. There is NO permanent warning overlay
  on Home and no silent replacement of an existing wallpaper.
- Existing `auto_home_icons_v1`, `auto_home_folders_v1`,
  `auto_home_dock_v1`, `auto_home_widgets_v1`, manual `config_v2`
  and imported water photo remain compatible.
- Layout re-render after page switch or icon removal is deferred until
  the next frame to avoid mutating FrameLayout during touch dispatch.
- All new user-facing strings are provided in EN, IT, ES, FR, DE, PT.
- Version `0.5.2+17` distinguishes this candidate from the test APKs.

## Still deliberately not claimed

- Reading or transferring exact old Xiaomi/Samsung/Pixel Home pages,
  grid positions, folder memberships or OEM widgets automatically.
  `LauncherApps` enumerates launchable apps, NOT their locations.
- Import from exported launcher backups; add explicit adapter support
  only for formats and permissions independently verified.
- Image-based voluntary screenshot migration; this would require an
  explicit privacy-local workflow with confidence scores, not background
  capture/scraping.
- A true photo-quality 4K ocean; no licensed reference image or user
  visual approval has been imported.
- App widgets on real device, accessibility, battery and heat, installation
  and direct APK delivery. Emulator test is NOT physical QA.

## Required gates

1. `python3 scripts/validate_repository.py` pass all native/Flutter/ARB
   source contracts.
2. Flutter analyze/test and exact-head Android debug/release compilation.
3. Android 35 emulator: app auto-populated, no debug chrome, menus and
   drawer via gestures, page transitions/occupancy, drag-to-folder,
   process-restart persistence and original Home role restoration.
4. Record SHA-256 and signing for an ARM64 APK.
5. Physical phone: user confirms top-level visual design and actual
   wallpaper pixels, no overlay, no icon clipping, correct touch targets,
   safe upgrade and return to previous OEM launcher.
6. Keep PR in DRAFT until all required gates. Do not merge the stacked
   dependency chain into main without approved physical evidence.

Next roadmap focus: optional **backup adapters first**, for launchers
that actually expose a transferable layout; then a privacy-preserving,
user-selected screenshot alignment assistant. Never promise exact
positions if the source does not expose them.

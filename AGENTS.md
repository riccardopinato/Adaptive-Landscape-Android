# Adaptive Landscape — agent contract

- App source owns functionality and code. `App-Factory-Core/CURRENT.yaml` owns generic process; pin one baseline before work.
- Never import upstream Deskland GPL-3.0 code/assets into this independently implemented commercial app; cite source only as PRODUCT/UX reference.
- Flutter is settings/editor UI; Android `WallpaperService` owns actual wallpaper. No invented icon coordinates: layout is manually calibrated.
- Avoid AccessibilityService, media projection, sensitive permissions, hidden background polling and network traffic. A user voluntarily selecting a Home screenshot with ACTION_OPEN_DOCUMENT for *in-memory* editor alignment is allowed, but do not capture, store, upload or analyze device activity automatically.
- 20fps visible, 8fps battery saver; stop when invisible, test OEM battery behavior physically.
- CI != physical verified; do not claim APK built or Play ready without exact-artifact proof.
- New features in bounded branch/PR, tests, source SHA and hardware evidence before merging production code.

- Schema-2 contract in `docs/SCENE_CONTRACT.md` is normative: keep Flutter scene and Kotlin renderer in sync when changing icon geometry or preferences.
- `shared_preferences` is local-only; do not add SDK telemetry without explicit user authorization and privacy gate.
- CI optimized release-mode APKs are **internal test artifacts**, not Play-signed Store releases.
- Preserve 6 ARB locales, validate keys in `scripts/validate_repository.py`, and avoid hardcoded translatable UI strings.
- Treat all physical QA IDs as NOT RUN unless user/device evidence includes exact package and APK hash.

- After v0.4.6: quality-specific FPS target ceilings ~12/16/20 (Balanced/High/Ultra), battery saver ~8; NEVER describe as measured FPS.
- User-selected genuine sea photo may live in Android noBackupFilesDir; screenshot remains transient. No image assets from Deskland.
- Stacked PR dependency chain #13 → #16 → #17 → #18 → #19 → #20 → prephysical QA. Never merge untested design or bypass required checks. See docs/PHYSICAL_QA_V042_V046.md.
- Do not publish internal QA files (screenshots, meminfo, device serial) to the repo. CI source SHA on PR may identify a synthetic merge; record both PR head SHA and actual workflow source.

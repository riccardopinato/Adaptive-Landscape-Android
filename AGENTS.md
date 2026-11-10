# Adaptive Landscape — agent contract

- App source owns functionality and code. `App-Factory-Core/CURRENT.yaml` owns generic process; pin one baseline before work.
- Never import upstream Deskland GPL-3.0 code/assets into this independently implemented commercial app; cite source only as PRODUCT/UX reference.
- Flutter is settings/editor UI; Android `WallpaperService` owns actual wallpaper. No invented icon coordinates: layout is manually calibrated.
- Avoid AccessibilityService, media projection, sensitive permissions, hidden background polling and network traffic.
- 20fps visible, 8fps battery saver; stop when invisible, test OEM battery behavior physically.
- CI != physical verified; do not claim APK built or Play ready without exact-artifact proof.
- New features in bounded branch/PR, tests, source SHA and hardware evidence before merging production code.

- Schema-2 contract in `docs/SCENE_CONTRACT.md` is normative: keep Flutter scene and Kotlin renderer in sync when changing icon geometry or preferences.
- `shared_preferences` is local-only; do not add SDK telemetry without explicit user authorization and privacy gate.
- CI optimized release-mode APKs are **internal test artifacts**, not Play-signed Store releases.
- Preserve 6 ARB locales, validate keys in `scripts/validate_repository.py`, and avoid hardcoded translatable UI strings.
- Treat all physical QA IDs as NOT RUN unless user/device evidence includes exact package and APK hash.

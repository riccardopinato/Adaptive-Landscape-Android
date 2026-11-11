# Interactive Scene Core — reuse assessment
Date: 2026-11-11
Status: PROPOSED EXTRACTION / NOT CERTIFIED
Authority: App-Factory-Core CURRENT.yaml (pin source commit per session); this app's Product Bible, ROADMAP and AGENTS.md remain authoritative.
Ideas register: https://github.com/riccardopinato/Idee-app-Archivi-riferimenti/blob/main/11-NUOVE-IDEE/AGENT_FACTORY_FINANCE_DESKLAND_2026-11-11.md (link becomes active after archive PR #90 merges).

## Product and provenance
This existing repo is the Android wallpaper/interactive scene product. It must NOT be duplicated for this initiative.
Upstream inspiration BLEZZ0102/Deskland: GPL-3.0, source reference commit 9818b577402ff476e1b59da07970c56472713126. Upstream code/assets are NOT cleared for copying into this independent commercial Android application. Continue a clean-room implementation.
Current app-specific Flutter/Kotlin, renderer and scene contract remain in this repo; this document does not introduce new code or change release behavior.

## Candidate reusable seams
1. Pure scene model: normalized viewport, scene objects, material parameters, translations/geometry; preserve docs/SCENE_CONTRACT.md as canonical app contract pending extraction.
2. Interaction adapter: taps, gesture semantics and user-calibrated icon zones. No claim of universal Android Home icon-coordinate APIs.
3. Render adapter: Android WallpaperService and GLES-specific accelerated rendering; do not require these native dependencies in Flutter-only apps.
4. Scene resources: animation shaders and effect presets as optional modules, NOT required for Notes canvas, calendar or finance products.
5. Performance harness: frame pacing, app visibility, thermal/battery saver, resolutions, OEM-specific differences and rollback.

## Evidence gates
- Verify each source file path and exact main SHA before extraction.
- Map imports and dependencies; check all assets/licenses and ensure no original Deskland GPL code is copied.
- Test extraction within original app first; check Flutter scene contract vs Kotlin renderer; maintain all localization and native lifecycle tests.
- Consumer #2 must be independent and real; pass independent tests with pinned version before proposing Golden promotion.
- Real Android visual quality, FPS, battery/thermal, Home icon alignment and permission-policy behavior are separate DEVICE_VERIFIED gates; passing GitHub CI is not enough.
- No new accessibility/media-projection/overlay privileges without explicit scope and review.
- Source and shared module can evolve independently via versioned packages; no manual copy-paste forks.

## Relevant open gaps (archive snapshot 2026-11-10)
The previous catalog recorded a physical Android v0.4 user acceptance FAIL for sea visuals/performance and missing automatic icon mapping. PR #13 was experimental and not merged at that snapshot. Recheck actual GitHub HEAD and PR state before citing as current.

## Next steps
(1) Extract dependency diagram; (2) write license provenance ledger; (3) benchmark base scene contract and rendering; (4) create separate Interactive-Scene-Core repository only after an independently useful module boundary is proven; (5) verify second consumer and propose donor/Golden with evidence.

This is a scoped research/architecture link; no runtime feature or code claimed implemented by this document.

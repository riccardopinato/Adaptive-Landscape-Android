# Factory provenance — pinned 2026-11-10

- Upstream: https://github.com/BLEZZ0102/Deskland
- Upstream commit: `9818b577402ff476e1b59da07970c56472713126`
- Upstream code/asset license: GPL-3.0; `NOTICE`, `TRADEMARKS.md` and `THIRD_PARTY_NOTICES.md` reviewed.
- Upstream referenced paths: `README.md`, `host/DesktopHost.cs`, `prototype/flat.html`, `prototype/meadow.html`, `prototype/themes.json`.
- Classification: `UI_REFERENCE` / `OSS_REFERENCE_REWRITE`, **NOT** `OSS_COPIED`.
- What was learned: Windows icon rectangle integration, occupancy field, smoothly adapting scenery, wallpaper process distinct from settings.
- What was NOT reused: original Windows host, icon reader, shader code, JS, images, trademarks, themes' artistic assets.
- Original implementation files: `lib/core/icon_field.dart`, `lib/ui/scene_painter.dart`, `android/app/src/main/kotlin/it/riccardopinato/adaptivelandscape/AdaptiveWallpaperService.kt`.
- Extra external dependencies: none beyond Flutter/Android SDK and Flutter localization support.
- Source evidence: source SHA + path E1; no direct upstream test/CI exact-head proof, no E3 Donor certification.
- Android-specific capability candidate: native wallpaper lifecycle engine, pending app-repo source SHA, test case verification on physical phone and CI before E3.

## Golden reuse ledger
- Theme M3: `golden/ui/GOLDEN_THEME_M3_FLUTTER_v1.txt`, `COPY-READY`: seed Material3 shell adopted conceptually.
- Localization: `golden/ui/GOLDEN_LOCALIZATION_FLUTTER_v1.txt`, `COPY-READY`: ARB generated baseline EN/IT/ES/FR/DE/PT.
- CI/release: `golden/release/GOLDEN_CI_RELEASE_WEB_DEPLOY_FLUTTER_v2.txt`, `COPY-READY`: build/verification contract identified; no production workflow or signing applied.
- App bootstrap: `golden/governance/GOLDEN_APP_BOOTSTRAP_FLUTTER_v1_DRAFT.txt`: minimal manifest/identity/permission boundary; draft only.
- Native reliability: `golden/native/GOLDEN_ANDROID_NATIVE_RELIABILITY_v1_DRAFT.txt`: single owner, visible-only frames, low-power, no background polling.
- OSS reconnaissance: `golden/governance/GOLDEN_OSS_REUSE_RECONNAISSANCE_FLUTTER_v1_DRAFT.txt`.
- Provenance: `golden/governance/GOLDEN_PROVENANCE_REUSE_LEDGER_v1_DRAFT.txt`.
- Donor admission: `golden/governance/GOLDEN_SINGLE_DONOR_CAPABILITY_ADMISSION_v1.txt`, E1 only.
- Release reality: `golden/release/GOLDEN_RELEASE_REALITY_PHYSICAL_VALIDATION_v1_DRAFT.txt`, physical gate pending.

## Beach v0.3 graphics provenance

User-directed correction: Beach has only top-down sand, moving sea, foam and wet shoreline. Deskland `prototype/flat.html` and `prototype/themes.json` served as product and visual-reference evidence only. Independent source files: `lib/core/beach_coast.dart`, `lib/ui/beach_surface.dart`, `android/.../CoastalBeachRenderer.kt`. **No screenshot, JS/WebGL shader, palm asset, proprietary artwork or GPL source copied**. Source-reconstruction fidelity and real-device visual quality have not been formally verified. The narrower user brief excludes optional kayaks, palms and seabirds included in the upstream free Beach.

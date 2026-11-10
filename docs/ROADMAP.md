# Adaptive Landscape — Five Maxi-step implementation and evidence ledger

Baseline Factory: `riccardopinato/App-Factory-Core@d4ed1185d36d419228fba6b57a979cde308bd321`.
App repository source of truth: https://github.com/riccardopinato/Adaptive-Landscape-Android

Status vocabulary: **CODED** = committed feature; **CI VERIFIED** = analyze/tests/Android and Web builds for exact SHA; **USER WEB CONFIRMED** = preview observed by user; **DEVICE UNVERIFIED** = no physical evidence. Physical and store gates must not be conflated.

| Phase | Deliverable | Evidence / remaining acceptance |
|---|---|---|
| 1. Foundation | Schema 2 normalized contract, local profiles, CI Flutter/Gradle, JDK17, tests, provenance and reproducible scaffold | CODED; verify CI exact PR head, source scaffold remains generated, not committed binaries |
| 2. Icon & widget calibration | Configurable 3–8 × 3–12 grid, movable/resizeable excluded zones, multi-profile selection, JSON export/import, legacy reader | CODED; physical launcher coordinate/fingerprint matching unverified |
| 3. Premium scenes | Meadow retained; Beach v0.3 replaced with independent top-down sand + ocean rendering, dynamically forming coast around icon/widget regions, moving tides/foam, granular wet-sand/shallow-water gradients, no sky or clouds. Dart and Kotlin separate renderers; battery saver & reduced motion kept | CODED; physical visual comparison, FPS, thermal/battery and graphic quality acceptance unverified |
| 4. UX & persistence | Responsive editor, six ARB locales, profile CRUD, clipboard import/export, no account/network, privacy notes | CODED; accessibility/locale manual screen QA unverified |
| 5. Release engineering | CI Android debug + optimized internal APKs and Web Pages, privacy/provenance gates, device QA plan, Play signing checklist | CI pipeline configuration; dedicated release keystore, physical QA, AAB signing and Play testing **not done** |

## Design/product remaining risks

**P0**: Pixel/Samsung/Redmi physical lifecycle, wallpaper picker, permissions, surface and power management. No user testing yet.  
**P0**: Scene geometry vs actual Home icon positions varies by launcher, widgets and page offsets. Manual approximation only.  
**P1**: Native Kotlin renderer and Flutter Dart renderer have coordinate parity by algorithm description but are not pixel-equivalent. Use screenshot comparisons in physical gate.  
**P1**: Multi-profile sync uses local preferences; verify process kills/reboots and migration from 0.1.  
**P1**: Theme artwork is an improved vector baseline; subjective commercial/premium art acceptance is pending.  
**P1**: Verify no upload/store privacy obligations overlooked before release.  
**P2**: Accessibility screen reader/touch target checks, small tablet layouts and dark-mode settings.

## Go/no-go gates

1. CI gates green on precise merged commit: Flutter analyze/test, Kotlin compilation, debug APK, optimized APK and Web. Failed tests block merge.
2. User verifies Web preview after deployed commit: controls, profiles, colors, zones, export/import.
3. Physical matrix `docs/QA_MATRIX.md` PASS with device/Android/launcher, reproducible evidence and APK SHA.
4. Google Play Internal Testing install/update `versionCode` monotonic, AAB signed with stable private release key, Play Console declarations and policies verified.
5. Commercial release only after battery, legal/art/license and accessibility acceptance. Proposed one-time price €1.99 pilot / €2.99 after differentiation; no ads/subscriptions.

## Visual correction — 10 Nov 2026 (Web-first)

Beach v0.2 sky/sun/cloud scene rejected as inconsistent with the intended original. See [BEACH_VISUAL_REFERENCE.md](BEACH_VISUAL_REFERENCE.md). V0.3 Beach is overhead sand-and-water only. Web user acceptance remains pending after deploy. Native performance and consistency require later physical Android QA; do not claim a pixel-exact Deskland port.

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

## v0.3.2 — GPU sea / occupancy-only sand, Web-first

- [CODED] Flutter GLSL fragment shader per framebuffer pixel, a cached 512×1024 layout influence mask; fallback to previous mesh on unsupported Web GPU renderers.
- [CODED] No initial sand baseline: no icons/widgets/dock yields pure ocean; merged sand patches follow icon/folder/widget occupied geometry.
- [CODED] Manual 3- or 4-icon bottom dock layouts, configurable off; edit guides can be hidden to inspect sea-only output. Profile import/export still schema 2.
- [CODED] Kotlin formula aligned to occupancy-only (NOT 4K); Android actual quality and battery not certified.
- [PENDING USER WEB ACCEPTANCE] 4K/high-DPI visual sharpness and aesthetic realism; browser GPU capability varies.
- [PENDING PHYSICAL] Android 4K quality/performance require dedicated native rendering strategy and battery measurement; don't mark done.

## v0.3.3 — failed visual acceptance repair

- Fix initial Web scene: new profile opens Beach with no selected icons/zone/dock and hidden guides, while existing saved profiles are preserved.
- Add explicit current-profile-only Ocean reset (with descriptive text); visual indicator shows no occupancy vs icon/widget/dock counts.
- Make GPU vs fallback renderer state visible, rather than falsely claiming a pixel-shaded sea when unavailable. Improve fallback mesh density.
- Improve independently authored multi-scale water surface/foam/sand color shader and add panoramic full-screen preview to inspect on high-DPI browser.
- Add widget and phase-grid regression tests. **Acceptance remains pending until user has actually inspected the deployed Web**. Mobile APK physical QA unchanged/deferred.

## Beach v0.4.0 — 4K realism target, not yet validated

- [CODED] Offline original 24×42 packed RGB565 ocean tonal reference for large-scale natural optical variations; no external asset or network, source is new generated artwork.
- [CODED] Revised high-detail Flutter GPU shader for water/caustics/foam with pixel-scale detail and stable no-land invariant. [CODED] Web quality tiers Balanced/High/Ultra persisted in schema2; Kotlin ignores optional quality tier.
- [TEST GATE] Analyze, test, Web and APK source builds, no Play signing changes.
- [USER VISUAL GATE OPEN] Actual 4K rendering/FPS, subjective near-photographic quality and performance on user's browser and Android device not certified. See BEACH_4K_ART_DIRECTION.md.

## v0.4.1 — first Android physical feedback: P0 repair

- **ACTUAL USER FAIL v0.4**: Android native `CoastalBeachRenderer` used a bitmap at max **360px** stretched to the screen. Previous Web shader and Ultra setting did not apply to Android; image visibly sgranata, pale and almost static.
- **CODED PR**: EGL14/GLES20 ocean renderer for native WallpaperService, per-device framebuffer-size pixel shading (not a 4K photographic source), animated caustics/feathered foam, respect native `renderQuality`, direct 360px CPU fallback if GPU unavailable and visible renderer diagnostic.
- **CODED PR**: opt-in Android document picker to select a user-produced Home screenshot *for in-memory editor overlay*; no additional permission and no screenshot persistence. This is guided **manual** icon/widget/dock calibration, NOT launcher auto detection.
- **REQUIRED DEVICE QA BEFORE MERGE**: verify GPU status, actual FPS/grain/wave, screenshot mapping across navigation/dock and icon clusters; record exact APK + device/launcher. Do not say "4K photorealism achieved" until proven. Keep auto-icon detection scoped to separate policy/consent research gate.


## Beach v0.4.2 — photographic image source, separate stacked PR

- [CODED] Native offline user-selected JPG/PNG photograph, bounded processing,
  EXIF stripping, app-private no-backup persistence and removal.
- [CODED] EGL/GLES2 blends real photo, subtle looped refraction and existing
  manual-occupancy sand mask. Photo upload on file change/context recreation.
- [CODED] Localized UI in six languages and distinct native backend indicator;
  Web preview is explicitly not a photographic Android preview.
- [CODED] version 0.4.2+9; follows v0.4.1+8 without data migration.
- [PENDING CI] GitHub Actions Flutter analyze/test/Web/Android builds against
  the stacked PR source SHA; failing build blocks merge.
- [PENDING PHYSICAL] Real-photo source, visual realism, shore/launcher
  calibration, seamless motion, lifecycle, battery, thermal and install/update.
- [DO NOT MERGE] PR #13 remains unmerged; this branch targets its exact
  head and is **not** a production Play candidate.

Detailed user test contract: [BEACH_PHOTOGRAPHIC_V042.md](BEACH_PHOTOGRAPHIC_V042.md).


## v0.4.3 — Web photographic preview and image comparison

- [CODED] Local browser image selection; bounds/size safety and downsampling.
- [CODED] Real photo sampler on Web with cyclic subtle refraction, cover crop.
- [CODED] Before/after toggle against existing synthetic ocean.
- [CODED] Six localized strings, zero-sand regression tests, version 0.4.3+10.
- [PENDING CI] Flutter analyze/test, Web shader build, Android APK.
- [PENDING USER] Visual/photo realism acceptance on Web.
- [OUT OF SCOPE] Native photo preview inside Flutter editor; Android photo is
  stored separately by WallpaperService. Do not imply pixel equivalence.


## v0.4.4 — visual realism optimization

- [CODED] Third periodic refraction layer on Web/native (no linear drift).
- [CODED] Photo-specific reduction of synthetic shoreline foam.
- [CODED] User sliders for refraction and foam (independent of motion speed).
- [CODED] Backward-compatible optional schema2 values with clamped defaults.
- [CODED] Six locales, source checks and profile tests, version 0.4.4+11.
- [NOT BUNDLED] Authentic default open-water asset. Curated CC0 sources are
  documented pending binary provenance/size/integration review.
- [PENDING CI] Flutter/Android tests and APK/web build.
- [PENDING PHYSICAL] Verify photo actually stays recognizable through motion,
  no hard coast/foam line, native GPU FPS/heat/battery, OEM parity.


## v0.4.5 — Smart Home Calibration (manual; no launcher introspection)

- [CODED] Horizontal margin `gridInset` normalized across native + Web.
- [CODED] Top offset and grid height sliders, screenshot overlay uses
  cover-fit instead of destructive fill stretching.
- [CODED] Fullscreen InteractiveViewer pan/pinch 1×–5× for manual positioning.
- [CODED] Sand mask/cache keys invalidate when margins change.
- [CODED] Six locales, regression tests, version 0.4.5+12.
- [PENDING CI] Flutter/Android/Web gates.
- [PENDING PHYSICAL] Test portrait screenshots with actual navbar/insets,
  launcher variants, grid/dock/widget alignment, tap and drag gesture priority.
- [NOT IMPLEMENTED] Automatic detection of installed apps or icon positions.

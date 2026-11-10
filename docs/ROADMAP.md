# Product Bible and 5 Maxi-Step roadmap — 2026-11-10

Product: original premium Android live wallpaper that reacts to a user's Home icon layout. Standalone paid-download plan: €1.99 for initial pilot, €2.99 if differentiation/quality validated. Prices are proposals, not published offers. No ads, sign-in, subscriptions, backend or AI. More themes can be added later without new app binaries, subject to quality and licensing.

## Current software evidence
- `SOURCE_PREPARED` local kit only. Generated Flutter Android project: not executed. Static XML/ARB/source structural checks only. APK and live wallpaper device runtime: NOT VERIFIED.
- Upstream Deskland: PATTERN/PRODUCT reference E1 at `BLEZZ0102/Deskland@9818b577402ff476e1b59da07970c56472713126` (GPL-3.0). Not E3/Golden; do not claim its Windows tests prove our Android app.

## Five Maxi-steps
1. **Foundation & real Android wallpaper**: make dedicated repo; copy overlay into fresh Flutter project; CI analyze/test/debug APK; set stable original brand icon, test WallpaperService on physical device. Gate: real Home wallpaper visible, visibility pause works, clean startup.
2. **Accurate icon calibration**: phone/density/launcher configuration; safe zones and dock; 5x8 grid editable per page; optional non-invasive user-guided import (never fake automatic access). Gate: icons/scene alignment on Pixel, Samsung, Xiaomi and common launchers.
3. **Visual quality and animation**: premium, original vector design; two polished scenes; animation engine (cloud/wave/shadows/flowers/objects) with occupancy-aware collision, parallax, day/night; reduced motion and low-power mode. Gate: measure frame time, FPS and battery on real phones.
4. **UX, local persistence, translations**: clear onboarding, profile export/import, ARB localized Android description, accessibility, privacy statement, theme presets. Gate: theme persists across process kill/restart/reboot and screen rotation; no unauthorized data collection.
5. **Paid launch readiness**: persistent signing identity, Play Console one-time purchase download SKU/price, 1.99 vs 2.99 pricing test, screenshots using original assets, QA/AppLab, source artifact fingerprint, internal testing and country policy checks. Gate: Play internal install and update-in-place, battery/OEM verification, then store publication only via authorized action.

## Prioritized risks
- P0 platform: icon positions are not provided by Android WallpaperService. Manual calibration is the truthful V1 approach.
- P0 license: GPL-3.0 upstream; independent implementation only for proprietary paid product.
- P1 OEM: wallpaper service lifecycle and battery saver; physical device tests mandatory.
- P1 mismatch: Flutter preview/native renderer are separate implementations in MVP; match scene parameters or eventually share engine.
- P2 battery/size: avoid large animation textures, keep low frame rates, pause when not visible.

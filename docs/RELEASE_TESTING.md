# APK e deploy — MVP 0.1.0

## What can be tested

1. CI Android: source overlay -> Flutter project -> analyzer -> Dart tests -> Android debug APK.
2. Android real device: download the artifact ZIP from the successful GitHub Actions workflow run and extract `app-debug.apk`. Enable install from source where permitted. Run and press **Set live wallpaper**. Validate service display, switch Meadow/Beach, toggle occupied cells, background/restart, and battery saver.
3. Web: run GitHub Pages on `main`; test scene previews, grid editing, layouts and six language fallbacks. It intentionally disables Android wallpaper installation. Web success is not native evidence.

## Distinct evidence states

- SOURCE CHECKED: files and scripts exist in the source commit.
- CI PASS / APK BUILT: only after a successful build run on the exact source SHA and APK artifact.
- WEB DEPLOYED: only after Pages job success and reachable URL.
- PHYSICAL VERIFIED: requires device model, Android version, exact APK SHA256, test run and observed behavior.
- STORE READY: requires stable signing identity, AAB, Play Internal Testing and store requirements.

## Upstream and licensing

Reference only: BLEZZ0102/Deskland@9818b577402ff476e1b59da07970c56472713126 (GPL-3.0). No Deskland code, art, name or logo included. Choose separate copyright/license policy before public distribution.

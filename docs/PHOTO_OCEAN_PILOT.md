# Native Beach photographic water pilot — v0.4.2 (NOT USER-ACCEPTED)

## Why this is a different direction

The user physically tested the native EGL v0.4.1 APK and reported a small improvement in movement/sharpness, but **far below the near-photographic 4K sea target**. OpenGL framebuffer shading fixes the old bitmap upscale; it cannot turn noise functions into a real overhead ocean photo.

## Opt-in real image input

The app's Android editor allows **Choose ocean photograph** through the platform's system document picker. Users can select any real top-down water-only image from their own phone storage. Choose a photo without sky, shoreline, rocks or boats, ideally from an original source at least 2160×3840 px or with enough detail to crop to portrait.

- Android safely decodes only a centered portrait region around 9:19.5, caps source dimensions and output size (2160×3840, 12 MB JPEG), and stores the compressed copy as `filesDir/ocean_wallpaper_user.jpg` after user consent. No upload or remote image URL.
- The selected photograph **persists app-privately** for the live wallpaper; it is NOT the transient Home screenshot used for icon calibration. The editor provides an explicit Remove action. No network, media/storage, camera or accessibility permission is introduced.
- The wallpaper loads this local file as a separate GL texture only after its EGL context exists, with fallback if GPU memory or max texture size is insufficient. 8-bit RGBA decode helps avoid banding. `GLES20.GL_TEXTURE1` samples photo color with ~0.004–0.005 normalized UV deformation per frame; synthetic grain and harsh foam are reduced whenever photo is active.
- No default third-party image is included in the APK. The image's license/provenance is the user's responsibility if used commercially; see source license (e.g. CC or stock provider terms).
- All existing icon/widget/dock layout data and schema2 profiles remain untouched. WallpaperService **does not** automatically detect launcher icon coordinates; related issue #14 remains open.

## Limits and QA exit criteria

A photographic image + moving UV is **not automatically a real fluid simulation** or 4K screen. Pixel output, true photo source quality, motion naturalness, crop, context resets and heat must be checked on a real device. Test: no photo → procedural fallback; selected photo → real-detail water; clear image → fallback; with and without occupied icon/widget/dock zones; power saver and 30-minute battery heat. Do not merge into main or publish as Play-complete until user acceptance.

Reference photo examples can be sourced from properly licensed libraries; do not embed Unsplash/Pexels/Deskland assets without verifying rights for a potentially paid wallpaper app. Prior rejected v0.4/v0.4.1 versions remain explicitly unaccepted visually.

# Schema-2 Flutter ↔ Kotlin scene contract

This is a **normative data contract**, not a claim of pixel-equivalent physical rendering.

```json
{"schema":2,"scene":"meadow","columns":5,"rows":8,"top":0.115,"span":0.765,"occupied":[0,5],"zones":[{"x":0.5,"y":0.75,"width":0.25,"height":0.1,"kind":"widget"}],"palette":"original","night":false,"motion":1,"reduceMotion":false}
```

- Coordinates are normalized to [0,1] relative to the wallpaper canvas (x horizontal, y downward). Android launcher icon origins are not accessible.
- Columns 3..8 and rows 3..12; `top` [0..0.5], `span` [0.2..(1-top)]. Grid index row-major.
- Grid cell center x = ((index % columns) + 0.5)/columns; y = top + ((index ~/ columns)+0.5) × span/rows.
- Explicit `zones` are centered axis-aligned rectangles with dimensions [0.03..1] and centers [0..1], at most 80; semantic kind icon, widget or dock. No launcher names, package IDs, screenshots, or sensitive identifiers.
- Influence saturates [0,1]. Grid obstacles use Gaussian with sigmaX = .58/columns, sigmaY = .58*span/rows. Zone influence is 1 inside rectangle and Gaussian outside with sigmaX = width*.38, sigmaY = height*.38. Both Dart and Kotlin should implement the same equations.
- Scene = meadow|beach; palette = original|sunset|mint; night boolean; motion in [0..2]; reduceMotion boolean. When reduceMotion or motion=0, Android renders only on meaningful changes, not continuous frames.
- Android persists config JSON in `adaptive_landscape/config_v2`; legacy `scene`/`occupied` remains for safe migration. Flutter stores named profiles locally via `shared_preferences`.
- Import is schema 2 only; invalid/oversized imported content must be rejected or normalized. No network.
- Changing grid dimensions intentionally clears marked cells in the editor; zone positions remain. User must recalibrate after grid/layout changes.
- Unit tests cover boundary, versioned serialization and zones. **On-device** screen dimensions, insets, OEM launcher workspace, render parity and frame timing are not yet validated.

### Beach 0.3.2 shoreline semantics

`shoreBase = clamp(sum(influence kernels),0,1) - 0.32 + influence * (0.010 sin(38y+0.8)+0.006 sin(83y+31x))`. Sea-only default is a strict invariant when all markers are empty; water tides cannot generate sand on an empty beach. The three/four optional dock icons are serialized as `zones[]` with `kind: 'dock'`, at y≈0.941. No silent dock assumption. Web GPU uses an influence-only mask and shades water natively at display framebuffer resolution; Kotlin uses raster and is not 4K certified.

### Android v0.4.1 native EGL renderer and screenshot calibration

The Android `renderQuality` optional setting is read from schema2 and passed to the first-party EGL14/GLES20 shader. This changes detail cost only; no native photographic 4K asset. Canvas remains last-resort emergency fallback. Color/geometry formulas differ from Flutter/Web, so visual parity MUST be tested. The Android editor accepts a user-selected screenshot **in memory** to help align its existing normalized icon/widget grid to the Home; screenshot content is not part of schema2 and no package names/launcher structures are collected. WallpaperService still cannot discover icons automatically.


### v0.4.2 separate local photo data

Schema-2 JSON is unchanged. A user-selected ocean photograph is not an
icon/layout attribute, not a JSON export field and not imported with profiles.
Android stores one photo for the current installation in private no-backup
storage, selected via ACTION_OPEN_DOCUMENT and used by every Beach profile.
The privacy-protected screenshot for calibration remains transient and
unrelated to the photo.

Android renderer status `gpu_photo_native` is reported only after an EGL
frame sampled a successfully uploaded photo; `gpu_native` means procedural
water, and `cpu_360px_fallback` remains a degraded emergency path. No
claim about Web/native pixel parity or automatic launcher detection.

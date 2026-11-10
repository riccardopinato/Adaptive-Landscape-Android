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

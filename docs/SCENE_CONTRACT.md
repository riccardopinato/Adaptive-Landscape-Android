# Shared Flutter–Android scene contract (v2)

Scope: contract **defined**, not certification of physical rendering fidelity.

- Both engines draw normalized positions, units [0,1] from top-left of wallpaper area.
- `schema=2`; default scene `meadow`; options `meadow` and `beach`.
- User grid `columns` 3..8, `rows` 3..12, with `top` and `span` relative to full height. `occupied` is a sorted list of integer cell indices.
- Cell center = ((index % columns + .5) / columns, top + (floor(index / columns) + .5) * span / rows).
- `zones` are bounded normalized rectangles, each center `x,y`, dimensions `width,height`, kind `icon|widget|dock`. At most 80; no app identifiers.
- `palette` original|sunset|mint, `night` boolean, `motion` 0..2, `reduceMotion` boolean.
- JSON is persisted locally, no server. Invalid/corrupt versioned fields fall back to conservative defaults.
- Existing v0.1 legacy `scene` and comma-delimited `occupied` are supported during migration.
- Visual behavior may deviate across Dart Canvas and Android Canvas; coordinate tests and device visual comparisons remain mandatory.
- Full screen geometry is not guaranteed to be identical to launcher workspace on every OEM. Provide manual calibration and visible distinction between physical test and preview.

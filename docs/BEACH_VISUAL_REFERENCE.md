# Beach v0.3 — original visual direction

**Product correction requested 2026-11-10:** Beach is **only an overhead sea-and-sand landscape**. No sky, sun, clouds, horizon, mountain/grass, or top-down birds/boats/palms in the requested minimal scene. Do not reuse the former sky/sun visual treatment.

## Reference evidence and licensing

- Deskland (product reference only): https://github.com/BLEZZ0102/Deskland/tree/9818b577402ff476e1b59da07970c56472713126
- README describes Beach as adaptive coastline with waves; `prototype/flat.html` identifies a 2D WebGL theme with deformed coastline, deep-to-shallow turquoise water, uneven low-opacity specular patches, thin foam and wet-sand bands.
- `prototype/themes.json` describes the shoreline bending around desktop icons, and waves reaching the sand.
- Original Deskland also includes optional palms, kayaks, and seabirds. **These are explicitly omitted per the user's narrower brief.**
- Do not copy GPL-3.0 code/shaders/graphics, screenshot pixels, layout or trademarks into a closed-source release; the Flutter and Kotlin implementations use a separate, original simplified model. Near-photorealistic visual *quality* remains a subjective target, not certified equivalence with the upstream animation.

## Implementation contract

1. The entire canvas is a top-down view. A smooth contour separates shore (positive field) from ocean (negative field).
2. Normalized `shore = 0.30 + 0.078 sin(5.4 y + 0.3) + 0.031 sin(14.2 y + 0.9) + 0.013 sin(29 y) - x + 0.84 occupancyInfluence(x,y)`. This is an independently designed shoreline formula.
3. `tide = 0.017 sin(phase+6.8 y) + 0.007 sin(2 phase+17 y)`; positive/negative phase shifts flood/recede near the shoreline.
4. Colors progress between dry cream sand, pinkish damp sand, nearshore transparency, pale-green shoal, turquoise and deep water, with small procedural grain and moving pale surf.
5. Shoreline geometry including occupancy field is cached for the same editor layout; only time-dependent appearance updates each frame. All wave/foam terms are 2π-periodic to avoid a jump when the 12-second animation repeats. No images loaded over the network, no extra UI scene permissions.
6. Android keeps the declared 20fps while visible / 8fps in battery saver schedule. Actual frame time and battery require deferred physical QA.
7. Any future change to the coast formula/color stops must update both Dart and Kotlin and their tests; mobile fidelity and battery tests remain deferred.

## Acceptance criteria (Web first)

- Beach is an overhead sea-and-sand preview with **no visible sky, sun, clouds or horizon**.
- The shore wraps around every occupied cell/widget region instead of ignoring most icons.
- Waves and foam pulse, shallow water fades to deeper sea, wet sand visible.
- Switching Beach↔Meadow, palettes and motion does not crash; profile persistence unchanged.
- Flutter analyze, tests and Web build PASS on the PR exact SHA. Do not claim smartphone QA from this.

### Seamless speed controls — v0.3.1

A non-integer speed (for example 0.5×) must not cause the phase to jump when the 12-second base animation period ends. Flutter changes its controller period to `12000 / speed` milliseconds and always sends phase [0,2π]; Android derives phase continuously from monotonic `SystemClock.uptimeMillis()` instead of modulo-wrapping the source time. Static/reduced motion still stops frame scheduling; the declared native 20fps/8fps cadence remains unchanged. This is source/CI verification only, not physical timing certification.

## Product direction change v0.3.2

- **Empty Home = 100% ocean**. Delete all grid selections, widgets and manual dock markers to remove every patch of sand.
- **Only an occupied region may generate sand**. The positive distance/strength threshold is computed from icon/kernel, widget rectangles, and each separately marked dock app; a cluster becomes contiguous beach.
- **Bottom launcher dock is not accessible through WallpaperService**. Provide explicit 0, 3, 4 bottom icons; presets saved in schema-2 `zones[]`. 0 means no dock and no sand from it. User can inspect pure output using the 'preview guides' switch.
- **4K-capable Web rendering**: new GPL-independent `shaders/beach_ocean.frag` performs fragment shading at output DPR; no 73×137 water mesh in the GPU path. A 512×1024 texture encodes only static icon/zone influence (recomputed when layout changes), not surface color. The fallback retains the mesh, so Web shader support must be visually verified.
- **Do not assert photographic equivalence** or Android 4K: Kotlin raster resolution is battery-limited pending on-device analysis; no physical QA was performed.

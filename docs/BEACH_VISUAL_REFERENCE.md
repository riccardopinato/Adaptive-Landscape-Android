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
3. `tide = 0.017 sin(phase+6.8 y) + 0.007 sin(1.65 phase+17 y)`; positive/negative phase shifts flood/recede near the shoreline.
4. Colors progress between dry cream sand, pinkish damp sand, nearshore transparency, pale-green shoal, turquoise and deep water, with small procedural grain and moving pale surf.
5. Geometry is cached for the same editor layout; wave appearance updates with animation phase. No images loaded over the network, no extra UI scene permissions.
6. Any future change to the coast formula/color stops must update both Dart and Kotlin and their tests; mobile fidelity and battery tests remain deferred.

## Acceptance criteria (Web first)

- Beach is an overhead sea-and-sand preview with **no visible sky, sun, clouds or horizon**.
- The shore wraps around every occupied cell/widget region instead of ignoring most icons.
- Waves and foam pulse, shallow water fades to deeper sea, wet sand visible.
- Switching Beach↔Meadow, palettes and motion does not crash; profile persistence unchanged.
- Flutter analyze, tests and Web build PASS on the PR exact SHA. Do not claim smartphone QA from this.

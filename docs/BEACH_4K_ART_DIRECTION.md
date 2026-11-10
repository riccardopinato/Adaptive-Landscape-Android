# Beach v0.4.0 — GPU high-detail aerial ocean

## Product intent

Target: realistic aerial **open sea by default**, with sand emerging only near manually marked app, folder, widget and dock occupancy. The user explicitly rejected prior 0.3.2 low-realism visuals. This is a new art-direction iteration, not user certification of photorealism.

## Rendering layers

1. **Offline original tonal atlas** `lib/core/ocean_tone_atlas.dart`: 24×42 packed RGB565 image sampled from a newly generated original aerial-ocean visual, then decoded to a smoothed image sampler at startup. It supplies *only large-scale water color, depth and light-distribution*. Its actual source is **not a native 4K photograph**. Asset provenance is original generated artwork created for this project, without third-party photographs or Deskland content.
2. **GPU fragment renderer** `shaders/beach_ocean.frag`: independently authored Flutter runtime-effect GLSL, per-fragment multiscale current, shallow gradient, restrained caustics, fine highlights, translucent water, wet/dry sand, broken wave-front foam and pixel dither. No sky/clouds/sun/horizon, no ocean bitmap stretched as the only scene. Uses framebuffer-native fragment shading on supported backends.
3. **Occupancy-only mask** remains 512×1024 (not a sea image). Every unoccupied pixel shows water; removing icons/widgets/dock removes the land and foam.
4. **Three shading tiers** persisted in optional schema-2 `renderQuality`: `balanced`, `high`, `ultra` (default). Kotlin Android wallpaper service ignores that optional Web rendering key; it is **not** a claim of Android 4K or parity.
5. **Fallback** uses the original tessellated mesh and is explicitly reported as reduced quality. Test only on actual browser/hardware for which the GPU is available; CI alone cannot certify dynamic appearance or frame pacing.

## 4K terminology

- **4K target output**: a browser/screen or high-resolution offscreen renderer must supply an appropriately sized framebuffer. `Ultra` alters the *detail model*, not the display's hardware pixel count.
- The original full raster concept is 941×1672 pixels; a 2160×3840 **resampled** reference is available in the conversation. Upscaling does not add authentic photographic information. The committed low-resolution color atlas is *not* advertised as a 4K ocean texture.
- Shader GPU executes at the surface output pixel density and adds separately computed detail at that resolution. Photographically plausible lighting is a design aim, not a measured result.
- Measure **real page pixel density**, FPS/frametime, browser fallback status, mobile heat and battery before claiming 4K quality/performance. Android native wallpaper is not upgraded by this Web-only quality tier.

## Acceptance gates

- Exact-head GitHub CI: Flutter analyze, tests including offline atlas decode and render-quality persistence, Android compile, Web compile.
- Main GitHub Pages deploy success on merged commit, and Web app shows Beach v0.4.0.
- Human visual inspection on actual high-resolution browser: depth, subtle caustics, small foam, no tiled/pixelated mesh, transition from water-only to new sand at marked icons, toggling ultra vs balanced.
- Physical Android and Play publishing remain deferred per user.

## Licenses and constraints

The earlier GPL-3.0 Deskland project is an **E1 visual reference only**; no third-party code, graphics, shaders or screenshots have been transferred into this implementation. The original generated water reference and all shader source are independently authored for this app. No Internet permission or network request is added.

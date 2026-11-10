# Beach v0.4.2 — Photographic ocean (Android experiment)

## Design decision

A full-frame **user-selected real photograph of open water** is the primary
color/detail source for Android Beach. The native EGL2 shader still operates at
the actual wallpaper framebuffer size, but keeps only a low-amplitude cyclic
UV deformation and soft shoreline effects. In photo mode, nearly all synthetic
caustics, noise, micro sparkle and forced color gradients are attenuated.

This is different from v0.4.1, which increased pixel density while still
inventing the visual texture through trigonometric fields. It is **not** a
built-in real 4K photo: user must select an authentic high-resolution image.

## Scope delivered in source

- On Android: **Choose ocean photograph** via system ACTION_OPEN_DOCUMENT.
- JPG/PNG or other decoder-supported images; <=12 MiB encoded, >=720px on
  each axis, <=45 MP original; decoded to <=2560px longest edge before a new
  app-private JPEG is committed.
- Strip EXIF/metadata on import, validate before replacement. Failed import
  leaves previously selected photo intact. No photo picker/media permission.
- Keep photographic file in `noBackupFilesDir`, exclude from Android automatic
  cloud backup; export/import of scene schema2 never includes the image.
- A single device-wide photo is used by all Beach profiles; other profiles'
  layout data stays independent. User may remove photo from editor at any time.
- Renderer loads photo once per file revision or EGL recreation; checks
  GL_MAX_TEXTURE_SIZE; uploads via GLUtils; handles image decode or device
  rejection without crashing wallpaper. Existing procedural native GPU renderer
  and 360px Canvas remain progressively lower-fidelity fallbacks.
- Status: `gpu_photo_native` vs `gpu_native` vs
  `cpu_360px_fallback`; the Android editor displays the actual last
  wallpaper backend after it has rendered.
- Native animation cadence still targets <=20fps while visible, <=8fps
  in battery saver. No background loop, no new permission or network.
- Existing user-selected Home screenshot calibration remains **temporary in
  memory**. It is distinct from the explicitly stored water photograph.

## Explicit limitations

- **No photograph is bundled.** To assess the new approach, first save a
  legitimately reusable real overhead/open-water photo on your device and
  choose it from Android's file picker. Example public-domain candidate:
  https://commons.wikimedia.org/wiki/File:Ocean_water_surface.jpg
  (check that its composition suits the desired top-down look).
- Web editor and panoramic Web preview remain the v0.4 procedural shader.
  They do not show the user-selected Android photograph. Web visuals cannot
  certify the native output; the editor informs the user.
- Android `WallpaperService` cannot read launcher app coordinates. Manually
  mark icons, folders, docks and widget zones via screenshot-assisted editor.
- This change does not prove actual 4K photographic source detail, realism,
  motion quality, native FPS, heat, battery draw or device compatibility.
- Neither upstream Deskland GPL source nor its graphic assets are included.

## Physical test matrix (not executed)

1. On Android v0.4.2 build, choose an overhead real photo (JPG/PNG).
   Confirm editor says photo stored and renderer later reads
   `gpu_photo_native` after the wallpaper has actually rendered.
2. With no manually marked icons/widgets/dock, photograph fills the whole
   wallpaper and **no new sand is generated**. Move from Home to another app
   and return; repeat after screen-off/on and reboot.
3. Mark a few Home icon positions and at least one widget; sand should appear
   **only** at those selected regions. Confirm photo remains photographic.
4. Compare frames 0/3/6/9/12 seconds for subtle motion and seamless loop,
   without conspicuous rolling mesh/caustic bands.
5. Replace the photo, remove it, attempt a corrupt/oversized/small image and
   confirm previous photo survives failed imports and fallback is visible.
6. Check screenshot-assisted calibration, all six locales, presets and scene
   preferences, no Storage/Internet/Accessibility prompt or data upload.
7. Check A25 30-minute run: FPS, frame-time, thermal, battery, crashes,
   texture memory on at least one actual device. Record exact APK SHA256,
   Android version, launcher, source commit and user visual PASS/FAIL.
8. Install/update in place from earlier package with higher versionCode,
   preserving schemas and existing profiles.

## Merge gate

**Stacked change, not independently mergeable into main while PR #13 is
pending.** PR v0.4.2 targets v0.4.1 source branch, and requires source/CI
verification plus physical screenshot/video and user visual approval. After
#13 is accepted/merged, rebase/retarget with exact SHA and re-run checks
without bypassing required gates. Neither PR is a Play release.

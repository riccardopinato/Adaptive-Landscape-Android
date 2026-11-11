# PC Deskland behavior → Android Auto Home parity

## Evidence and provenance

- User supplied these X video URLs as the intended visual reference:
  - https://x.com/Sn0wbrave/status/2119808831941160965/video/1
  - https://x.com/onmyway133/status/2119909125966667983/video/1
- **Playback of these specific X videos was unavailable** in the inspection
  environment. No frame-by-frame parity claim is supported by those URLs.
- Official project description: https://deskland.app/
- Free source README: https://github.com/BLEZZ0102/Deskland
- Official product behavior (verified): landscapes grow around desktop
  icons' coordinates, reshape smoothly when dragged/added/removed, stop
  animating behind full-screen apps, and do not upload file contents.
- The Windows source/assets carry **GPL-3.0**; no source code or asset from
  that repository is copied or linked into the independently developed
  Android app. Product behavior is a reference, not code-license permission.

## Behavioral mapping

| Desktop | Android target | Status on experimental v0.4.7 |
|---|---|---|
| Windows host sees desktop icon coordinates | Optional native Deskland Home owns its icon View coordinates | IMPLEMENTED SOURCE; DEVICE NOT VERIFIED |
| User drags icon; landscape grows/moves | Home drag publishes normalized zones, renderer smooth-blends previous/current **visible** sand mask over 380ms | IMPLEMENTED SOURCE; DEVICE NOT VERIFIED |
| User creates/removes desktop icon | Home app picker add/remove changes sand without manual marking | IMPLEMENTED SOURCE; DEVICE NOT VERIFIED |
| Coastline flows rather than jumps | GLES2 uses two RGBA occupancy textures and a smoothstep temporal morph; every new change starts from the current interpolated mask | IMPLEMENTED SOURCE; GPU TEST NOT RUN |
| Wallpaper remains behind icons | Transparent native Home Activity over WallpaperService | IMPLEMENTED SOURCE; DEVICE NOT VERIFIED |
| Background work pauses if desktop covered | WallpaperService stops scheduling frames when invisible | EXISTING CODE; PHYSICAL NOT VERIFIED |
| Original Windows icon layout recognized | Xiaomi/Samsung default launcher exposes no corresponding public Android API for third-party wallpaper | **NOT POSSIBLE** without owning launcher |
| Widgets, shortcuts, folders, work profiles | Android AppWidgetHost/launcher features need a separate roadmap | NOT IMPLEMENTED |
| Theme count/props equal to all paid Desktop themes | Native Android Beach/Meadow only. Authentic image props may be custom-designed later. | NOT IMPLEMENTED |

## Design principles for the native implementation

1. No user-marked zones inside Deskland's optional Home. Only actual tile
   positions drive `auto_home_config_v1`. Never infer another launcher's
   locations by screenshot/capture, overlay or Accessibility.
2. User must opt in via the Android **default Home** selection dialog.
   Home role can be undone in Android settings. The manual wallpaper editor
   remains available for people who do not want a launcher replacement.
3. Existing `config_v2`/profile files remain unchanged by the live Home;
   live mask contains only normalized geometry, not labels or app names.
4. Handle changes while moving with a bounded 90ms publisher. Coalesce
   updates; visually ease between buffered occupancy masks on GLES2.
5. Optimize for battery: shade only while wallpaper visible, do not poll the
   stock launcher, skip negligible far-away zone kernels, retain the
   12/16/20fps quality targets and 8fps battery-saver cap.
6. Stop-before-release gates: Kotlin/Flutter/CI build, Android system Home
   role consent and rollback, visual scene alignment in edge-to-edge layout,
   smooth dragging, add/remove, screenshot comparison, thermal and power.

## Physical acceptance (all pending)

- Empty Home: sea everywhere; zero islands without real icon tiles
- One icon: one soft coastline footprint at its *actual* view coordinate
- Move: smooth morph over multiple rendered frames, no teleport/no white flash
- Rapid dragging: coastline follows last position without repeated backward
  resets; idle position remains accurate after finger release
- Add/remove: island appears/disappears, with no stale residual after 1 s
- Switching default Home back to Xiaomi/Samsung: live icon zones ignored,
  manual profiles intact and stock Home unchanged
- Lock screen/other apps/battery saver: no unnecessary updates or active
  animation when wallpaper is not visible
- App interactions: tapping a tile opens the correct app; launcher must
  remain functional when file picker, status shade, system gesture, rotation
  or multiple desktops are used
- Test with Android device and exact APK SHA + manufacturer/Android version;
  **do not certify based on CI alone**

## Known release blockers

- Not a complete daily-driver launcher: multi-page Home, AppWidgetHost,
  folders, dock, badges, keyboard/TalkBack, work profiles, locale system
  integration, backup and full OEM gesture/navigation parity are pending.
- No migration of the **existing** Xiaomi/Samsung Home layout. Default
  layout in prototype is an automatic *new* layout, not captured data.
- Native GLES2 shader compiles on actual device, not validated by `flutter
  analyze` or Kotlin compilation alone. CPU fallback does not yet
  interpolate the beach mask and is **not equivalent**.
- The PC Deskland's artistic assets, animations and private/premium themes
  are not included and must not be described as present.

## Next requirements after proof-of-concept

- Improve the launcher feature set (widgets/dock/folders, account/work
  profiles, home pages) before proposing a daily-driver replacement.
- Design original thematic decorations and authentic optical water without
  importing GPL assets.
- Run real device visual QA using user-supplied videos as side-by-side
  acceptance once those clips are accessible.

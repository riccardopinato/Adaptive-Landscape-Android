# Android physical QA & release gate — v0.4 initial user failure, retest pending

User **resumed device testing** on v0.4 and reported a visually unacceptable sea (CPU ~360px raster visibly pixelated, near-static wave) and missing actual launcher alignment. Exact device model, installed APK SHA and OS/launcher not recorded, so this is a **reported failure, not a complete signed QA run**. The v0.4.1 experimental PR targets only a subset; do not mark it PASS until the user re-tests. Use this matrix for the follow-up. Record for each test: date, exact source commit, APK SHA-256, package+versionCode, Android version, manufacturer/model, launcher, battery saver state, result PASS/FAIL and screenshot/log where appropriate (never collect installed-app identities unnecessarily).

| ID | Scenario | Acceptance |
|---|---|---|
| A01 | Clean install debug APK | Opens without crash, correct app name/icon, no unnecessary permission prompt |
| A02 | Open Android live wallpaper picker | Shows own wallpaper preview and applies to Home |
| A03 | Meadow switch | Native wallpaper matches selected scene |
| A04 | Beach switch | Native wallpaper matches selected scene |
| A05 | Mark/clear 3×8 and 7×11 grid icons | Influence visible near selected occupied cells |
| A06 | Move/resize widget obstacle | Nearby clouds/hills/waves adapt without blank zones or crashes |
| A07 | Add many zones | 80-zone bound respected; no ANR |
| A08 | Multiple profiles | Names/layouts persist, switching applies selected profile |
| A09 | Export and reimport JSON | Matching schema-2 configuration restored offline |
| A10 | Corrupt JSON import | User gets error; wallpaper remains safe |
| A11 | Switch palette Original/Sunset/Mint | Expected colors appear in Android wallpaper |
| A12 | Night variant | Expected darker palette and stars |
| A13 | Motion 0, reduce motion | Native stops continuous frame scheduling |
| A14 | Battery saver | Target cap around 8 fps while visible; verify traces, not assumptions |
| A15 | Home hidden/another app opened | No continued frame loop, no wake locks |
| A16 | Screen off and unlock | Surface recovered, wallpaper visible |
| A17 | Rotate device / resize screen | No process crash, geometry remains bounded |
| A18 | Home pages and scrolling offsets | Parallax remains stable, no accidental jumps |
| A19 | Device reboot | Active theme/profile persists |
| A20 | App force-stop/process recreation | Preferences restore and wallpaper survives within Android rules |
| A21 | Android update via higher versionCode | User data persists |
| A22 | Small phone / tall phone / tablet | Touch targets, viewports and safe insets |
| A23 | TalkBack & system large font | All settings discoverable, controls usable |
| A24 | IT/EN/ES/FR/DE/PT | No missing strings, clipped controls or broken plural UI |
| A25 | 30-minute wallpaper stress | Observe FPS, frame pacing, memory, battery and device heat |
| A26 | Xiaomi/Redmi, Samsung, Pixel/OEM launcher | At least one device per family before broad compatibility claim |
| A27 | Offline/airplane mode | Wallpaper/editor work with no network |
| A28 | Play Internal Testing signed AAB | Store install + update-in-place succeeds |

Do not mark these PASS from GitHub Actions alone. Store release, donor certification, and commercial reliability depend on these facts.

## User issue report — v0.4 first Android install (10 November 2026)

- **A04 / A05 / A06 / A25: USER-REPORTED FAIL.** Beach rendered poorly (grain, heavy white outline), limited visible motion; sand appears only for manually configured 3-dock preset. Underlying code did not detect the actual Home icon/window grid.
- Device model, operating system, launcher and exact APK SHA: **not yet supplied**, therefore no false detailed hardware certification.
- v0.4.1 experimental acceptance: native renderer status reads `gpu_native` after reopening app, ocean surface detailed/moving and the coast softly feathered. Status `cpu_360px_fallback` means GPU initialization failed; record device/Android information and keep quality gate open.
- Screenshot-assisted calibration: user supplies their own Home screenshot using system picker; taps icon/folder positions and widget rectangles; checks current dock separately; only then can shoreline follow marked positions. Screenshot never automatically read, saved or transmitted; test portrait crop/navigation bars.
- **Auto-detection** of launcher icons is **not implemented**. Generic `WallpaperService` provides no icon coordinates. AccessibilityService would require separate opt-in and Google Play disclosure/consent; do not silently add it.
- Web GPU and Android EGL2 are distinct implementations; no pixel-equivalence guaranteed. Native 4K target is framebuffer shading, not a real 4K photographic texture.


## v0.4.2 photographic native Android gate (NOT RUN)

| ID | Scenario | Required evidence |
|---|---|---|
| P29 | Import overhead open-water photo | JPG/PNG accepted; status only changes after valid persisted import |
| P30 | Replace photo, cancel picker, import corrupt/oversize | Previous valid photo preserved; cancellation no-op; errors visible |
| P31 | Restart/force stop/reboot & no-backup file | Image preserved after lifecycle; absent in Android auto-backup |
| P32 | Renderer source | `gpu_photo_native` on actual rendered Home; no false photo claim on CPU/GPU fallback |
| P33 | Ocean-only / marked icons | No sand with empty layout; manual regions trigger soft coast |
| P34 | Motion & perceptual QA | 12-second loop, subtle waves/no visible procedural tiling |
| P35 | 30-minute test | FPS, frame time, memory, battery and temperature on hardware |
| P36 | 0.4.1 -> 0.4.2 in-place upgrade | Same app id and signature, higher versionCode, profiles preserved |

Web photo rendering and automatic launcher icon detection are **not part of
v0.4.2**. See [BEACH_PHOTOGRAPHIC_V042.md](BEACH_PHOTOGRAPHIC_V042.md).

# Android physical QA & release gate — not executed

Use this matrix **only when the user resumes device testing**. Record for each test: date, exact source commit, APK SHA-256, package+versionCode, Android version, manufacturer/model, launcher, battery saver state, result PASS/FAIL and screenshot/log where appropriate (never collect installed-app identities unnecessarily).

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

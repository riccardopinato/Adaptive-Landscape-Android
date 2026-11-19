# Deskland Windows → Android — REA intelligence pilot (source-only)

**2026-11-19 | SOURCE-OBSERVED | INSTALLER-BINARY BLOCKED | NO APP CODE CHANGED**

## Immutable study scope

| Item | Verified source | Evidence status |
|---|---|---|
| Windows upstream | BLEZZ0102/Deskland@9818b577402ff476e1b59da07970c56472713126 | SOURCE |
| Windows product rights | Upstream LICENSE GPL-3.0, NOTICE and TRADEMARKS | SOURCE |
| Android consumer | riccardopinato/Adaptive-Landscape-Android@2dcaf70a1d4962e9e81490d84df4f89f355cb7d4 | SOURCE |
| External research tool | morluto/rea@4491c84c82140a43fa12390b727840a433875ba8 (MIT, package v6.9.0) | DOCUMENTATION ONLY; NOT RUN |
| Original uploaded Deskland-Setup.exe | Previously attached to a different chat; its binary bytes are not available in this session | **BLOCKED / NO HASH** |
| Public upstream release v0.1.3 | GitHub release contains a 28,001,125-byte Deskland-Preview-0.1.3-win-x64-Setup.exe | METADATA ONLY; not the uploaded installer |

An earlier conversation described an NSIS Windows installer of about 260 MiB,
“Deskland Website Installer v0.1.31”. That statement is **historical context**,
not binary inspection here. Do not equate v0.1.31 with public preview v0.1.3,
infer content, fingerprints, or silently replace the supplied installer.

## Architecture and behavior visible from upstream SOURCE

1. **Host process and UI**: host/DesktopHost.cs and host/build.ps1 use C#
   .NET Framework with Microsoft.Web.WebView2 to display local HTML/JS themes.
   host/build.ps1 pins WebView2 SDK 1.0.4191.47 at source build time.
2. **Desktop attachment**: Windows host places a borderless WebView2 window
   behind the icon desktop layer; Progman/WorkerW handling depends on Windows
   version and host state. This is a *Windows-specific shell contract*.
3. **Icon positions**: original host reads Windows shell desktop ListView
   icon rectangles through cross-process messaging approximately every 400ms,
   sending updates only when changed. This is not an Android API.
4. **Lifecycle**: original host pauses animation on fullscreen/maximized
   foreground windows, supports tray pause/resume/exit, handles wallpaper layer
   reconstruction and restores original wallpaper on exit.
5. **Theme configuration**: prototype/themes.json declares meadow and beach
   as ready/free; other concepts appear with locked status. Source parameters
   include shore distance/warp, shallows and optional beach objects. Browser
   pages host settings, theme rendering and a local message protocol.
6. **Install-time behavior**: source names startup behavior, local settings,
   logs and single-instance semantics, but **the actual installer payload,
   file tree, registry mutations and embedded binary dependencies are UNKNOWN**
   until those bytes are inspected. No claims of observed Windows execution.

Evidence source URLs:
- https://github.com/BLEZZ0102/Deskland/blob/9818b577402ff476e1b59da07970c56472713126/host/DesktopHost.cs
- https://github.com/BLEZZ0102/Deskland/blob/9818b577402ff476e1b59da07970c56472713126/host/build.ps1
- https://github.com/BLEZZ0102/Deskland/blob/9818b577402ff476e1b59da07970c56472713126/host/README.md
- https://github.com/BLEZZ0102/Deskland/blob/9818b577402ff476e1b59da07970c56472713126/prototype/themes.json

## Installer static inventory — source recipe only, NOT extracted bytes

The *public preview* installer recipe is fully visible in:
- https://github.com/BLEZZ0102/Deskland/blob/9818b577402ff476e1b59da07970c56472713126/tools/installer.nsi
- https://github.com/BLEZZ0102/Deskland/blob/9818b577402ff476e1b59da07970c56472713126/tools/build-installer.ps1
- https://github.com/BLEZZ0102/Deskland/blob/9818b577402ff476e1b59da07970c56472713126/tools/version.json

| Source-declared property | Expected outcome **if built from these scripts** |
|---|---|
| Package format | NSIS 3.13, LZMA solid compression, x64 Windows 10+ |
| Install authority | Per-user, no elevation; $LOCALAPPDATA/Programs/Deskland Preview |
| Runtime engine | Deskland.exe + WebView2 Core/WinForms/Loader DLLs |
| Runtime prerequisite | Edge WebView2 Runtime; installer checks registry and can run a Microsoft-signed bootstrapper if absent, requiring network |
| Themes / bundled files | Beach and Meadow only as executable themes; eleven other preview cards remain locked; local HTML/JS and artwork |
| Source preservation | Deskland-source.zip deliberately bundled beside program assets, containing selected GPL source and notices |
| Windows integration | Start-menu launcher/uninstaller; HKCU installer and uninstall registry keys; optional autostart handled by host |
| Install/update/uninstall flow | Stop previously installed running instance, write Uninstall.exe, delete only scripted files/shortcuts and matching Run key |
| Release metadata | tools/version.json declares 0.1.3; build recipe writes a SHA-256 companion and per-file manifest |

**Evidence boundary:** all rows above are source-code-derived *expected behavior*
for the upstream v0.1.3 build recipe. None confirms that the old uploaded
Deskland-Setup.exe has the same version, structure, signature, files, hashes,
registry effects, installer runtime actions or network behavior. No execution,
binary extraction or installation performed.

## Android design decision matrix

| Windows capability | Android alternative | Feasibility / current evidence |
|---|---|---|
| Desktop shell wallpaper window | Native Android WallpaperService, Flutter settings editor | **IMPLEMENTED IN SOURCE**; physical lifecycle unverified |
| Exact position of desktop icons | Manual 3–8 × 3–12 grid + exclusion zones | **IMPLEMENTED**; system-wide automatic icon position access not supported by ordinary wallpaper APIs |
| Icons shaping clouds or coastline | Normalized Gaussian field, scene-2 contract, separate Dart/Kotlin rendering | **IMPLEMENTED** as independent algorithm; visual equivalence not proven |
| WebView2 HTML rendering | Android canvas/GL/native surface with original shaders | **ADAPTED**; no WebView2 port, no GPL JS/shader copies |
| Windows tray and Win+D recovery | Wallpaper visibility/surface lifecycle, Android settings flow | **PLATFORM-SPECIFIC REWRITE**; do not copy tray or Windows messages |
| Fullscreen energy pause | Android onVisibilityChanged, reduced motion, battery saver | **IMPLEMENTED BY CONTRACT**; actual OEM behavior NOT VERIFIED |
| Local settings / theme choice | SharedPreferences and normalized schema-2 settings bridge | **IMPLEMENTED BY CONTRACT**; reboot/update tests pending |
| Theme gallery and paid unlocks | Limited to currently approved meadow/beach; no unlock/billing without Product Bible | **OUT OF SCOPE** for locked upstream themes |

### Scope / provenance exclusions

- The app is an independent Flutter/Kotlin Android implementation, not a
  source-level or pixel-exact port. Upstream GPL-3.0 source and artwork are
  **not authorized to copy into this commercial-target repository** under
  its current independent-code policy.
- Do not recreate recognizably proprietary art, brand, logos, UI assets or
  protected source from a decompiler output.
- Do not use AccessibilityService, hidden launcher scraping, screenshots,
  background telemetry or sensitive permissions to bypass Android limits.
- Preserve schema-2, 6 locales, profile data and existing Beach visual scope.
- Findings here describe source; no APK build, binary inventory, installation,
  Windows runtime capture, user visual sign-off or physical device certification.

## Next evidence gate — Windows installer (separately authorized)

1. Obtain the **exact historic installer bytes** through a legitimate accessible
   file attachment or local on-device path; calculate SHA-256, bytes, PE architecture
   and static signature. No execution or installation required.
2. AppLab scripts/rea_artifact_intake.py provides a bounded offline identity
   receipt, requiring exact source and REA commit pins.
3. If identity warrants deeper study, run pinned REA read-only/appropriate
   tooling in an isolated environment; capture actual file sections, static
   dependencies, expected installed files/registry paths with limitations.
   No automatic network, install, debugger attach or CI upload.
4. Compare *new* binary findings to this source-only baseline. Count
   genuinely independent insights, not duplicated source facts; record
   excluded data and platform limitations.
5. Assess Android relevance against the existing source/QA and commercial
   rights. Propose only bounded independently authored changes and test
   rendered UI plus physical wallpaper lifecycle and battery effect.

**Pilot outcome to date:** 7 source-side architecture/packaging groups
and 8 Android feasibility decisions documented; **0 validated findings from
the unavailable installer; 0 copied code/assets; 0 Golden promotions**.

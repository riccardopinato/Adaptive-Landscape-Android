# Deskland Windows → Android — REA intelligence pilot (source + verified public binary)

**2026-11-19 | SOURCE + PUBLIC PREVIEW BINARY VERIFIED | HISTORICAL INSTALLER BLOCKED | NO APP CODE CHANGED**

## Verified real public installer and .NET host (incremental addendum)

- **Canonical report:** [AppLab public Preview real-binary dossier](https://github.com/riccardopinato/AppLab/blob/main/integration/intelligence/REA_DESKLAND_PREVIEW_BINARY_REPORT_2026-11-19.md); [REA CLI run #44615407360](https://github.com/riccardopinato/AppLab/actions/runs/44615407360) (repo branch/pending merge at this update).
- The official 28,001,125-byte `Deskland-Preview-0.1.3-win-x64-Setup.exe` has SHA-256 `e253cad56f1ea0ee2fb9d13ebe8a44f7dbe7232a506ba752cbe54c939c17bf44`, verified against the official checksum. Its **NSIS outer installer is x86 PE32** and imports 7 DLLs / 163 Windows functions. No PE embedded certificate security table.
- Static NSIS 7-Zip extraction (no execution) produced **48 physical files** and checked **all 43/43 published payload hashes**. Five extra installer stage files include Microsoft WebView2 bootstrapper and NSIS plugins; the actual runtime UI/side effects have not been observed.
- Extracted **`host/bin/Deskland.exe`**: SHA-256 `5337c513e8c14dc492900d315cd57e287e9dfb4ba750ccf96c2fb668fa4ea530` (matches manifest), 94,208 bytes, **PE32+ x86_64 managed IL-only**; `Deskland` assembly v0.1.3.0, CLR metadata `v4.0.30319`, 28 defined types, 200 managed methods and 40 P/Invoke `ImplMap` records. Seven assembly references include WebView2 Core + WinForms v1.0.4191.47.
- **Android decision:** no new transferable product capability was found beyond the source-only map below. Do not copy GPL code/assets, add Windows shell dependencies or alter current Android editor/wallpaper. Continue physical/visual wallpaper and battery QA separately.
- **Scope restriction:** the user's originally uploaded `Deskland-Setup.exe` remains inaccessible in this chat. This v0.1.3 preview is a **different binary**, not a substitute or a claim of analysing the historical upload.

## Immutable study scope

| Item | Verified source | Evidence status |
|---|---|---|
| Windows upstream | BLEZZ0102/Deskland@9818b577402ff476e1b59da07970c56472713126 | SOURCE |
| Windows product rights | Upstream LICENSE GPL-3.0, NOTICE and TRADEMARKS | SOURCE |
| Android consumer | riccardopinato/Adaptive-Landscape-Android@2dcaf70a1d4962e9e81490d84df4f89f355cb7d4 | SOURCE |
| External research tool | morluto/rea@4491c84c82140a43fa12390b727840a433875ba8 (MIT, package v6.9.0) | Real CLI inspection of **public** preview PASS; no MCP |
| Original uploaded Deskland-Setup.exe | Previously attached to a different chat; its binary bytes are not available in this session | **BLOCKED / NO HASH** |
| Public upstream release v0.1.3 | GitHub release contains a 28,001,125-byte Deskland-Preview-0.1.3-win-x64-Setup.exe | REAL STATIC BINARY + NSIS EXTRACTION VERIFIED, not the uploaded installer |

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

## Next evidence gate — historic Windows installer (separately authorized)

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

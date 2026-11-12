# Deskland v0.5.6 — Xiaomi/HyperOS native gesture compatibility

App Factory pinned baseline: `riccardopinato/App-Factory-Core@c5adc147046beb7024629466d10eeb799c3c2619` (CURRENT Master v27 / Golden Index v29).
Stacked parent v0.5.5+20: `bf8c7e09d477abcd8e75cf3738207aec64793257`, Draft PR #37.
This branch does not merge into main.

## Physical user problem

On a Xiaomi using gestures rather than navigation buttons, choosing Deskland
as the default Android launcher caused HyperOS to expose **three navigation
buttons** and disabled the native system gestures. This is user-observed
failure on their device, not an Android app bug that can be fixed by declaring
another permission.

Community documentation and upstream third-party launcher issues:
- Xiaomi HyperOS user guide: Settings > Home screen > System navigation > Gestures
  https://gl123.alsgp0.mi-fds.com/gl123/Generic%20User%20Guide%20for%20HyperOS%202/Generic%20User%20Guide%20for%20HyperOS%202.pdf
- Lawnchair issue #6065, closed not planned: HyperOS forces buttons on third-party launcher:
  https://github.com/LawnchairLauncher/lawnchair/issues/6065
- Google Android gesture navigation fundamentals:
  https://codelabs.developers.google.com/codelabs/gesture-navigation
- An independent developer explains the Xiaomi settings screen intent and why
  changing system overlay or secure settings needs privileges:
  https://github.com/h3nc4/HyperGesture/blob/main/docs/hyperos-navigation.md

Deskland as a regular unprivileged APK cannot force the OS to retain
privileged native gesture/recents integration while it owns the HOME role.
Do not make an accessibility/overlay replacement, silently install root hooks,
patch proprietary firmware, or hide navigation buttons without a viable
system-level Back/Home/Recents path.

## Shipping as source candidate — v0.5.6+21

The editor exposes a Xiaomi-specific, clearly explained opt-in compatibility
panel with **three independent system-mediated actions**:

1. Use the Android live wallpaper picker to set the existing Deskland
   WallpaperService. Android asks for confirmation.
2. Open Android's `Settings.ACTION_HOME_SETTINGS`. The user selects the
   original Xiaomi Home manually and can cancel freely.
3. Open Xiaomi's best-effort navigation settings screen
   `com.android.settings.NAVIGATION_MODE_SETTINGS`, falling back to generic
   Android Settings with a localized instruction. The user chooses Gestures
   manually. On OEM builds where this is disabled, no success is claimed.

The native Deskland Home long-press/empty-area management menu also offers a
localized "Keep system Xiaomi gestures" help dialog (Home settings + live
wallpaper). This menu is offered only on device families identified by
`Build.MANUFACTURER` (Xiaomi/Redmi/Poco/BlackShark).

Before requesting the native HOME role through Flutter on Xiaomi, a dialog
warns that HyperOS can switch to three buttons. The app offers to cancel or
continue with informed consent. All new strings are localized to EN/IT/ES/FR/DE/PT.

**No saved Deskland icons, folders, dock, widgets, `config_v2`, imported ocean
photo or wallpaper settings are deleted or migrated.** Home selection and
navigation changes are performed only in system Settings, by the user.
When Deskland no longer holds HOME, WallpaperService already uses the manual
`config_v2` source instead of pretending to read Xiaomi icon coordinates.

## Trade-off — non-negotiable UX truth

| Mode | Genuine Xiaomi system gestures | Automatically follow Home icon positions |
|---|---|---|
| Deskland Home as default | May be blocked by HyperOS (3 buttons) | Yes, positions from Deskland-owned views |
| Original Xiaomi Home + Deskland live wallpaper | Supported via Xiaomi settings if the ROM allows | No: only manual editor calibration/screenshot overlay |

Gesture-compatible wallpaper mode preserves Xiaomi's own Home, app drawer,
widgets, folders and system gesture/Recents integration but sacrifices the
automatic animated sand following independently owned icon views.

This build does **not** emulate global Home/Back/Recents with accessibility
or force OEM settings via Shizuku/root. The optional separate investigation
of these mechanisms needs its own security, privacy and device-compatibility
review and explicit user approval.

## CI and QA gates

- Flutter l10n generator, `flutter analyze`, widget/unit tests and Android
  debug + signed-for-internal-test ARM64 APK builds on exact PR SHA.
- Static source checks: native vendor warning, user-consent dialogs, platform
  bridge, app locale parity (6), no forbidden permissions and no shell overlay
  calls. Pure Xiaomi vendor-detection fixture.
- Android 35 emulator: existing Home/gesture, page swipe, drawer, folder,
  screenshot import/undo, process restart, system role restore and debug-only
  `deskland_qa_gesture_compat_v1` which checks only code wiring, that system
  HOME role did not change and that user layouts remain intact. Emulator is
  not Xiaomi/HyperOS and cannot certify native Xiaomi gesture behavior.

## Required REAL Xiaomi/HyperOS acceptance

1. Record model, HyperOS/Android version, current default launcher/navigation,
   exact APK SHA-256, version and signing certificate. Confirm wallpaper
   backup/profile export and never uninstall populated launcher for signature.
2. In editor choose first action: install Desktop live wallpaper via Android
   preview and confirm. Exit editor; manually choose original Xiaomi Home.
3. In Xiaomi Settings > Home screen > System navigation choose Gestures.
   Test OS-native Back (edge), Home (bottom), Recents (bottom swipe and hold)
   inside multiple apps, plus switching apps and returning to Xiaomi Home.
4. Confirm Beach still animates as wallpaper, manual icon calibration works,
   Deskland layout is not lost, original Xiaomi icons/widgets persist.
5. Switch back to Deskland Home by explicit system role selection. Verify
   the warning appears before consent, system gesture limitation remains
   truthfully reported and layout is recoverable. Restore original launcher
   after test.
6. Test cancellation of all three screens, no role changes without consent,
   offline mode, lock/unlock, Xiaomi Settings fallback on a different ROM.

This is only a **source/CI candidate**, not confirmation that full gestures
work with Deskland as the default launcher. Final physical QA and user
acceptance are a hard merge gate.

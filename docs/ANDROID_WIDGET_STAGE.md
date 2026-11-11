# Android Widget Host — exploratory implementation (PR #26)

Source baseline: Draft PR #24 @ b3283e5ce3ede38099f2b3e13a106775b05448e0, itself stacked on experimental PR #23.

## What source now does
- Shows Android AppWidgetManager-installed providers in an opt-in picker.
- Allocates a private AppWidgetHost ID and binds only where the OS allows; otherwise starts the system bind-consent flow.
- Starts widget configuration Activity when required; cancels/deletes IDs on explicit rejection or failure.
- Persists widget identity, starting cell and spans locally under `auto_home_widgets_v1`; no network.
- Displays AppWidgetHostView widgets on the active Home page, with a simple manager to move to the next page, enlarge, or delete.
- Sends just each visible widget rectangle as `kind: widget` in the native wallpaper's `auto_home_config_v1`. The wallpaper does NOT read widget content or user-private data.
- Existing manual editor and `config_v2` are not deleted or overwritten.

## Known missing/incomplete behaviors
1. Provider-specific configuration/restore and Home role variability have no physical evidence.
2. Widget settings can reject binding or require a dedicated configuration screen; the request sequence has NOT been exercised.
3. Widgets begin at a simple 2×2 cell size and can enlarge; minimum/maximum constraints and reliable drag-resize handles are NOT implemented.
4. Widget IDs are kept in local preferences; complete encrypted backup/restore and migration across reinstall are NOT implemented.
5. This stage does not support Android 15 private-space widgets, profile-scoped widget bind or hidden OEM features.
6. On-screen widgets may have internal touch gestures that must not be intercepted by the Home; physical confirmation required.
7. Folder tiles, pinned shortcuts and robust launcher parity remain separate.
8. More than ~80 active icon/widget regions would hit existing JSON renderer bounds; test and enforce on real layouts.
9. Widget placement currently uses a 5×8 page grid and normalized renderer geometry; test insets/rotation/tablets.

## Required test matrix
- **WT-01** list compatible providers, select one, cancel permission => no ghost widget/land.
- **WT-02** select provider, approve bind => widget visible, land surrounds exact bounds.
- **WT-03** choose provider requiring config => success, cancellation and process lifecycle.
- **WT-04** move widget to next page => no land on previous page, land at new page.
- **WT-05** resize widget => bounded view and matching land rectangle, provider receives size update.
- **WT-06** remove => release widget ID and erase coastline.
- **WT-07** kill/relaunch Home => widgets and pages restored without crashes.
- **WT-08** app update/OS reboot => widget identity intact, provider updates still visible.
- **WT-09** role revocation => no live Auto Home geometry leaking onto stock launcher.
- **WT-10** 0-motion (static scene) => coast moves immediately after widget changes.
- **WT-11** package uninstall or widget provider unavailable => safe blank/cleanup path.
- **WT-12** TalkBack, rotation, gesture navigation, battery saver and landscape tablet.
- **WT-13** native frame timings, CPU/GPU thermal during rapid widget relocation.
Each needs exact APK SHA-256, device model, Android API and observed result. All currently **NOT RUN**.

## License / security
- No upstream GPL source or binary assets imported.
- No ACCESSIBILITY_SERVICE, QUERY_ALL_PACKAGES, screenshots or network access.
- Android widget host binding requires explicit OS consent if not privileged.
- This is an **experimental launcher feature** and must not be shipped to production or described as proven until CI and device QA pass.

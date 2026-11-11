# Deskland real Home — local Android launcher, candidate v0.5.0
Factory baseline: riccardopinato/App-Factory-Core@c5adc147046beb7024629466d10eeb799c3c2619.
Product intent: a real user-curated Android HOME with actual installed app icons, folders, 4-slot dock, 3 pages and first-party dynamic sand wallpaper.
No reading OEM launcher-private layout. No Accessibility/overlay/QUERY_ALL_PACKAGES.
## Persistence
- auto_home_icons_v1: preserves v0.4.7 pinned app positions, including old auto-seeded layouts, without destructive migration.
- auto_home_folders_v1: versioned JSON array of {id,cell,name,members}, where members are real installed component IDs.
- auto_home_dock_v1: order of four real app component IDs; never inferred or automatically seeded.
- auto_home_widgets_v1: unchanged AppWidgetHost state.
Existing manual config_v2 and profiles remain untouched.
## Evidence boundaries
Source implementation is not user-device certification. Sand zones must include every on-screen real icon, folder, dock app and widget tile. The user must set Deskland as HOME and select its live wallpaper via separate Android confirmations.

# Photographic source provenance — proposed licensed candidates (not bundled)

The app's visual identity requires **actual aerial open-water imagery with no sky,
horizon, sun, mountains or permanent beach**, not generated noise pretending to
be a photograph.

## Sources inspected on 10 November 2026

1. [Ocean Water Background — Matt Moloney, Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Ocean_Water_Background.jpg).
   License CC0 1.0, as described on the official file page; original 4000x2667.
   The user should inspect the visual composition and crop before choosing it.
2. [Ocean Water — Lionello DelPiccolo, ISO Republic](https://isorepublic.com/photo/ocean-water/).
   Official source explicitly labels the image CC0 and commercial use.
   Original listed as 5910x3940; verify composition and file authenticity.
3. [Ocean Waves Background — Matt Bango, ISO Republic](https://isorepublic.com/photo/ocean-waves-background/).
   Official source explicitly labels the image CC0 and commercial use.
   Original listed as 8192x5464. Verify its composition because large
   white breaking waves may dominate the desired clear-turquoise mood.

## Operational rule

These links are **a sourcing shortlist**, not an offline bundled gallery.
At time of this change no third-party image bytes were committed, no
modified original was embedded, and no default photograph was claimed.
Before bundling any photo:
- Independently inspect the full image for overhead/open-water composition.
- Reconfirm author, source, license and commercial usage conditions;
  store license record, attribution, original link, SHA256 and date.
- Make a private-downsampled original photo rendition ~1536-2048 pixels,
  no EXIF, and size/performance check on APK. Keep visual approval gated.
- Avoid making a third-party image appear as proprietary or AI-generated
  artwork. Do not copy Deskland/other GPL application assets.

For testing now use Android native's **Choose ocean photograph** and Web
editor's **Choose water photo**. The file remains on device (Android) or
in browser memory (Web); the app makes no network request. This approach
also allows replacing a visually poor stock image without code updates.

## Known unresolved item

**Bundled offline real-photo presets not yet shipped.** This requires
an actual reviewed photo binary and a separate rights, composition, memory
and APK-size gate. Passing source or CI is not aesthetic approval.

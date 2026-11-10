# Release build and Google Play gate

## Available now

- CI debug APK: unoptimized development build; not a store binary.
- CI optimized per-ABI APK: `flutter build apk --release --split-per-abi` using the **default generated Flutter Android template signing setup**, which may use a debug keystore. **Internal QA only. It is NOT Play-signed and must not be uploaded as an approved commercial release.**
- Web editor: GitHub Pages on `main`, not Android wallpaper functionality.

## Required later (not done)

1. Freeze package ID `it.riccardopinato.adaptive_landscape` or explicitly confirm actual Gradle application ID from generated project and Play Console; never change an existing published package accidentally.
2. Generate/store a long-term private Android upload keystore **outside GitHub**, never commit keys or passwords. Use organization-approved secrets handling.
3. Configure Android release signing using that keystore; check release binaries independently with `apksigner` / `bundletool`.
4. Build signed AAB and verify Google Play target SDK, Google Play policy, icon, screenshots, Data Safety, privacy URL, correct country listing, app title and pricing at time of submission.
5. Complete `QA_MATRIX.md` with device evidence and exact SHA256, including install/update and permissions.
6. Upload to Play Internal Testing and record Google Play processing/result. Confirm a subsequent higher `versionCode` updates in place without data loss.
7. Only then open a production release PR and verify all independent gates. No hidden background task or automatic Store publication.

## Security/OSS

- No GPL-3.0 Deskland code/assets copied; review original art/commercial branding.
- Keep source and package provenance with per-release SHA and artifact hash.
- Do not add INTERNET/AccessibilityService/MediaProjection/QUERY_ALL_PACKAGES without a new written purpose and privacy review.

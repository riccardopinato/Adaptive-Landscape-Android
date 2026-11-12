#!/usr/bin/env python3
"""Offline static source hygiene checks; not a device privacy certification."""
import json
import pathlib
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
arbs = sorted((root / "lib/l10n").glob("app_*.arb"))
assert len(arbs) == 6, "Six supported locales required"
parsed = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in arbs}
keys = {k for k in parsed["app_en"] if not k.startswith("@")}
for name, values in parsed.items():
    actual = {k for k in values if not k.startswith("@")}
    assert actual == keys, f"Localization schema mismatch: {name}"
    assert all(isinstance(values[k], str) and values[k].strip() for k in keys), f"Missing {name} translation"
manifest = root / "android/app/src/main/AndroidManifest.xml"
xml = ET.parse(manifest).getroot()
android = "{http://schemas.android.com/apk/res/android}"
for tag in xml.findall("uses-permission"):
    name = tag.attrib.get(android + "name", "")
    assert name not in {
        "android.permission.INTERNET",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.PACKAGE_USAGE_STATS",
    }, f"Forbidden unexpected permission: {name}"
source = "\n".join(p.read_text(encoding="utf-8") for p in (root / "lib").rglob("*.dart"))
assert "adaptive_landscape/wallpaper" in source
native = (root / "android/app/src/main/kotlin/it/riccardopinato/adaptivelandscape/MainActivity.kt").read_text()
assert "adaptive_landscape/wallpaper" in native
assert "config_v2" in native
print(f"PASS: {len(arbs)} locale files, {len(keys)} keys, no prohibited manifest permissions, Android channel contract")

# v0.3.2: verify ocean shader asset is included in both bootstrap scripts.
shader = root / 'shaders/beach_ocean.frag'
assert shader.is_file() and 'FlutterFragCoord' in shader.read_text(encoding='utf-8')
for script in ['BOOTSTRAP_LINUX_MAC.sh', 'BOOTSTRAP_WINDOWS.ps1']:
    assert 'shaders' in (root / script).read_text(encoding='utf-8'), f'Missing shader bootstrap in {script}'
assert 'shaders/beach_ocean.frag' in (root / 'pubspec.yaml').read_text(encoding='utf-8')
print('PASS: pixel-shaded Beach asset declared and both bootstraps include shader')

# Beach v0.4.0: ensure independent tonal atlas and shader sampler are present.
assert (root / 'lib/core/ocean_tone_atlas.dart').is_file()
shader_source = (root / 'shaders/beach_ocean.frag').read_text(encoding='utf-8')
assert 'uniform sampler2D uToneAtlas;' in shader_source
assert 'uniform float uQuality;' in shader_source
assert 'renderQuality' in (root / 'lib/core/landscape_config.dart').read_text(encoding='utf-8')
print('PASS: Web 4K art-direction atlas and runtime-effect uniforms')

# Native v0.4.1 P0: native wallpaper must try framebuffer GPU rendering.
base = root / 'android/app/src/main/kotlin/it/riccardopinato/adaptivelandscape'
engine = (base / 'AdaptiveWallpaperService.kt').read_text(encoding='utf-8')
gpu = (base / 'GpuOceanRenderer.kt').read_text(encoding='utf-8')
activity = (base / 'MainActivity.kt').read_text(encoding='utf-8')
assert 'gpuOcean.render(' in engine and 'EGL14.eglSwapBuffers' in gpu
assert 'gl_FragCoord' in gpu and 'uResolution' in gpu
assert 'render_backend_v1' in engine and 'renderQuality' in (base / 'WallpaperState.kt').read_text(encoding='utf-8')
assert 'ACTION_OPEN_DOCUMENT' in activity and 'pickHomeScreenshot' in activity
assert 'AccessibilityService' not in (root / 'android/app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
assert 'SYSTEM_ALERT_WINDOW' not in (root / 'android/app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
print('PASS: native EGL framebuffer renderer, privacy-safe manual screenshot calibration contract')

# v0.4.2: offline photographic source and controlled native renderer switching.
photo = (base / 'OceanPhotoStore.kt').read_text(encoding='utf-8')
assert 'MAX_INPUT_BYTES' in photo and 'MAX_DECODED_PIXELS' in photo
assert 'inJustDecodeBounds' in photo and 'FileOutputStream(candidate)' in photo
assert 'ACTION_OPEN_DOCUMENT' in activity and 'pickOceanPhoto' in activity
assert 'clearOceanPhoto' in activity and 'OceanPhotoStore.exists' in activity
assert 'ocean_photo_generation_v1' in engine and 'gpu_photo_native' in engine
assert 'uniform sampler2D uOceanPhoto;' in gpu and 'GLUtils.texImage2D' in gpu
assert 'photographActive' in gpu and 'photoDrift' in gpu
channel = (root / 'lib/platform/wallpaper_channel.dart').read_text(encoding='utf-8')
assert 'pickOceanPhoto' in channel and 'clearOceanPhoto' in channel
dart = (root / 'lib/main.dart').read_text(encoding='utf-8')
assert 'al-import-ocean-photo' in dart and 'al-ocean-photo-status' in dart
# Since the v0.4.2 source persists in stacked later releases, verify minimum.
version_line = next((line.strip() for line in
    (root / 'pubspec.yaml').read_text(encoding='utf-8').splitlines()
    if line.startswith('version:')), '')
assert version_line.startswith(('version: 0.4.', 'version: 0.5.'))
parts = version_line.split(':', 1)[1].strip().split('+')
release = tuple(int(part) for part in parts[0].split('.'))
assert len(parts) == 2 and release >= (0, 4, 2) and int(parts[1]) >= 9
print('PASS: v0.4.2 local photo import, metadata stripping, GPU texture and UI contract')

# v0.4.3: photo preview uses local image sampler and is explicitly ephemeral.
web = (root / 'lib/ui/beach_gpu_surface.dart').read_text(encoding='utf-8')
frag = (root / 'shaders/beach_ocean.frag').read_text(encoding='utf-8')
editor = (root / 'lib/main.dart').read_text(encoding='utf-8')
assert 'uniform sampler2D uPhotograph;' in frag
assert 'uniform float uUsePhoto;' in frag and 'uPhotoAspect' in frag
assert 'setImageSampler(2, photograph ?? _toneAtlas!' in web
assert 'FilePicker.pickFile(type: FileType.image)' in editor
assert 'al-web-before-after' in editor and '_clearWebOceanPhoto' in editor
assert release >= (0, 4, 3) and int(parts[1]) >= 10
print('PASS: v0.4.3 local Web photographic preview and comparison source contract')

# v0.4.4: optical tuning is optional in schema2 and bound on native/Web.
cfg = (root / 'lib/core/landscape_config.dart').read_text(encoding='utf-8')
native_state = (base / 'WallpaperState.kt').read_text(encoding='utf-8')
assert "'photoRefraction': photoRefraction" in cfg and "'foamIntensity': foamIntensity" in cfg
assert 'photoRefraction = number(json' in native_state and 'foamIntensity = number(json' in native_state
assert 'uRefraction' in frag and 'uFoamIntensity' in frag
assert 'uRefraction' in gpu and 'uFoamIntensity' in gpu
assert 'al-photo-refraction' in editor and 'al-foam-intensity' in editor
print('PASS: v0.4.4 schema2 optional optics controls and photorealistic foam source gates')

# v0.4.5: screenshot zoom + horizontal/vertical insets share the same geometry.
cfg = (root / 'lib/core/landscape_config.dart').read_text(encoding='utf-8')
editor = (root / 'lib/main.dart').read_text(encoding='utf-8')
field = (root / 'lib/core/icon_field.dart').read_text(encoding='utf-8')
mask = (root / 'lib/core/beach_mask.dart').read_text(encoding='utf-8')
assert 'gridInset' in cfg and 'gridInset' in native_state
assert 'field.gridInset' in mask and 'gridInset' in field
assert 'InteractiveViewer(' in editor and 'al-precision-calibration' in editor
assert 'al-calibrate-top' in editor and 'al-calibrate-side' in editor
assert release >= (0, 4, 5) and int(parts[1]) >= 12
print('PASS: v0.4.5 normalized Home insets and manual zoom calibration')

# v0.4.6: native photo path skips procedural shading, quality controls image memory and pacing.
gpu = (base / 'GpuOceanRenderer.kt').read_text(encoding='utf-8')
engine = (base / 'AdaptiveWallpaperService.kt').read_text(encoding='utf-8')
assert 'if (uHasPhoto < 0.5)' in gpu
assert '"balanced" -> 1024' in gpu and '"high" -> 1536' in gpu
assert 'quality.hashCode().toLong()' in gpu
assert '"balanced" -> 83L' in engine and '"high" -> 63L' in engine
assert 'maxOf(125L, target)' in engine
assert '_framesPerPreviewCycle' in (root / 'lib/main.dart').read_text(encoding='utf-8')
assert release >= (0, 4, 6) and int(parts[1]) >= 13
print('PASS: v0.4.6 photo fast path, tiered GPU texture budget and pacing targets')

# Prephysical QA handoff: source capture is never a physical PASS.
qa = (root / 'lib/core/qa_snapshot.dart').read_text(encoding='utf-8')
editor = (root / 'lib/main.dart').read_text(encoding='utf-8')
assert 'UNTESTED_PHYSICAL' in qa and 'manualMarkedIcons' in qa
assert 'al-copy-qa-snapshot' in editor
for p in ('physical_qa_capture.sh', 'physical_qa_capture.ps1'):
    assert (root / 'scripts' / p).is_file()
assert (root / 'docs/PHYSICAL_QA_V042_V046.md').is_file()
assert 'physical_qa_local/' in (root / '.gitignore').read_text(encoding='utf-8')
print('PASS: prephysical QA evidence workflow exists, hardware results remain pending')

# Universal APK delivery requires a separately validated all-ABI package.
ci = (root / '.github/workflows/development.yml').read_text(encoding='utf-8')
assert 'flutter build apk --release --dart-define=BUILD_SOURCE_SHA=' in ci
assert 'adaptive-landscape-universal-internal-NOT-PLAY-SIGNED' in ci
assert 'scripts/verify_universal_apk.sh' in ci
assert 'chore/v050-prephysical-qa' in ci, 'CI must include this PR target branch'
assert (root / 'scripts/verify_universal_apk.sh').is_file()
assert "armeabi-v7a arm64-v8a x86_64" in (root / 'scripts/verify_universal_apk.sh').read_text(encoding='utf-8')
print('PASS: Android universal QA artifact with ABI and SDK metadata gate')

# Experimental Auto Home: no accessibility service and no automatic role grant.
home = (base / 'AdaptiveHomeActivity.kt').read_text(encoding='utf-8')
wallpaper = (base / 'AdaptiveWallpaperService.kt').read_text(encoding='utf-8')
native_bridge = (base / 'MainActivity.kt').read_text(encoding='utf-8')
manifest_source = manifest.read_text(encoding='utf-8')
assert 'requestAdaptiveHome' in native_bridge
assert 'RoleManager.ROLE_HOME' in native_bridge
assert 'AdaptiveHomeActivity' in manifest_source
assert 'android.intent.category.HOME' in manifest_source
assert 'AccessibilityService' not in manifest_source
assert 'auto_home_config_v1' in home and 'auto_home_config_v1' in wallpaper
assert 'auto_home_config_v1' in (base / 'WallpaperState.kt').read_text(encoding='utf-8')
assert 'auto_home_icons_v1' in home and 'requestPublish()' in home
assert 'gridInset' in (root / 'lib/core/landscape_config.dart').read_text(encoding='utf-8')
for lang in ('values', 'values-it', 'values-es', 'values-fr', 'values-de', 'values-pt'):
    assert (root / 'android/app/src/main/res' / lang / 'auto_home_strings.xml').is_file()
assert '0.5.2+17' in (root / 'pubspec.yaml').read_text(encoding='utf-8')
print('PASS: experimental opt-in native Auto Home source; NOT physical tested')

# Desktop-behavior parity requires a temporal transition, not instantaneous
# mesh/raster jumps. This is source inspection, NOT a GLES device runtime test.
gpu = (base / 'GpuOceanRenderer.kt').read_text(encoding='utf-8')
home = (base / 'AdaptiveHomeActivity.kt').read_text(encoding='utf-8')
assert 'uMaskPrevious' in gpu and 'uMaskMix' in gpu
assert 'blendCurrentMask' in gpu and 'maskMorphDurationMs' in gpu
assert 'maskUploadScratch' in gpu and 'glTexSubImage2D' in gpu
assert 'maskFromHasLand && mixAmount < 1f' in gpu
assert 'publishHomePositions()' in home
assert 'private fun pinAppToCurrentPage(' in home and 'firstOrNull { freeCell(it) }' in home
assert (root / 'test/auto_home_zone_contract_test.dart').is_file()
assert (root / 'docs/PC_TO_MOBILE_BEHAVIOR_PARITY.md').is_file()
print('PASS: auto Home dual-mask smooth interpolation and drag publisher source gates')

# v0.5.1 P0: wallpaper engine callbacks must never do GLES/Canvas render
# on Android's main thread. Shader upload must reuse fixed image storage.
engine = (base / 'AdaptiveWallpaperService.kt').read_text(encoding='utf-8')
gpu = (base / 'GpuOceanRenderer.kt').read_text(encoding='utf-8')
assert 'HandlerThread(' in engine and 'renderHandler.post(frame)' in engine
assert 'renderThread.quitSafely()' in engine
assert 'private fun drawFrame()' in engine
assert 'if (visible) drawFrame()' not in engine, 'Do not render in UI Engine callbacks'
assert 'renderHandler.post { gpuOcean.resetSurface() }' in engine
assert 'GLES20.glTexSubImage2D' in gpu
assert 'maskUploadScratch.clear()' in gpu
assert 'ByteBuffer.allocateDirect(bytes.size)' not in gpu
print('PASS: v0.5.1 serialized wallpaper renderer thread and reusable coast GPU storage')

# v0.5.2: ordinary Home must be free of full-screen diagnostic controls.
# First-run placement is local-only and never claims OEM position access.
home = (base / 'AdaptiveHomeActivity.kt').read_text(encoding='utf-8')
assert 'showHomeMenu()' in home and 'showAppDrawer()' in home
assert 'GestureDetector(' in home and 'onLongPress(e: MotionEvent)' in home
assert 'fillHomeFromInstalledApps(onlyIfEmpty = true)' in home
assert 'auto_home_auto_arranged_v1' in home
assert 'renderPageDots()' in home and 'renderChrome()' not in home
assert 'auto_home_fill_explanation' in home
for lang in ('values', 'values-it', 'values-es', 'values-fr', 'values-de', 'values-pt'):
    strings = (root / 'android/app/src/main/res' / lang / 'auto_home_strings.xml').read_text(encoding='utf-8')
    assert 'name="auto_home_fill"' in strings
    assert 'name="auto_home_settings"' in strings
print('PASS: v0.5.2 clean wallpaper Home, gesture controls and privacy-safe auto app fill')

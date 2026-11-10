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

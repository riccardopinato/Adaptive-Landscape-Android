#!/usr/bin/env bash
# Usage: bash BOOTSTRAP_LINUX_MAC.sh [destination]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
DEST="${1:-$ROOT/generated_app}"
if [ -e "$DEST" ]; then echo "Refusing to overwrite $DEST" >&2; exit 1; fi
flutter --version
flutter create --platforms=android,web --template=app --org it.riccardopinato --project-name adaptive_landscape "$DEST"
for path in pubspec.yaml l10n.yaml lib test shaders android/app/src/main/AndroidManifest.xml android/app/src/main/res/xml android/app/src/main/res/values/strings.xml android/app/src/main/res/drawable/ic_landscape.xml android/app/src/main/kotlin/it/riccardopinato/adaptivelandscape; do
  if [ -d "$ROOT/$path" ]; then
    mkdir -p "$DEST/$path"
    cp -R "$ROOT/$path/." "$DEST/$path/"
  else
    mkdir -p "$(dirname "$DEST/$path")"
    cp "$ROOT/$path" "$DEST/$path"
  fi
done
rm -f "$DEST/test/widget_test.dart" # remove only Flutter-generated counter demo test
cd "$DEST"
flutter pub get
flutter gen-l10n
flutter analyze
flutter test
flutter build apk --debug --dart-define=BUILD_SOURCE_SHA="${GITHUB_SHA:-manual-local}"
echo "Debug APK: $DEST/build/app/outputs/flutter-apk/app-debug.apk"

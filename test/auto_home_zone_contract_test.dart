import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/beach_mask.dart';
import 'package:adaptive_landscape/core/icon_field.dart';
import 'package:adaptive_landscape/core/landscape_config.dart';

void main() {
  test('A live Home with no actual icon views produces pure ocean', () {
    const autoHome = LandscapeConfig(
      scene: 'beach', occupied: {}, zones: []);
    final field = IconField.fromConfig(autoHome);
    expect(BeachMask.isEmpty(field), isTrue);
    expect(field.influence(0.5, 0.5), 0);
  });

  test('Actual icon rectangles determine land without marked grid cells', () {
    const autoHome = LandscapeConfig(
      scene: 'beach', occupied: {},
      zones: [
        LayoutZone(x: 0.25, y: 0.28, width: 0.11,
          height: 0.08, kind: 'icon'),
      ],
    );
    final field = IconField.fromConfig(autoHome);
    expect(field.influence(0.25, 0.28), closeTo(1, 0.000001));
    expect(field.influence(0.95, 0.95), lessThan(0.01));
    expect(BeachMask.isEmpty(field), isFalse);
  });

  test('Moving an icon changes the land only through live scene data', () {
    const before = LandscapeConfig(
      scene: 'beach', occupied: {},
      zones: [LayoutZone(x: 0.2, y: 0.3, width: 0.1,
        height: 0.08, kind: 'icon')],
    );
    const after = LandscapeConfig(
      scene: 'beach', occupied: {},
      zones: [LayoutZone(x: 0.8, y: 0.7, width: 0.1,
        height: 0.08, kind: 'icon')],
    );
    final initial = IconField.fromConfig(before);
    final moved = IconField.fromConfig(after);
    expect(initial.influence(0.2, 0.3), greaterThan(0.99));
    expect(moved.influence(0.2, 0.3), lessThan(0.01));
    expect(moved.influence(0.8, 0.7), greaterThan(0.99));
    expect(before.occupied, isEmpty);
    expect(after.occupied, isEmpty);
  });

  test('Auto Home can serialize its bounded normalized zones in schema2', () {
    const state = LandscapeConfig(
      scene: 'beach', occupied: {},
      zones: [LayoutZone(x: 0.4, y: 0.6, width: 0.1,
        height: 0.09, kind: 'icon')],
    );
    final copy = LandscapeConfig.decode(state.encode());
    expect(copy.scene, 'beach');
    expect(copy.zones, hasLength(1));
    expect(copy.zones.single.kind, 'icon');
    expect(copy.occupied, isEmpty);
  });
}

import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/beach_mask.dart';
import 'package:adaptive_landscape/core/icon_field.dart';
import 'package:adaptive_landscape/core/landscape_config.dart';
import 'package:adaptive_landscape/core/beach_coast.dart';

void main() {
  test('Empty Home and empty dock produce exact zero-sand occupancy mask', () {
    const field = IconField(occupied: {});
    final data = BeachMask.bake(field, width: 24, height: 48);
    for (var i = 0; i < data.length; i += 4) {
      expect(data[i], 0);
      expect(data[i + 3], 255);
    }
    expect(BeachCoast(field).coast(0.03, 0.42), lessThan(0));
  });

  test('One icon creates land at its center without making full-screen sand', () {
    final field = IconField.fromConfig(const LandscapeConfig(occupied: {12}));
    final data = BeachMask.bake(field, width: 100, height: 160);
    final center = const LandscapeConfig().cellCenter(12);
    final cx = (center.$1 * 100).floor();
    final cy = (center.$2 * 160).floor();
    expect(data[(cy * 100 + cx) * 4], greaterThan(200));
    expect(data[0], 0);
    expect(BeachCoast(field).coast(center.$1, center.$2), greaterThan(0));
  });

  test('Three and four dock apps become three/four distinct bottom sand seeds', () {
    for (final n in [3, 4]) {
      final zones = DockLayout.withCount([], n);
      expect(DockLayout.count(zones), n);
      expect(zones.every((z) => z.kind == 'dock' && z.y > 0.9), true);
      final field = IconField(occupied: {}, zones: zones);
      for (final z in zones) {
        expect(BeachCoast(field).coast(z.x, z.y), greaterThan(0));
      }
    }
    expect(DockLayout.withCount(const [], 0), isEmpty);
  });

  test('Changing dock occupancy keeps widgets and icon cells untouched', () {
    const widget = LayoutZone(x: 0.2, y: 0.35, width: 0.2, height: 0.1, kind: 'widget');
    var zones = DockLayout.withCount([widget], 3);
    zones = DockLayout.withCount(zones, 4);
    expect(zones.first.kind, 'widget');
    expect(DockLayout.count(zones), 4);
    zones = DockLayout.withCount(zones, 0);
    expect(zones, [widget]);
  });

  test('Dynamic grid cache key retains selected cells above 40', () {
    final field = IconField.fromConfig(const LandscapeConfig(
      columns: 8, rows: 12, occupied: {80, 95}));
    expect(field.encodeOccupied(), '80,95');
    expect(field.influence(0.06, 0.82), greaterThanOrEqualTo(0));
  });

  test('Dock cannot exceed 80-zone migration contract', () {
    final widgets = List.generate(77, (i) =>
        const LayoutZone(x: 0.5, y: 0.5, width: 0.1, height: 0.1, kind: 'widget'));
    expect(() => DockLayout.withCount(widgets, 4), throwsArgumentError);
    expect(DockLayout.withCount(widgets, 3).length, 80);
  });

  test('Baked texture agrees with normalized influence at centers', () {
    const config = LandscapeConfig(occupied: {0, 7}, zones: [
      LayoutZone(x: 0.7, y: 0.7, width: 0.2, height: 0.12, kind: 'widget')
    ]);
    final field = IconField.fromConfig(config);
    const w = 256, h = 512;
    final mask = BeachMask.bake(field, width: w, height: h);
    for (final pt in [(0.1, 0.16), (0.5, 0.5), (0.7, 0.7), (0.9, 0.9)]) {
      final px = (pt.$1 * w).floor();
      final py = (pt.$2 * h).floor();
      final baked = mask[(py * w + px) * 4] / 255;
      final direct = field.influence((px + 0.5) / w, (py + 0.5) / h);
      expect(baked, closeTo(direct, 0.009));
    }
  });
}

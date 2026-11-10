import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/landscape_config.dart';

void main() {
  test('V2 encoded contract roundtrip preserves geometry and settings', () {
    final original = LandscapeConfig(
      scene: 'beach', columns: 6, rows: 9, top: 0.1, span: 0.78,
      occupied: {0, 7, 53}, zones: const [LayoutZone(x: 0.5, y: 0.8, width: 0.3, height: 0.12, kind: 'dock')],
      palette: 'sunset', night: true, motion: 0.5, reduceMotion: true,
    );
    final copy = LandscapeConfig.decode(original.encode());
    expect(copy.toJson(), original.toJson());
    expect(copy.cellCenter(53).$1, greaterThan(0));
  });
  test('Invalid configuration cannot escape normalized geometry', () {
    final c = LandscapeConfig.fromJson({
      'scene': 'unknown', 'columns': 100, 'rows': 0, 'top': -3, 'span': 44,
      'occupied': [-1, 0, 9999], 'zones': [
        {'x': -5, 'y': 3, 'width': 100, 'height': -3, 'kind': 'other'},
      ],
      'motion': 44,
    });
    expect(c.columns, 8);
    expect(c.rows, 3);
    expect(c.occupied, {0});
    expect(c.top, 0);
    expect(c.span, 1);
    expect(c.zones.single.x, 0);
    expect(c.zones.single.kind, 'icon');
    expect(c.motion, 2);
    expect(c.scene, 'meadow');
  });
  test('Broken JSON falls back without throwing', () {
    expect(LandscapeConfig.decode('{broken').scene, 'meadow');
    expect(LandscapeConfig.decode('null').columns, 5);
  });
  test('Legacy comma-list is migrated to v2 occupancy', () {
    final c = LandscapeConfig.fromJson({'scene': 'beach', 'occupied': '0,7,39,999,abc'});
    expect(c.scene, 'beach');
    expect(c.occupied, {0, 7, 39});
  });
  test('Rectangle edges stay in normalized coordinates after import/resize', () {
    final z = LayoutZone.fromJson({'x': 0.99, 'y': 0.01, 'width': 0.3, 'height': 0.4});
    expect(z.x + z.width / 2, lessThanOrEqualTo(1));
    expect(z.y - z.height / 2, greaterThanOrEqualTo(0));
    final r = z.resize(0.5, 0.5);
    expect(r.x + r.width / 2, lessThanOrEqualTo(1));
    expect(r.y + r.height / 2, lessThanOrEqualTo(1));
  });
  test('Zones can move and resize within safe geometry', () {
    const z = LayoutZone(x: 0.5, y: 0.5, width: 0.2, height: 0.1);
    final moved = z.move(7, -7);
    expect(moved.x, lessThanOrEqualTo(0.9));
    expect(moved.y, greaterThanOrEqualTo(0.05));
    expect(z.resize(7, -7).height, 0.03);
  });
  test('Profiles are self-contained and redact launcher identity', () {
    const p = NamedLayout(id: 'home', name: 'Home', config: LandscapeConfig());
    final exported = p.encode();
    expect(NamedLayout.fromJson(jsonDecode(exported)).name, 'Home');
    expect(exported, isNot(contains('packageName')));
  });
}

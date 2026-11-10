import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/landscape_config.dart';
import 'package:adaptive_landscape/core/icon_field.dart';
import 'package:adaptive_landscape/core/beach_mask.dart';

void main() {
  test('Symmetric side margin preserves normalized center and layout schema2', () {
    const config = LandscapeConfig(scene: 'beach', gridInset: 0.12,
        columns: 5, rows: 8, occupied: {0, 4});
    final first = config.cellCenter(0);
    final last = config.cellCenter(4);
    expect(first.$1, greaterThan(0.12));
    expect(last.$1, lessThan(0.88));
    expect(first.$1 + last.$1, closeTo(1, 0.00001));
    final copy = LandscapeConfig.decode(config.encode());
    expect(copy.gridInset, 0.12);
    expect(copy.occupied, {0, 4});
  });

  test('Legacy grid has no inset and is still centered', () {
    final config = LandscapeConfig.fromJson({'schema': 2, 'scene':'beach'});
    expect(config.gridInset, 0);
    expect(config.cellCenter(0).$1, closeTo(0.1, 0.00001));
  });

  test('Grid inset changes actual sand mask footprint', () {
    const left = IconField(occupied: {0}, gridColumns: 5, gridRows: 8);
    const inset = IconField(occupied: {0}, gridColumns: 5, gridRows: 8,
      gridInset: 0.18);
    final a = BeachMask.bake(left, width: 80, height: 120);
    final b = BeachMask.bake(inset, width: 80, height: 120);
    expect(a, isNot(equals(b)));
  });

  test('Imported horizontal grid inset is clamped to safe limits', () {
    expect(LandscapeConfig.fromJson({'gridInset': -1}).gridInset, 0);
    expect(LandscapeConfig.fromJson({'gridInset': 3}).gridInset, 0.2);
  });
}

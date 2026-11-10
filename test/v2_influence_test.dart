import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/icon_field.dart';
import 'package:adaptive_landscape/core/landscape_config.dart';

void main() {
  test('Variable grid positions remain bounded', () {
    const c = LandscapeConfig(columns: 7, rows: 11, top: 0.1, span: 0.8, occupied: {76});
    final (x, y) = c.cellCenter(76);
    expect(x, inInclusiveRange(0, 1));
    expect(y, inInclusiveRange(0, 1));
    expect(IconField.fromConfig(c).influence(x, y), closeTo(1, 1e-6));
  });
  test('Rectangle zone clears an entire region, not only center', () {
    const c = LandscapeConfig(zones: [
      LayoutZone(x: 0.5, y: 0.7, width: 0.35, height: 0.2, kind: 'widget'),
    ]);
    final f = IconField.fromConfig(c);
    expect(f.obstructed(0.5, 0.7), true);
    expect(f.obstructed(0.65, 0.7), true);
    expect(f.obstructed(0.02, 0.1), false);
  });
  test('Original empty scenery has no obstacles', () {
    const f = IconField(occupied: {});
    expect(f.influence(0.5, 0.5), 0);
  });
}

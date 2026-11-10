import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/icon_field.dart';

void main() {
  test('Grid is independent of launcher package/label information', () {
    const layout = IconField(occupied: {0, 4, 17, 38});
    expect(layout.occupied.length, 4);
    expect(IconField.decode(IconField.encode(layout.occupied)), layout.occupied);
  });
  test('Geometry cannot read beyond the grid', () {
    expect(() => IconField.centerFor(-1), throwsRangeError);
    expect(() => IconField.centerFor(40), throwsRangeError);
  });
}

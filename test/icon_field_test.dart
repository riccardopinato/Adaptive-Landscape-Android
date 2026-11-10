import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/icon_field.dart';

void main() {
  test('Encode/decode stable, sorted, validated', () {
    expect(IconField.encode({7, 2, -1, 99}), '2,7');
    expect(IconField.decode('7,2,7,xyz,-1,99'), {2, 7});
  });
  test('All cells have valid, stable normalized coordinates', () {
    for (var i = 0; i < IconField.cellCount; i++) {
      final (x, y) = IconField.centerFor(i);
      expect(x, inInclusiveRange(0, 1));
      expect(y, inInclusiveRange(0, 1));
    }
  });
  test('Icon field responds locally and is bounded', () {
    const empty = IconField(occupied: {});
    expect(empty.influence(0.1, 0.2), 0);
    const field = IconField(occupied: {10});
    final (x, y) = IconField.centerFor(10);
    expect(field.influence(x, y), greaterThan(0.99));
    expect(field.influence(x + 0.5, y + 0.5), lessThan(0.01));
    expect(field.influence(x, y), lessThanOrEqualTo(1));
  });
}

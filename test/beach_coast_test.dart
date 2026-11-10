import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/beach_coast.dart';
import 'package:adaptive_landscape/core/icon_field.dart';

void main() {
  test('Beach is top-down sand on left and deep water on right', () {
    final beach = BeachCoast(const IconField(occupied: {}));
    expect(beach.coast(0.03, 0.4), greaterThan(0.1));
    expect(beach.coast(0.96, 0.4), lessThan(-0.4));
    expect(beach.colorAt(0.02, 0.4, 0, 0),
        isNot(beach.colorAt(0.95, 0.4, 0, 0)));
  });
  test('Icons form new sand around their actual coordinates', () {
    final noIcons = BeachCoast(const IconField(occupied: {}));
    final icons = BeachCoast(const IconField(occupied: {24}));
    const x = 0.9, y = 0.5;
    expect(icons.coast(x, y), greaterThan(noIcons.coast(x, y) + 0.5));
    expect(icons.coast(x, y), greaterThan(0));
  });
  test('Two frames move shoreline without changing stored occupancy', () {
    final beach = BeachCoast(const IconField(occupied: {14}));
    expect(BeachCoast.tide(0.45, 0), isNot(BeachCoast.tide(0.45, 1.0)));
    expect(beach.coast(0.4, 0.5), beach.coast(0.4, 0.5));
  });
  test('All theme variants remain fully opaque RGBA', () {
    final beach = BeachCoast(const IconField(occupied: {3, 8, 15}));
    for (final palette in ['original', 'sunset', 'mint']) {
      for (final night in [false, true]) {
        for (var xi = 0; xi <= 10; xi++) {
          final pixel = beach.colorAt(xi / 10, 0.5, 1.0,
              BeachCoast.grain(xi, 11), palette: palette, night: night);
          expect(pixel & 0xff000000, 0xff000000);
        }
      }
    }
  });
}

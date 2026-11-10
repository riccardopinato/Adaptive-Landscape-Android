import 'dart:math' as math;
import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/beach_coast.dart';
import 'package:adaptive_landscape/core/icon_field.dart';

void main() {
  test('Empty Beach is entirely ocean at every tested coordinate', () {
    final beach = BeachCoast(const IconField(occupied: {}));
    for (var x = 0; x <= 10; x++) {
      for (var y = 0; y <= 10; y++) {
        expect(beach.coast(x / 10, y / 10), lessThan(0),
            reason: 'No permanent sand, even at corners');
      }
    }
  });
  test('Icons form new sand around their actual coordinates', () {
    final noIcons = BeachCoast(const IconField(occupied: {}));
    final icons = BeachCoast(const IconField(occupied: {24}));
    const x = 0.9, y = 0.5;
    expect(icons.coast(x, y), greaterThan(noIcons.coast(x, y) + 0.5));
    expect(icons.coast(x, y), greaterThan(0));
  });
  test('Tide and foam colors loop seamlessly over full 2 pi cycle', () {
    final beach = BeachCoast(const IconField(occupied: {14}));
    for (final y in [0.0, 0.33, 0.62, 1.0]) {
      expect(BeachCoast.tide(y, 0), closeTo(BeachCoast.tide(y, 2 * math.pi), 1e-12));
      expect(beach.colorAt(0.36, y, 0, 0),
          beach.colorAt(0.36, y, 2 * math.pi, 0));
    }
  });
  test('Cached geometry sample equals direct geometry sample', () {
    final beach = BeachCoast(const IconField(occupied: {14, 20}));
    final base = beach.coast(0.3, 0.6);
    expect(beach.colorAtCached(base, 0.3, 0.6, 1.5, 0.2),
        beach.colorAt(0.3, 0.6, 1.5, 0.2));
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

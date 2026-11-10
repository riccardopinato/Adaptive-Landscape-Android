import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/ocean_tone_atlas.dart';

void main() {
  test('Original tonal atlas is local, nonempty and pixel format valid', () {
    final data = OceanToneAtlas.bytes();
    expect(OceanToneAtlas.width, 24);
    expect(OceanToneAtlas.height, 42);
    expect(data.length, OceanToneAtlas.width * OceanToneAtlas.height * 4);
    expect(OceanToneAtlas.encoded.length, 2688);
    for (var i = 3; i < data.length; i += 4) {
      expect(data[i], 255);
    }
  });

  test('Aerial sea has a real depth-to-shallow tonal progression', () {
    final bytes = OceanToneAtlas.bytes();
    double meanGreen(int row) {
      var sum = 0;
      for (var x = 0; x < OceanToneAtlas.width; x++) {
        sum += bytes[(row * OceanToneAtlas.width + x) * 4 + 1];
      }
      return sum / OceanToneAtlas.width;
    }
    expect(meanGreen(41), greaterThan(meanGreen(0)));
  });
}

import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/landscape_config.dart';

void main() {
  test('v0.4.4 optional photo optical controls round-trip schema2', () {
    const config = LandscapeConfig(
      scene: 'beach', photoRefraction: 0.35, foamIntensity: 0.15);
    final restored = LandscapeConfig.decode(config.encode());
    expect(restored.photoRefraction, 0.35);
    expect(restored.foamIntensity, 0.15);
    expect(restored.scene, 'beach');
    expect(restored.toJson()['schema'], 2);
  });

  test('Legacy saved profiles use safe optical defaults', () {
    final restored = LandscapeConfig.fromJson({
      'schema': 2, 'scene': 'beach', 'occupied': [3],
    });
    expect(restored.photoRefraction, 0.55);
    expect(restored.foamIntensity, 0.45);
    expect(restored.occupied, {3});
  });

  test('Imported values outside range are clamped', () {
    final config = LandscapeConfig.fromJson({
      'schema': 2, 'scene': 'beach',
      'photoRefraction': 900, 'foamIntensity': -1,
    });
    expect(config.photoRefraction, 1.0);
    expect(config.foamIntensity, 0.0);
  });
}

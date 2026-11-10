import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/beach_mask.dart';
import 'package:adaptive_landscape/core/icon_field.dart';
import 'package:adaptive_landscape/core/landscape_config.dart';
import 'package:adaptive_landscape/core/animation_timing.dart';

void main() {
  test('Photographic water has no sand mask until manually occupied', () {
    const onlySea = IconField(occupied: {});
    expect(BeachMask.isEmpty(onlySea), isTrue);
    final mask = BeachMask.bake(onlySea, width: 8, height: 16);
    for (var pixel = 0; pixel < mask.length; pixel += 4) {
      expect(mask[pixel], 0);
      expect(mask[pixel + 3], 255);
    }
  });

  test('Manual zones remain in schema2 and activate sand geometry', () {
    const config = LandscapeConfig(scene: 'beach',
        zones: [LayoutZone(x: 0.5, y: 0.5, width: 0.2, height: 0.1)]);
    final restored = LandscapeConfig.decode(config.encode());
    expect(restored.zones, hasLength(1));
    expect(BeachMask.isEmpty(IconField.fromConfig(restored)), isFalse);
  });

  test('Reduce motion disables native and web animation by config', () {
    const config = LandscapeConfig(scene: 'beach', reduceMotion: true);
    expect(config.reduceMotion, isTrue);
    expect(motionLoopDuration(0), Duration.zero);
  });
}

import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/landscape_config.dart';
import 'package:adaptive_landscape/core/qa_snapshot.dart';

void main() {
  test('QA snapshot includes environment summary but no identifiers', () {
    const config = LandscapeConfig(
      scene: 'beach', renderQuality: 'high', occupied: {1, 7},
      zones: [LayoutZone(x: 0.4, y: 0.55, width: 0.2,
        height: 0.12, kind: 'widget')]);
    final text = buildQaSnapshot(
      config: config, renderer: 'gpu_photo_native', photoStored: true,
      runtime: 'android', sourceSha: 'test-commit');
    expect(text, contains('renderer: gpu_photo_native'));
    expect(text, contains('manualMarkedIcons: 2'));
    expect(text, contains('manualWidgets: 1'));
    expect(text, contains('source: test-commit'));
    expect(text, contains('status: UNTESTED_PHYSICAL'));
    expect(text, isNot(contains('androidId')));
    expect(text, isNot(contains('serialNumber')));
  });

  test('Empty layout produces no fake icon or widget reporting', () {
    final report = buildQaSnapshot(
      config: const LandscapeConfig(scene: 'beach'),
      renderer: 'not started', photoStored: false, runtime: 'web');
    expect(report, contains('manualMarkedIcons: 0'));
    expect(report, contains('manualWidgets: 0'));
    expect(report, contains('manualDockIcons: 0'));
  });
}

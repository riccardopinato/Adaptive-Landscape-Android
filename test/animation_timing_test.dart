import 'package:flutter_test/flutter_test.dart';
import 'package:adaptive_landscape/core/animation_timing.dart';

void main() {
  test('Motion speed uses whole phase cycles at every setting', () {
    expect(motionLoopDuration(0.5), const Duration(seconds: 24));
    expect(motionLoopDuration(1), const Duration(seconds: 12));
    expect(motionLoopDuration(2), const Duration(seconds: 6));
  });
  test('Pause and invalid speeds never schedule a running animation', () {
    expect(motionLoopDuration(0), Duration.zero);
    expect(motionLoopDuration(-2), Duration.zero);
    expect(motionLoopDuration(double.nan), Duration.zero);
    expect(motionLoopDuration(double.infinity), Duration.zero);
  });
  test('Slower movement is possible without modulo discontinuity', () {
    expect(motionLoopDuration(0.1), const Duration(seconds: 120));
  });
}

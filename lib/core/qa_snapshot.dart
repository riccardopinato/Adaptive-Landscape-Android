import 'landscape_config.dart';

/// A privacy-minimized snapshot voluntarily copied by the user for QA.
/// Contains no device IDs, launcher app names, screen captures, or coordinates
/// beyond the normalized scene settings already present in the saved profile.
String buildQaSnapshot({
  required LandscapeConfig config,
  required String renderer,
  required bool photoStored,
  required String runtime,
  String sourceSha = 'not embedded',
}) {
  final kinds = <String, int>{};
  for (final zone in config.zones) {
    kinds[zone.kind] = (kinds[zone.kind] ?? 0) + 1;
  }
  return [
    'Adaptive Landscape v0.4.6+13 - QA snapshot (NOT certification)',
    'runtime: $runtime',
    'source: $sourceSha',
    'scene: ${config.scene}',
    'quality: ${config.renderQuality}',
    'renderer: $renderer',
    'photographStored: $photoStored',
    'grid: ${config.columns}x${config.rows}',
    'manualMarkedIcons: ${config.occupied.length}',
    'manualWidgets: ${kinds['widget'] ?? 0}',
    'manualDockIcons: ${kinds['dock'] ?? 0}',
    'gridTop: ${config.top.toStringAsFixed(3)}',
    'gridSpan: ${config.span.toStringAsFixed(3)}',
    'gridInset: ${config.gridInset.toStringAsFixed(3)}',
    'refraction: ${config.photoRefraction.toStringAsFixed(2)}',
    'foam: ${config.foamIntensity.toStringAsFixed(2)}',
    'motion: ${config.motion.toStringAsFixed(2)}',
    'reduceMotion: ${config.reduceMotion}',
    'status: UNTESTED_PHYSICAL',
  ].join('\n');
}

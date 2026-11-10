/// Shared Flutter animation-period policy. Speed changes the period, never
/// the phase amplitude, so any supported speed loops smoothly at 2*pi.
Duration motionLoopDuration(double speed) {
  if (!speed.isFinite || speed <= 0) return Duration.zero;
  final millis = (12000 / speed).round().clamp(6000, 120000).toInt();
  return Duration(milliseconds: millis);
}

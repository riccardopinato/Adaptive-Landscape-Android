import 'dart:math' as math;
import 'landscape_config.dart';

/// Deterministic influence field; normalized geometry only, no launcher data.
class IconField {
  const IconField({
    required this.occupied,
    this.gridColumns = 5,
    this.gridRows = 8,
    this.top = 0.115,
    this.span = 0.765,
    this.zones = const [],
  });

  factory IconField.fromConfig(LandscapeConfig config) => IconField(
        occupied: config.occupied, gridColumns: config.columns, gridRows: config.rows,
        top: config.top, span: config.span, zones: config.zones,
      );

  static const int columns = 5;
  static const int rows = 8;
  static const int cellCount = columns * rows;

  final Set<int> occupied;
  final int gridColumns;
  final int gridRows;
  final double top;
  final double span;
  final List<LayoutZone> zones;

  static (double, double) centerFor(int index) {
    if (index < 0 || index >= cellCount) throw RangeError.range(index, 0, cellCount - 1, 'index');
    return (((index % columns) + 0.5) / columns, 0.115 + ((index ~/ columns) + 0.5) * 0.765 / rows);
  }

  double influence(double x, double y) {
    var total = 0.0;
    for (final index in occupied) {
      if (index < 0 || index >= gridColumns * gridRows) continue;
      final cx = ((index % gridColumns) + 0.5) / gridColumns;
      final cy = top + ((index ~/ gridColumns) + 0.5) * span / gridRows;
      final dx = (x - cx) / (0.58 / gridColumns);
      final dy = (y - cy) / (0.58 * span / gridRows);
      total += math.exp(-0.5 * (dx * dx + dy * dy));
    }
    for (final zone in zones) {
      final dx = (x - zone.x).abs();
      final dy = (y - zone.y).abs();
      final outsideX = math.max(0.0, dx - zone.width / 2) / (zone.width * 0.38);
      final outsideY = math.max(0.0, dy - zone.height / 2) / (zone.height * 0.38);
      total += math.exp(-0.5 * (outsideX * outsideX + outsideY * outsideY));
    }
    return total.clamp(0.0, 1.0);
  }

  bool obstructed(double x, double y) => influence(x, y) > 0.38;

  static Set<int> decode(String encoded) => encoded
      .split(',').map(int.tryParse).whereType<int>()
      .where((x) => x >= 0 && x < cellCount).toSet();

  static String encode(Set<int> cells) =>
      (cells.where((x) => x >= 0 && x < cellCount).toList()..sort()).join(',');
}

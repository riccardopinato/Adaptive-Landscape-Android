import 'dart:math' as math;

/// Pure coordinate model; zero access to launcher data or user applications.
class IconField {
  const IconField({required this.occupied});

  static const int columns = 5;
  static const int rows = 8;
  static const int cellCount = columns * rows;

  final Set<int> occupied;

  /// Normalized screen coordinates, with approximate margins for Home launchers.
  static (double, double) centerFor(int index) {
    if (index < 0 || index >= cellCount) {
      throw RangeError.range(index, 0, cellCount - 1, 'index');
    }
    final col = index % columns;
    final row = index ~/ columns;
    return ((col + 0.5) / columns, 0.115 + (row + 0.5) * 0.765 / rows);
  }

  /// Continuous, bounded influence field used to move shoreline, clouds and objects.
  double influence(double x, double y) {
    var amount = 0.0;
    for (final index in occupied) {
      if (index < 0 || index >= cellCount) continue;
      final (ix, iy) = centerFor(index);
      final dx = (x - ix) / 0.115;
      final dy = (y - iy) / 0.073;
      amount += math.exp(-0.5 * (dx * dx + dy * dy));
    }
    return amount.clamp(0.0, 1.0);
  }

  bool obstructed(double x, double y) => influence(x, y) > 0.38;

  static Set<int> decode(String encoded) => encoded
      .split(',')
      .map(int.tryParse)
      .whereType<int>()
      .where((x) => x >= 0 && x < cellCount)
      .toSet();

  static String encode(Set<int> cells) =>
      (cells.where((x) => x >= 0 && x < cellCount).toList()..sort()).join(',');
}

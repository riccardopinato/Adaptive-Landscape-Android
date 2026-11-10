import 'dart:math' as math;
import 'dart:typed_data';
import 'icon_field.dart';
import 'landscape_config.dart';

/// Normalized landscape occupancy as a reusable 2-D texture, NOT the sea image.
/// Stable geometry is rasterized only when icons/widgets/dock change.
/// GPU fragment shading subsequently operates at the canvas' real pixel density.
class BeachMask {
  const BeachMask._();
  static const width = 512;
  static const height = 1024;

  static bool isEmpty(IconField field) => field.occupied.isEmpty && field.zones.isEmpty;

  static Uint8List bake(IconField field, {int width = BeachMask.width, int height = BeachMask.height}) {
    if (width < 2 || height < 2) throw ArgumentError('Invalid mask dimensions');
    final image = Uint8List(width * height * 4);
    if (isEmpty(field)) {
      for (var i = 3; i < image.length; i += 4) { image[i] = 255; }
      return image;
    }
    final values = Float32List(width * height);
    void stamp({
      required double cx, required double cy,
      required double rx, required double ry,
      double halfW = 0, double halfH = 0,
    }) {
      if (rx <= 0 || ry <= 0) return;
      final x0 = ((cx - halfW - 4 * rx) * width).floor().clamp(0, width - 1).toInt();
      final x1 = ((cx + halfW + 4 * rx) * width).ceil().clamp(0, width - 1).toInt();
      final y0 = ((cy - halfH - 4 * ry) * height).floor().clamp(0, height - 1).toInt();
      final y1 = ((cy + halfH + 4 * ry) * height).ceil().clamp(0, height - 1).toInt();
      // Gaussian is separable: compute expensive exponentials once per
      // occupied column and row, then use multiplications inside the grid.
      final xs = Float32List(x1 - x0 + 1);
      for (var x = x0; x <= x1; x++) {
        final px = (x + 0.5) / width;
        final dx = math.max(0.0, (px - cx).abs() - halfW) / rx;
        xs[x - x0] = math.exp(-0.5 * dx * dx);
      }
      for (var y = y0; y <= y1; y++) {
        final py = (y + 0.5) / height;
        final dy = math.max(0.0, (py - cy).abs() - halfH) / ry;
        final gy = math.exp(-0.5 * dy * dy);
        final row = y * width;
        for (var x = x0; x <= x1; x++) {
          values[row + x] += xs[x - x0] * gy;
        }
      }
    }

    for (final index in field.occupied) {
      if (index < 0 || index >= field.gridColumns * field.gridRows) continue;
      final cx = field.gridInset + ((index % field.gridColumns) + 0.5) *
        (1 - 2 * field.gridInset) / field.gridColumns;
      final cy = field.top + ((index ~/ field.gridColumns) + 0.5) * field.span / field.gridRows;
      stamp(cx: cx, cy: cy,
          rx: 0.58 * (1 - 2 * field.gridInset) / field.gridColumns,
          ry: 0.58 * field.span / field.gridRows);
    }
    for (final zone in field.zones) {
      stamp(cx: zone.x, cy: zone.y, rx: zone.width * 0.38,
          ry: zone.height * 0.38, halfW: zone.width / 2, halfH: zone.height / 2);
    }
    for (var i = 0, j = 0; i < values.length; i++, j += 4) {
      final value = (values[i].clamp(0, 1) * 255).round();
      image[j] = value;
      image[j + 1] = value;
      image[j + 2] = value;
      image[j + 3] = 255;
    }
    return image;
  }
}

/// Manually calibrated launcher dock: each app gets its own beach island.
/// An empty dock is truly empty sea; no launcher introspection is attempted.
class DockLayout {
  const DockLayout._();
  static int count(List<LayoutZone> zones) => zones.where((z) => z.kind == 'dock').length;

  static List<LayoutZone> withCount(List<LayoutZone> previous, int count) {
    if (count < 0 || count > 4) throw RangeError.range(count, 0, 4);
    final other = previous.where((zone) => zone.kind != 'dock').toList();
    if (other.length + count > 80) throw ArgumentError('Too many layout zones');
    final dock = List.generate(count, (i) => LayoutZone(
        x: 0.12 + 0.76 * (i + 0.5) / count,
        y: 0.941,
        width: 0.115,
        height: 0.075,
        kind: 'dock'));
    return [...other, ...dock];
  }
}

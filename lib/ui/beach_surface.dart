import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import '../core/beach_coast.dart';
import '../core/icon_field.dart';

/// Independently authored procedural aerial coastline. No sky, sun, clouds,
/// stock images, copied GPL assets or WebGL dependencies.
class BeachSurface {
  BeachSurface._();

  static _Mesh? _cached;

  static void paint(Canvas canvas, Size size, IconField field, double phase, {
    String palette = 'original',
    bool night = false,
  }) {
    if (size.isEmpty) return;
    final key = [
      size.width.toStringAsFixed(1), size.height.toStringAsFixed(1),
      field.gridColumns, field.gridRows, field.top, field.span,
      IconField.encode(field.occupied),
      for (final zone in field.zones)
        '${zone.x.toStringAsFixed(5)},${zone.y.toStringAsFixed(5)},'
        '${zone.width.toStringAsFixed(5)},${zone.height.toStringAsFixed(5)}',
    ].join('|');
    final mesh = _cached?.key == key ? _cached! : (_cached = _Mesh.build(key, field));
    final coast = BeachCoast(field);
    final colors = Int32List(mesh.positions.length ~/ 2);

    for (var index = 0; index < colors.length; index++) {
      colors[index] = coast.colorAtCached(
        mesh.coast[index], mesh.x[index], mesh.y[index], phase, mesh.grain[index],
        palette: palette, night: night,
      );
    }
    final vertices = ui.Vertices.raw(
      ui.VertexMode.triangles,
      mesh.absolutePositions(size),
      colors: colors,
      indices: mesh.indices,
    );
    canvas.drawVertices(vertices, BlendMode.modulate,
        Paint()..color = const Color(0xffffffff));
  }
}

class _Mesh {
  const _Mesh(this.key, this.positions, this.x, this.y, this.coast, this.grain, this.indices);
  final String key;
  final Float32List positions; // normalized positions
  final Float32List x;
  final Float32List y;
  final Float32List coast;
  final Float32List grain;
  final Uint16List indices;

  static _Mesh build(String key, IconField field) {
    // 73 × 137 = 10,001 vertices; indices fit Uint16, rendered using the GPU.
    const cols = 72, rows = 136;
    final count = (cols + 1) * (rows + 1);
    final positions = Float32List(count * 2);
    final xs = Float32List(count), ys = Float32List(count);
    final grain = Float32List(count);
    final coastValues = Float32List(count);
    final coast = BeachCoast(field);
    var i = 0;
    for (var row = 0; row <= rows; row++) {
      for (var col = 0; col <= cols; col++) {
        final x = col / cols, y = row / rows;
        positions[i * 2] = x;
        positions[i * 2 + 1] = y;
        xs[i] = x; ys[i] = y;
        grain[i] = BeachCoast.grain(col, row);
        coastValues[i] = coast.coast(x, y);
        i++;
      }
    }
    final indices = Uint16List(cols * rows * 6);
    var p = 0;
    for (var row = 0; row < rows; row++) {
      for (var col = 0; col < cols; col++) {
        final tl = row * (cols + 1) + col;
        final tr = tl + 1, bl = tl + cols + 1, br = bl + 1;
        indices[p++] = tl; indices[p++] = tr; indices[p++] = bl;
        indices[p++] = tr; indices[p++] = br; indices[p++] = bl;
      }
    }
    return _Mesh(key, positions, xs, ys, coastValues, grain, indices);
  }

  Float32List absolutePositions(Size size) {
    final output = Float32List(positions.length);
    for (var i = 0; i < positions.length; i += 2) {
      output[i] = positions[i] * size.width;
      output[i + 1] = positions[i + 1] * size.height;
    }
    return output;
  }
}

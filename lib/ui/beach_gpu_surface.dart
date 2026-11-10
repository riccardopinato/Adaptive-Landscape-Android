import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import '../core/beach_mask.dart';
import '../core/icon_field.dart';

/// Full-framebuffer procedural ocean shader; 512×1024 texture stores only
/// static occupancy. Every sea pixel is shaded by the GPU at actual output DPR.
/// On unsupported renderers, BeachSurface supplies a conservative fallback.
class BeachGpuSurface {
  BeachGpuSurface._();
  static String? _key;
  static ui.Image? _mask;
  static ui.FragmentShader? _shader;
  static ui.FragmentProgram? _program;

  static void invalidate() {
    _key = null;
    _mask?.dispose();
    _mask = null;
  }

  static bool paint(Canvas canvas, Size size, IconField field, double phase,
      {required ui.FragmentProgram? program, String palette = 'original',
       bool night = false}) {
    if (program == null || size.isEmpty) return false;
    try {
      final key = [
        field.gridColumns, field.gridRows, field.top, field.span,
        IconField.encode(field.occupied),
        for (final z in field.zones)
          '${z.x.toStringAsFixed(6)}|${z.y.toStringAsFixed(6)}|'
          '${z.width.toStringAsFixed(6)}|${z.height.toStringAsFixed(6)}',
      ].join(';');
      if (key != _key || _mask == null) {
        invalidate();
        final empty = BeachMask.isEmpty(field);
        final mask = empty ? Uint8List.fromList([0, 0, 0, 255]) : BeachMask.bake(field);
        _mask = ui.decodeImageFromPixelsSync(mask,
          empty ? 1 : BeachMask.width, empty ? 1 : BeachMask.height,
          ui.PixelFormat.rgba8888);
        _key = key;
      }
      if (!identical(program, _program) || _shader == null) {
        _shader?.dispose();
        _program = program;
        _shader = program.fragmentShader();
      }
      final shader = _shader!;
      shader.setFloat(0, size.width);
      shader.setFloat(1, size.height);
      shader.setFloat(2, phase);
      shader.setFloat(3, palette == 'sunset' ? 1 : palette == 'mint' ? 2 : 0);
      shader.setFloat(4, night ? 1 : 0);
      shader.setFloat(5, BeachMask.isEmpty(field) ? 0 : 1);
      shader.setImageSampler(0, _mask!, filterQuality: ui.FilterQuality.high);
      canvas.drawRect(Offset.zero & size, Paint()..shader = shader);
      return true;
    } catch (_) {
      // Unsupported GPU shader backend: preserve a functional original scene.
      return false;
    }
  }
}

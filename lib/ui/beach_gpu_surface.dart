import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import '../core/beach_mask.dart';
import '../core/icon_field.dart';
import '../core/ocean_tone_atlas.dart';

enum BeachRenderStatus { loading, gpu, fallback }

/// Full-framebuffer procedural ocean shader; 512×1024 texture stores only
/// static occupancy. Every sea pixel is shaded by the GPU at actual output DPR.
/// On unsupported renderers, BeachSurface supplies a conservative fallback.
class BeachGpuSurface {
  BeachGpuSurface._();
  static String? _key;
  static ui.Image? _mask;
  static ui.FragmentShader? _shader;
  static ui.FragmentProgram? _program;
  static ui.Image? _toneAtlas;
  static final ValueNotifier<BeachRenderStatus> status =
      ValueNotifier(BeachRenderStatus.loading);
  static bool _programUnavailable = false;

  static void _report(BeachRenderStatus next) {
    if (status.value == next) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (status.value != next) status.value = next;
    });
  }

  static void markUnavailable() {
    _programUnavailable = true;
    _report(BeachRenderStatus.fallback);
  }

  static void invalidate() {
    _key = null;
    _mask?.dispose();
    _mask = null;
  }

  static bool paint(Canvas canvas, Size size, IconField field, double phase,
      {required ui.FragmentProgram? program, String palette = 'original',
       bool night = false, String renderQuality = 'ultra', ui.Image? photograph,
       double photoRefraction = 0.55, double foamIntensity = 0.45}) {
    if (program == null || size.isEmpty) {
      if (_programUnavailable) _report(BeachRenderStatus.fallback);
      return false;
    }
    try {
      final key = [
        field.gridColumns, field.gridRows, field.top, field.span,
        field.gridInset,
        field.encodeOccupied(),
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
      shader.setFloat(6, renderQuality == 'balanced' ? 0 : renderQuality == 'high' ? 1 : 2);
      shader.setFloat(7, photograph == null ? 0 : 1);
      shader.setFloat(8, photograph == null ? 1 : photograph.width / photograph.height);
      shader.setFloat(9, photoRefraction.clamp(0.0, 1.0));
      shader.setFloat(10, foamIntensity.clamp(0.0, 1.0));
      shader.setImageSampler(0, _mask!, filterQuality: ui.FilterQuality.high);
      _toneAtlas ??= OceanToneAtlas.decode();
      shader.setImageSampler(1, _toneAtlas!, filterQuality: ui.FilterQuality.high);
      shader.setImageSampler(2, photograph ?? _toneAtlas!, filterQuality: ui.FilterQuality.high);
      canvas.drawRect(Offset.zero & size, Paint()..shader = shader);
      _report(BeachRenderStatus.gpu);
      return true;
    } catch (_) {
      // Expose the fallback state instead of silently claiming 4K graphics.
      _report(BeachRenderStatus.fallback);
      return false;
    }
  }
}

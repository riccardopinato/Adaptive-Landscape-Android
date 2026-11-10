import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../core/icon_field.dart';

/// Original light 2D vector preview. Live rendering is owned by Android WallpaperService.
class ScenePainter extends CustomPainter {
  const ScenePainter({required this.scene, required this.field, this.phase = 0});
  final String scene;
  final IconField field;
  final double phase;

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;
    final sky = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: scene == 'beach'
            ? [const Color(0xff6bd9e7), const Color(0xffdbfbec)]
            : [const Color(0xff7bb8e6), const Color(0xfff2f7df)],
      ).createShader(Offset.zero & size);
    canvas.drawRect(Offset.zero & size, sky);

    final sun = Paint()..color = const Color(0xfffff3c2);
    canvas.drawCircle(Offset(w * 0.77, h * 0.18), w * 0.09, sun);

    for (var i = 0; i < 3; i++) {
      final cx = w * (0.12 + 0.32 * i) + math.sin(phase + i) * w * 0.02;
      final cy = h * (0.16 + (i % 2) * 0.10);
      final move = field.influence(cx / w, cy / h) * h * 0.11;
      final cloud = Paint()..color = Colors.white.withValues(alpha: 0.77);
      canvas.drawOval(Rect.fromCenter(center: Offset(cx, cy - move), width: w * 0.23, height: h * 0.045), cloud);
      canvas.drawCircle(Offset(cx - w * 0.04, cy - move - h * 0.012), w * 0.045, cloud);
    }

    if (scene == 'beach') {
      canvas.drawRect(Rect.fromLTWH(0, h * 0.52, w, h * 0.48), Paint()..color = const Color(0xfff5dc9f));
      final water = Path()..moveTo(0, h * 0.54);
      for (var i = 0; i <= 80; i++) {
        final x = i / 80;
        final y = 0.60 + math.sin(x * math.pi * 3 + phase) * 0.018 + field.influence(x, 0.61) * 0.095;
        water.lineTo(x * w, y * h);
      }
      water..lineTo(w, h)..lineTo(0, h)..close();
      canvas.drawPath(water, Paint()..color = const Color(0xff36bfca));
      for (var i = 0; i < 5; i++) {
        final y = h * (0.69 + 0.057 * i);
        final wave = Path()..moveTo(0, y);
        for (var j = 0; j <= 40; j++) {
          final x = j / 40;
          wave.lineTo(x * w, y + math.sin(x * 16 + phase * 2 + i) * 3);
        }
        canvas.drawPath(wave, Paint()..color = Colors.white.withValues(alpha: 0.28)..style = PaintingStyle.stroke..strokeWidth = 1.8);
      }
    } else {
      canvas.drawRect(Rect.fromLTWH(0, h * 0.49, w, h * 0.51), Paint()..color = const Color(0xff92b96d));
      for (var layer = 0; layer < 2; layer++) {
        final base = 0.53 + 0.115 * layer;
        final hill = Path()..moveTo(0, base * h);
        for (var i = 0; i <= 70; i++) {
          final x = i / 70;
          final y = base + math.sin(x * 9 + layer * 3) * 0.035 + field.influence(x, base) * 0.065;
          hill.lineTo(x * w, y * h);
        }
        hill..lineTo(w, h)..lineTo(0, h)..close();
        canvas.drawPath(hill, Paint()..color = layer == 0 ? const Color(0xff6f9f62) : const Color(0xff4b885a));
      }
      for (var n = 0; n < 90; n++) {
        final x = ((n * 73) % 97) / 97.0;
        final y = 0.68 + (((n * 37) % 29) / 29.0) * 0.32;
        if (field.obstructed(x, y)) continue;
        canvas.drawCircle(Offset(x * w, y * h), n % 5 == 0 ? 2.0 : 1.0,
            Paint()..color = n % 3 == 0 ? const Color(0xfff6dfa6) : const Color(0xffd9eaa9));
      }
    }
  }

  @override
  bool shouldRepaint(ScenePainter oldDelegate) =>
      scene != oldDelegate.scene || phase != oldDelegate.phase ||
      IconField.encode(field.occupied) != IconField.encode(oldDelegate.field.occupied);
}

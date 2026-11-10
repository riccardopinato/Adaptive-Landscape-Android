import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../core/icon_field.dart';

/// Original vector preview; matches the versioned Android Canvas scene model.
class ScenePainter extends CustomPainter {
  const ScenePainter({
    required this.scene, required this.field, this.phase = 0,
    this.palette = 'original', this.night = false,
  });

  final String scene;
  final IconField field;
  final double phase;
  final String palette;
  final bool night;

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width, h = size.height;
    final beach = scene == 'beach';
    final sunset = palette == 'sunset', mint = palette == 'mint';

    final skyTop = night ? const Color(0xff10234c)
        : sunset ? const Color(0xffef9197)
        : mint ? const Color(0xff85cfc5)
        : beach ? const Color(0xff6bd9e7) : const Color(0xff7bb8e6);
    final skyBottom = night ? const Color(0xff4d638d)
        : sunset ? const Color(0xfffad4af)
        : mint ? const Color(0xffe7f7d3)
        : beach ? const Color(0xffdbfbec) : const Color(0xfff2f7df);
    canvas.drawRect(Offset.zero & size, Paint()
      ..shader = LinearGradient(
        colors: [skyTop, skyBottom], begin: Alignment.topCenter,
        end: Alignment.bottomCenter).createShader(Offset.zero & size));

    if (night) {
      for (var i = 0; i < 28; i++) {
        final x = ((i * 71) % 101) / 101.0, y = ((i * 41) % 43) / 100;
        if (field.influence(x, y) > 0.4) continue;
        canvas.drawCircle(Offset(x * w, y * h), i % 7 == 0 ? 2.5 : 1.3,
          Paint()..color = const Color(0xfff6f8ff).withValues(alpha: (160 + i % 90) / 255));
      }
    }
    final sunY = 0.18 - field.influence(0.77, 0.18) * 0.08;
    canvas.drawCircle(Offset(w * 0.77, h * sunY), w * 0.09,
      Paint()..color = night ? const Color(0xffdce9fd) : const Color(0xfffff3c2));

    for (var i = 0; i < 3; i++) {
      final cx = w * (0.12 + 0.32 * i) + math.sin(phase + i) * w * 0.02;
      final cy = h * (0.16 + i % 2 * 0.10);
      final moved = cy - field.influence(cx / w, cy / h) * h * 0.11;
      final p = Paint()..color = night
          ? const Color(0xffc1d1ea).withValues(alpha: 0.45)
          : Colors.white.withValues(alpha: 0.76);
      canvas.drawOval(Rect.fromCenter(
        center: Offset(cx, moved), width: w * 0.23, height: h * 0.044), p);
      canvas.drawCircle(Offset(cx - w * 0.04, moved - h * 0.012), w * 0.045, p);
    }

    if (beach) {
      canvas.drawRect(Rect.fromLTWH(0, h * 0.52, w, h * 0.48),
        Paint()..color = night ? const Color(0xff4e5c7a) : const Color(0xfff5dc9f));
      final water = Path()..moveTo(0, h * 0.54);
      for (var i = 0; i <= 80; i++) {
        final x = i / 80;
        final y = 0.60 + math.sin(x * math.pi * 3 + phase) * 0.018 +
            field.influence(x, 0.61) * 0.095;
        water.lineTo(x * w, y * h);
      }
      water..lineTo(w, h)..lineTo(0, h)..close();
      canvas.drawPath(water, Paint()..color = night
          ? const Color(0xff245f8b)
          : sunset ? const Color(0xff4ea7b1) : const Color(0xff36bfca));
      for (var i = 0; i < 6; i++) {
        final y = h * (0.68 + 0.055 * i);
        final wave = Path()..moveTo(0, y);
        for (var j = 0; j <= 48; j++) {
          final x = j / 48.0;
          wave.lineTo(x * w, y + math.sin(x * 16 + phase * 2 + i) * 3);
        }
        canvas.drawPath(wave, Paint()
          ..color = Colors.white.withValues(alpha: night ? 0.24 : 0.4)
          ..style = PaintingStyle.stroke..strokeWidth = 2);
      }
      for (var i = 0; i < 35; i++) {
        final x = ((i * 59) % 97) / 97.0;
        final y = 0.63 + ((i * 29) % 31) / 31 * 0.37;
        if (field.influence(x, y) > 0.3) continue;
        canvas.drawCircle(Offset(x * w, y * h), 1.2 + i % 3,
          Paint()..color = const Color(0xfff5f9ec).withValues(alpha: 0.39));
      }
    } else {
      canvas.drawRect(Rect.fromLTWH(0, h * 0.49, w, h * 0.51),
        Paint()..color = night ? const Color(0xff4c6b5d) : const Color(0xff92b96d));
      const daytime = [Color(0xff6f9f62), Color(0xff4b885a), Color(0xff356f4b)];
      const nights = [Color(0xff3c5c57), Color(0xff2e4b4c), Color(0xff223f46)];
      const mints = [Color(0xff83b38b), Color(0xff639783), Color(0xff407569)];
      for (var layer = 0; layer < 3; layer++) {
        final base = 0.53 + 0.087 * layer;
        final hill = Path()..moveTo(0, base * h);
        for (var i = 0; i <= 70; i++) {
          final x = i / 70.0;
          final y = base + math.sin(x * 9 + layer * 3) * 0.035 +
              field.influence(x, base) * 0.065;
          hill.lineTo(x * w, y * h);
        }
        hill..lineTo(w, h)..lineTo(0, h)..close();
        canvas.drawPath(hill, Paint()..color =
            night ? nights[layer] : mint ? mints[layer] : daytime[layer]);
      }
      for (var i = 0; i < 100; i++) {
        final x = ((i * 73) % 97) / 97.0;
        final y = 0.67 + ((i * 37) % 29) / 29 * 0.33;
        if (field.influence(x, y) > 0.38) continue;
        final sway = math.sin(phase + i) * 2;
        canvas.drawCircle(Offset(x * w + sway, y * h),
          i % 5 == 0 ? 2.8 : 1.4,
          Paint()..color = night
              ? const Color(0xffb8d3b3).withValues(alpha: 0.47)
              : i % 3 == 0 ? const Color(0xfff6dfa6) : const Color(0xffd9eaa9));
      }
    }
  }

  @override
  bool shouldRepaint(ScenePainter old) =>
    scene != old.scene || palette != old.palette || night != old.night ||
    phase != old.phase || field.gridColumns != old.field.gridColumns ||
    field.gridRows != old.field.gridRows || field.top != old.field.top ||
    field.span != old.field.span ||
    field.zones.length != old.field.zones.length ||
    IconField.encode(field.occupied) != IconField.encode(old.field.occupied) ||
    !identical(field.zones, old.field.zones);
}

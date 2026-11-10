import 'dart:math' as math;
import 'icon_field.dart';

/// Original top-down shore/water model, inspired by the *concept* (not code)
/// of Deskland's GPL Beach. Coordinates are normalized to the wallpaper.
class BeachCoast {
  const BeachCoast(this.field);
  final IconField field;

  /// Positive -> sand; negative -> water. Icon regions grow dry land.
  double coast(double x, double y) {
    final strength = field.influence(x, y);
    // No occupied icons, widgets or dock -> no sand anywhere.
    // Adjacent icon kernels merge naturally; subtle irregularity scales with
    // land strength so ocean-only scenes cannot accidentally create beaches.
    return strength - 0.32 + strength *
        (0.010 * math.sin(y * 38.0 + 0.8) +
         0.006 * math.sin(y * 83.0 + x * 31.0));
  }

  /// Waves advance and retreat along a vertical, irregular coastline.
  static double tide(double y, double phase) =>
      0.017 * math.sin(phase + y * 6.8) +
      0.007 * math.sin(phase * 2.0 + y * 17.0);

  /// Sparse deterministic micro-grain, no bitmap/network texture.
  static double grain(int x, int y) {
    var n = ((x + 17) * 374761393 + (y + 53) * 668265263) & 0x7fffffff;
    n = ((n ^ (n >> 13)) * 1274126177) & 0x7fffffff;
    return (n % 4096) / 4095.0 * 2.0 - 1.0;
  }

  static const stops = <(double, int, int, int)>[
    (-1.0, 8, 111, 151),
    (-0.42, 10, 133, 166),
    (-0.23, 22, 163, 185),
    (-0.13, 65, 191, 191),
    (-0.073, 112, 209, 196),
    (-0.027, 183, 229, 207),
    (-0.005, 203, 227, 204),
    (0.018, 195, 190, 179),
    (0.072, 218, 207, 190),
    (0.18, 241, 228, 207),
    (1.0, 248, 239, 222),
  ];

  /// Colors are computed on low-resolution mesh vertices for Web performance.
  /// All color stops represent water or sand — never sky.
  int colorAt(double x, double y, double phase, double speckle, {
    String palette = 'original',
    bool night = false,
  }) => colorAtCached(
        coast(x, y), x, y, phase, speckle, palette: palette, night: night,
      );

  /// Use a cached shoreline value for animation: no repeated Gaussian work.
  int colorAtCached(double baseCoast, double x, double y, double phase, double speckle, {
    String palette = 'original',
    bool night = false,
  }) {
    final s = baseCoast + tide(y, phase);
    var high = 1;
    while (high < stops.length - 1 && stops[high].$1 < s) {
      high++;
    }
    final a = stops[high - 1], b = stops[high];
    final k = ((s - a.$1) / (b.$1 - a.$1)).clamp(0.0, 1.0);
    double ch(int from, int to) => from + (to - from) * k;
    var r = ch(a.$2, b.$2);
    var g = ch(a.$3, b.$3);
    var bl = ch(a.$4, b.$4);

    // Water caustics drift with time; wet/dry sand keeps delicate grain.
    final water = s < -0.025;
    final shimmer = water
        ? (math.sin(y * 84 + x * 32 - phase * 2.0) +
            math.sin(y * 133 - x * 17 + phase * 1.0)) * 2.5
        : 0.0;
    final noise = speckle * (water ? 6.0 : 3.0);
    r += shimmer + noise;
    g += shimmer + noise;
    bl += shimmer + noise;

    // Intermittent translucent foam at the contact line and nearshore ripples.
    final foam = math.exp(-math.pow(s / 0.012, 2).toDouble()) * 0.77 +
        math.exp(-math.pow((s + 0.068 +
                    0.012 * math.sin(y * 22 + phase * 2.0)) / 0.008, 2).toDouble()) *
            0.23;
    final f = foam.clamp(0.0, 0.88);
    r += (246 - r) * f;
    g += (247 - g) * f;
    bl += (237 - bl) * f;

    if (palette == 'sunset') {
      r = r * 1.05 + 3;
      g = g * 0.94 + 6;
      bl = bl * 0.89 + 4;
    } else if (palette == 'mint') {
      r *= 0.93;
      g = g * 1.02 + 2;
      bl = bl * 0.96 + 3;
    }
    if (night) {
      r = r * 0.52;
      g = g * 0.59 + 3;
      bl = bl * 0.70 + 11;
    }
    int clamp(double channel) => channel.round().clamp(0, 255).toInt();
    return 0xff000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
  }
}

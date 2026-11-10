import 'dart:convert';

/// Versioned, normalized geometry shared by the Flutter editor and Android engine.
/// No application names, screenshots, or launcher inspection are stored.
class LayoutZone {
  const LayoutZone({required this.x, required this.y, required this.width, required this.height, this.kind = 'icon'});
  final double x;
  final double y;
  final double width;
  final double height;
  final String kind;

  Map<String, Object> toJson() => {'x': x, 'y': y, 'width': width, 'height': height, 'kind': kind};

  static LayoutZone fromJson(Object? value) {
    final m = value is Map ? value : const {};
    double n(String key, double fallback) {
      final v = m[key];
      return v is num && v.isFinite ? v.toDouble() : fallback;
    }
    final width = n('width', 0.12).clamp(0.03, 1.0);
    final height = n('height', 0.07).clamp(0.03, 1.0);
    return LayoutZone(
      x: n('x', 0.5).clamp(width / 2, 1 - width / 2),
      y: n('y', 0.5).clamp(height / 2, 1 - height / 2),
      width: width,
      height: height,
      kind: m['kind'] == 'widget' ? 'widget' : m['kind'] == 'dock' ? 'dock' : 'icon',
    );
  }

  LayoutZone move(double dx, double dy) => LayoutZone(
        x: (x + dx).clamp(width / 2, 1 - width / 2),
        y: (y + dy).clamp(height / 2, 1 - height / 2),
        width: width,
        height: height,
        kind: kind,
      );
  LayoutZone resize(double dw, double dh) => LayoutZone(
        x: x, y: y,
        width: (width + dw).clamp(0.03, (2 * (x < 0.5 ? x : 1 - x)).clamp(0.03, 1.0)),
        height: (height + dh).clamp(0.03, (2 * (y < 0.5 ? y : 1 - y)).clamp(0.03, 1.0)),
        kind: kind,
      );
}

class LandscapeConfig {
  const LandscapeConfig({
    this.scene = 'meadow',
    this.columns = 5,
    this.rows = 8,
    this.top = 0.115,
    this.span = 0.765,
    this.occupied = const {},
    this.zones = const [],
    this.palette = 'original',
    this.night = false,
    this.motion = 1.0,
    this.reduceMotion = false,
    this.renderQuality = 'ultra',
  });

  static const int schemaVersion = 2;
  final String scene;
  final int columns;
  final int rows;
  final double top;
  final double span;
  final Set<int> occupied;
  final List<LayoutZone> zones;
  final String palette;
  final bool night;
  final double motion;
  final bool reduceMotion;
  /// Web GPU shading tier; Android native wallpaper ignores this optional key.
  final String renderQuality;

  int get cellCount => columns * rows;
  (double, double) cellCenter(int index) {
    if (index < 0 || index >= cellCount) throw RangeError.index(index, List.filled(cellCount, 0));
    return (((index % columns) + 0.5) / columns, top + ((index ~/ columns) + 0.5) * span / rows);
  }

  Map<String, Object> toJson() => {
        'schema': schemaVersion, 'scene': scene, 'columns': columns, 'rows': rows,
        'top': top, 'span': span,
        'occupied': (occupied.where((i) => i >= 0 && i < cellCount).toList()..sort()),
        'zones': zones.map((x) => x.toJson()).toList(),
        'palette': palette, 'night': night, 'motion': motion,
        'reduceMotion': reduceMotion, 'renderQuality': renderQuality,
      };

  String encode() => jsonEncode(toJson());

  static LandscapeConfig decode(String source) {
    try {
      return fromJson(jsonDecode(source));
    } catch (_) {
      return const LandscapeConfig();
    }
  }

  static LandscapeConfig fromJson(Object? data) {
    if (data is! Map) return const LandscapeConfig();
    final m = data;
    int i(String key, int fallback, int min, int max) {
      final v = m[key];
      return (v is num && v.isFinite ? v.toInt() : fallback).clamp(min, max);
    }
    double d(String key, double fallback, double min, double max) {
      final v = m[key];
      return (v is num && v.isFinite ? v.toDouble() : fallback).clamp(min, max);
    }
    final columns = i('columns', 5, 3, 8);
    final rows = i('rows', 8, 3, 12);
    final top = d('top', 0.115, 0.0, 0.5);
    final span = d('span', 0.765, 0.2, 1.0 - top);
    final rawCells = m['occupied'];
    final rawZones = m['zones'];
    final occupied = rawCells is List
        ? rawCells.whereType<num>().map((x) => x.toInt()).where((x) => x >= 0 && x < columns * rows).toSet()
        : rawCells is String
            ? rawCells.split(',').map(int.tryParse).whereType<int>()
                .where((x) => x >= 0 && x < columns * rows).toSet()
            : <int>{};
    final zones = rawZones is List
        ? rawZones.take(80).map(LayoutZone.fromJson).toList(growable: false)
        : <LayoutZone>[];
    return LandscapeConfig(
      scene: m['scene'] == 'beach' ? 'beach' : 'meadow',
      columns: columns, rows: rows, top: top, span: span,
      occupied: occupied, zones: zones,
      palette: m['palette'] == 'sunset' ? 'sunset' : m['palette'] == 'mint' ? 'mint' : 'original',
      night: m['night'] == true,
      motion: d('motion', 1.0, 0.0, 2.0),
      reduceMotion: m['reduceMotion'] == true,
      renderQuality: m['renderQuality'] == 'balanced' ? 'balanced' :
          m['renderQuality'] == 'high' ? 'high' : 'ultra',
    );
  }

  LandscapeConfig copyWith({
    String? scene, int? columns, int? rows, double? top, double? span,
    Set<int>? occupied, List<LayoutZone>? zones, String? palette,
    bool? night, double? motion, bool? reduceMotion,
    String? renderQuality,
  }) => LandscapeConfig(
        scene: scene ?? this.scene,
        columns: columns ?? this.columns, rows: rows ?? this.rows,
        top: top ?? this.top, span: span ?? this.span,
        occupied: occupied ?? this.occupied, zones: zones ?? this.zones,
        palette: palette ?? this.palette, night: night ?? this.night,
        motion: motion ?? this.motion, reduceMotion: reduceMotion ?? this.reduceMotion,
        renderQuality: renderQuality ?? this.renderQuality,
      );
}

class NamedLayout {
  const NamedLayout({required this.id, required this.name, required this.config});
  final String id;
  final String name;
  final LandscapeConfig config;

  Map<String, Object> toJson() => {'id': id, 'name': name, 'config': config.toJson()};
  String encode() => jsonEncode(toJson());

  static NamedLayout fromJson(Object? raw) {
    final m = raw is Map ? raw : const {};
    final id = m['id'] is String && (m['id'] as String).isNotEmpty ? (m['id'] as String).substring(0, (m['id'] as String).length.clamp(0, 64)) : 'default';
    final name = m['name'] is String && (m['name'] as String).trim().isNotEmpty
        ? (m['name'] as String).trim().substring(0, (m['name'] as String).trim().length.clamp(0, 48))
        : 'Home';
    return NamedLayout(id: id, name: name, config: LandscapeConfig.fromJson(m['config']));
  }
}

import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import 'landscape_config.dart';

/// Local-only persistence, including Flutter Web browser storage.
class ProfileStore {
  static const _profilesKey = 'adaptive_landscape_profiles_v2';
  static const _activeKey = 'adaptive_landscape_active_v2';

  Future<List<NamedLayout>> readProfiles() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_profilesKey);
    if (raw == null) return [const NamedLayout(id: 'default', name: 'Home', config: LandscapeConfig())];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return [const NamedLayout(id: 'default', name: 'Home', config: LandscapeConfig())];
      final result = decoded.take(16).map(NamedLayout.fromJson).toList();
      return result.isEmpty ? [const NamedLayout(id: 'default', name: 'Home', config: LandscapeConfig())] : result;
    } catch (_) {
      return [const NamedLayout(id: 'default', name: 'Home', config: LandscapeConfig())];
    }
  }

  Future<String> readActiveId() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_activeKey) ?? 'default';
  }

  Future<void> writeProfiles(List<NamedLayout> profiles, String activeId) async {
    if (profiles.isEmpty || profiles.length > 16 || !profiles.any((p) => p.id == activeId)) {
      throw ArgumentError('Invalid profile collection');
    }
    final prefs = await SharedPreferences.getInstance();
    final ok = await prefs.setString(_profilesKey, jsonEncode(profiles.map((x) => x.toJson()).toList()));
    final activeOk = await prefs.setString(_activeKey, activeId);
    if (!ok || !activeOk) throw StateError('Unable to persist profiles');
  }
}

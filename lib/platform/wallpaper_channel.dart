import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class WallpaperConfig {
  const WallpaperConfig({this.scene = 'meadow', this.occupied = '', this.configJson, this.nativeBackend, this.oceanPhotoEnabled = false,
    this.desklandHomeSelected = false, this.desklandWallpaperSelected = false,
    this.xiaomiGestureRestrictionPossible = false,
    this.autoHomeActive = false, this.autoHomeZones = 0, this.autoHomeRevision = 0,
    this.renderSource = 'not_rendered', this.renderZones = 0, this.renderRevision = -1});
  final String scene;
  final String occupied;
  final String? configJson;
  final String? nativeBackend;
  final bool oceanPhotoEnabled;
  final bool desklandHomeSelected;
  final bool desklandWallpaperSelected;
  final bool xiaomiGestureRestrictionPossible;
  final bool autoHomeActive;
  final int autoHomeZones;
  final int autoHomeRevision;
  final String renderSource;
  final int renderZones;
  final int renderRevision;
}

class WallpaperChannel {
  static const MethodChannel _channel = MethodChannel('adaptive_landscape/wallpaper');
  static bool get supported => !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  Future<WallpaperConfig> read() async {
    if (!supported) return const WallpaperConfig();
    final data = await _channel.invokeMapMethod<String, dynamic>('read');
    return WallpaperConfig(
      scene: data?['scene'] == 'beach' ? 'beach' : 'meadow',
      occupied: (data?['occupied'] ?? '').toString(),
      configJson: data?['config'] is String ? data!['config'] as String : null,
      nativeBackend: data?['backend'] is String ? data!['backend'] as String : null,
      oceanPhotoEnabled: data?['oceanPhoto'] == true,
      desklandHomeSelected: data?['desklandHomeSelected'] == true,
      xiaomiGestureRestrictionPossible: data?['xiaomiGestureRestrictionPossible'] == true,
      desklandWallpaperSelected: data?['desklandWallpaperSelected'] == true,
      autoHomeActive: data?['autoHomeActive'] == true,
      autoHomeZones: data?['autoHomeZones'] is int ? data!['autoHomeZones'] as int : 0,
      autoHomeRevision: data?['autoHomeRevision'] is int ? data!['autoHomeRevision'] as int : 0,
      renderSource: data?['renderSource'] is String ? data!['renderSource'] as String : 'not_rendered',
      renderZones: data?['renderZones'] is int ? data!['renderZones'] as int : 0,
      renderRevision: data?['renderRevision'] is int ? data!['renderRevision'] as int : -1,
    );
  }

  Future<void> save(WallpaperConfig config) async {
    if (!supported) return;
    await _channel.invokeMethod<void>('save', {
      'scene': config.scene,
      'occupied': config.occupied,
      if (config.configJson != null) 'config': config.configJson,
    });
  }

  /// A one-time user-selected local screenshot; never uploaded or persisted.
  Future<Uint8List?> pickHomeScreenshot() async {
    if (!supported) return null;
    return _channel.invokeMethod<Uint8List>('pickHomeScreenshot');
  }

  /// Imports a photo into Android app-private storage. Never leaves the device.
  Future<bool> pickOceanPhoto() async {
    if (!supported) return false;
    return await _channel.invokeMethod<bool>('pickOceanPhoto') ?? false;
  }

  Future<void> clearOceanPhoto() async {
    if (!supported) return;
    await _channel.invokeMethod<void>('clearOceanPhoto');
  }

  /// Presents the system Home-role dialog. It never silently replaces
  /// another launcher; the user can cancel without side effects.
  Future<bool> requestAdaptiveHome() async {
    if (!supported) return false;
    return await _channel.invokeMethod<bool>('requestAdaptiveHome') ?? false;
  }

  /// Opens the selected system HOME role, never an in-editor simulation.
  Future<void> openAdaptiveHome() async {
    if (!supported) return;
    await _channel.invokeMethod<void>('openAdaptiveHome');
  }

  /// Opens system default-app selection. Never silently changes Home.
  Future<void> openSystemHomeSettings() async {
    if (!supported) return;
    await _channel.invokeMethod<void>('openSystemHomeSettings');
  }

  /// Xiaomi-specific navigation-mode settings, if available; falls back
  /// to general Android settings. Returns true only for the direct page.
  /// Does NOT change the OS gesture mode or grant privileges.
  Future<bool> openSystemNavigationSettings() async {
    if (!supported) return false;
    return await _channel.invokeMethod<bool>('openSystemNavigationSettings') ?? false;
  }

  Future<void> openLiveWallpaperPicker() async {
    if (!supported) throw UnsupportedError('Android is required for live wallpaper.');
    await _channel.invokeMethod<void>('openPicker');
  }
}

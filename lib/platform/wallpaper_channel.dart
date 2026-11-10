import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class WallpaperConfig {
  const WallpaperConfig({this.scene = 'meadow', this.occupied = '', this.configJson, this.nativeBackend, this.oceanPhotoEnabled = false});
  final String scene;
  final String occupied;
  final String? configJson;
  final String? nativeBackend;
  final bool oceanPhotoEnabled;
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

  Future<void> openLiveWallpaperPicker() async {
    if (!supported) throw UnsupportedError('Android is required for live wallpaper.');
    await _channel.invokeMethod<void>('openPicker');
  }
}

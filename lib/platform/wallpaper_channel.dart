import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class WallpaperConfig {
  const WallpaperConfig({this.scene = 'meadow', this.occupied = ''});
  final String scene;
  final String occupied;
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
    );
  }

  Future<void> save(WallpaperConfig config) async {
    if (!supported) return;
    await _channel.invokeMethod<void>('save', {
      'scene': config.scene,
      'occupied': config.occupied,
    });
  }

  Future<void> openLiveWallpaperPicker() async {
    if (!supported) throw UnsupportedError('Android is required for live wallpaper.');
    await _channel.invokeMethod<void>('openPicker');
  }
}

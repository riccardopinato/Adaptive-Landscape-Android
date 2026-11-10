package it.riccardopinato.adaptivelandscape

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "adaptive_landscape/wallpaper")
            .setMethodCallHandler { call, result ->
                val prefs = getSharedPreferences("adaptive_landscape", MODE_PRIVATE)
                when (call.method) {
                    "read" -> result.success(mapOf(
                        "scene" to prefs.getString("scene", "meadow"),
                        "occupied" to prefs.getString("occupied", "")
                    ))
                    "save" -> {
                        val scene = call.argument<String>("scene")
                        val icons = call.argument<String>("occupied")
                        if (scene !in setOf("meadow", "beach") || icons == null ||
                            !Regex("[0-9,]*").matches(icons) || icons.length > 150) {
                            result.error("INVALID_LAYOUT", "Invalid theme or icon grid.", null)
                            return@setMethodCallHandler
                        }
                        val ok = prefs.edit().putString("scene", scene)
                            .putString("occupied", icons).commit()
                        if (ok) result.success(null)
                        else result.error("SAVE_FAILED", "Could not save wallpaper settings.", null)
                    }
                    "openPicker" -> {
                        try {
                            val component = ComponentName(this, AdaptiveWallpaperService::class.java)
                            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
                            startActivity(intent)
                            result.success(null)
                        } catch (e: Exception) {
                            try {
                                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                                result.success(null)
                            } catch (fallback: Exception) {
                                result.error("PICKER_UNAVAILABLE", fallback.message, null)
                            }
                        }
                    }
                    else -> result.notImplemented()
                }
            }
    }
}

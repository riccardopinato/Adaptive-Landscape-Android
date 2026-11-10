package it.riccardopinato.adaptivelandscape

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

/** Android-only bridge: no launcher usage, screenshots, or sensitive permissions. */
class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "adaptive_landscape/wallpaper")
            .setMethodCallHandler { call, result ->
                val prefs = getSharedPreferences("adaptive_landscape", MODE_PRIVATE)
                when (call.method) {
                    "read" -> result.success(mapOf(
                        "scene" to prefs.getString("scene", "meadow"),
                        "occupied" to prefs.getString("occupied", ""),
                        "config" to prefs.getString("config_v2", null),
                    ))
                    "save" -> {
                        val scene = call.argument<String>("scene")
                        val icons = call.argument<String>("occupied")
                        val config = call.argument<String>("config")
                        if (scene !in setOf("meadow", "beach") || icons == null ||
                            !Regex("[0-9,]*").matches(icons) || icons.length > 320) {
                            result.error("INVALID_LAYOUT", "Invalid theme or icon grid.", null)
                            return@setMethodCallHandler
                        }
                        if (config != null) {
                            try {
                                if (config.length > 16000 || JSONObject(config).optInt("schema", -1) != 2) {
                                    throw IllegalArgumentException("Invalid scene contract")
                                }
                            } catch (_: Exception) {
                                result.error("INVALID_CONFIG", "Unsupported version or malformed JSON.", null)
                                return@setMethodCallHandler
                            }
                        }
                        val write = prefs.edit().putString("scene", scene).putString("occupied", icons)
                        if (config != null) write.putString("config_v2", config)
                        if (write.commit()) result.success(null)
                        else result.error("SAVE_FAILED", "Could not save wallpaper settings.", null)
                    }
                    "openPicker" -> {
                        try {
                            val component = ComponentName(this, AdaptiveWallpaperService::class.java)
                            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component))
                            result.success(null)
                        } catch (_: Exception) {
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

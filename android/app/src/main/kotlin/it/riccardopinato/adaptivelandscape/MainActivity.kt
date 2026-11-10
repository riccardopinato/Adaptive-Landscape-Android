package it.riccardopinato.adaptivelandscape

import android.app.WallpaperManager
import android.app.Activity
import kotlin.concurrent.thread
import java.io.ByteArrayOutputStream
import android.content.ComponentName
import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

/** Android-only bridge: no launcher usage, screenshots, or sensitive permissions. */
class MainActivity : FlutterActivity() {
    companion object {
        private const val SCREENSHOT_PICKER = 9127
        private const val OCEAN_PICKER = 9128
        private const val MAX_SCREENSHOT_BYTES = 10 * 1024 * 1024
    }

    private var screenshotResult: MethodChannel.Result? = null
    private var oceanResult: MethodChannel.Result? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OCEAN_PICKER) {
            val callback = oceanResult ?: return
            if (resultCode != Activity.RESULT_OK || data?.data == null) {
                oceanResult = null
                callback.success(false)
                return
            }
            val uri = data.data!!
            thread(name = "ocean-photo-import") {
                try {
                    OceanPhotoStore.import(this, uri)
                    getSharedPreferences("adaptive_landscape", MODE_PRIVATE).edit()
                        .putLong(OceanPhotoStore.CHANGE_KEY, System.currentTimeMillis()).apply()
                    runOnUiThread {
                        oceanResult = null
                        callback.success(true)
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        oceanResult = null
                        callback.error("PHOTO_IMPORT_FAILED",
                            error.message ?: "Could not import image", null)
                    }
                }
            }
            return
        }
        if (requestCode != SCREENSHOT_PICKER) return
        val callback = screenshotResult ?: return
        screenshotResult = null
        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            callback.success(null)
            return
        }
        val screenshotUri = data.data!!
        thread(name = "screenshot-picker-read") {
          try {
            val contents = ByteArrayOutputStream()
            contentResolver.openInputStream(screenshotUri).use { stream ->
                requireNotNull(stream) { "Screenshot not accessible" }
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    if (contents.size() + n > MAX_SCREENSHOT_BYTES)
                        throw IllegalArgumentException("Screenshot exceeds 10 MB")
                    contents.write(buffer, 0, n)
                }
            }
            val bytes = contents.toByteArray()
            runOnUiThread { callback.success(bytes) }
          } catch (_: Exception) {
            runOnUiThread {
              callback.error("IMAGE_READ_FAILED", "Unable to read the selected image.", null)
            }
          }
        }
    }

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
                        "backend" to prefs.getString("render_backend_v1", "not_started"),
                        "hasOceanPhoto" to OceanPhotoStore.exists(this),
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
                    "pickOceanPhoto" -> {
                        if (oceanResult != null) {
                            result.error("IMAGE_BUSY", "Photo picker already open", null)
                            return@setMethodCallHandler
                        }
                        oceanResult = result
                        try {
                            startActivityForResult(
                                Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("image/*")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                OCEAN_PICKER
                            )
                        } catch (error: Exception) {
                            oceanResult = null
                            result.error("PICKER_UNAVAILABLE", error.message, null)
                        }
                    }
                    "clearOceanPhoto" -> {
                        if (oceanResult != null) {
                            result.error("IMAGE_BUSY", "Ocean photo import is in progress", null)
                            return@setMethodCallHandler
                        }
                        if (!OceanPhotoStore.remove(this)) {
                            result.error("DELETE_FAILED", "Could not remove ocean photo", null)
                        } else {
                            prefs.edit().putLong(OceanPhotoStore.CHANGE_KEY,
                                System.currentTimeMillis()).apply()
                            result.success(null)
                        }
                    }
                    "pickHomeScreenshot" -> {
                        if (screenshotResult != null) {
                            result.error("IMAGE_BUSY", "An image selection is already in progress.", null)
                            return@setMethodCallHandler
                        }
                        screenshotResult = result
                        try {
                            startActivityForResult(
                                Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("image/*")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                SCREENSHOT_PICKER
                            )
                        } catch (_: Exception) {
                            screenshotResult = null
                            result.error("IMAGE_PICKER_UNAVAILABLE", "Could not open screenshot picker.", null)
                        }
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

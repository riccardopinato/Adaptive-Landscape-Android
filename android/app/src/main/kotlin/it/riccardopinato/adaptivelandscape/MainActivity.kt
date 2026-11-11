package it.riccardopinato.adaptivelandscape

import android.app.WallpaperManager
import android.app.role.RoleManager
import android.os.Build
import android.provider.Settings
import android.content.Context
import android.app.Activity
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
        private const val OCEAN_PHOTO_PICKER = 9128
        private const val AUTO_HOME_ROLE_REQUEST = 9129
        private const val MAX_SCREENSHOT_BYTES = 10 * 1024 * 1024
    }

    private var screenshotResult: MethodChannel.Result? = null
    private var oceanPhotoResult: MethodChannel.Result? = null
    private var autoHomeResult: MethodChannel.Result? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTO_HOME_ROLE_REQUEST) {
            val callback = autoHomeResult
            autoHomeResult = null
            if (resultCode == Activity.RESULT_OK) {
                startActivity(Intent(this, AdaptiveHomeActivity::class.java))
                callback?.success(true)
            } else callback?.success(false)
            return
        }
        if (requestCode == OCEAN_PHOTO_PICKER) {
            val callback = oceanPhotoResult ?: return
            oceanPhotoResult = null
            if (resultCode != Activity.RESULT_OK || data?.data == null) {
                callback.success(false)
                return
            }
            val uri = data.data!!
            // Decode/resize and disk I/O must never block the Android UI thread.
            Thread {
                val operation = runCatching { OceanPhotoStore.importPhoto(applicationContext, uri) }
                runOnUiThread {
                    operation.onSuccess { callback.success(true) }
                        .onFailure { error -> callback.error("OCEAN_PHOTO_REJECTED",
                            error.message ?: "Unable to import ocean photograph", null) }
                }
            }.start()
            return
        }
        if (requestCode != SCREENSHOT_PICKER) return
        val callback = screenshotResult ?: return
        screenshotResult = null
        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            callback.success(null)
            return
        }
        try {
            val contents = ByteArrayOutputStream()
            contentResolver.openInputStream(data.data!!).use { stream ->
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
            callback.success(contents.toByteArray())
        } catch (_: Exception) {
            callback.error("IMAGE_READ_FAILED", "Unable to read the selected image.", null)
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
                        "oceanPhoto" to OceanPhotoStore.exists(this),
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
                    "pickHomeScreenshot" -> {
                        if (screenshotResult != null || oceanPhotoResult != null) {
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
                    "pickOceanPhoto" -> {
                        if (oceanPhotoResult != null || screenshotResult != null) {
                            result.error("IMAGE_BUSY", "Another image selection is active", null)
                            return@setMethodCallHandler
                        }
                        oceanPhotoResult = result
                        try {
                            startActivityForResult(
                                Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("image/*")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                OCEAN_PHOTO_PICKER
                            )
                        } catch (_: Exception) {
                            oceanPhotoResult = null
                            result.error("IMAGE_PICKER_UNAVAILABLE", "Cannot open photo picker", null)
                        }
                    }
                    "clearOceanPhoto" -> {
                        try {
                            OceanPhotoStore.clear(this)
                            result.success(null)
                        } catch (error: Exception) {
                            result.error("OCEAN_PHOTO_REMOVE_FAILED", error.message, null)
                        }
                    }
                    "requestAdaptiveHome" -> {
                        if (autoHomeResult != null) {
                            result.error("HOME_ROLE_BUSY", "Home selection is already active", null)
                            return@setMethodCallHandler
                        }
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val roles = getSystemService(Context.ROLE_SERVICE) as RoleManager
                                if (!roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                                    result.error("HOME_ROLE_UNAVAILABLE", "This Android device has no Home role", null)
                                } else if (roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                                    startActivity(Intent(this, AdaptiveHomeActivity::class.java))
                                    result.success(true)
                                } else {
                                    autoHomeResult = result
                                    startActivityForResult(
                                        roles.createRequestRoleIntent(RoleManager.ROLE_HOME),
                                        AUTO_HOME_ROLE_REQUEST)
                                }
                            } else {
                                // Android 8/9 uses default-app Settings instead of RoleManager.
                                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                                result.success(false)
                            }
                        } catch (error: Exception) {
                            autoHomeResult = null
                            result.error("HOME_ROLE_FAILED", error.message, null)
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

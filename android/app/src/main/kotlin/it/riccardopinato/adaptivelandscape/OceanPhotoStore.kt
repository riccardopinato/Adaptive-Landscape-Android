package it.riccardopinato.adaptivelandscape

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * User-selected photographic water only. Never fetches images from the network.
 * Input is validated, resized, stripped of metadata and written atomically to
 * app-private storage; a rejected import leaves the preceding photo untouched.
 */
internal object OceanPhotoStore {
    private const val FILE_NAME = "ocean_photo_v1.jpg"
    private const val MAX_INPUT_BYTES = 12 * 1024 * 1024
    private const val MAX_DECODED_PIXELS = 45_000_000L
    private const val MAX_EDGE = 2560
    private const val GENERATION_KEY = "ocean_photo_generation_v1"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)
    fun exists(context: Context): Boolean = file(context).isFile && file(context).length() > 0L

    private fun notifyWallpaper(context: Context) {
        val prefs = context.getSharedPreferences("adaptive_landscape", Context.MODE_PRIVATE)
        val next = prefs.getLong(GENERATION_KEY, 0L) + 1L
        check(prefs.edit().putLong(GENERATION_KEY, next).commit()) {
            "Could not notify wallpaper about photo change"
        }
    }

    fun importPhoto(context: Context, uri: Uri) {
        val source = File.createTempFile("ocean_input_", ".tmp", context.cacheDir)
        val candidate = File(context.filesDir, "$FILE_NAME.tmp")
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Selected photo cannot be opened")
            stream.use { input ->
                FileOutputStream(source).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_INPUT_BYTES) { "Photo must be 12 MB or smaller" }
                        output.write(buffer, 0, n)
                    }
                    require(total > 0) { "Empty photo" }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            require(bounds.outWidth >= 720 && bounds.outHeight >= 720) {
                "Photo must be at least 720px in both directions"
            }
            require(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= MAX_DECODED_PIXELS) {
                "Photo resolution is too large"
            }
            var sampleSize = 1
            while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_EDGE)
                sampleSize *= 2
            val bitmap = BitmapFactory.decodeFile(source.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }) ?: throw IllegalArgumentException("Invalid or unsupported photo format")
            try {
                FileOutputStream(candidate).use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 91, output)) {
                        "Photo encoding failed"
                    }
                    output.fd.sync()
                }
            } finally {
                bitmap.recycle()
            }
            require(candidate.length() > 0L) { "Encoded photo is empty" }
            // Atomic replacement on the same app-private filesystem.
            check(candidate.renameTo(file(context))) { "Unable to save selected photo" }
            notifyWallpaper(context)
        } finally {
            source.delete()
            candidate.delete()
        }
    }

    fun clear(context: Context) {
        val stored = file(context)
        check(!stored.exists() || stored.delete()) { "Unable to remove ocean photo" }
        notifyWallpaper(context)
    }
}

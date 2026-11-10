package it.riccardopinato.adaptivelandscape

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * User-provided real-ocean photograph. Local-only persistent app asset, not a
 * screenshot, photo library scan, upload, analytics event, or bundled stock.
 * Region decoding caps both decoded memory and GPU texture footprint.
 */
internal object OceanPhotoStore {
    const val CHANGE_KEY = "ocean_photo_rev"
    private const val NAME = "ocean_wallpaper_user.jpg"

    fun file(context: Context): File = File(context.filesDir, NAME)
    fun exists(context: Context): Boolean = file(context).isFile
    fun remove(context: Context): Boolean = !exists(context) || file(context).delete()

    fun import(context: Context, uri: Uri) {
        context.contentResolver.openInputStream(uri).use { source ->
            val decoder = BitmapRegionDecoder.newInstance(
                requireNotNull(source) { "Could not open selected photo" }, false)
            requireNotNull(decoder) { "Unsupported image format" }
            try {
                val w = decoder.width
                val h = decoder.height
                require(w in 800..16000 && h in 1400..16000) {
                    "Choose a detailed ocean photo (at least 800×1400 pixels)"
                }
                // Original photograph remains unmodified outside this app.
                // Crop a portrait region matching typical launcher wallpaper.
                val ratio = 9.0 / 19.5
                val cropWidth: Int
                val cropHeight: Int
                if (w.toDouble() / h > ratio) {
                    cropHeight = h
                    cropWidth = (h * ratio).roundToInt().coerceAtMost(w)
                } else {
                    cropWidth = w
                    cropHeight = (w / ratio).roundToInt().coerceAtMost(h)
                }
                val left = (w - cropWidth) / 2
                val top = (h - cropHeight) / 2
                val box = Rect(left, top, left + cropWidth, top + cropHeight)
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = if (cropWidth > 3200 || cropHeight > 5700) 2 else 1
                }
                val bitmap = requireNotNull(decoder.decodeRegion(box, options)) {
                    "Unable to decode the selected ocean photo"
                }
                val scale = min(1.0, min(2160.0 / bitmap.width, 3840.0 / bitmap.height))
                val resized = if (scale < 1) Bitmap.createScaledBitmap(bitmap,
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1), true) else bitmap
                val temp = File(context.filesDir, "$NAME.tmp")
                try {
                    FileOutputStream(temp).use {
                        check(resized.compress(Bitmap.CompressFormat.JPEG, 90, it))
                    }
                    require(temp.length() in 1024..(12 * 1024 * 1024)) {
                        "Ocean photo exceeds the 12 MB on-device limit"
                    }
                    // Atomic replace when supported by app-private storage.
                    if (!temp.renameTo(file(context))) {
                        temp.copyTo(file(context), overwrite = true)
                        temp.delete()
                    }
                } finally {
                    if (resized !== bitmap) resized.recycle()
                    bitmap.recycle()
                    if (temp.exists()) temp.delete()
                }
            } finally {
                decoder.recycle()
            }
        }
    }
}

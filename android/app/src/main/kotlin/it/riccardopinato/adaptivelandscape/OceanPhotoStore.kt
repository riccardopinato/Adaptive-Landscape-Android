package it.riccardopinato.adaptivelandscape

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.graphics.Matrix
import android.media.ExifInterface
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

    fun saveFromUri(context: Context, uri: Uri) {
        val orientation = context.contentResolver.openInputStream(uri).use { metadata ->
            if (metadata == null) ExifInterface.ORIENTATION_NORMAL else {
                ExifInterface(metadata).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }
        val swapped = orientation in setOf(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE)
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
                val ratio = if (swapped) 19.5 / 9.0 else 9.0 / 19.5
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
                val targetW = if (swapped) 3840 else 2160
                val targetH = if (swapped) 2160 else 3840
                var sample = 1
                while (cropWidth / sample > targetW * 1.25 ||
                       cropHeight / sample > targetH * 1.25) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply {
                    // RGB_565 was quantizing the photographic colors before
                    // JPEG export; retain 8-bit/channel to avoid banding.
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = sample
                }
                val bitmap = requireNotNull(decoder.decodeRegion(box, options)) {
                    "Unable to decode the selected ocean photo"
                }
                val transform = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> transform.setRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> transform.setRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> transform.setRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform.setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform.setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        transform.setRotate(90f)
                        transform.postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        transform.setRotate(270f)
                        transform.postScale(-1f, 1f)
                    }
                }
                val upright = if (transform.isIdentity) bitmap else
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
                val scale = min(1.0, min(2160.0 / upright.width, 3840.0 / upright.height))
                val resized = if (scale < 1) Bitmap.createScaledBitmap(upright,
                    (upright.width * scale).roundToInt().coerceAtLeast(1),
                    (upright.height * scale).roundToInt().coerceAtLeast(1), true) else upright
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
                    if (resized !== upright) resized.recycle()
                    if (upright !== bitmap) upright.recycle()
                    bitmap.recycle()
                    if (temp.exists()) temp.delete()
                }
            } finally {
                decoder.recycle()
            }
        }
    }
}

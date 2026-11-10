package it.riccardopinato.adaptivelandscape

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils

/** A real user-supplied ocean image, decoded only when the EGL context exists. */
internal class OceanTexture(private val context: Context) {
    var id = 0
        private set
    val available get() = id != 0

    fun upload() {
        release()
        val photo = OceanPhotoStore.file(context)
        if (!photo.isFile) return
        var bitmap: Bitmap? = null
        try {
            bitmap = BitmapFactory.decodeFile(photo.absolutePath,
                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            val decoded = bitmap ?: return
            val max = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0)
            if (max[0] < decoded.width || max[0] < decoded.height) return
            val result = IntArray(1)
            GLES20.glGenTextures(1, result, 0)
            if (result[0] == 0) return
            id = result[0]
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, decoded, 0)
            if (GLES20.glGetError() != GLES20.GL_NO_ERROR) release()
        } catch (_: Exception) {
            release()
        } catch (_: OutOfMemoryError) {
            release()
        } finally {
            bitmap?.recycle()
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        }
    }

    fun bindOrFallback(maskId: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (available) id else maskId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    fun release() {
        if (id != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(id), 0)
            id = 0
        }
    }
}

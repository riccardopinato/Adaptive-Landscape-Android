package it.riccardopinato.adaptivelandscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Original aerial shore + sea renderer. No horizon, sky, clouds or scene assets.
 * The geometry is cached when settings/surface change. Dynamic water/foam are
 * rendered into a small bitmap and stretched with bilinear sampling.
 * No GPL shaders, assets or source code are copied from Deskland.
 */
internal class CoastalBeachRenderer {
    private var raster: Bitmap? = null
    private var land: FloatArray = floatArrayOf()
    private var grains: FloatArray = floatArrayOf()
    private var pixels: IntArray = intArrayOf()
    private var gridWidth = 0
    private var gridHeight = 0
    private var cachedState: WallpaperState? = null
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun invalidate() { cachedState = null }

    fun dispose() {
        raster?.recycle()
        raster = null
        pixels = intArrayOf()
        land = floatArrayOf()
        grains = floatArrayOf()
        cachedState = null
        gridWidth = 0
        gridHeight = 0
    }

    private fun ensureGeometry(canvas: Canvas, state: WallpaperState) {
        val maxSide = max(canvas.width, canvas.height).coerceAtLeast(1)
        val ratio = 360.0 / maxSide
        val w = (canvas.width * ratio).roundToInt().coerceIn(2, 360)
        val h = (canvas.height * ratio).roundToInt().coerceIn(2, 360)
        if (cachedState === state && w == gridWidth && h == gridHeight && raster != null) return
        val updated = w != gridWidth || h != gridHeight || raster == null
        if (updated) {
            raster?.recycle()
            raster = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            pixels = IntArray(w * h)
            gridWidth = w
            gridHeight = h
        }
        land = FloatArray(w * h)
        grains = FloatArray(w * h)
        for (y in 0 until h) {
            val ny = (y + 0.5f) / h
            val shoreline = 0.30 + 0.078 * sin(ny * 5.4 + 0.3) +
                0.031 * sin(ny * 14.2 + 0.9) + 0.013 * sin(ny * 29.0)
            for (x in 0 until w) {
                val nx = (x + 0.5f) / w
                val i = y * w + x
                land[i] = (shoreline - nx + 0.84 * state.influence(nx, ny)).toFloat()
                grains[i] = grain(x, y)
            }
        }
        cachedState = state
    }

    private fun grain(x: Int, y: Int): Float {
        var n = ((x + 17) * 374761393 + (y + 53) * 668265263) and 0x7fffffff
        n = ((n xor (n ushr 13)) * 1274126177) and 0x7fffffff
        return ((n % 4096) / 4095.0 * 2.0 - 1.0).toFloat()
    }

    private val levels = doubleArrayOf(
        -1.0, -0.42, -0.23, -0.13, -0.073, -0.027,
        -0.005, 0.018, 0.072, 0.18, 1.0,
    )
    private val red = intArrayOf(8, 10, 22, 65, 112, 183, 203, 195, 218, 241, 248)
    private val green = intArrayOf(111, 133, 163, 191, 209, 229, 227, 190, 207, 228, 239)
    private val blue = intArrayOf(151, 166, 185, 191, 196, 207, 204, 179, 190, 207, 222)

    private fun color(
        s: Double, speckle: Float, ripple: Double, nearWave: Double,
        palette: String, night: Boolean,
    ): Int {
        var high = 1
        while (high < levels.size - 1 && levels[high] < s) high++
        val k = ((s - levels[high - 1]) / (levels[high] - levels[high - 1])).coerceIn(0.0, 1.0)
        var r = red[high - 1] * (1 - k) + red[high] * k
        var g = green[high - 1] * (1 - k) + green[high] * k
        var b = blue[high - 1] * (1 - k) + blue[high] * k

        val water = s < -0.025
        val shimmer = if (water) ripple * 2.5 else 0.0
        val noise = speckle * if (water) 6.0 else 3.0
        r += shimmer + noise; g += shimmer + noise; b += shimmer + noise

        val foam = exp(-((s / 0.012) * (s / 0.012))) * 0.77 +
            exp(-((nearWave / 0.008) * (nearWave / 0.008))) * 0.23
        val f = foam.coerceIn(0.0, 0.88)
        r += (246 - r) * f; g += (247 - g) * f; b += (237 - b) * f

        when (palette) {
            "sunset" -> { r = r * 1.05 + 3; g = g * 0.94 + 6; b = b * 0.89 + 4 }
            "mint" -> { r *= 0.93; g = g * 1.02 + 2; b = b * 0.96 + 3 }
        }
        if (night) {
            r *= 0.52; g = g * 0.59 + 3; b = b * 0.70 + 11
        }
        return Color.rgb(
            r.roundToInt().coerceIn(0, 255),
            g.roundToInt().coerceIn(0, 255),
            b.roundToInt().coerceIn(0, 255),
        )
    }

    fun draw(canvas: Canvas, state: WallpaperState, phase: Double) {
        ensureGeometry(canvas, state)
        val w = gridWidth
        val h = gridHeight
        if (w == 0 || h == 0) return

        for (y in 0 until h) {
            val ny = (y + 0.5) / h
            val tide = 0.017 * sin(phase + ny * 6.8) +
                0.007 * sin(phase * 1.65 + ny * 17.0)
            val nearWaveOffset = 0.068 + 0.012 * sin(ny * 22 + phase * 1.7)
            val shimmer = sin(ny * 84 - phase * 2.2) + sin(ny * 133 + phase * 1.4)
            for (x in 0 until w) {
                val i = y * w + x
                val s = land[i] + tide
                pixels[i] = color(s, grains[i], shimmer, s + nearWaveOffset,
                    state.palette, state.night)
            }
        }
        val surface = raster ?: return
        surface.setPixels(pixels, 0, w, 0, 0, w, h)
        canvas.drawBitmap(surface, null, Rect(0, 0, canvas.width, canvas.height), bitmapPaint)
    }
}

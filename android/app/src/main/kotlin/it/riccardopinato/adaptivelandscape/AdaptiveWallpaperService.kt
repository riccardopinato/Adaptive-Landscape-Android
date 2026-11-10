package it.riccardopinato.adaptivelandscape

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Original, first-party Android renderer. No DesktopHost, WebView2 or GPL code/graphics. */
class AdaptiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LandscapeEngine()

    private inner class LandscapeEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val prefs = getSharedPreferences("adaptive_landscape", Context.MODE_PRIVATE)
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var visible = false
        private var scene = "meadow"
        private var occupied = emptySet<Int>()
        private var offset = 0f
        private var startTime = System.currentTimeMillis()
        private val frame = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, frameDelay())
            }
        }

        init {
            prefs.registerOnSharedPreferenceChangeListener(this)
            reload()
        }

        private fun frameDelay(): Long {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            return if (power.isPowerSaveMode) 125L else 50L // 8 or 20 fps, only while visible.
        }

        private fun reload() {
            scene = if (prefs.getString("scene", "meadow") == "beach") "beach" else "meadow"
            occupied = prefs.getString("occupied", "")!!.split(',')
                .mapNotNull { it.toIntOrNull() }.filter { it in 0 until 40 }.toSet()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            if (key == "scene" || key == "occupied") {
                reload()
                if (visible) drawFrame()
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(frame)
            if (isVisible) handler.post(frame)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float,
                                      yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            offset = xOffset
            if (visible) drawFrame()
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(frame)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }

        private fun influence(x: Float, y: Float): Float {
            var total = 0.0
            for (index in occupied) {
                val iconX = ((index % 5) + 0.5) / 5.0
                val iconY = 0.115 + ((index / 5) + 0.5) * 0.765 / 8.0
                val dx = (x - iconX) / 0.115
                val dy = (y - iconY) / 0.073
                total += exp(-0.5 * (dx * dx + dy * dy))
            }
            return total.coerceIn(0.0, 1.0).toFloat()
        }

        private fun drawFrame() {
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas() ?: return
                drawLandscape(canvas)
            } catch (_: IllegalArgumentException) {
                // Surface may have been disposed during launcher transition.
            } catch (_: IllegalStateException) {
                // Do not crash wallpaper process on transient surface loss.
            } finally {
                if (canvas != null) {
                    try { surfaceHolder.unlockCanvasAndPost(canvas) }
                    catch (_: Exception) { /* Surface destroyed between lock and unlock. */ }
                }
            }
        }

        private fun drawLandscape(c: Canvas) {
            val w = c.width.toFloat()
            val h = c.height.toFloat()
            if (w <= 0f || h <= 0f) return
            val phase = ((System.currentTimeMillis() - startTime) % 12000L) / 12000.0 * 2 * PI
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, 0f, 0f, h,
                if (scene == "beach") Color.rgb(107, 217, 231) else Color.rgb(123, 184, 230),
                if (scene == "beach") Color.rgb(219, 251, 236) else Color.rgb(242, 247, 223),
                Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            paint.color = Color.rgb(255, 243, 194)
            c.drawCircle(w * 0.77f, h * 0.18f, w * 0.09f, paint)

            // Clouds move with the scene and avoid the selected icon footprints.
            for (i in 0 until 3) {
                val cx = w * (0.12f + 0.32f * i) + (sin(phase + i) * w * 0.02f).toFloat() + (offset - 0.5f) * w * 0.03f
                val cy = h * (0.16f + (i % 2) * 0.10f)
                val displaced = cy - influence(cx / w, cy / h) * h * 0.11f
                paint.color = Color.argb(200, 255, 255, 255)
                c.drawOval(cx - w * 0.115f, displaced - h * 0.022f,
                    cx + w * 0.115f, displaced + h * 0.022f, paint)
                c.drawCircle(cx - w * 0.04f, displaced - h * 0.012f, w * 0.045f, paint)
            }

            if (scene == "beach") {
                paint.color = Color.rgb(245, 220, 159)
                c.drawRect(0f, h * 0.52f, w, h, paint)
                val water = Path().apply { moveTo(0f, h * 0.54f) }
                for (i in 0..80) {
                    val x = i / 80f
                    val y = 0.60f + (sin(x * PI * 3 + phase) * 0.018).toFloat() + influence(x, 0.61f) * 0.095f
                    water.lineTo(x * w, y * h)
                }
                water.lineTo(w, h); water.lineTo(0f, h); water.close()
                paint.color = Color.rgb(54, 191, 202)
                c.drawPath(water, paint)
                paint.color = Color.argb(85, 255, 255, 255)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.0f
                for (i in 0 until 5) {
                    val y = h * (0.69f + 0.057f * i)
                    val wave = Path().apply { moveTo(0f, y) }
                    for (j in 0..40) {
                        val x = j / 40f
                        wave.lineTo(x * w, y + (sin(x * 16 + phase * 2 + i) * 3).toFloat())
                    }
                    c.drawPath(wave, paint)
                }
                paint.style = Paint.Style.FILL
            } else {
                paint.color = Color.rgb(146, 185, 109)
                c.drawRect(0f, h * 0.49f, w, h, paint)
                for (layer in 0..1) {
                    val base = 0.53f + 0.115f * layer
                    val hill = Path().apply { moveTo(0f, base * h) }
                    for (i in 0..70) {
                        val x = i / 70f
                        val y = base + (sin(x * 9 + layer * 3.0) * 0.035).toFloat() + influence(x, base) * 0.065f
                        hill.lineTo(x * w, y * h)
                    }
                    hill.lineTo(w, h); hill.lineTo(0f, h); hill.close()
                    paint.color = if (layer == 0) Color.rgb(111, 159, 98) else Color.rgb(75, 136, 90)
                    c.drawPath(hill, paint)
                }
                for (i in 0 until 90) {
                    val x = ((i * 73) % 97) / 97f
                    val y = 0.68f + ((i * 37) % 29) / 29f * 0.32f
                    if (influence(x, y) > 0.38f) continue
                    paint.color = if (i % 3 == 0) Color.rgb(246, 223, 166) else Color.rgb(217, 234, 169)
                    c.drawCircle(x * w, y * h, if (i % 5 == 0) 2.8f else 1.4f, paint)
                }
            }
        }
    }
}

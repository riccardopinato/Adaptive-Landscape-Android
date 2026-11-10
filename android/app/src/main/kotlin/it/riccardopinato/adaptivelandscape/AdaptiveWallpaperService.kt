package it.riccardopinato.adaptivelandscape

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
import kotlin.math.sin

/** First-party Canvas renderer; independent from the GPL desktop inspiration. */
class AdaptiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LandscapeEngine()

    private inner class LandscapeEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val prefs = getSharedPreferences("adaptive_landscape", Context.MODE_PRIVATE)
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val coastalBeach = CoastalBeachRenderer()
        private var visible = false
        private var settings = WallpaperState()
        private var offset = 0.5f
        private var startTime = System.currentTimeMillis()
        private val frame = object : Runnable {
            override fun run() {
                if (!visible) return
                drawFrame()
                if (visible && !settings.staticScene) handler.postDelayed(this, frameDelay())
            }
        }

        init {
            prefs.registerOnSharedPreferenceChangeListener(this)
            reload()
        }

        private fun frameDelay(): Long {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            return if (power.isPowerSaveMode) 125L else 50L // 8fps / 20fps while visible
        }

        private fun reload() {
            settings = WallpaperState.fromPrefs(prefs)
            coastalBeach.invalidate()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            if (key == "scene" || key == "occupied" || key == "config_v2") {
                reload()
                handler.removeCallbacks(frame)
                if (visible) handler.post(frame)
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(frame)
            if (visible) handler.post(frame)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (visible) drawFrame()
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float,
                                      yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            offset = xOffset.coerceIn(0f, 1f)
            if (visible) drawFrame()
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(frame)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            coastalBeach.dispose()
            super.onDestroy()
        }

        private fun influence(x: Float, y: Float): Float = settings.influence(x, y)

        private fun drawFrame() {
            if (!surfaceHolder.surface.isValid) return
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas() ?: return
                drawLandscape(canvas)
            } catch (_: IllegalArgumentException) {
                // Launcher may destroy/recreate the wallpaper surface mid-frame.
            } catch (_: IllegalStateException) {
                // Surface not ready during activity lifecycle change.
            } finally {
                if (canvas != null) {
                    try { surfaceHolder.unlockCanvasAndPost(canvas) }
                    catch (_: Exception) { /* Surface changed during release. */ }
                }
            }
        }

        private fun drawLandscape(c: Canvas) {
            val w = c.width.toFloat()
            val h = c.height.toFloat()
            if (w <= 0f || h <= 0f) return
            val t = if (settings.staticScene) 0.0 else
                ((System.currentTimeMillis() - startTime) % 12000L) / 12000.0 * 2 * PI * settings.motion
            if (settings.scene == "beach") {
                coastalBeach.draw(c, settings, t)
                return
            }
            val night = settings.night
            val beach = settings.scene == "beach"
            val sunset = settings.palette == "sunset"
            val mint = settings.palette == "mint"

            val skyTop = when {
                night -> Color.rgb(16, 35, 76)
                sunset -> Color.rgb(239, 145, 151)
                mint -> Color.rgb(133, 207, 197)
                beach -> Color.rgb(107, 217, 231)
                else -> Color.rgb(123, 184, 230)
            }
            val skyBottom = when {
                night -> Color.rgb(77, 99, 141)
                sunset -> Color.rgb(250, 212, 175)
                mint -> Color.rgb(231, 247, 211)
                beach -> Color.rgb(219, 251, 236)
                else -> Color.rgb(242, 247, 223)
            }
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, 0f, 0f, h, skyTop, skyBottom, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null

            if (night) {
                for (i in 0 until 28) {
                    val x = ((i * 71) % 101) / 101f
                    val y = ((i * 41) % 43) / 100f
                    if (influence(x, y) > 0.4f) continue
                    paint.color = Color.argb(160 + i % 90, 246, 248, 255)
                    c.drawCircle(x * w, y * h, if (i % 7 == 0) 2.5f else 1.3f, paint)
                }
            }
            var sunY = 0.18f
            sunY -= influence(0.77f, sunY) * 0.08f
            paint.color = if (night) Color.rgb(220, 233, 253) else Color.rgb(255, 243, 194)
            c.drawCircle(w * 0.77f, h * sunY, w * 0.09f, paint)

            for (i in 0 until 3) {
                val cx = w * (0.12f + 0.32f * i) + (sin(t + i) * w * 0.02f).toFloat() +
                    (offset - 0.5f) * w * 0.03f
                val cy = h * (0.16f + (i % 2) * 0.10f)
                val displaced = cy - influence(cx / w, cy / h) * h * 0.11f
                paint.color = if (night) Color.argb(115, 193, 209, 234) else Color.argb(195, 255, 255, 255)
                c.drawOval(cx - w * 0.115f, displaced - h * 0.022f,
                    cx + w * 0.115f, displaced + h * 0.022f, paint)
                c.drawCircle(cx - w * 0.04f, displaced - h * 0.012f, w * 0.045f, paint)
            }

            if (beach) {
                paint.color = if (night) Color.rgb(78, 92, 122) else Color.rgb(245, 220, 159)
                c.drawRect(0f, h * 0.52f, w, h, paint)
                val water = Path().apply { moveTo(0f, h * 0.54f) }
                for (i in 0..80) {
                    val x = i / 80f
                    val y = 0.60f + (sin(x * PI * 3 + t) * 0.018).toFloat() +
                        influence(x, 0.61f) * 0.095f
                    water.lineTo(x * w, y * h)
                }
                water.lineTo(w, h); water.lineTo(0f, h); water.close()
                paint.color = if (night) Color.rgb(36, 95, 139) else if (sunset) Color.rgb(78, 167, 177)
                    else Color.rgb(54, 191, 202)
                c.drawPath(water, paint)
                paint.color = Color.argb(if (night) 60 else 100, 255, 255, 255)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.0f
                for (i in 0 until 6) {
                    val y = h * (0.68f + 0.055f * i)
                    val wave = Path().apply { moveTo(0f, y) }
                    for (j in 0..48) {
                        val x = j / 48f
                        wave.lineTo(x * w, y + (sin(x * 16 + t * 2 + i) * 3).toFloat())
                    }
                    c.drawPath(wave, paint)
                }
                paint.style = Paint.Style.FILL
                // Sparse foam and reflected light stay away from icons and widgets.
                for (i in 0 until 35) {
                    val x = ((i * 59) % 97) / 97f
                    val y = 0.63f + ((i * 29) % 31) / 31f * 0.37f
                    if (influence(x, y) > 0.3f) continue
                    paint.color = Color.argb(100, 245, 249, 236)
                    c.drawCircle(x * w, y * h, 1.2f + (i % 3), paint)
                }
            } else {
                paint.color = if (night) Color.rgb(76, 107, 93) else Color.rgb(146, 185, 109)
                c.drawRect(0f, h * 0.49f, w, h, paint)
                for (layer in 0..2) {
                    val base = 0.53f + 0.087f * layer
                    val hill = Path().apply { moveTo(0f, base * h) }
                    for (i in 0..70) {
                        val x = i / 70f
                        val y = base + (sin(x * 9 + layer * 3.0) * 0.035).toFloat() +
                            influence(x, base) * 0.065f
                        hill.lineTo(x * w, y * h)
                    }
                    hill.lineTo(w, h); hill.lineTo(0f, h); hill.close()
                    paint.color = if (night)
                        listOf(Color.rgb(60, 92, 87), Color.rgb(46, 75, 76), Color.rgb(34, 63, 70))[layer]
                        else if (mint)
                        listOf(Color.rgb(131, 179, 139), Color.rgb(99, 151, 131), Color.rgb(64, 117, 105))[layer]
                        else listOf(Color.rgb(111, 159, 98), Color.rgb(75, 136, 90), Color.rgb(53, 111, 75))[layer]
                    c.drawPath(hill, paint)
                }
                for (i in 0 until 100) {
                    val x = ((i * 73) % 97) / 97f
                    val y = 0.67f + ((i * 37) % 29) / 29f * 0.33f
                    if (influence(x, y) > 0.38f) continue
                    val size = if (i % 5 == 0) 2.8f else 1.4f
                    val sway = (sin(t + i) * 2.0).toFloat()
                    paint.color = if (night) Color.argb(120, 184, 211, 179)
                        else if (i % 3 == 0) Color.rgb(246, 223, 166)
                        else Color.rgb(217, 234, 169)
                    c.drawCircle(x * w + sway, y * h, size, paint)
                }
            }
        }
    }
}

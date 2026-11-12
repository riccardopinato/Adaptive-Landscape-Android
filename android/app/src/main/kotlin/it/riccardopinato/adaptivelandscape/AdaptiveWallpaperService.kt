package it.riccardopinato.adaptivelandscape

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.app.role.RoleManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicBoolean
import android.os.Process
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.sin

/** First-party Canvas renderer; independent from the GPL desktop inspiration. */
class AdaptiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LandscapeEngine()

    private inner class LandscapeEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val prefs = getSharedPreferences("adaptive_landscape", Context.MODE_PRIVATE)
        // WallpaperService.Engine callbacks are on Android's UI thread.
        // Own all Canvas, EGL, GLES, and renderer-cache mutations on one
        // dedicated thread to keep the HOME gestures responsive.
        private val renderThread = HandlerThread(
            // Wallpaper must not out-prioritize Android's Home/touch UI thread.
            "DesklandWallpaperRender", Process.THREAD_PRIORITY_DEFAULT
        ).apply { start() }
        private val renderHandler = Handler(renderThread.looper)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val coastalBeach = CoastalBeachRenderer()
        private val gpuOcean = GpuOceanRenderer()
        private var backendStatus = ""
        private var renderEvidence = ""
        @Volatile private var visible = false
        @Volatile private var surfaceReady = false
        @Volatile private var destroyed = false
        @Volatile private var settings = WallpaperState()
        @Volatile private var offset = 0.5f
        private var startTime = SystemClock.uptimeMillis()
        private val requestFrameQueued = AtomicBoolean(false)
        @Volatile private var lastFrameStartedAt = 0L
        private val frame = object : Runnable {
            override fun run() {
                if (!visible || !surfaceReady || destroyed) return
                lastFrameStartedAt = SystemClock.uptimeMillis()
                drawFrame() // only the rendering worker calls EGL/Canvas
                if (visible && surfaceReady && !destroyed && !settings.staticScene) {
                    renderHandler.postDelayed(this, frameDelay())
                }
            }
        }

        init {
            prefs.registerOnSharedPreferenceChangeListener(this)
            reload()
        }

        private fun frameDelay(): Long {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            val target = when (settings.renderQuality) {
                "balanced" -> 83L  // about 12fps cap
                "high" -> 63L      // about 16fps cap
                else -> 50L        // up to 20fps cap
            }
            // When a real launcher icon is being dragged, cap the expensive
            // full-screen GPU rendering to ~8fps. The wallpaper keeps
            // animating; Android UI gestures take priority. The gesture
            // deadline also expires after abrupt app/process termination.
            val now = System.currentTimeMillis()
            val deadline = prefs.getLong("auto_home_interaction_until_v1", 0L)
            val interacting = deadline > now && deadline - now <= 30_000L
            return if (power.isPowerSaveMode || interacting) maxOf(125L, target)
                else target
            // Delay is a ceiling target, not measured FPS; CPU/GPU time is extra.
        }

        private fun isAdaptiveHomeSelected(): Boolean {
            if (!prefs.getBoolean("auto_home_active_v1", false)) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roles = getSystemService(Context.ROLE_SERVICE) as? RoleManager
                return roles?.isRoleHeld(RoleManager.ROLE_HOME) == true
            }
            val home = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME)
            val match = packageManager.resolveActivity(home, 0)
            return match?.activityInfo?.packageName == packageName
        }

        private fun reload() {
            settings = WallpaperState.fromPrefs(prefs, isAdaptiveHomeSelected())
            renderHandler.post {
                if (destroyed) return@post
                coastalBeach.invalidate()
                gpuOcean.invalidateLayout()
            }
        }

        private fun requestFrame() {
            // Source/wallpaper changes can arrive many times per second.
            // Serialize rescheduling on the render thread and never bypass
            // the frame-rate limit simply because preferences changed.
            if (!requestFrameQueued.compareAndSet(false, true)) return
            renderHandler.post {
                requestFrameQueued.set(false)
                renderHandler.removeCallbacks(frame)
                if (visible && surfaceReady && !destroyed) {
                    val elapsed = (SystemClock.uptimeMillis() -
                        lastFrameStartedAt).coerceAtLeast(0L)
                    renderHandler.postDelayed(frame,
                        (frameDelay() - elapsed).coerceAtLeast(0L))
                }
            }
        }

        private fun reportBackend(name: String) {
            val automatic = isAdaptiveHomeSelected()
            val source = if (automatic) "auto_home" else "manual"
            val zones = settings.occupied.size + settings.zones.size
            val revision = if (automatic) prefs.getLong("auto_home_revision_v1", 0L) else -1L
            val next = "$name/$source/$zones/$revision"
            if (next == renderEvidence) return
            renderEvidence = next
            backendStatus = name
            // This records the state passed to an actual render attempt.
            // It is NOT pixel proof. Never collect app labels or icon IDs.
            prefs.edit()
                .putString("render_backend_v1", name)
                .putString("render_source_v1", source)
                .putInt("render_zone_count_v1", zones)
                .putLong("render_auto_home_revision_v1", revision)
                .apply()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            if (key == "ocean_photo_generation_v1") {
                renderHandler.post {
                    if (!destroyed) gpuOcean.invalidatePhotograph()
                }
            }
            if (key == "scene" || key == "occupied" || key == "config_v2" ||
                key == "auto_home_config_v1" || key == "auto_home_active_v1" ||
                key == "ocean_photo_generation_v1") {
                reload()
                requestFrame()
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (visible) reload() // Role can change while the engine is hidden.
            requestFrame()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceReady = width > 0 && height > 0
            renderHandler.removeCallbacks(frame)
            renderHandler.post {
                gpuOcean.resetSurface() // same thread as EGL creation and draw
                if (visible && surfaceReady && !destroyed) frame.run()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            renderHandler.removeCallbacks(frame)
            renderHandler.post { gpuOcean.resetSurface() }
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float,
                                      yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            offset = xOffset.coerceIn(0f, 1f)
            // Beach doesn't use xOffset; requesting a frame on every
            // horizontal Home swipe would override frame pacing.
            if (settings.scene != "beach") requestFrame()
        }

        override fun onDestroy() {
            visible = false
            surfaceReady = false
            destroyed = true
            renderHandler.removeCallbacks(frame)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            renderHandler.post {
                // Dispose only after the in-flight GL frame exits.
                coastalBeach.dispose()
                gpuOcean.dispose()
                renderThread.quitSafely()
            }
            super.onDestroy()
        }

        private fun influence(x: Float, y: Float): Float = settings.influence(x, y)

        private fun drawFrame() {
            if (!surfaceHolder.surface.isValid) return
            if (settings.scene == "beach") {
                val frameSize = surfaceHolder.surfaceFrame
                val phase = if (settings.staticScene) 0.0 else
                    (SystemClock.uptimeMillis() - startTime).coerceAtLeast(0L) /
                    12000.0 * 2 * PI * settings.motion
                if (gpuOcean.render(this@AdaptiveWallpaperService, surfaceHolder.surface,
                        frameSize.width(), frameSize.height(), settings, phase)) {
                    reportBackend(if (gpuOcean.photographActive) "gpu_photo_native" else "gpu_native")
                    return
                }
                // A driver with no EGL2 support must keep the wallpaper visible.
                reportBackend("cpu_360px_fallback")
            } else {
                reportBackend("meadow_canvas")
            }
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
                (SystemClock.uptimeMillis() - startTime).coerceAtLeast(0L) / 12000.0 * 2 * PI * settings.motion
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

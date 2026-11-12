package it.riccardopinato.adaptivelandscape

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLUtils
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.SystemClock
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI

/**
 * Native, framebuffer-resolution animated ocean for the actual Android
 * WallpaperService surface. Unlike the old 360px Canvas raster, the GPU shades
 * each wallpaper pixel; this DOES NOT imply a photographic source image.
 *
 * GPU and EGL resources live on the WallpaperService engine thread.
 * A Canvas fallback remains if GLES2/EGL is not usable on a device.
 */
internal class GpuOceanRenderer {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE
    private var hostSurface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var shaderProgram = 0
    private var maskTexture = 0
    private var maskPreviousTexture = 0
    private var photoTexture = 0
    // Stable resolution avoids framebuffer-size changes and supports smooth
    // shape morphing when the user moves, adds or removes an icon.
    private val maskWidth = 160
    private val maskHeight = 320
    // One reusable direct transfer buffer for the two fixed-size 2D masks.
    // Never allocate a new direct buffer and storage object on each drag.
    private val maskUploadScratch: ByteBuffer =
        ByteBuffer.allocateDirect(maskWidth * maskHeight * 4)
            .order(ByteOrder.nativeOrder())
    private var maskFrom: ByteArray? = null
    private var maskTo: ByteArray? = null
    private var maskFromHasLand = false
    private var maskToHasLand = false
    private var maskTransitionStarted = 0L
    private val maskMorphDurationMs = 380L
    private var photoFingerprint = Long.MIN_VALUE
    var photographActive = false
        private set
    private var photographAspect = 1f
    private var currentConfig: WallpaperState? = null
    private var disabledForSurface = false
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    private val vertexShader = """
        attribute vec2 aPosition;
        void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }
    """.trimIndent()

    private val fragmentShader = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform vec2 uResolution;
        uniform float uPhase;
        uniform float uNight;
        uniform float uPalette;
        uniform float uQuality;
        uniform float uHasLand;
        uniform sampler2D uMask;
        uniform sampler2D uMaskPrevious;
        uniform float uMaskMix;
        uniform sampler2D uOceanPhoto;
        uniform float uHasPhoto;
        uniform vec2 uPhotoScale;
        uniform float uRefraction;
        uniform float uFoamIntensity;

        float hash(vec2 p) {
            p = fract(p * vec2(123.34, 456.21));
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }
        float noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            vec2 v = f * f * (3.0 - 2.0 * f);
            return mix(
                mix(hash(i), hash(i + vec2(1.0, 0.0)), v.x),
                mix(hash(i + vec2(0.0, 1.0)),
                    hash(i + vec2(1.0, 1.0)), v.x), v.y);
        }
        float fbm(vec2 p) {
            float v = 0.0;
            float a = 0.58;
            for (int i = 0; i < 3; i++) {
                v += a * noise(p);
                p = mat2(1.72, -0.98, 0.98, 1.72) * p + vec2(13.3, 19.7);
                a *= 0.48;
            }
            return v;
        }
        float gaussian(float x, float spread) {
            float z = x / spread;
            return exp(-z * z);
        }

        void main() {
            // Canvas convention: (0,0) is top left; mask uses the same origin.
            vec2 uv = gl_FragCoord.xy / max(uResolution, vec2(1.0));
            uv.y = 1.0 - uv.y;
            float t = uPhase;
            // Blend two privately generated icon masks, not icon graphics.
            // The previous mask is the last *interpolated* coast, so a new
            // drag event cannot cause the shore to jump backward.
            float occ = mix(texture2D(uMaskPrevious, uv).r,
                            texture2D(uMask, uv).r, uMaskMix) * uHasLand;

            // Empty layout -> no sand, foam or fake permanent coast.
            float rough = 0.008 * sin(uv.y * 37.0 + uv.x * 9.0) +
                          0.004 * sin(uv.y * 71.0 - uv.x * 24.0);
            float tide = 0.013 * sin(t + uv.y * 7.5) +
                         0.005 * sin(2.0 * t - uv.y * 18.2);
            float shore = occ - 0.33 + occ * rough + uHasLand * tide;
            float wet = uHasLand * smoothstep(-0.29, 0.015, shore);
            float land = uHasLand * smoothstep(-0.013, 0.045, shore);

            vec2 drift = vec2(0.032 * cos(t), 0.026 * sin(t));
            float slow = 0.52;
            vec2 displace = vec2(0.5);
            vec2 uvWater = uv;
            float medium = 0.5;
            float detail = 0.5;
            // On a real photo these expensive synthetic procedural layers
            // cannot improve authenticity. Skip them entirely to save GPU work.
            if (uHasPhoto < 0.5) {
                slow = fbm(uv * vec2(5.0, 8.0) + drift);
                displace = vec2(
                    fbm(uv * vec2(12.0, 18.0) + drift),
                    fbm(uv * vec2(13.0, 15.0) - drift * 0.8));
                uvWater = uv + (displace - 0.5) * 0.008;
                medium = fbm(uvWater * vec2(24.0, 42.0) + drift);
                detail = fbm(uvWater * vec2(80.0, 136.0) - drift);
            }

            vec3 deep = vec3(0.010, 0.345, 0.480);
            vec3 aqua = vec3(0.026, 0.660, 0.715);
            vec3 lagoon = vec3(0.276, 0.820, 0.754);
            vec3 water = mix(deep, aqua, 0.30 + 0.50 * slow);
            // A genuine user-selected photograph supplies the optical structure.
            // Only a very small cyclic displacement is used: no synthetic waves
            // or harsh caustics should paint over real photographic detail.
            vec2 photoDrift = vec2(
                0.0022 * sin(uv.y * 19.0 + t) +
                0.0011 * sin(uv.x * 37.0 - t * 2.0) +
                0.00033 * cos(3.0 * t + uv.y * 41.0 + uv.x * 12.0),
                0.0017 * sin(uv.x * 23.0 - t) +
                0.0009 * sin(uv.y * 32.0 + t * 2.0) +
                0.00027 * sin(3.0 * t - uv.y * 53.0 + uv.x * 17.0)
            ) * uRefraction;
            vec2 coverUv = (uv + photoDrift - 0.5) * uPhotoScale + 0.5;
            vec3 photograph = texture2D(uOceanPhoto,
                clamp(coverUv, vec2(0.001), vec2(0.999))).rgb;
            water = mix(water, photograph, uHasPhoto);
            water = mix(water, lagoon, wet * (0.66 - 0.55 * uHasPhoto));
            water += (medium - 0.48) * vec3(0.065, 0.082, 0.076) * (1.0 - 0.90 * uHasPhoto);

            // Deliberately visible, fluid caustics; no nearest-neighbor pixels.
            if (uHasPhoto < 0.5) {
            float scaleX = min(200.0, uResolution.x * 0.25);
            float scaleY = min(300.0, uResolution.y * 0.23);
            vec2 waves = uvWater * vec2(scaleX, scaleY);
            waves += (displace - 0.5) * 10.0;
            float a = sin(waves.x * 0.76 + waves.y * 0.35 +
                          2.0 * t + medium * 4.7);
            float b = sin(waves.y * 0.83 - waves.x * 0.55 -
                          t + slow * 5.3);
            float crest = pow(max(0.0, 1.0 - abs(a * 0.6 + b * 0.4)), 10.0);
            float light = (0.040 + wet * 0.082) * crest * (1.0 - 0.87 * uHasPhoto);
            water += light * vec3(0.75, 0.99, 0.91);
            if (uQuality > 0.5) {
                water += (detail - 0.45) * vec3(0.033, 0.051, 0.047) * (1.0 - 0.9 * uHasPhoto);
            }
            if (uQuality > 1.5) {
                float micro = noise(uvWater * vec2(
                    min(350.0, uResolution.x * 0.33),
                    min(520.0, uResolution.y * 0.33)) + drift);
                float sparkle = pow(max(0.0, a * 0.6 + b * 0.4), 15.0);
                water += sparkle * vec3(0.048, 0.073, 0.070) * (1.0 - 0.9 * uHasPhoto);
                water += (micro - 0.5) * vec3(0.016, 0.026, 0.022) * (1.0 - 0.95 * uHasPhoto);
            }
            } // synthetic-only caustics and microfacets

            float grain = noise(uv * vec2(240.0, 385.0));
            vec3 sandWet = vec3(0.739, 0.719, 0.666);
            vec3 sandDry = vec3(0.969, 0.935, 0.854);
            vec3 sand = mix(sandWet, sandDry, smoothstep(0.01, 0.27, shore));
            sand += (grain - 0.5) * vec3(0.030, 0.025, 0.021);
            vec3 color = mix(water, sand, land);

            // Feathered wash and broken foam; no hard contrast outline.
            float ragged = (medium - 0.47) * 0.013;
            float edge = gaussian(shore + ragged, 0.025);
            float back = gaussian(shore + 0.056 +
                0.009 * sin(2.0 * t + uv.y * 25.0), 0.014);
            float foam = uHasLand * clamp(
                edge * (0.31 + 0.23 * detail) +
                back * 0.19 * medium, 0.0, 0.58);
            foam *= uFoamIntensity * mix(1.0, 0.38, uHasPhoto);
            color = mix(color, vec3(0.959, 0.972, 0.941), foam);

            if (uPalette > 1.5) color *= vec3(0.96, 1.02, 1.00);
            else if (uPalette > 0.5) color *= vec3(1.06, 0.96, 0.91);
            if (uNight > 0.5) color = color * vec3(0.50, 0.60, 0.72);
            float dither = (hash(gl_FragCoord.xy) - 0.5) / 400.0;
            gl_FragColor = vec4(clamp(color + dither, 0.0, 1.0), 1.0);
        }
    """.trimIndent()

    private fun compile(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        if (id == 0) throw IllegalStateException("OpenGL shader allocation failed")
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)
        val success = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, success, 0)
        if (success[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(id)
            GLES20.glDeleteShader(id)
            throw IllegalStateException("OpenGL compile error: " + error.take(240))
        }
        return id
    }

    private fun prepare(surface: Surface, w: Int, h: Int): Boolean {
        if (disabledForSurface) return false
        if (display != EGL14.EGL_NO_DISPLAY && hostSurface === surface &&
            w == surfaceWidth && h == surfaceHeight) return true
        releaseInternal()
        try {
            val found = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (found == EGL14.EGL_NO_DISPLAY) throw IllegalStateException("EGL display missing")
            display = found
            if (!EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
                throw IllegalStateException("EGL initialization failed")
            val attrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val foundConfigs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attrs, 0, foundConfigs, 0, 1, count, 0) ||
                count[0] < 1 || foundConfigs[0] == null)
                throw IllegalStateException("EGL configuration unavailable")
            val cfg = foundConfigs[0]!!
            context = EGL14.eglCreateContext(display, cfg, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            if (context == EGL14.EGL_NO_CONTEXT) throw IllegalStateException("EGL context unavailable")
            window = EGL14.eglCreateWindowSurface(display, cfg, surface,
                intArrayOf(EGL14.EGL_NONE), 0)
            if (window == EGL14.EGL_NO_SURFACE) throw IllegalStateException("EGL window unavailable")
            if (!EGL14.eglMakeCurrent(display, window, window, context))
                throw IllegalStateException("EGL makeCurrent failed")

            val v = compile(GLES20.GL_VERTEX_SHADER, vertexShader)
            val f = compile(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
            shaderProgram = GLES20.glCreateProgram()
            GLES20.glAttachShader(shaderProgram, v)
            GLES20.glAttachShader(shaderProgram, f)
            GLES20.glLinkProgram(shaderProgram)
            val status = IntArray(1)
            GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, status, 0)
            GLES20.glDeleteShader(v)
            GLES20.glDeleteShader(f)
            if (status[0] == 0) throw IllegalStateException(
                "OpenGL linking failed: " + GLES20.glGetProgramInfoLog(shaderProgram).take(240))

            val texture = IntArray(1)
            GLES20.glGenTextures(1, texture, 0)
            maskTexture = texture[0]
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            // Allocate GLES2 texture storage once per context, then update in
            // place via glTexSubImage2D instead of reallocating every drag.
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                maskWidth, maskHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glGenTextures(1, texture, 0)
            maskPreviousTexture = texture[0]
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskPreviousTexture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            // Allocate GLES2 texture storage once per context, then update in
            // place via glTexSubImage2D instead of reallocating every drag.
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                maskWidth, maskHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glGenTextures(1, texture, 0)
            photoTexture = texture[0]
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, photoTexture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            photoFingerprint = Long.MIN_VALUE
            photographActive = false
            photographAspect = 1f
            currentConfig = null
            maskFrom = null
            maskTo = null
            maskFromHasLand = false
            maskToHasLand = false
            maskTransitionStarted = 0L
            hostSurface = surface
            surfaceWidth = w
            surfaceHeight = h
            return true
        } catch (_: Exception) {
            releaseInternal()
            disabledForSurface = true
            return false
        }
    }

    fun invalidateLayout() {
        currentConfig = null
    }

    fun invalidatePhotograph() {
        photoFingerprint = Long.MIN_VALUE
    }

    private fun maskMix(now: Long): Float {
        val elapsed = (now - maskTransitionStarted).coerceAtLeast(0L)
        val t = (elapsed.toFloat() / maskMorphDurationMs).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t) // Continuous ease-in/out.
    }

    private fun blendCurrentMask(now: Long): ByteArray {
        val target = maskTo ?: return ByteArray(maskWidth * maskHeight * 4)
        val previous = maskFrom ?: return target.copyOf()
        val t = maskMix(now)
        if (t <= 0f) return previous.copyOf()
        if (t >= 1f) return target.copyOf()
        val mixed = ByteArray(target.size)
        for (i in target.indices step 4) {
            // RGBA grayscale occupancy; only the R channel is used by GLES.
            val from = previous[i].toInt() and 255
            val to = target[i].toInt() and 255
            val v = (from + (to - from) * t).toInt().coerceIn(0, 255).toByte()
            mixed[i] = v
            mixed[i + 1] = v
            mixed[i + 2] = v
            mixed[i + 3] = 255.toByte()
        }
        return mixed
    }

    private fun fillMask(state: WallpaperState): ByteArray {
        // The former implementation evaluated every obstacle for every one of
        // 51,200 pixels on every drag event. Stamp bounded kernels instead:
        // distant influence is visually negligible and the active Home can
        // respond without repeatedly performing a full screen × icons scan.
        val data = ByteArray(maskWidth * maskHeight * 4)
        for (i in 3 until data.size step 4) data[i] = 255.toByte()
        if (state.occupied.isEmpty() && state.zones.isEmpty()) return data

        val accum = FloatArray(maskWidth * maskHeight)
        val inset = state.gridInset.toDouble()
        val usable = 1.0 - 2.0 * inset
        val span = state.span.toDouble()
        for (index in state.occupied) {
            if (index !in 0 until state.columns * state.rows) continue
            val centerX = inset + (index % state.columns + 0.5) * usable / state.columns
            val centerY = state.top + (index / state.columns + 0.5) * span / state.rows
            val sigmaX = 0.58 * usable / state.columns
            val sigmaY = 0.58 * span / state.rows
            val fromX = kotlin.math.floor((centerX - 5 * sigmaX) * maskWidth)
                .toInt().coerceIn(0, maskWidth - 1)
            val toX = kotlin.math.ceil((centerX + 5 * sigmaX) * maskWidth)
                .toInt().coerceIn(0, maskWidth - 1)
            val fromY = kotlin.math.floor((centerY - 5 * sigmaY) * maskHeight)
                .toInt().coerceIn(0, maskHeight - 1)
            val toY = kotlin.math.ceil((centerY + 5 * sigmaY) * maskHeight)
                .toInt().coerceIn(0, maskHeight - 1)
            for (row in fromY..toY) {
                val y = (row + 0.5) / maskHeight
                val dy = (y - centerY) / sigmaY
                for (col in fromX..toX) {
                    val ix = row * maskWidth + col
                    if (accum[ix] >= 1f) continue
                    val x = (col + 0.5) / maskWidth
                    val dx = (x - centerX) / sigmaX
                    val influence = kotlin.math.exp(-0.5 * (dx * dx + dy * dy)).toFloat()
                    accum[ix] = (accum[ix] + influence).coerceAtMost(1f)
                }
            }
        }

        for (zone in state.zones) {
            val cx = zone.x.toDouble()
            val cy = zone.y.toDouble()
            val halfWidth = zone.width.toDouble() / 2.0
            val halfHeight = zone.height.toDouble() / 2.0
            val spreadX = zone.width.toDouble() * 0.38
            val spreadY = zone.height.toDouble() * 0.38
            if (spreadX <= 0.0 || spreadY <= 0.0) continue
            val reachX = halfWidth + 4.0 * spreadX
            val reachY = halfHeight + 4.0 * spreadY
            val fromX = kotlin.math.floor((cx - reachX) * maskWidth)
                .toInt().coerceIn(0, maskWidth - 1)
            val toX = kotlin.math.ceil((cx + reachX) * maskWidth)
                .toInt().coerceIn(0, maskWidth - 1)
            val fromY = kotlin.math.floor((cy - reachY) * maskHeight)
                .toInt().coerceIn(0, maskHeight - 1)
            val toY = kotlin.math.ceil((cy + reachY) * maskHeight)
                .toInt().coerceIn(0, maskHeight - 1)
            for (row in fromY..toY) {
                val y = (row + 0.5) / maskHeight
                val dy = kotlin.math.max(0.0, kotlin.math.abs(y - cy) - halfHeight) / spreadY
                if (dy > 4.0) continue
                for (col in fromX..toX) {
                    val ix = row * maskWidth + col
                    if (accum[ix] >= 1f) continue
                    val x = (col + 0.5) / maskWidth
                    val dx = kotlin.math.max(0.0, kotlin.math.abs(x - cx) - halfWidth) / spreadX
                    if (dx > 4.0) continue
                    val influence = kotlin.math.exp(-0.5 * (dx * dx + dy * dy)).toFloat()
                    accum[ix] = (accum[ix] + influence).coerceAtMost(1f)
                }
            }
        }

        for (i in accum.indices) {
            val value = (accum[i] * 255f).toInt().coerceIn(0, 255).toByte()
            val pos = i * 4
            data[pos] = value
            data[pos + 1] = value
            data[pos + 2] = value
        }
        return data
    }

    private fun uploadMaskTexture(textureId: Int, unit: Int, bytes: ByteArray) {
        GLES20.glActiveTexture(unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        require(bytes.size == maskWidth * maskHeight * 4) {
            "Invalid occupancy texture buffer"
        }
        maskUploadScratch.clear()
        maskUploadScratch.put(bytes)
        maskUploadScratch.position(0)
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0,
            maskWidth, maskHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
            maskUploadScratch)
    }

    private fun uploadMask(state: WallpaperState) {
        val previous = currentConfig
        if (previous != null &&
            previous.scene == state.scene &&
            previous.columns == state.columns &&
            previous.rows == state.rows &&
            previous.top == state.top &&
            previous.span == state.span &&
            previous.gridInset == state.gridInset &&
            previous.occupied == state.occupied &&
            previous.zones == state.zones) {
            // Palette, night, quality, animation and optical settings affect
            // uniforms, not the sand occupancy mask. Keep the GPU textures,
            // coastline morph and 160x320 influence field unchanged.
            currentConfig = state
            return
        }
        val now = SystemClock.uptimeMillis()
        val target = fillMask(state)
        val newHasLand = state.occupied.isNotEmpty() || state.zones.isNotEmpty()
        if (maskTo == null) {
            // First frame must have no transition from unknown/GPU garbage.
            maskFrom = target.copyOf()
            maskFromHasLand = newHasLand
            maskTransitionStarted = now - maskMorphDurationMs
        } else {
            // On every drag update, use the actual visible blended mask as
            // the next 'from' frame. This avoids coastline teleportation.
            val elapsedMix = maskMix(now)
            maskFrom = blendCurrentMask(now)
            maskFromHasLand =
                (maskFromHasLand && elapsedMix < 1f) || maskToHasLand
            maskTransitionStarted = now
        }
        maskTo = target
        maskToHasLand = newHasLand
        uploadMaskTexture(maskPreviousTexture, GLES20.GL_TEXTURE2, maskFrom!!)
        uploadMaskTexture(maskTexture, GLES20.GL_TEXTURE0, target)
        currentConfig = state
    }

    private fun uploadPhotograph(context: Context, quality: String) {
        val imageFile = OceanPhotoStore.file(context)
        val fingerprint = if (imageFile.isFile)
            imageFile.lastModified() xor (imageFile.length() shl 1) xor
                quality.hashCode().toLong() else -1L
        if (fingerprint == photoFingerprint) return
        photoFingerprint = fingerprint
        photographActive = false
        photographAspect = 1f
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, photoTexture)
        // Valid fallback sampler prevents undefined behavior when photo is absent.
        val blank = ByteBuffer.allocateDirect(4)
            .put(byteArrayOf(12, 93, 121, -1)).apply { position(0) }
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, blank)
        if (!imageFile.isFile) return
        try {
            val maxTexture = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTexture, 0)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
            val qualityLimit = when (quality) {
                "balanced" -> 1024
                "high" -> 1536
                else -> 2560
            }
            val limit = minOf(qualityLimit, maxTexture[0])
            if (limit < 256) return
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > limit) sample *= 2
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
            try {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                photographActive = GLES20.glGetError() == GLES20.GL_NO_ERROR
                if (photographActive) {
                    photographAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                }
            } finally {
                bitmap.recycle()
            }
        } catch (_: Exception) {
            photographActive = false // Keep procedural GPU fallback available.
        }
    }

    fun render(appContext: Context, surface: Surface, width: Int, height: Int,
               state: WallpaperState, phase: Double): Boolean {
        if (width < 2 || height < 2 || !surface.isValid) return false
        if (!prepare(surface, width, height)) return false
        return try {
            if (!EGL14.eglMakeCurrent(display, window, window, context))
                throw IllegalStateException("EGL context lost")
            uploadMask(state)
            uploadPhotograph(appContext, state.renderQuality)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glUseProgram(shaderProgram)
            fun f(name: String, value: Float) {
                GLES20.glUniform1f(GLES20.glGetUniformLocation(shaderProgram, name), value)
            }
            GLES20.glUniform2f(GLES20.glGetUniformLocation(shaderProgram, "uResolution"),
                width.toFloat(), height.toFloat())
            f("uPhase", (phase % (2 * PI)).toFloat())
            f("uNight", if (state.night) 1f else 0f)
            f("uHasPhoto", if (photographActive) 1f else 0f)
            f("uRefraction", state.photoRefraction)
            f("uFoamIntensity", state.foamIntensity)
            val screenAspect = width.toFloat() / height.toFloat()
            GLES20.glUniform2f(GLES20.glGetUniformLocation(shaderProgram, "uPhotoScale"),
                minOf(1f, screenAspect / photographAspect),
                minOf(1f, photographAspect / screenAspect))
            // Static scenes must display the latest coast immediately: no pending frames
            // will advance a transition when motion is set to zero.
            val mixAmount = if (state.staticScene) 1f else
                maskMix(SystemClock.uptimeMillis())
            f("uMaskMix", mixAmount)
            f("uHasLand", if (maskToHasLand ||
                (maskFromHasLand && mixAmount < 1f)) 1f else 0f)
            f("uPalette", when (state.palette) { "sunset" -> 1f; "mint" -> 2f; else -> 0f })
            f("uQuality", when (state.renderQuality) {
                "balanced" -> 0f; "high" -> 1f; else -> 2f
            })
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(shaderProgram, "uMask"), 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskPreviousTexture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(shaderProgram, "uMaskPrevious"), 2)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, photoTexture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(shaderProgram, "uOceanPhoto"), 1)
            val position = GLES20.glGetAttribLocation(shaderProgram, "aPosition")
            if (position < 0) throw IllegalStateException("Missing position attribute")
            quad.position(0)
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, quad)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(position)
            if (!EGL14.eglSwapBuffers(display, window)) throw IllegalStateException("EGL swap failed")
            true
        } catch (_: Exception) {
            releaseInternal()
            disabledForSurface = true
            false
        }
    }

    fun resetSurface() {
        releaseInternal()
        disabledForSurface = false
    }

    fun dispose() = resetSurface()

    private fun releaseInternal() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (context != EGL14.EGL_NO_CONTEXT &&
                window != EGL14.EGL_NO_SURFACE)
                EGL14.eglMakeCurrent(display, window, window, context)
            if (maskTexture != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(maskTexture), 0)
                maskTexture = 0
            }
            if (maskPreviousTexture != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(maskPreviousTexture), 0)
                maskPreviousTexture = 0
            }
            if (photoTexture != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(photoTexture), 0)
                photoTexture = 0
            }
            if (shaderProgram != 0) {
                GLES20.glDeleteProgram(shaderProgram)
                shaderProgram = 0
            }
            EGL14.eglMakeCurrent(display,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (window != EGL14.EGL_NO_SURFACE)
                EGL14.eglDestroySurface(display, window)
            if (context != EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        window = EGL14.EGL_NO_SURFACE
        hostSurface = null
        surfaceWidth = 0
        surfaceHeight = 0
        currentConfig = null
        maskFrom = null
        maskTo = null
        maskFromHasLand = false
        maskToHasLand = false
        maskTransitionStarted = 0L
        photoFingerprint = Long.MIN_VALUE
        photographActive = false
        photographAspect = 1f
    }
}

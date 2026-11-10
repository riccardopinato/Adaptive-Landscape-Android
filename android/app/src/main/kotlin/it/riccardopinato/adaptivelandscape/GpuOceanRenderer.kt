package it.riccardopinato.adaptivelandscape

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
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
internal class GpuOceanRenderer(context: Context) {
    private val oceanPhoto = OceanTexture(context)
    val photoActive: Boolean get() = oceanPhoto.available
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE
    private var hostSurface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var shaderProgram = 0
    private var maskTexture = 0
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
        uniform float uMeadow;
        uniform sampler2D uMask;
        uniform sampler2D uOcean;
        uniform float uPhotoActive;

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
            float occ = texture2D(uMask, uv).r * uHasLand;
            // Render both themes through one EGL producer on the same Surface.
            // Never mix lockCanvas() and EGL when a wallpaper theme changes.
            if (uMeadow > 0.5) {
                vec3 skyTop = vec3(0.48, 0.72, 0.90);
                vec3 skyBottom = vec3(0.92, 0.96, 0.87);
                if (uPalette > 0.5 && uPalette < 1.5) {
                    skyTop = vec3(0.94, 0.57, 0.60);
                    skyBottom = vec3(0.98, 0.83, 0.69);
                }
                if (uPalette > 1.5) {
                    skyTop = vec3(0.53, 0.82, 0.77);
                    skyBottom = vec3(0.91, 0.97, 0.84);
                }
                if (uNight > 0.5) {
                    skyTop = vec3(0.07, 0.14, 0.30);
                    skyBottom = vec3(0.30, 0.39, 0.55);
                }
                vec3 meadow = mix(skyTop, skyBottom, smoothstep(0.0, 0.83, uv.y));
                // Same three hill layers/normalized occupancy as original Canvas.
                float h0 = 0.53 + 0.035*sin(uv.x*9.0) + 0.045*occ;
                float h1 = 0.61 + 0.035*sin(uv.x*9.0 + 3.0) + 0.045*occ;
                float h2 = 0.70 + 0.035*sin(uv.x*9.0 + 6.0) + 0.045*occ;
                if (uv.y > h0) meadow = vec3(0.43, 0.63, 0.39);
                if (uv.y > h1) meadow = vec3(0.29, 0.53, 0.35);
                if (uv.y > h2) meadow = vec3(0.20, 0.44, 0.30);
                if (uv.y > h0) meadow += (noise(uv*vec2(118.0,210.0))-0.5)*0.025;
                vec2 sun = (uv - vec2(0.77, 0.17)) *
                    vec2(uResolution.x/uResolution.x, uResolution.y/uResolution.x);
                float sunMask = 1.0-smoothstep(0.084,0.094,length(sun));
                meadow = mix(meadow, (uNight>0.5) ?
                    vec3(0.86,0.91,0.99) : vec3(1.0,0.95,0.75),
                    sunMask * (1.0-step(h0,uv.y)));
                if (uNight > 0.5 && uv.y > h0) {
                    meadow *= vec3(0.46, 0.61, 0.70);
                }
                gl_FragColor = vec4(clamp(meadow,0.0,1.0),1.0);
                return;
            }

            // Empty layout -> no sand, foam or fake permanent coast.
            float rough = 0.008 * sin(uv.y * 37.0 + uv.x * 9.0) +
                          0.004 * sin(uv.y * 71.0 - uv.x * 24.0);
            float tide = 0.013 * sin(t + uv.y * 7.5) +
                         0.005 * sin(2.0 * t - uv.y * 18.2);
            float shore = occ - 0.33 + occ * rough + uHasLand * tide;
            float wet = uHasLand * smoothstep(-0.29, 0.015, shore);
            float land = uHasLand * smoothstep(-0.013, 0.045, shore);

            vec2 drift = vec2(0.032 * cos(t), 0.026 * sin(t));
            float slow = fbm(uv * vec2(5.0, 8.0) + drift);
            vec2 displace = vec2(
                fbm(uv * vec2(12.0, 18.0) + drift),
                fbm(uv * vec2(13.0, 15.0) - drift * 0.8));
            vec2 uvWater = uv + (displace - 0.5) * 0.008;
            float medium = fbm(uvWater * vec2(24.0, 42.0) + drift);
            float detail = fbm(uvWater * vec2(80.0, 136.0) - drift);

            vec3 deep = vec3(0.010, 0.345, 0.480);
            vec3 aqua = vec3(0.026, 0.660, 0.715);
            vec3 lagoon = vec3(0.276, 0.820, 0.754);
            vec3 water = mix(deep, aqua, 0.30 + 0.50 * slow);
            water = mix(water, lagoon, wet * 0.66);
            water += (medium - 0.48) * vec3(0.065, 0.082, 0.076);
            // Photo-first color/detail: live GL refraction deforms the image.
            // No photo present? Keep the procedural fallback.
            if (uPhotoActive > 0.5) {
                vec2 refraction = vec2(
                    0.005 * sin(2.0*t + uv.y*17.0 + slow*3.0),
                    0.004 * cos(t + uv.x*14.0 + medium*3.5));
                vec3 captured = texture2D(uOcean,
                    clamp(uv + refraction, vec2(0.001), vec2(0.999))).rgb;
                water = mix(water, captured, 0.95);
                water = mix(water, lagoon, wet * 0.13);
            }

            // Deliberately visible, fluid caustics; no nearest-neighbor pixels.
            float scaleX = min(200.0, uResolution.x * 0.25);
            float scaleY = min(300.0, uResolution.y * 0.23);
            vec2 waves = uvWater * vec2(scaleX, scaleY);
            waves += (displace - 0.5) * 10.0;
            float a = sin(waves.x * 0.76 + waves.y * 0.35 +
                          2.0 * t + medium * 4.7);
            float b = sin(waves.y * 0.83 - waves.x * 0.55 -
                          t + slow * 5.3);
            float crest = pow(max(0.0, 1.0 - abs(a * 0.6 + b * 0.4)), 10.0);
            float light = (0.040 + wet * 0.082) * crest * mix(1.0, 0.20, uPhotoActive);
            water += light * vec3(0.75, 0.99, 0.91);
            if (uQuality > 0.5) {
                water += (detail - 0.45) * vec3(0.033, 0.051, 0.047) * (1.0 - 0.92 * uPhotoActive);
            }
            if (uQuality > 1.5) {
                float micro = noise(uvWater * vec2(
                    min(350.0, uResolution.x * 0.33),
                    min(520.0, uResolution.y * 0.33)) + drift);
                float sparkle = pow(max(0.0, a * 0.6 + b * 0.4), 15.0);
                water += sparkle * vec3(0.048, 0.073, 0.070) * (1.0 - 0.9 * uPhotoActive);
                water += (micro - 0.5) * vec3(0.016, 0.026, 0.022) * (1.0 - 0.94 * uPhotoActive);
            }

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
            color = mix(color, vec3(0.959, 0.972, 0.941), foam * mix(1.0, 0.45, uPhotoActive));

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
            oceanPhoto.upload()
            currentConfig = null
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

    fun invalidatePhoto() = resetSurface()

    private fun uploadMask(state: WallpaperState) {
        if (state == currentConfig) return
        val empty = state.occupied.isEmpty() && state.zones.isEmpty()
        val w = if (empty) 1 else 192
        val h = if (empty) 1 else 384
        val bytes = ByteBuffer.allocateDirect(w * h * 4)
            .order(ByteOrder.nativeOrder())
        for (row in 0 until h) {
            val y = (row + 0.5f) / h
            for (col in 0 until w) {
                val x = (col + 0.5f) / w
                val value = if (empty) 0 else
                    (state.influence(x, y) * 255f).toInt().coerceIn(0, 255)
                bytes.put(value.toByte())
                bytes.put(value.toByte())
                bytes.put(value.toByte())
                bytes.put(255.toByte())
            }
        }
        bytes.position(0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexture)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bytes)
        currentConfig = state
    }

    fun render(surface: Surface, width: Int, height: Int,
               state: WallpaperState, phase: Double): Boolean {
        if (width < 2 || height < 2 || !surface.isValid) return false
        if (!prepare(surface, width, height)) return false
        return try {
            if (!EGL14.eglMakeCurrent(display, window, window, context))
                throw IllegalStateException("EGL context lost")
            uploadMask(state)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glUseProgram(shaderProgram)
            fun f(name: String, value: Float) {
                GLES20.glUniform1f(GLES20.glGetUniformLocation(shaderProgram, name), value)
            }
            GLES20.glUniform2f(GLES20.glGetUniformLocation(shaderProgram, "uResolution"),
                width.toFloat(), height.toFloat())
            f("uPhase", (phase % (2 * PI)).toFloat())
            f("uNight", if (state.night) 1f else 0f)
            f("uMeadow", if (state.scene == "meadow") 1f else 0f)
            f("uHasLand", if (state.occupied.isEmpty() && state.zones.isEmpty()) 0f else 1f)
            f("uPalette", when (state.palette) { "sunset" -> 1f; "mint" -> 2f; else -> 0f })
            f("uQuality", when (state.renderQuality) {
                "balanced" -> 0f; "high" -> 1f; else -> 2f
            })
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(shaderProgram, "uMask"), 0)
            oceanPhoto.bindOrFallback(maskTexture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(shaderProgram, "uOcean"), 1)
            f("uPhotoActive", if (oceanPhoto.available) 1f else 0f)
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
            oceanPhoto.release()
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
    }
}

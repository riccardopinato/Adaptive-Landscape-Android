package it.riccardopinato.adaptivelandscape

import android.content.SharedPreferences
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Exact normalized geometry/field version shared with Flutter schema 2. */
internal data class LandscapeZone(val x: Float, val y: Float, val width: Float, val height: Float)

internal data class WallpaperState(
    val scene: String = "meadow",
    val columns: Int = 5,
    val rows: Int = 8,
    val top: Float = 0.115f,
    val span: Float = 0.765f,
    val occupied: Set<Int> = emptySet(),
    val zones: List<LandscapeZone> = emptyList(),
    val palette: String = "original",
    val night: Boolean = false,
    val motion: Float = 1.0f,
    val reduceMotion: Boolean = false,
    val renderQuality: String = "ultra",
    val photoRefraction: Float = 0.55f,
    val foamIntensity: Float = 0.45f,
) {
    val staticScene: Boolean get() = reduceMotion || motion <= 0f

    fun influence(x: Float, y: Float): Float {
        var sum = 0.0
        for (index in occupied) {
            if (index !in 0 until (columns * rows)) continue
            val ix = ((index % columns) + 0.5) / columns.toDouble()
            val iy = top + ((index / columns) + 0.5) * span / rows
            val dx = (x - ix) / (0.58 / columns)
            val dy = (y - iy) / (0.58 * span / rows)
            sum += exp(-0.5 * (dx * dx + dy * dy))
        }
        for (zone in zones) {
            val outsideX = max(0.0, abs(x - zone.x).toDouble() - zone.width / 2) / (zone.width * 0.38)
            val outsideY = max(0.0, abs(y - zone.y).toDouble() - zone.height / 2) / (zone.height * 0.38)
            sum += exp(-0.5 * (outsideX * outsideX + outsideY * outsideY))
        }
        return sum.coerceIn(0.0, 1.0).toFloat()
    }

    companion object {
        private fun number(o: JSONObject, key: String, fallback: Double, min: Double, max: Double): Float {
            val value = o.optDouble(key, fallback)
            return if (value.isFinite()) value.coerceIn(min, max).toFloat() else fallback.toFloat()
        }

        fun fromPrefs(prefs: SharedPreferences): WallpaperState {
            val raw = prefs.getString("config_v2", null)
            if (!raw.isNullOrBlank()) {
                try {
                    val json = JSONObject(raw)
                    if (json.optInt("schema", -1) == 2) {
                        val columns = json.optInt("columns", 5).coerceIn(3, 8)
                        val rows = json.optInt("rows", 8).coerceIn(3, 12)
                        val top = number(json, "top", 0.115, 0.0, 0.5)
                        val span = number(json, "span", 0.765, 0.2, 1.0 - top.toDouble())
                        val array = json.optJSONArray("occupied")
                        val occupied = buildSet {
                            if (array != null) for (i in 0 until array.length()) {
                                val index = array.optInt(i, -1)
                                if (index in 0 until (columns * rows)) add(index)
                            }
                        }
                        val zoneArray = json.optJSONArray("zones")
                        val zones = buildList {
                            if (zoneArray != null) for (i in 0 until minOf(zoneArray.length(), 80)) {
                                val z = zoneArray.optJSONObject(i) ?: continue
                                val width = number(z, "width", 0.12, 0.03, 1.0)
                                val height = number(z, "height", 0.07, 0.03, 1.0)
                                add(LandscapeZone(
                                    number(z, "x", 0.5, width / 2.0, 1.0 - width / 2.0),
                                    number(z, "y", 0.5, height / 2.0, 1.0 - height / 2.0),
                                    width, height,
                                ))
                            }
                        }
                        return WallpaperState(
                            scene = if (json.optString("scene") == "beach") "beach" else "meadow",
                            columns = columns, rows = rows, top = top, span = span,
                            occupied = occupied, zones = zones,
                            palette = json.optString("palette").let {
                                if (it == "sunset" || it == "mint") it else "original"
                            },
                            night = json.optBoolean("night", false),
                            motion = number(json, "motion", 1.0, 0.0, 2.0),
                            reduceMotion = json.optBoolean("reduceMotion", false),
                            renderQuality = json.optString("renderQuality", "ultra").let {
                                if (it == "balanced" || it == "high") it else "ultra"
                            },
                            photoRefraction = number(json, "photoRefraction", 0.55, 0.0, 1.0),
                            foamIntensity = number(json, "foamIntensity", 0.45, 0.0, 1.0),
                        )
                    }
                } catch (_: Exception) {
                    // Corrupt imported settings cannot crash wallpaper engine.
                }
            }
            val legacy = prefs.getString("occupied", "").orEmpty()
                .split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0 until 40 }.toSet()
            return WallpaperState(
                scene = if (prefs.getString("scene", "meadow") == "beach") "beach" else "meadow",
                occupied = legacy,
            )
        }
    }
}

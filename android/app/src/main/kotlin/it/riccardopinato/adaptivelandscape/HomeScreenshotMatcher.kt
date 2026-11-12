package it.riccardopinato.adaptivelandscape

import android.content.pm.LauncherActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * On-device opt-in visual matching, not OCR, screen capture, AccessibilityService,
 * or access to another launcher's database. Conservative by design: only accept
 * high-confidence icon artwork matches. Unknown icons are left unresolved.
 *
 * A screenshot is held in RAM only and its pixels are never written to prefs,
 * a log, a server or an analytics SDK. A caller must ask for user confirmation
 * before committing any result.
 */
internal object HomeScreenshotMatcher {
    data class Match(
        val id: String,
        val label: String,
        val x: Float,
        val y: Float,
        val confidence: Float,
        val isDock: Boolean,
        val destCell: Int
    )
    data class Result(
        val matches: List<Match>,
        val examinedApps: Int,
        val unresolvedCandidates: Int,
        val imageWidth: Int,
        val imageHeight: Int
    )
    private data class Template(val id: String, val label: String, val colors: IntArray)
    private data class Hit(val template: Template, val x: Float, val y: Float, val score: Float)
    private const val SAMPLE = 5
    private const val ICON_PIXELS = 60
    private const val SIMILARITY_MIN = 0.885f

    /** Drawable pixels and screenshots are treated as untrusted and bounded. */
    fun find(screenshot: Bitmap, apps: List<LauncherActivityInfo>): Result {
        require(screenshot.width > 100 && screenshot.height > 160)
        require(screenshot.width.toLong() * screenshot.height <= 12_000_000L)
        val scale = min(1f, min(480f / screenshot.width, 1050f / screenshot.height))
        val w = max(1, (screenshot.width * scale).toInt())
        val h = max(1, (screenshot.height * scale).toInt())
        val compact = if (w == screenshot.width && h == screenshot.height)
            screenshot else Bitmap.createScaledBitmap(screenshot, w, h, true)
        try {
            val pixels = IntArray(w * h)
            compact.getPixels(pixels, 0, w, 0, 0, w, h)
            val samples = floatArrayOf(-.36f, -.18f, 0f, .18f, .36f)
            val templates = apps.take(240).mapNotNull { info ->
                try {
                    val bmp = Bitmap.createBitmap(
                        ICON_PIXELS, ICON_PIXELS, Bitmap.Config.ARGB_8888)
                    try {
                        val drawable = (info.getIcon(0).constantState?.newDrawable()
                            ?: info.getIcon(0)).mutate()
                        drawable.setBounds(0, 0, ICON_PIXELS, ICON_PIXELS)
                        drawable.draw(Canvas(bmp))
                        val colors = IntArray(SAMPLE * SAMPLE)
                        var pos = 0
                        for (dy in samples) for (dx in samples) {
                            colors[pos++] = bmp.getPixel(
                                ((.5f + dx) * ICON_PIXELS).toInt().coerceIn(0, ICON_PIXELS - 1),
                                ((.5f + dy) * ICON_PIXELS).toInt().coerceIn(0, ICON_PIXELS - 1))
                        }
                        // Uniform icons match arbitrary parts of a background;
                        // exclude those from automatic matching, not from drawer.
                        val avg = colors.sumOf {
                            (Color.red(it) + Color.green(it) + Color.blue(it)).toLong()
                        } / (colors.size * 3)
                        val spread = colors.sumOf {
                            (abs(Color.red(it) - avg) + abs(Color.green(it) - avg) +
                                abs(Color.blue(it) - avg)).toLong()
                        } / (colors.size * 3)
                        if (spread < 18L) null else Template(
                            info.componentName.flattenToString(), info.label.toString(), colors)
                    } finally { bmp.recycle() }
                } catch (_: RuntimeException) { null }
            }
            // Coarse search across the entire plausible icon and dock region,
            // with no assumption that an OEM uses our own 5-column Home grid.
            val sizes = floatArrayOf(.075f, .095f, .115f, .137f, .16f)
            val hits = ArrayList<Hit>(templates.size)
            for (template in templates) {
                var best = 0f
                var bestX = 0f
                var bestY = 0f
                var bestS = 0f
                var yy = .105f
                while (yy <= .949f) {
                    var xx = .065f
                    while (xx <= .935f) {
                        for (size in sizes) {
                            val score = similarity(pixels, w, h, template.colors,
                                xx, yy, size, samples)
                            if (score > best) {
                                best = score; bestX = xx; bestY = yy; bestS = size
                            }
                        }
                        xx += .025f
                    }
                    yy += .020f
                }
                if (best < .83f) continue
                // Refine near the winning location for screenshot scaling and
                // launcher artwork alignment. Avoid discrete-grid quantization.
                var yDelta = -.014f
                while (yDelta <= .0141f) {
                    var xDelta = -.014f
                    while (xDelta <= .0141f) {
                        val x = bestX + xDelta
                        val y = bestY + yDelta
                        for (adjust in floatArrayOf(-.009f, 0f, .009f)) {
                            val score = similarity(pixels, w, h, template.colors,
                                x, y, (bestS + adjust).coerceIn(.06f, .18f), samples)
                            if (score > best) { best = score; bestX = x; bestY = y }
                        }
                        xDelta += .007f
                    }
                    yDelta += .007f
                }
                if (best >= SIMILARITY_MIN) hits.add(Hit(template, bestX, bestY, best))
            }
            val accepted = ArrayList<Match>()
            val usedCells = HashSet<Int>()
            val usedIds = HashSet<String>()
            var dockCount = 0
            for (hit in hits.sortedByDescending { it.score }) {
                if (!usedIds.add(hit.template.id)) continue
                val dock = hit.y >= .81f
                if (dock && ++dockCount > 4) continue
                val col = (hit.x * 5).toInt().coerceIn(0, 4)
                // Normalize common OEM top status bar and bottom dock spaces.
                val row = (((hit.y - .105f) / .69f) * 8).toInt().coerceIn(0, 7)
                val cell = row * 5 + col
                if (!dock && !usedCells.add(cell)) continue
                if (accepted.any {
                    abs(it.x - hit.x) < .061f && abs(it.y - hit.y) < .032f
                }) continue
                accepted.add(Match(hit.template.id, hit.template.label,
                    hit.x, hit.y, hit.score, dock, cell))
            }
            // This is an estimate, NOT an invented count of unrecognized apps.
            return Result(accepted, templates.size,
                (templates.size - accepted.size).coerceAtLeast(0), w, h)
        } finally {
            if (compact !== screenshot) compact.recycle()
        }
    }

    private fun similarity(
        pixels: IntArray, width: Int, height: Int, reference: IntArray,
        x: Float, y: Float, size: Float, offsets: FloatArray
    ): Float {
        if (x < .045f || x > .955f || y < .07f || y > .965f) return 0f
        val px = x * width
        val py = y * height
        val iconSize = size * width
        var diff = 0L
        var n = 0
        for (dy in offsets) for (dx in offsets) {
            val ix = (px + dx * iconSize).toInt()
            val iy = (py + dy * iconSize).toInt()
            if (ix !in 0 until width || iy !in 0 until height) return 0f
            val actual = pixels[iy * width + ix]
            val expected = reference[n++]
            diff += abs(Color.red(actual) - Color.red(expected))
            diff += abs(Color.green(actual) - Color.green(expected))
            diff += abs(Color.blue(actual) - Color.blue(expected))
        }
        return 1f - diff.toFloat() / (offsets.size * offsets.size * 3 * 255)
    }

    /**
     * Deterministic synthetic fixture for the emulator smoke test. The tested
     * screenshot is generated entirely in RAM from two installed app icons;
     * this does not read the real device screen or private user content.
     */
    fun syntheticSelfTest(apps: List<LauncherActivityInfo>): Boolean {
        val distinct = apps.distinctBy { it.componentName.flattenToString() }
        if (distinct.size < 2) return false
        val example = distinct.take(2)
        val bitmap = Bitmap.createBitmap(360, 760, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(15, 111, 139))
            val positions = listOf(.3f to .28f, .7f to .56f)
            for (i in example.indices) {
                val drawable = example[i].getIcon(0).constantState?.newDrawable()
                    ?: example[i].getIcon(0)
                val x = (positions[i].first * bitmap.width).toInt()
                val y = (positions[i].second * bitmap.height).toInt()
                val r = 24
                drawable.setBounds(x-r, y-r, x+r, y+r)
                drawable.draw(canvas)
            }
            val result = find(bitmap, example)
            return example.all { original ->
                result.matches.any { it.id == original.componentName.flattenToString() }
            } && result.matches.size == 2
        } finally { bitmap.recycle() }
    }

    /** Produces a local-only annotated thumbnail for user review. */
    fun preview(source: Bitmap, matches: List<Match>): Bitmap {
        val width = min(360, source.width)
        val height = max(1, (source.height.toFloat() * width / source.width).toInt())
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(source, null,
            android.graphics.Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(73, 222, 164)
            style = Paint.Style.STROKE
            strokeWidth = max(2f, width / 125f)
        }
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 0, 30, 38)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = max(11f, width / 32f)
        }
        for ((index, match) in matches.withIndex()) {
            val px = match.x * width
            val py = match.y * height
            c.drawCircle(px, py, width * .049f, ring)
            val label = (index + 1).toString()
            c.drawRoundRect(px - 11f, py - 27f, px + 11f, py - 10f, 5f, 5f, background)
            c.drawText(label, px - text.measureText(label) / 2, py - 14f, text)
        }
        return out
    }
}

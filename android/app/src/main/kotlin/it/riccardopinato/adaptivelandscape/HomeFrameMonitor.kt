package it.riccardopinato.adaptivelandscape

import android.os.SystemClock
import android.view.Choreographer
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Local-only Home UI pacing estimate, NOT a GPU or SurfaceFlinger frame trace.
 *
 * One reusable Choreographer callback samples main-thread frame intervals
 * while this Home activity is resumed. No app names, screenshots, serials,
 * usage history, network SDKs, or continuous background sampling.
 */
internal class HomeFrameMonitor(private val onSustainedJank: () -> Unit) :
    Choreographer.FrameCallback {

    internal data class Summary(
        val samples: Int,
        val measuredIntervals: Int,
        val refreshRateHz: Float,
        val frameBudgetMs: Float,
        val medianMs: Int,
        val p95Ms: Int,
        val slowFrames: Int,
        val severeFrames: Int,
        val durationMs: Long,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("metric", "home_choreographer_interval_estimate")
            .put("samples_in_recent_window", samples)
            .put("measured_intervals", measuredIntervals)
            .put("refresh_rate_hz", refreshRateHz.toDouble())
            .put("expected_frame_budget_ms", frameBudgetMs.toDouble())
            .put("median_interval_ms", medianMs)
            .put("p95_interval_ms", p95Ms)
            .put("slow_intervals", slowFrames)
            .put("severe_intervals", severeFrames)
            .put("session_duration_ms", durationMs)
    }

    private val recentIntervalsMs = IntArray(512)
    private var sampleCount = 0
    private var totalIntervals = 0
    private var slowIntervals = 0
    private var severeIntervals = 0
    private var refreshHz = 60f
    private var active = false
    private var lastFrameNs = 0L
    private var startedAtMs = 0L
    private var pressureWindowNs = 0L
    private var pressureSamples = 0
    private var pressureSlow = 0
    private var pressureSevere = 0

    fun start(hz: Float) {
        if (active) return
        refreshHz = if (hz.isFinite() && hz in 30f..240f) hz else 60f
        active = true
        startedAtMs = SystemClock.uptimeMillis()
        lastFrameNs = 0L
        pressureWindowNs = 0L
        sampleCount = 0
        totalIntervals = 0
        slowIntervals = 0
        severeIntervals = 0
        pressureSamples = 0
        pressureSlow = 0
        pressureSevere = 0
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!active) return
        val budgetNs = (1_000_000_000.0 / refreshHz).toLong()
        if (lastFrameNs != 0L) {
            val delta = frameTimeNanos - lastFrameNs
            // Ignore screen-off, app switch, display-mode changes and long
            // pauses: those are not meaningful UI frame-time measurements.
            if (delta in 1L..250_000_000L) {
                val millis = (delta / 1_000_000.0).roundToInt().coerceAtLeast(1)
                recentIntervalsMs[sampleCount % recentIntervalsMs.size] = millis
                sampleCount++
                totalIntervals++
                pressureSamples++
                if (delta > budgetNs * 3 / 2) {
                    slowIntervals++
                    pressureSlow++
                }
                if (delta > budgetNs * 5 / 2) {
                    severeIntervals++
                    pressureSevere++
                }
            }
        }
        lastFrameNs = frameTimeNanos
        if (pressureWindowNs == 0L) pressureWindowNs = frameTimeNanos
        if (frameTimeNanos - pressureWindowNs >= 2_000_000_000L) {
            // Require sustained jank rather than a brief page transition.
            if (pressureSamples >= 20 && pressureSlow >= 8 &&
                pressureSlow * 5 >= pressureSamples &&
                pressureSevere >= 2) {
                onSustainedJank()
            }
            pressureWindowNs = frameTimeNanos
            pressureSamples = 0
            pressureSlow = 0
            pressureSevere = 0
        }
        if (active) Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop(): Summary {
        if (active) Choreographer.getInstance().removeFrameCallback(this)
        active = false
        val size = minOf(sampleCount, recentIntervalsMs.size)
        val sorted = IntArray(size) { index ->
            // Only the most recent samples if the ring buffer wrapped.
            recentIntervalsMs[(sampleCount - size + index) % recentIntervalsMs.size]
        }.apply { sort() }
        val median = if (size == 0) 0 else sorted[(size - 1) / 2]
        val p95 = if (size == 0) 0 else sorted[((size - 1) * 0.95).toInt()]
        return Summary(
            samples = size,
            measuredIntervals = totalIntervals,
            refreshRateHz = refreshHz,
            frameBudgetMs = 1000f / refreshHz,
            medianMs = median,
            p95Ms = p95,
            slowFrames = slowIntervals,
            severeFrames = severeIntervals,
            durationMs = if (startedAtMs == 0L) 0L else
                (SystemClock.uptimeMillis() - startedAtMs).coerceAtLeast(0L),
        )
    }

    companion object {
        /** Pure synthetic threshold check: not a substitute for real device jank. */
        fun syntheticSelfTest(): Boolean {
            val nominal = 1_000_000_000L / 60
            val deltas = listOf(nominal, nominal * 2, nominal * 3)
            val slow = deltas.count { it > nominal * 3 / 2 }
            val severe = deltas.count { it > nominal * 5 / 2 }
            return slow == 2 && severe == 1 &&
                Summary(3, 3, 60f, 16.666f, 16, 50, slow, severe, 50)
                    .toJson().optInt("slow_intervals") == 2
        }
    }
}

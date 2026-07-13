package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.StallSeverity
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.StallSummary

/**
 * Analyzes a sampled time series for stall (thrashing) behaviour.
 *
 * Stateless and pure, so a single instance can be shared as a singleton or
 * injected via constructor. The stall threshold is configurable to let callers
 * tune sensitivity without subclassing.
 *
 * Each sample below the threshold contributes its surrounding half-intervals of
 * time to [StallSummary.stalledTimeMillis] (trapezoidal time weighting), which
 * makes the estimate stable for uniformly sampled data and convergent with density
 * rather than dependent on raw sample count. Episodes are counted as transitions
 * from a working sample (>= threshold) into a stalled one.
 *
 * Returns a stable, empty [StallSummary] when fewer than two samples are provided
 * or the sampled span is non-positive.
 *
 * @param stallThreshold value at or above which the series is considered "working".
 */
class StallAnalyzer(
    private val stallThreshold: Double = 20.0,
) {
    fun analyze(points: List<Pair<Long, Double>>): StallSummary {
        if (points.size < 2) {
            return StallSummary(0, 0, 0.0, 0, StallSeverity.STABLE)
        }

        val sorted = points.sortedBy { it.first }
        val totalTime = (sorted.last().first - sorted.first().first)
        if (totalTime <= 0) {
            return StallSummary(0, 0, 0.0, 0, StallSeverity.STABLE)
        }

        var stalledTime = 0.0
        var episodes = 0
        val n = sorted.size
        for (i in sorted.indices) {
            val (time, value) = sorted[i]
            if (value < stallThreshold) {
                val halfBefore = if (i > 0) (time - sorted[i - 1].first) / 2.0 else 0.0
                val halfAfter = if (i < n - 1) (sorted[i + 1].first - time) / 2.0 else 0.0
                stalledTime += halfBefore + halfAfter
            }
            if (i < n - 1 && value >= stallThreshold && sorted[i + 1].second < stallThreshold) {
                episodes++
            }
        }

        val stalledFraction = (stalledTime / totalTime).coerceIn(0.0, 1.0)
        val severity = when {
            stalledFraction >= 0.30 -> StallSeverity.SEVERE
            stalledFraction >= 0.10 -> StallSeverity.VOLATILE
            else -> StallSeverity.STABLE
        }

        return StallSummary(
            stalledTimeMillis = stalledTime.toLong(),
            totalTimeMillis = totalTime,
            stalledFraction = stalledFraction,
            stallEpisodes = episodes,
            severity = severity,
        )
    }
}

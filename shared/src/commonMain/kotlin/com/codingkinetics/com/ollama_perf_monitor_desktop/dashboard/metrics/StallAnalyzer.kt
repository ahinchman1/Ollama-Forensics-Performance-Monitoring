package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.StallSeverity
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.StallSummary

/**
 * Analyzes a sampled time series for stall behavior based on workload saturation.
 *
 * This version isolates the active inference job by stripping out initial model-loading
 * dead time, beginning evaluation only when the CPU first crosses the working threshold.
 */
class StallAnalyzer(
    private val stallThreshold: Double = 20.0,
) {
    fun analyze(points: List<Pair<Long, Double>>): StallSummary {
        if (points.size < 2) {
            return StallSummary(0, 0, 0.0, 0, StallSeverity.STABLE)
        }

        // Sort chronologically
        val sortedAll = points.sortedBy { it.first }

        // LOCK ONTO ACTIVE JOB: Find the first index where the CPU actually wakes up and does work
        val activeJobStartIndex = sortedAll.indexOfFirst { it.second >= stallThreshold }

        // If the CPU never even hit the threshold, the job never actively ran
        if (activeJobStartIndex == -1 || activeJobStartIndex >= sortedAll.size - 1) {
            return StallSummary(0, 0, 0.0, 0, StallSeverity.STABLE)
        }

        // Slice the data so we ONLY evaluate from the first moment of active CPU onwards
        val activeJobPoints = sortedAll.subList(activeJobStartIndex, sortedAll.size)

        val totalActiveTime = (activeJobPoints.last().first - activeJobPoints.first().first)
        if (totalActiveTime <= 0) {
            return StallSummary(0, 0, 0.0, 0, StallSeverity.STABLE)
        }

        var totalDowntime = 0.0
        var episodes = 0
        val n = activeJobPoints.size

        for (i in activeJobPoints.indices) {
            val (time, value) = activeJobPoints[i]

            // Calculate exact downtime accumulation only within the active execution window
            if (value < stallThreshold) {
                val halfBefore = if (i > 0) (time - activeJobPoints[i - 1].first) / 2.0 else 0.0
                val halfAfter = if (i < n - 1) (activeJobPoints[i + 1].first - time) / 2.0 else 0.0
                totalDowntime += (halfBefore + halfAfter)
            }

            // Count a drop episode when transitioning from active work down to a stall state
            if (i < n - 1 && value >= stallThreshold && activeJobPoints[i + 1].second < stallThreshold) {
                episodes++
            }
        }

        val totalActiveTimeSeconds = totalActiveTime / 1000.0
        val dropFrequencyPerSecond = episodes / totalActiveTimeSeconds
        val stalledFraction = (totalDowntime / totalActiveTime).coerceIn(0.0, 1.0)
        val uptimeFraction = 1.0 - stalledFraction

        val severity = when {
            dropFrequencyPerSecond > 1.5 && stalledFraction > 0.25 -> StallSeverity.SEVERE
            dropFrequencyPerSecond > 0.8 || stalledFraction > 0.15 -> {
                if (totalActiveTimeSeconds < 8.0 || uptimeFraction > 0.87) {
                    StallSeverity.STABLE
                } else {
                    StallSeverity.VOLATILE
                }
            }
            else -> StallSeverity.STABLE
        }

        return StallSummary(
            stalledTimeMillis = totalDowntime.toLong(),
            totalTimeMillis = totalActiveTime,
            stalledFraction = stalledFraction,
            stallEpisodes = episodes,
            severity = severity,
        )
    }
}
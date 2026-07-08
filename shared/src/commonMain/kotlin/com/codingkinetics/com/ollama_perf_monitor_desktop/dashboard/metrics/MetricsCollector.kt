package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OSMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.CpuTimeSeriesSnapshot
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxSessionName

/**
 * Collects operating-system performance telemetry for an Ollama run by driving a tmux-hosted
 * metrics dashboard i.e. `btop` and parsing its captured panes.
 *
 * Implementations are platform-specific (Unix-like tooling, `tmux`, `ps`) and are not expected
 * to be portable. Telemetry values are sampled over time; callers typically start the dashboard,
 * poll [parseBtopData] on a periodic coroutine, then read the [getPeakMetricsCollected] peak
 * snapshot once the job completes.
 */
interface MetricsCollector {
    /**
     * Captures the raw text content of a tmux pane.
     * @param targetPane tmux pane target i.e. `model_profiling_session:0.0`.
     * @return raw pane text, or an error marker string if capture fails.
     */
    fun captureMetricsInWindowPane(targetPane: String = "${tmuxSessionName}:0.0"): String

    /** Launches the tmux metrics dashboard session (and any split GPU/telemetry panes). */
    fun startMetricsDashboard()

    /** Tears down the tmux metrics dashboard session. */
    fun stopMetricsDashboard()

    /** Extracts the captured CPU graph lines (braille rendering) from raw btop output. */
    fun extractCpuGraph(rawBtopOutput: String): List<String>

    /**
     * Parses the current btop pane into structured [OSMetrics].
     * @return the parsed metrics, or [Result.Failure] when the pane could not be read.
     */
    suspend fun parseBtopData(): Result<OSMetrics>

    /**
     * Returns the peak [OSMetrics] observed across all [parseBtopData] samples since the last
     * [resetPeakMetrics].
     */
    fun getPeakMetricsCollected(): OSMetrics

    /** Returns the time-series CPU snapshots captured so far. */
    fun getCpuTimeSeriesSnapshots(): List<CpuTimeSeriesSnapshot>

    /** Clears the peak metrics snapshot. */
    fun resetPeakMetrics()

    /** Clears the captured time-series snapshots. */
    fun resetTimeSeriesSnapshots()

    /**
     * Resets all collected state (peak snapshot and time series) together. Convenience for callers
     * that want a clean slate before/after a run instead of resetting each independently.
     */
    fun resetCollectedMetrics()
}

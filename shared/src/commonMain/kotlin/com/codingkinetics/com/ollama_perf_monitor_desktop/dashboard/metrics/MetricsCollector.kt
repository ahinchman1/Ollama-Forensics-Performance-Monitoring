package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OSMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.CpuTimeSeriesSnapshot
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxSessionName

interface MetricsCollector {
    fun captureMetricsInWindowPane(targetPane: String = "${tmuxSessionName}:0.0"): String
    fun startMetricsDashboard()
    fun stopMetricsDashboard()
    fun extractCpuGraph(rawBtopOutput: String): List<String>
    suspend fun parseBtopData(): Result<OSMetrics>

    fun getPeakMetricsCollected(): OSMetrics

    fun getCpuTimeSeriesSnapshots(): List<CpuTimeSeriesSnapshot>

    fun resetPeakMetrics()
    fun resetTimeSeriesSnapshots()
}

package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.metrics

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OSMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.tmuxSessionName
import com.codingkinetics.com.ollama_perf_monitor_desktop.util.Result

interface MetricsCollector {
    fun captureMetricsInWindowPane(targetPane: String = "${tmuxSessionName}:0.0"): String
    fun startMetricsDashboard()
    fun stopStopDashboard()
    fun extractCpuGraph(rawBtopOutput: String): List<String>
    fun parseBtopData(): Result<OSMetrics>

    fun getPeakMetricsCollected(): OSMetrics

    fun resetPeakMetrics()
}

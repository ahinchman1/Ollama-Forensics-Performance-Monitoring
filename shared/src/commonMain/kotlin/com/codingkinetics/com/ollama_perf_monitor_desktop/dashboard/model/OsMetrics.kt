package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model

data class OSMetrics(
    val temperature: Int,
    val processCpuConsumption: Long,
    val cpuTelemetry: String,
    val cores: List<Core> = listOf(),
    val cpuGraph: List<String> = listOf(),
    val threadCount: Int = 0,
)

data class Core(
    val name: String,
    val temperature: Int,
)
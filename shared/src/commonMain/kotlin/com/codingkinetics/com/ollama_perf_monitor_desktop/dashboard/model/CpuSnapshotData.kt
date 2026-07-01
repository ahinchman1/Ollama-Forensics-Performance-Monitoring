package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model

data class CpuSnapshotData(
    val globalTemperature: Int,
    val processCpu: Long,
    val cores: List<Core>,
)

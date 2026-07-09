package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model

data class CpuTimeSeriesSnapshot(
    val timestampMillis: Long,
    val cpuConsumption: Double,
    val aggregateCpuConsumption: Double,
    val temperature: Int,
    val threadCount: Int,
)

data class TokenTimeSeriesSnapshot(
    val timestampMillis: Long,
    val cumulativePromptTokens: Long,
    val cumulativeGeneratedTokens: Long,
)

data class ScenarioTimeSeries(
    val cpuSnapshots: List<CpuTimeSeriesSnapshot> = emptyList(),
    val tokenSnapshots: List<TokenTimeSeriesSnapshot> = emptyList(),
    val scenarioId: String = "",
)

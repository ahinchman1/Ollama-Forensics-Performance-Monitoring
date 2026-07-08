package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model

/**
 * One sampled point in the CPU telemetry time series.
 *
 * @param timestampMillis epoch milliseconds when the sample was taken.
 * @param cpuConsumption per-process CPU % of the Ollama server (0-100+).
 * @param aggregateCpuConsumption sum of all core CPU % (can exceed 100%).
 * @param temperature core package temperature in °C.
 * @param threadCount live Ollama process thread count.
 */
data class CpuTimeSeriesSnapshot(
    val timestampMillis: Long,
    val cpuConsumption: Double,
    val aggregateCpuConsumption: Double,
    val temperature: Int,
    val threadCount: Int,
)

/**
 * One sampled point in the token-count time series.
 *
 * @param timestampMillis epoch milliseconds when the sample was taken.
 * @param cumulativePromptTokens running total of prompt-eval tokens.
 * @param cumulativeGeneratedTokens running total of generated tokens.
 */
data class TokenTimeSeriesSnapshot(
    val timestampMillis: Long,
    val cumulativePromptTokens: Long,
    val cumulativeGeneratedTokens: Long,
)

/**
 * Container holding the CPU and token time series captured for a single benchmark scenario.
 *
 * @param cpuSnapshots ordered CPU samples for the scenario.
 * @param tokenSnapshots ordered token samples for the scenario.
 * @param scenarioId identifier of the scenario these snapshots belong to.
 */
data class ScenarioTimeSeries(
    val cpuSnapshots: List<CpuTimeSeriesSnapshot> = emptyList(),
    val tokenSnapshots: List<TokenTimeSeriesSnapshot> = emptyList(),
    val scenarioId: String = "",
)

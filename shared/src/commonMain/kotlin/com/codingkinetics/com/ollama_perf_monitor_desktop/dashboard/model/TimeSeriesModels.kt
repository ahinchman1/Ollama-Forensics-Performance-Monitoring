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
 * Severity classification of pipeline stalls derived from a stall analysis.
 *
 * This is intentionally decoupled from Compose so the same analysis can be unit
 * tested and reused by non-UI consumers.
 */
enum class StallSeverity {
    STABLE,
    VOLATILE,
    SEVERE,
}

/**
 * Summary of pipeline-stall behaviour computed from a sampled time series.
 *
 * Unlike a naive "average jump between samples" metric, every field here is
 * normalised against the real elapsed time between samples rather than the raw
 * sample count, so the estimate is stable for uniformly sampled data and converges
 * toward the true duty cycle as sampling density increases. It is a trapezoidal
 * approximation of time-below-threshold, so it is not perfectly invariant across
 * very different sampling rates - that is an inherent limit of point samples, not
 * a bug. For an inference pipeline that is GPU-bound, a low CPU % is *expected and
 * healthy*; a stall is specifically a sustained period where the CPU drops below
 * [stallThreshold] (e.g. blocked on locks or
 * I/O). The dominant signal is [stalledFraction] (how much of the run was spent
 * stalled), with [stallEpisodes] reported as supplementary context.
 *
 * @param stalledTimeMillis cumulative milliseconds spent below the threshold.
 * @param totalTimeMillis total span covered by the samples (last - first timestamp).
 * @param stalledFraction fraction of [totalTimeMillis] spent stalled, in [0, 1].
 * @param stallEpisodes number of times the series transitioned from working to stalled.
 * @param severity derived classification.
 */
data class StallSummary(
    val stalledTimeMillis: Long,
    val totalTimeMillis: Long,
    val stalledFraction: Double,
    val stallEpisodes: Int,
    val severity: StallSeverity,
)

/**
 * Container holding the CPU and token time series captured for a single benchmark scenario.
 * @param cpuSnapshots ordered CPU samples for the scenario.
 * @param tokenSnapshots ordered token samples for the scenario.
 * @param scenarioId identifier of the scenario these snapshots belong to.
 */
data class ScenarioTimeSeries(
    val cpuSnapshots: List<CpuTimeSeriesSnapshot> = emptyList(),
    val tokenSnapshots: List<TokenTimeSeriesSnapshot> = emptyList(),
    val scenarioId: String = "",
)

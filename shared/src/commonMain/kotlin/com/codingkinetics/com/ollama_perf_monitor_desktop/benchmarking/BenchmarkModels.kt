package com.codingkinetics.com.ollama_perf_monitor_desktop.benchmarking

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OSMetrics
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.ScenarioTimeSeries

/**
 * Per-scenario benchmark result.
 *
 * Durations are **nanoseconds** (raw, from the Ollama API); speeds are **tokens/second**;
 * [hallucinationIndex] and [faithfulnessScore] are **derived 0.0–1.0** scores from the Groq/Ragas
 * evaluation (near 0.5 indicates a fallback). [timestamp] is a human-readable date string.
 *
 * @param generatedTokens tokens produced by the model (`eval_count`).
 * @param promptTokens tokens evaluated from the prompt (`prompt_eval_count`).
 * @param totalDurationNanos total request duration (ns).
 * @param generationDurationNanos generation duration (ns).
 * @param loadDurationNanos model-weights load duration (ns).
 * @param promptIngestionSpeed prompt-eval velocity in **tokens/second**, derived from
 *   [promptTokens] / prompt-eval duration.
 * @param tokenGenerationSpeed generation velocity in **tokens/second**, derived from
 *   [generatedTokens] / generation duration.
 */
data class BenchmarkScenarioResult(
    val scenarioId: String,
    val scenarioName: String,
    val prompt: String,
    val response: String,
    val generatedTokens: Long,
    val promptTokens: Long,
    val totalDurationNanos: Long,
    val generationDurationNanos: Long,
    val loadDurationNanos: Long,
    val promptIngestionSpeed: Double,
    val tokenGenerationSpeed: Double,
    val hallucinationIndex: Double,
    val faithfulnessScore: Double,
    val timestamp: String,
    val osMetrics: OSMetrics,
    val timeSeries: ScenarioTimeSeries = ScenarioTimeSeries(),
) {
    val scenarioLabel: String
        get() = "Scenario $scenarioId ($scenarioName)"

    val formattedTotalDuration: String
        get() = "%.1fs".format(totalDurationNanos / 1_000_000_000.0)

    val formattedGenerationSpeed: String
        get() = "%.2f t/s".format(tokenGenerationSpeed)

    val formattedIngestionSpeed: String
        get() = "%.2f t/s".format(promptIngestionSpeed)
}

/**
 * Aggregate report across all benchmark scenarios.
 *
 * Aggregate CPU/temperature values are **peak** readings across scenarios; speed averages are
 * arithmetic means over the per-scenario results. [toMarkdown] renders a human-readable report
 * (used by the benchmark report exporter).
 */
data class BenchmarkSuiteReport(
    val results: List<BenchmarkScenarioResult>,
    val timestamp: String,
) {
    val averagePromptIngestionSpeed: Double
        get() = results.map { it.promptIngestionSpeed }.average()

    val averageTokenGenerationSpeed: Double
        get() = results.map { it.tokenGenerationSpeed }.average()

    val meanTotalExecutionTime: Double
        get() = results.map { it.totalDurationNanos }.average() / 1_000_000_000.0

    /** Peak per-process CPU % (from `ps`) across scenarios. */
    val peakCpuConsumption: Long
        get() = results.maxOfOrNull { it.osMetrics.processCpuConsumption } ?: 0L

    /** Peak per-process CPU % as reported by btop across scenarios. */
    val peakBtopCpuConsumption: Double
        get() = results.maxOfOrNull { it.osMetrics.btopProcessCpuConsumption } ?: 0.0

    /** Peak aggregate (sum-of-cores) CPU % across scenarios. */
    val peakAggregateCpuConsumption: Double
        get() = results.maxOfOrNull { it.osMetrics.aggregateCpuConsumption } ?: 0.0

    /** Peak core package temperature (°C) across scenarios. */
    val peakTemperature: Int
        get() = results.maxOfOrNull { it.osMetrics.temperature } ?: 0

    /** Peak live thread count across scenarios. */
    val peakThreadCount: Int
        get() = results.maxOfOrNull { it.osMetrics.threadCount } ?: 0

    val threadSpikeDetected: Boolean
        get() = peakThreadCount > 50

    val threadSpikeSeverity: String
        get() = when {
            peakThreadCount > 100 -> "CRITICAL"
            peakThreadCount > 50 -> "WARNING"
            else -> "NORMAL"
        }

    /** Renders the report as Markdown for export to disk. */
    fun toMarkdown(): String = buildString {
        appendLine("# OLLAMA NATIVE FORENSICS PERFORMANCE REPORT")
        appendLine("Date: $timestamp")
        appendLine()

        appendLine("## Core Engine Velocity Baseline")
        appendLine("- Average Prompt Ingestion Speed : ${String.format("%.1f", averagePromptIngestionSpeed)} t/s")
        appendLine("- Average Token Generation Speed: ${String.format("%.2f", averageTokenGenerationSpeed)} t/s")
        appendLine("- Mean Total Execution Time     : ${String.format("%.1f", meanTotalExecutionTime)}s")
        appendLine()

        appendLine("## Alignment & Hallucination Metrics")
        results.forEach { result ->
            val fallbackMarker = if (result.hallucinationIndex in 0.49..0.51) {
                " (Fallback Triggered)"
            } else ""
            appendLine("- ${result.scenarioLabel}: Hallucination ${String.format("%.4f", result.hallucinationIndex)} | " +
                    "Faithfulness ${String.format("%.4f", result.faithfulnessScore)}$fallbackMarker")
        }
        appendLine()

        appendLine("## Physical Hardware Strain Profile")
        appendLine("- Peak CPU Consumption (ps / absolute) : ${peakCpuConsumption}%")
        appendLine("- Peak CPU Load (btop / normalized)    : ${String.format("%.1f", peakBtopCpuConsumption)}%")
        appendLine("- Peak Aggregate CPU (sum of cores)    : ${String.format("%.1f", peakAggregateCpuConsumption)}%")
        appendLine("- Peak Core Package Temp      : ${peakTemperature}°C")
        val coreDelta = results.flatMap { it.osMetrics.cores }
            .let { cores ->
                val max = cores.maxOfOrNull { it.temperature } ?: 0
                val min = cores.minOfOrNull { it.temperature } ?: 0
                "$max°C vs ${min}°C"
            }
        appendLine("- Active Core Thermal Delta   : $coreDelta")
        appendLine()

        appendLine("## Thread Concurrency Profile")
        appendLine("- Peak Thread Count           : $peakThreadCount")
        appendLine("- Thread Spike Detected       : ${if (threadSpikeDetected) "YES ($threadSpikeSeverity)" else "No"}")
        appendLine()

        appendLine("## Detailed Scenario Results")
        results.forEach { result ->
            appendLine("### ${result.scenarioLabel}")
            appendLine("- Prompt Tokens: ${result.promptTokens}")
            appendLine("- Generated Tokens: ${result.generatedTokens}")
            appendLine("- Total Duration: ${result.formattedTotalDuration}")
            appendLine("- Generation Speed: ${result.formattedGenerationSpeed}")
            appendLine("- Ingestion Speed: ${result.formattedIngestionSpeed}")
            appendLine("- Aggregate CPU Load: ${String.format("%.1f", result.osMetrics.aggregateCpuConsumption)}%")
            appendLine()
        }
    }
}
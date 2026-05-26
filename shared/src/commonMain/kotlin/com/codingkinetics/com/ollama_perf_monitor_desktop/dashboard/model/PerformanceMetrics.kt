package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model

import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.nanosToSeconds
import java.util.Locale

data class PerformanceMetrics(
    val osMetrics: BtopMetrics,
    val loadDurationNanos: Long,              // Keep raw for precise calculations/sorting
    val totalDurationNanos: Long,             // Keep raw for precise calculations/sorting
    val done: Boolean,
    val doneReason: String,
    val promptTokensCount: Long,
    val promptEvaluationDurationNanos: Long,
    val generatedTokensCount: Long,           // Translates from 'tabCount'
    val generationDurationNanos: Long,        // Translates from 'tabDuration'
    val hallucinationScore: String = ""       // Ready for your future Ragas integration
) {
// --- CORE NUMERIC TELEMETRY MATH ---

    /** Velocity of token generation (tokens/sec) */
    val tokensPerSecond: Double
        get() = if (generationDurationNanos > 0) {
            generatedTokensCount.toDouble() / generationDurationNanos.nanosToSeconds()
        } else 0.0

    /** Velocity of prompt ingestion (tokens/sec) */
    val promptIngestionSpeed: Double
        get() = if (promptEvaluationDurationNanos > 0) {
            promptTokensCount.toDouble() / promptEvaluationDurationNanos.nanosToSeconds()
        } else 0.0

    val formattedGenerationSpeed: String
        get() = String.format(Locale.US, "%.2f t/s", tokensPerSecond)

    val formattedIngestionSpeed: String
        get() = String.format(Locale.US, "%.2f t/s", promptIngestionSpeed)

    val formattedLoadDuration: String
        get() = String.format(Locale.US, "%.2fs", loadDurationNanos.nanosToSeconds())

    val formattedTotalDuration: String
        get() = String.format(Locale.US, "%.2fs", totalDurationNanos.nanosToSeconds())

}

class BtopMetrics(
    val temperature: Int,
    val processCpuConsumption: Long,
    val cpuTelemetry: String,
    val cores: List<Core> = listOf(),
    val threadCount: Int = 0, // TODO thread count and presence of leaking threads
)

data class Core(
    val name: String,
    val temperature: Int,
)
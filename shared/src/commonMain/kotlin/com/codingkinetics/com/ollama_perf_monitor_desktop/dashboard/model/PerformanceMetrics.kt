package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model

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
    // Computed properties handle the presentation layer dynamically
    val tokensPerSecond: Double
        get() = if (generationDurationNanos > 0) {
            generatedTokensCount.toDouble() / (generationDurationNanos / 1_000_000_000.0)
        } else 0.0

    val formattedGenerationSpeed: String
        get() = String.format("%.2f t/s", tokensPerSecond)
}

val PerformanceMetrics.calculatedTokensPerSecond: Double
    get() = if (timeSpentGeneratingResponseTokens > 0) {
        tokensGenerated.toDouble() / timeSpentGeneratingResponseTokens.nanosToSeconds()
    } else {
        0.0
    }



class BtopMetrics(
    val cores: List<Core>,
    val temperature: Int,
    val processCpuConsumption: Long,
    val cpuTelemetry: String,
    val threadCount: String,
)

data class Core(
    val name: String,
    val temperature: Int,
)
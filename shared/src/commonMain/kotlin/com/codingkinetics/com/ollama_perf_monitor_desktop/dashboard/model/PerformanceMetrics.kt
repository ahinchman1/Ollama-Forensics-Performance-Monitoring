package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model

import com.codingkinetics.com.ollama_perf_monitor_desktop.util.nanosToSeconds
import java.util.Locale

/**
 * Aggregated performance metrics for one Ollama generation job, combining model timing/token
 * data with captured OS telemetry and the forensic evaluation result.
 *
 * All `*Nanos` durations are **nanoseconds** sourced from the Ollama API kept raw for precise
 * sorting/calculation; use the `formatted*` properties for display. Token counts are exact,
 * measured by Ollama. [hallucinationIndex] and [faithfulnessScore] are **derived 0.0–1.0**
 * from the Groq/Ragas evaluation; when evaluation is disabled or falls back they default to 0.0
 * - a value near 0.5 indicates the heuristic fallback was triggered.
 *
 * If OS telemetry cannot be collected, [osMetrics] falls back to a zeroed snapshot (0 °C, 0% CPU,
 * empty cores) rather than failing the run — so the CPU/temperature fields may be 0 when `btop`/
 * `tmux` are unavailable.
 */
data class PerformanceMetrics(
    val prompt: String,
    val output: String,
    val osMetrics: OSMetrics,
    /** Model-weights load duration in nanoseconds - Ollama `load_duration`. */
    val loadDurationNanos: Long,
    /** Total request duration in nanoseconds - Ollama `total_duration`. */
    val totalDurationNanos: Long,
    val done: Boolean,
    val doneReason: String,
    /** Prompt-evaluation token count - Ollama `prompt_eval_count`. */
    val promptTokensCount: Long,
    /** Prompt-evaluation duration in nanoseconds - Ollama `prompt_eval_duration` */
    val promptEvaluationDurationNanos: Long,
    /** Generated token count - Ollama `eval_count`. */
    val generatedTokensCount: Long,
    /** Generation duration in nanoseconds - Ollama `eval_duration`. */
    val generationDurationNanos: Long,
    /** 0.0–1.0, higher means more hallucination. Derived via Groq/Ragas evaluation. */
    val hallucinationIndex: Double = 0.0,
    /** 0.0–1.0, higher means more faithful. Derived via Groq/Ragas evaluation. */
    val faithfulnessScore: Double = 0.0,
) {

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

    val formattedPromptEvaluation: String
        get() = String.format(Locale.US, "%.2fs", promptEvaluationDurationNanos.nanosToSeconds())

    val formattedGenerationDuration: String
        get() = String.format(Locale.US, "%.2fs", generationDurationNanos.nanosToSeconds())

    override fun toString(): String {
        return """
            ====================================================================
            OLLAMA INFERENCE PERFORMANCE FORENSICS
            ====================================================================
            Pipeline Status       : Done (Reason: $doneReason)
            Total Execution Time  : $formattedTotalDuration
            
            CORE VELOCITY ENGINE METRICS:
            --------------------------------------------------------------------
            Prompt Ingestion Speed: $formattedIngestionSpeed
            Token Generation Speed: $formattedGenerationSpeed
            
            TOKEN COUNTS:
            --------------------------------------------------------------------
            Prompt Tokens In     : $promptTokensCount tokens
            Output Tokens Out     : $generatedTokensCount tokens
            Total Processed Vol   : ${promptTokensCount + generatedTokensCount} tokens
            
            SUBSYSTEM LATENCY BREAKDOWN:
            --------------------------------------------------------------------
            Model Weights Load    : $formattedLoadDuration
            Prompt Evaluation     : $formattedPromptEvaluation
            Generation Duration   : $formattedGenerationDuration
            
            DIAGNOSTICS & RESEARCH METRICS:
            --------------------------------------------------------------------
            Hallucination Index   : $hallucinationIndex
            Underlying OS Metrics : $osMetrics
            ====================================================================
        """.trimIndent()
    }
}

private fun Double.ifZero(ifZero: () -> String) = if (this == 0.0) ifZero() else toString()
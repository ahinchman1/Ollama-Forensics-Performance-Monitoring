package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model

import kotlinx.serialization.Serializable

/**
 * Full result of a single Ollama generation job.
 *
 * @param generatedText concatenated streamed output text.
 * @param completedData terminal Ollama completion payload (timing in nanoseconds, token counts).
 */
@Serializable
data class OllamaJobResult(
    val generatedText: String,
    val completedData: OllamaResponseCompletedData,
)
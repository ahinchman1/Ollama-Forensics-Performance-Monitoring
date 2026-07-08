package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single frame of the streamed Ollama `/api/generate` response.
 *
 * @param response incremental text delta emitted for this frame (empty until content arrives).
 * @param done true on the terminal frame; only then are [evalCount]/[promptEvalCount] final.
 * @param evalCount cumulative generated tokens produced so far (Ollama `eval_count`).
 * @param promptEvalCount cumulative prompt-evaluation tokens processed so far
 *   (Ollama `prompt_eval_count`).
 */
@Serializable
data class OllamaStreamChunk(
    val response: String = "",
    val done: Boolean = false,
    @SerialName("eval_count") val evalCount: Long = 0L,
    @SerialName("prompt_eval_count") val promptEvalCount: Long = 0L,
)

/**
 * Terminal completion payload from the Ollama `/api/generate` response.
 *
 * All `*Duration` fields are **nanoseconds** as reported directly by the Ollama API
 * (`total_duration`, `load_duration`, `prompt_eval_duration`, `eval_duration`). Token counts are
 * exact values measured by Ollama.
 *
 * @param model Ollama model name that produced the response.
 * @param createdAt ISO-8601 timestamp string of when generation completed.
 * @param response full generated text.
 * @param done always true on the completed payload.
 * @param doneReason Ollama termination reason (e.g. `stop`); `"unknown"` if absent.
 * @param totalDuration total request duration in nanoseconds.
 * @param loadDuration model-weights load duration in nanoseconds.
 * @param promptEvalCount prompt tokens evaluated.
 * @param promptEvalDuration prompt-evaluation duration in nanoseconds.
 * @param generatedTokenCount generated tokens (`eval_count`).
 * @param generationDuration generation duration in nanoseconds (`eval_duration`).
 */
@Serializable
data class OllamaResponseCompletedData(
    val model: String,
    @SerialName("created_at") val createdAt: String,
    val response: String,
    val done: Boolean,
    @SerialName("done_reason") val doneReason: String = "unknown",
    @SerialName("total_duration") val totalDuration: Long = 0L,
    @SerialName("load_duration") val loadDuration: Long = 0L,
    @SerialName("prompt_eval_count") val promptEvalCount: Long = 0L,
    @SerialName("prompt_eval_duration") val promptEvalDuration: Long = 0L,
    @SerialName("eval_count") val generatedTokenCount: Long,
    @SerialName("eval_duration") val generationDuration: Long,
)
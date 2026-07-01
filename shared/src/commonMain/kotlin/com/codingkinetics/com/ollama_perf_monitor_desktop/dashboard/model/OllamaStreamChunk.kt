package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaStreamChunk(
    val response: String = "",
    val done: Boolean = false,
)

// todo evaluate token count, and also use RAGAs to measure hallucination
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
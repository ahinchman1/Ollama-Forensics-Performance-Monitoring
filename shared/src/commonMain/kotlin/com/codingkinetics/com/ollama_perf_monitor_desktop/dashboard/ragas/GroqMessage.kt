package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas

import kotlinx.serialization.Serializable

@Serializable
data class GroqMessage(val role: String, val content: String)

@Serializable
data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double
)

@Serializable
data class GroqError(val error: GroqErrorDetail)

@Serializable
data class GroqErrorDetail(val message: String)

/**
 * Forensic evaluation scores for a generated response.
 *
 * @param faithfulnessScore 0.0–1.0, higher means the response is more faithful to the prompt and
 *   telemetry context. Derived from the Groq LLM judge.
 * @param hallucinationIndex 0.0–1.0, higher means more hallucination. Derived as
 *   `1.0 - faithfulnessScore`. A value near 0.5 indicates the heuristic fallback was used.
 */
@Serializable
data class EvaluationResult(
    val faithfulnessScore: Double,
    val hallucinationIndex: Double,
)

@Serializable
data class GroqResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: GroqMessage
)
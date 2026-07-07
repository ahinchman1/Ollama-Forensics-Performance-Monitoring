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
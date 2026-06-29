package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ragas

import kotlinx.serialization.Serializable

@Serializable
data class GroqMessage(val role: String, val content: String)

@Serializable
data class GroqRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.0
)

@Serializable
data class EvaluationResult(
    val faithfulnessScore: Double,
    val hallucinationIndex: Double,
)
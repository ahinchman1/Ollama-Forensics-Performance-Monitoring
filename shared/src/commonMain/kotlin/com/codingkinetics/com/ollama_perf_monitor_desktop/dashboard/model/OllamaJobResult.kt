package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model

import kotlinx.serialization.Serializable

@Serializable
data class OllamaJobResult(
    val generatedText: String,
    val completedData: OllamaResponseCompletedData,
)
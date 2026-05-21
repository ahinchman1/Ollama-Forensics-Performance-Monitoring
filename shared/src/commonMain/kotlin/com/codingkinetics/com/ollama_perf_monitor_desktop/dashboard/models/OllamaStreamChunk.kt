package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.models
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaStreamChunk(
    @SerialName("response") val response: String = "",
    @SerialName("done") val done: Boolean = false
)
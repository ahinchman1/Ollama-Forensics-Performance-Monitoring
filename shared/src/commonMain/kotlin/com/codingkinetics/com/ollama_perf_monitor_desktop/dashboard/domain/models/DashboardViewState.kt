package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.domain.models

import androidx.compose.runtime.Immutable

@Immutable
sealed interface DashboardViewState {

    data object Idle : DashboardViewState
    data class ActiveJob(
        val statusMessage: String = "Pipeline is starting...",
        val metricsPanel: String = "Initializing btop...",
        val gpuPanel: String = "Initializing system metrics...",
        val essayText: String = "Preparing Ollama pre-fill..."
    ) : DashboardViewState
    data class Error(
        val errorMessage: String,
        val tmuxPath: String,
        val btopPath: String,
        val ollamaPath: String,
    ) : DashboardViewState

    data class CompletedJob(
        val statusMessage: String,
        val metricsPanel: String,
        val gpuPanel: String,
        val essayText: String,
        val completedData: OllamaResponseCompletedData,
    ): DashboardViewState
}
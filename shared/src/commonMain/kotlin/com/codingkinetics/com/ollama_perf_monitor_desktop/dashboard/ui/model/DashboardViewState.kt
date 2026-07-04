package com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.ui.model

import androidx.compose.runtime.Immutable
import com.codingkinetics.com.ollama_perf_monitor_desktop.benchmarking.BenchmarkSuiteReport
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.OllamaResponseCompletedData
import com.codingkinetics.com.ollama_perf_monitor_desktop.dashboard.model.PerformanceMetrics

@Immutable
sealed interface DashboardViewState {

    data object Idle : DashboardViewState
    data class ActiveJob(
        val statusMessage: String = "Pipeline is starting...",
        val metricsPanel: String = "Initializing btop...",
        val gpuPanel: String = "Initializing system metrics...",
        val essayText: String = "Preparing Ollama pre-fill..."
    ) : DashboardViewState

    sealed interface PipelineFailure : DashboardViewState {
        val errorMessage: String
        val installHint: String

        data class MissingDependency(
            override val errorMessage: String,
            override val installHint: String,
        ) : PipelineFailure

        data class ExecutionError(
            override val errorMessage: String,
            override val installHint: String = ""
        ) : PipelineFailure
    }

    data class CompletedJob(
        val statusMessage: String,
        val metricsPanel: String,
        val gpuPanel: String,
        val essayText: String,
        val completedData: PerformanceMetrics,
    ): DashboardViewState

    data class BenchmarkResults(
        val report: BenchmarkSuiteReport,
    ): DashboardViewState
}